package com.mobilebuildengine.app

import java.io.File
import java.net.URL
import java.util.regex.Pattern

/**
 * 負責自動下載依賴庫 (.aar/.jar) 並處理 Maven 倉庫請求
 */
class DependencyResolver(private val cacheDir: File) {

    // 簡單的 Maven Central 下載 URL
    private val MAVEN_URL = "https://repo1.maven.org/maven2"

    fun resolve(gradleContent: String): List<File> {
        val dependencies = mutableListOf<File>()
        // 使用正則匹配 implementation 'groupId:artifactId:version'
        val pattern = Pattern.compile("implementation\\s+['\"]([^'\"]+):([^'\"]+):([^'\"]+)['\"]")
        val matcher = pattern.matcher(gradleContent)

        while (matcher.find()) {
            val group = matcher.group(1).replace(".", "/")
            val artifact = matcher.group(2)
            val version = matcher.group(3)
            
            val file = downloadArtifact(group, artifact, version)
            if (file != null) dependencies.add(file)
        }
        return dependencies
    }

    private fun downloadArtifact(group: String, artifact: String, version: String): File? {
        val fileName = "$artifact-$version.jar" // 簡化為 .jar，實際需處理 .aar
        val targetFile = File(cacheDir, fileName)
        
        if (targetFile.exists()) return targetFile

        return try {
            val url = "$MAVEN_URL/$group/$artifact/$version/$fileName"
            URL(url).openStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            null
        }
    }
}
