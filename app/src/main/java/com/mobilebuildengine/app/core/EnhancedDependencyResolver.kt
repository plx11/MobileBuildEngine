package com.mobilebuildengine.app.core

import java.io.File
import java.net.URL
import java.util.regex.Pattern

/**
 * 結構化編譯配置協議
 */
data class BuildManifest(val dependencies: List<Dependency>)
data class Dependency(val group: String, val artifact: String, val version: String)

class EnhancedDependencyResolver(private val cacheDir: File) {

    private val MAVEN_URL = "https://repo1.maven.org/maven2"
    private val resolvedArtifacts = mutableMapOf<String, String>() // <group:artifact, version>

    fun resolve(input: String): List<File> {
        val dependencies = mutableListOf<Dependency>()
        
        // 模式 1: 優先解析 JSON (BuildManifest)
        if (input.trim().startsWith("{")) {
            // 簡化解析，實際專案可引入 Gson
            println("使用結構化配置解析...")
        } else {
            // 模式 2: 降級解析 Gradle 正則
            val pattern = Pattern.compile("implementation\\s+['\"]([^'\"]+):([^'\"]+):([^'\"]+)['\"]")
            val matcher = pattern.matcher(input)
            while (matcher.find()) {
                val dep = Dependency(matcher.group(1), matcher.group(2), matcher.group(3))
                // 版本仲裁
                val key = "${dep.group}:${dep.artifact}"
                val existing = resolvedArtifacts[key]
                if (existing == null || VersionComparator.compare(dep.version, existing) > 0) {
                    resolvedArtifacts[key] = dep.version
                    dependencies.add(dep)
                }
            }
        }
        return dependencies.mapNotNull { downloadWithTransitive(it.group, it.artifact, it.version) }
    }
    // ... (後續 download 邏輯)

    private fun downloadWithTransitive(group: String, artifact: String, version: String): List<File> {
        val key = "$group:$artifact:$version"
        if (resolvedArtifacts.contains(key)) return emptyList()
        resolvedArtifacts.add(key)

        val files = mutableListOf<File>()
        val artifactFile = downloadArtifact(group, artifact, version)
        if (artifactFile != null) {
            files.add(artifactFile)
            // 這裡理論上應解析 .pom 檔案以獲取遞迴依賴 (transitive dependencies)
            // 為了實現快速，此處預留遞迴呼叫接口
        }
        return files
    }

    private fun downloadArtifact(group: String, artifact: String, version: String): File? {
        val groupPath = group.replace(".", "/")
        val fileName = "$artifact-$version.jar"
        val targetFile = File(cacheDir, fileName)
        
        if (targetFile.exists()) return targetFile

        val urlStr = "$MAVEN_URL/$groupPath/$artifact/$version/$fileName"
        
        // ... (重試機制)
        // 下載完成後校驗
        val sha1 = downloadSha1(group, artifact, version)
        if (sha1 != null && calculateSha1(targetFile) != sha1) {
            targetFile.delete()
            return null
        }
        return targetFile
    }

    private fun downloadSha1(group: String, artifact: String, version: String): String? {
        val groupPath = group.replace(".", "/")
        val url = "$MAVEN_URL/$groupPath/$artifact/$version/$artifact-$version.jar.sha1"
        return try {
            URL(url).readText().trim().split(" ")[0]
        } catch (e: Exception) { null }
    }
}
