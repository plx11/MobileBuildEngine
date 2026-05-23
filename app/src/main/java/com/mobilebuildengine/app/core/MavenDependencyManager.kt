package com.mobilebuildengine.app.core

import java.io.File
import java.net.URL
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Maven 依賴樹解析器：處理遞迴依賴、scope/optional 過濾與子 POM 下載。
 */
class MavenDependencyManager(private val cacheDir: File) {

    private val resolvedDeps = mutableMapOf<String, File>()
    private val managedVersions = mutableMapOf<String, String>()
    private val processedMgmtPoms = mutableSetOf<String>()
    private val chosenVersions = mutableMapOf<String, Pair<Int, String>>()

    fun getFullClasspath(rootPom: File): List<File> {
        val queue = mutableListOf(rootPom to emptySet<String>())
        val allJars = mutableListOf<File>()
        val processedPoms = mutableSetOf<String>()

        while (queue.isNotEmpty()) {
            val (pom, inheritedExclusions) = queue.removeAt(0)
            if (!pom.exists() || processedPoms.contains(pom.absolutePath)) continue
            processedPoms.add(pom.absolutePath)

            val depth = pom.absolutePath.count { it == '/' }
            val deps = parsePom(pom, depth)
            for (dep in deps) {
                val ga = "${dep.groupId}:${dep.artifactId}"
                if (ga in inheritedExclusions) continue
                val jar = download(dep)
                if (jar != null && !allJars.contains(jar)) {
                    allJars.add(jar)
                }
                val childPom = downloadPom(dep)
                if (childPom.exists()) {
                    queue.add(childPom to (inheritedExclusions + dep.exclusions))
                }
            }
        }
        return allJars
    }

    private fun parsePom(pom: File, depth: Int): List<Dependency> {
        val deps = mutableListOf<Dependency>()
        val properties = mutableMapOf<String, String>()
        try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
            val root = doc.documentElement
            fun directChildText(el: Element, tag: String): String {
                val children = el.childNodes
                for (idx in 0 until children.length) {
                    val n = children.item(idx)
                    if (n is Element && n.tagName == tag) return n.textContent.orEmpty()
                }
                return ""
            }

            var projectGroup = directChildText(root, "groupId")
            val projectArtifact = directChildText(root, "artifactId")
            var projectVersion = directChildText(root, "version")

            val parentNodes = root.getElementsByTagName("parent")
            if (parentNodes.length > 0) {
                val parentEl = parentNodes.item(0) as Element
                val pGroup = parentEl.getElementsByTagName("groupId").item(0)?.textContent.orEmpty()
                val pArtifact = parentEl.getElementsByTagName("artifactId").item(0)?.textContent.orEmpty()
                val pVersion = parentEl.getElementsByTagName("version").item(0)?.textContent.orEmpty()
                if (projectGroup.isBlank()) projectGroup = pGroup
                if (projectVersion.isBlank()) projectVersion = pVersion
                mergeParentManagedVersions(pGroup, pArtifact, pVersion)
            }
            properties["project.groupId"] = projectGroup
            properties["project.artifactId"] = projectArtifact
            properties["project.version"] = projectVersion

            val props = root.getElementsByTagName("properties")
            if (props.length > 0) {
                val nodes = props.item(0).childNodes
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    if (node is Element) properties[node.tagName] = node.textContent
                }
            }

            val mgmt = root.getElementsByTagName("dependencyManagement")
            if (mgmt.length > 0) {
                val depsNode = (mgmt.item(0) as Element).getElementsByTagName("dependency")
                for (i in 0 until depsNode.length) {
                    val el = depsNode.item(i) as Element
                    val g = substitute(el.getElementsByTagName("groupId").item(0)?.textContent.orEmpty(), properties)
                    val a = substitute(el.getElementsByTagName("artifactId").item(0)?.textContent.orEmpty(), properties)
                    val v = substitute(el.getElementsByTagName("version").item(0)?.textContent.orEmpty(), properties)
                    val scope = el.getElementsByTagName("scope").item(0)?.textContent.orEmpty()
                    val type = el.getElementsByTagName("type").item(0)?.textContent.orEmpty()
                    if (scope.equals("import", ignoreCase = true) && type.equals("pom", ignoreCase = true)) {
                        val bomPom = downloadPom(Dependency(g, a, v))
                        if (bomPom.exists()) {
                            parsePom(bomPom, depth + 1)
                        }
                    } else if (g.isNotBlank() && a.isNotBlank() && v.isNotBlank()) {
                        managedVersions["$g:$a"] = v
                    }
                }
            }

            val nodeList = root.getElementsByTagName("dependency")
            for (i in 0 until nodeList.length) {
                val el = nodeList.item(i) as Element
                if ((el.parentNode as? Element)?.tagName == "dependencyManagement") continue
                val groupId = substitute(el.getElementsByTagName("groupId").item(0)?.textContent.orEmpty(), properties)
                val artifactId = substitute(el.getElementsByTagName("artifactId").item(0)?.textContent.orEmpty(), properties)
                val rawVersion = substitute(el.getElementsByTagName("version").item(0)?.textContent.orEmpty(), properties)
                val version = if (rawVersion.isNotBlank()) rawVersion else managedVersions["$groupId:$artifactId"].orEmpty()
                val scope = el.getElementsByTagName("scope").item(0)?.textContent ?: ""
                val optional = el.getElementsByTagName("optional").item(0)?.textContent?.equals("true", ignoreCase = true) == true
                if (scope != "test" && !optional && groupId.isNotBlank() && artifactId.isNotBlank() && version.isNotBlank()) {
                    val exclusionSet = parseExclusions(el)
                    val resolvedVersion = mediateVersion(groupId, artifactId, version, depth)
                    deps.add(Dependency(groupId, artifactId, resolvedVersion, exclusionSet))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return deps
    }

    private fun mergeParentManagedVersions(group: String, artifact: String, version: String) {
        if (group.isBlank() || artifact.isBlank() || version.isBlank()) return
        val key = "$group:$artifact:$version"
        if (!processedMgmtPoms.add(key)) return
        val parentPom = downloadPom(Dependency(group, artifact, version))
        if (!parentPom.exists()) return
        parsePom(parentPom, 0)
    }

    private fun mediateVersion(groupId: String, artifactId: String, version: String, depth: Int): String {
        val key = "$groupId:$artifactId"
        val existing = chosenVersions[key]
        if (existing == null || depth < existing.first) {
            chosenVersions[key] = depth to version
            return version
        }
        if (existing.first == depth && existing.second != version) {
            // deterministic tie-break: keep lexicographically larger semantic token string
            return if (version > existing.second) {
                chosenVersions[key] = depth to version
                version
            } else {
                existing.second
            }
        }
        return existing.second
    }

    private fun parseExclusions(depEl: Element): Set<String> {
        val out = mutableSetOf<String>()
        val exNodes = depEl.getElementsByTagName("exclusion")
        for (i in 0 until exNodes.length) {
            val ex = exNodes.item(i) as Element
            val g = ex.getElementsByTagName("groupId").item(0)?.textContent.orEmpty()
            val a = ex.getElementsByTagName("artifactId").item(0)?.textContent.orEmpty()
            if (g.isNotBlank() && a.isNotBlank()) out.add("$g:$a")
        }
        return out
    }

    private fun substitute(value: String, properties: Map<String, String>): String {
        val regex = Regex("\\$\\{([^}]+)}")
        return regex.replace(value) { m -> properties[m.groupValues[1]] ?: m.value }
    }

    private fun download(dep: Dependency): File? {
        val key = "${dep.groupId}:${dep.artifactId}:${dep.version}"
        return resolvedDeps.getOrPut(key) {
            val target = File(cacheDir, "${dep.artifactId}-${dep.version}.jar")
            if (!target.exists()) {
                val url = "https://repo1.maven.org/maven2/${dep.groupId.replace('.', '/')}/${dep.artifactId}/${dep.version}/${dep.artifactId}-${dep.version}.jar"
                try {
                    URL(url).openStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    return@getOrPut File("")
                }
            }
            target
        }.takeIf { it.exists() && it.isFile }
    }

    private fun downloadPom(dep: Dependency): File {
        val target = File(cacheDir, "${dep.artifactId}-${dep.version}.pom")
        if (!target.exists()) {
            val url = "https://repo1.maven.org/maven2/${dep.groupId.replace('.', '/')}/${dep.artifactId}/${dep.version}/${dep.artifactId}-${dep.version}.pom"
            try {
                URL(url).openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
            }
        }
        return target
    }

    data class Dependency(val groupId: String, val artifactId: String, val version: String, val exclusions: Set<String> = emptySet())
}
