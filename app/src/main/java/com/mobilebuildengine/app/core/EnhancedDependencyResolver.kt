package com.mobilebuildengine.app.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 依賴解析器：支援 Maven 下載、.aar/.jar 區分、POM 遞迴與 dependencyManagement/BOM。
 */
class EnhancedDependencyResolver(private val cacheDir: File) {

    private val MAVEN_URL = "https://repo1.maven.org/maven2"
    private val DOWNLOAD_RETRIES = 2

    data class Dependency(val group: String, val artifact: String, val version: String)
    private data class ManagedDependency(
        val group: String,
        val artifact: String,
        val version: String,
        val scope: String,
        val type: String
    )
    private data class PomModel(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val parent: Dependency?,
        val properties: Map<String, String>,
        val dependencies: List<RawDependency>,
        val managedDependencies: List<ManagedDependency>
    )
    private data class Relocation(val groupId: String, val artifactId: String, val version: String)
    private data class RawDependency(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val scope: String,
        val optional: Boolean,
        val type: String,
        val classifier: String,
        val exclusions: Set<String>
    )

    fun resolveWithTransitives(group: String, artifact: String, version: String): List<File> {
        val cacheKey = "$group:$artifact:$version"
        val cacheFile = File(cacheDir, "${cacheKey.replace(":", "_")}.lock")
        if (cacheFile.exists()) {
            try {
                val cachedPaths = cacheFile.readLines()
                val files = cachedPaths.map { File(it) }.filter { it.exists() }
                if (files.isNotEmpty()) return files
            } catch (_: Exception) {}
        }

        val resolvedArtifacts = mutableListOf<File>()
        val visited = mutableSetOf<String>()
        val managedVersions = mutableMapOf<String, String>()
        val chosenVersions = mutableMapOf<String, Pair<Int, String>>()

        fun resolveRecursive(depGroup: String, depArtifact: String, depVersion: String, depth: Int, inheritedExclusions: Set<String>) {
            if (depGroup.isBlank() || depArtifact.isBlank() || depVersion.isBlank()) return
            val ga = "$depGroup:$depArtifact"
            if (ga in inheritedExclusions) return
            val existing = chosenVersions[ga]
            if (existing != null) {
                if (existing.first < depth) return
                if (existing.first == depth && existing.second != depVersion) return
            } else {
                chosenVersions[ga] = depth to depVersion
            }

            val key = "$depGroup:$depArtifact:$depVersion"
            if (!visited.add(key)) return

            resolvedArtifacts.addAll(downloadAndExtract(depGroup, depArtifact, depVersion, ""))

            val pomFile = downloadPom(depGroup, depArtifact, depVersion) ?: return
            val relocation = parseRelocation(pomFile)
            if (relocation != null) {
                resolveRecursive(relocation.groupId, relocation.artifactId, relocation.version, depth, inheritedExclusions)
                return
            }
            val pom = parsePomModel(pomFile)
            mergeManagedDependencies(pom, managedVersions)
            val resolvedDeps = resolvePomDependencies(pom, managedVersions)
            resolvedDeps.forEach { child ->
                val childGa = "${child.group}:${child.artifact}"
                if (childGa !in inheritedExclusions) {
                    resolveRecursive(child.group, child.artifact, child.version, depth + 1, inheritedExclusions + child.exclusions)
                }
            }
        }

        resolveRecursive(group, artifact, version, 0, emptySet())
        val result = resolvedArtifacts.distinctBy { it.absolutePath }
        
        // 寫入快取
        try {
            cacheFile.writeText(result.joinToString("\n") { it.absolutePath })
        } catch (_: Exception) {}
        
        return result
    }

    fun parsePomDependencies(pomFile: File): List<Dependency> {
        val pom = parsePomModel(pomFile)
        val managed = mutableMapOf<String, String>()
        mergeManagedDependencies(pom, managed)
        return resolvePomDependencies(pom, managed)
            .map { Dependency(it.group, it.artifact, it.version) }
    }


    private data class ResolvedDep(val group: String, val artifact: String, val version: String, val exclusions: Set<String>)

    private fun parsePomModel(pomFile: File): PomModel {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
        val root = doc.documentElement

        fun childText(el: org.w3c.dom.Element, name: String): String {
            val nodes = el.getElementsByTagName(name)
            return if (nodes.length > 0) nodes.item(0).textContent.orEmpty() else ""
        }

        var projectGroupId = childText(root, "groupId")
        val projectArtifactId = childText(root, "artifactId")
        var projectVersion = childText(root, "version")

        var parent: Dependency? = null
        val parentNodes = root.getElementsByTagName("parent")
        if (parentNodes.length > 0) {
            val p = parentNodes.item(0) as org.w3c.dom.Element
            val pg = childText(p, "groupId")
            val pa = childText(p, "artifactId")
            val pv = childText(p, "version")
            if (pg.isNotBlank() && pa.isNotBlank() && pv.isNotBlank()) {
                parent = Dependency(pg, pa, pv)
            }
        }

        val mergedProperties = mutableMapOf<String, String>()
        if (parent != null) {
            val parentPom = downloadPom(parent.group, parent.artifact, parent.version)
            if (parentPom != null) {
                val parentModel = parsePomModel(parentPom)
                mergedProperties.putAll(parentModel.properties)
                if (projectGroupId.isBlank()) projectGroupId = parentModel.groupId
                if (projectVersion.isBlank()) projectVersion = parentModel.version
            }
        }

        val propNodes = root.getElementsByTagName("properties")
        if (propNodes.length > 0) {
            val propsEl = propNodes.item(0) as org.w3c.dom.Element
            val children = propsEl.childNodes
            for (i in 0 until children.length) {
                val n = children.item(i)
                if (n is org.w3c.dom.Element) mergedProperties[n.tagName] = n.textContent.orEmpty()
            }
        }

        mergedProperties["project.groupId"] = projectGroupId
        mergedProperties["project.artifactId"] = projectArtifactId
        mergedProperties["project.version"] = projectVersion
        mergedProperties["groupId"] = projectGroupId
        mergedProperties["artifactId"] = projectArtifactId
        mergedProperties["version"] = projectVersion
        mergedProperties["parent.version"] = parent?.version.orEmpty()

        val managedDependencies = mutableListOf<ManagedDependency>()
        val dependencyManagementNodes = root.getElementsByTagName("dependencyManagement")
        if (dependencyManagementNodes.length > 0) {
            val dm = dependencyManagementNodes.item(0) as org.w3c.dom.Element
            val deps = dm.getElementsByTagName("dependency")
            for (i in 0 until deps.length) {
                val d = deps.item(i) as org.w3c.dom.Element
                managedDependencies.add(
                    ManagedDependency(
                        substituteProperties(childText(d, "groupId"), mergedProperties),
                        substituteProperties(childText(d, "artifactId"), mergedProperties),
                        substituteProperties(childText(d, "version"), mergedProperties),
                        childText(d, "scope"),
                        childText(d, "type").ifBlank { "jar" }
                    )
                )
            }
        }

        val dependencies = mutableListOf<RawDependency>()
        val depsNodes = root.getElementsByTagName("dependencies")
        for (i in 0 until depsNodes.length) {
            val depsEl = depsNodes.item(i) as org.w3c.dom.Element
            val parentTag = (depsEl.parentNode as? org.w3c.dom.Element)?.tagName.orEmpty()
            if (parentTag == "dependencyManagement") continue
            val deps = depsEl.getElementsByTagName("dependency")
            for (j in 0 until deps.length) {
                val d = deps.item(j) as org.w3c.dom.Element
                dependencies.add(
                    RawDependency(
                        groupId = substituteProperties(childText(d, "groupId"), mergedProperties),
                        artifactId = substituteProperties(childText(d, "artifactId"), mergedProperties),
                        version = substituteProperties(childText(d, "version"), mergedProperties),
                        scope = childText(d, "scope"),
                        optional = childText(d, "optional").equals("true", ignoreCase = true),
                        type = childText(d, "type").ifBlank { "jar" },
                        classifier = childText(d, "classifier"),
                        exclusions = parseExclusions(d, mergedProperties)
                    )
                )
            }
        }

        return PomModel(
            groupId = projectGroupId,
            artifactId = projectArtifactId,
            version = projectVersion,
            parent = parent,
            properties = mergedProperties,
            dependencies = dependencies,
            managedDependencies = managedDependencies
        )
    }

    private fun mergeManagedDependencies(model: PomModel, managedVersions: MutableMap<String, String>) {
        model.managedDependencies
            .filter { !(it.scope.equals("import", ignoreCase = true) && it.type.equals("pom", ignoreCase = true)) }
            .forEach { dep ->
                val key = "${substituteProperties(dep.group, model.properties)}:${substituteProperties(dep.artifact, model.properties)}"
                val value = substituteProperties(dep.version, model.properties)
                if (key != ":" && value.isNotBlank()) managedVersions[key] = value
            }

        // BOM import support
        model.managedDependencies
            .filter {
                it.version.isNotBlank() &&
                    it.scope.equals("import", ignoreCase = true) &&
                    it.type.equals("pom", ignoreCase = true)
            }
            .forEach { dep ->
                val bomPom = downloadPom(
                    substituteProperties(dep.group, model.properties),
                    substituteProperties(dep.artifact, model.properties),
                    substituteProperties(dep.version, model.properties)
                )
                if (bomPom != null) {
                    val bomModel = parsePomModel(bomPom)
                    bomModel.managedDependencies.forEach { m ->
                        val k = "${substituteProperties(m.group, bomModel.properties)}:${substituteProperties(m.artifact, bomModel.properties)}"
                        val v = substituteProperties(m.version, bomModel.properties)
                        if (k != ":" && v.isNotBlank()) managedVersions[k] = v
                    }
                }
            }
    }


    private fun parseExclusions(depEl: org.w3c.dom.Element, properties: Map<String, String>): Set<String> {
        val out = mutableSetOf<String>()
        val exNodes = depEl.getElementsByTagName("exclusion")
        for (k in 0 until exNodes.length) {
            val ex = exNodes.item(k) as org.w3c.dom.Element
            val g = substituteProperties(ex.getElementsByTagName("groupId").item(0)?.textContent.orEmpty(), properties)
            val a = substituteProperties(ex.getElementsByTagName("artifactId").item(0)?.textContent.orEmpty(), properties)
            if (g.isNotBlank() && a.isNotBlank()) out.add("$g:$a")
        }
        return out
    }

    private fun resolvePomDependencies(model: PomModel, managedVersions: Map<String, String>): List<ResolvedDep> {
        return model.dependencies.mapNotNull { raw ->
            if (raw.scope == "test" || raw.optional) return@mapNotNull null
            val group = substituteProperties(raw.groupId, model.properties)
            val artifact = substituteProperties(raw.artifactId, model.properties)
            val managedKey = "$group:$artifact"
            val rawVersion = substituteProperties(raw.version, model.properties)
            val version = if (rawVersion.isNotBlank()) rawVersion else managedVersions[managedKey].orEmpty()
            if (group.isBlank() || artifact.isBlank() || version.isBlank()) return@mapNotNull null
            if (raw.classifier.isNotBlank()) {
                downloadAndExtract(group, artifact, version, raw.classifier)
            }
            ResolvedDep(group, artifact, version, raw.exclusions)
        }
    }

    private fun substituteProperties(value: String, properties: Map<String, String>): String {
        val regex = Regex("\\$\\{([^}]+)}")
        return regex.replace(value) { match -> properties[match.groupValues[1]] ?: match.value }
    }

    fun downloadAndExtract(group: String, artifact: String, version: String, classifier: String = ""): List<File> {
        val extractedFiles = mutableListOf<File>()
        val resolvedVersion = resolveSnapshotVersion(group, artifact, version)

        val aarFile = File(cacheDir, "$artifact-$resolvedVersion.aar")
        val jarFile = File(cacheDir, "$artifact-$resolvedVersion.jar")

        if (!aarFile.exists() && !jarFile.exists()) {
            val suffix = if (classifier.isBlank()) "" else "-$classifier"
            val baseUrl = "$MAVEN_URL/${group.replace(".", "/")}/$artifact/$version/$artifact-$resolvedVersion$suffix"
            if (!downloadArtifact("$baseUrl.aar", aarFile)) {
                downloadArtifact("$baseUrl.jar", jarFile)
            }
        }

        val targetFile = when {
            aarFile.exists() -> aarFile
            jarFile.exists() -> jarFile
            else -> null
        }

        if (targetFile != null && targetFile.exists()) {
            if (targetFile.extension == "aar") {
                val extractDir = File(cacheDir, "$artifact-$resolvedVersion")
                if (!extractDir.exists()) {
                    extractDir.mkdirs()
                    ZipInputStream(FileInputStream(targetFile)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outputFile = File(extractDir, entry.name)
                            if (entry.isDirectory) {
                                outputFile.mkdirs()
                            } else {
                                outputFile.parentFile.mkdirs()
                                FileOutputStream(outputFile).use { fos -> zis.copyTo(fos) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                val classesJar = File(extractDir, "classes.jar")
                if (classesJar.exists()) extractedFiles.add(classesJar)
            } else {
                extractedFiles.add(targetFile)
            }
        }

        return extractedFiles
    }

    private fun downloadArtifact(url: String, targetFile: File): Boolean {
        repeat(DOWNLOAD_RETRIES + 1) { attempt ->
            try {
                URL(url).openStream().use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (targetFile.exists() && targetFile.length() > 0L) {
                    if (verifySha1IfPresent(url, targetFile)) return true
                }
            } catch (_: Exception) {
                if (attempt == DOWNLOAD_RETRIES) return false
            }
        }
        return false
    }

    private fun verifySha1IfPresent(url: String, targetFile: File): Boolean {
        return try {
            val sha1 = URL("$url.sha1").readText().trim().split(" ", "\n", "\r", "\t").firstOrNull().orEmpty()
            if (sha1.isBlank()) return true
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val hash = targetFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read > 0) {
                    md.update(buffer, 0, read)
                    read = input.read(buffer)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            hash.equals(sha1, ignoreCase = true)
        } catch (_: Exception) {
            true
        }
    }

    private fun resolveSnapshotVersion(group: String, artifact: String, version: String): String {
        if (!version.endsWith("-SNAPSHOT")) return version
        val metadataUrl = "$MAVEN_URL/${group.replace(".", "/")}/$artifact/$version/maven-metadata.xml"
        return try {
            val metadata = URL(metadataUrl).readText()
            val ts = Regex("<timestamp>([^<]+)</timestamp>").find(metadata)?.groupValues?.get(1)
            val bn = Regex("<buildNumber>([^<]+)</buildNumber>").find(metadata)?.groupValues?.get(1)
            if (ts != null && bn != null) version.removeSuffix("-SNAPSHOT") + "-$ts-$bn" else version
        } catch (_: Exception) {
            version
        }
    }

    private fun parseRelocation(pomFile: File): Relocation? {
        return try {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
            val relocationNodes = doc.getElementsByTagName("relocation")
            if (relocationNodes.length == 0) return null
            val relocation = relocationNodes.item(0) as org.w3c.dom.Element
            val g = relocation.getElementsByTagName("groupId").item(0)?.textContent.orEmpty()
            val a = relocation.getElementsByTagName("artifactId").item(0)?.textContent.orEmpty()
            val v = relocation.getElementsByTagName("version").item(0)?.textContent.orEmpty()
            if (g.isBlank() || a.isBlank() || v.isBlank()) null else Relocation(g, a, v)
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadPom(group: String, artifact: String, version: String): File? {
        val resolvedVersion = resolveSnapshotVersion(group, artifact, version)
        val pomFile = File(cacheDir, "$artifact-$resolvedVersion.pom")
        if (pomFile.exists()) return pomFile

        val pomUrl = "$MAVEN_URL/${group.replace(".", "/")}/$artifact/$version/$artifact-$resolvedVersion.pom"
        return if (downloadArtifact(pomUrl, pomFile)) pomFile else null
    }
}
