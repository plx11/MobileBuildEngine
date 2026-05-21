package com.mobilebuildengine.app.core

import java.io.*
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 完整實作的依賴解析器：支援 Maven 下載、.aar/.jar 自動區分與解壓提取
 */
class EnhancedDependencyResolver(private val cacheDir: File) {

    private val MAVEN_URL = "https://repo1.maven.org/maven2"

    fun downloadAndExtract(group: String, artifact: String, version: String): List<File> {
        val extractedFiles = mutableListOf<File>()
        
        // 嘗試 .aar, 若無則嘗試 .jar
        val aarFile = File(cacheDir, "$artifact-$version.aar")
        val jarFile = File(cacheDir, "$artifact-$version.jar")
        
        val targetFile = if (aarFile.exists()) aarFile else jarFile
        
        if (!targetFile.exists()) {
            val downloadUrl = if (aarFile.exists()) {
                "$MAVEN_URL/${group.replace(".", "/")}/$artifact/$version/$artifact-$version.aar"
            } else {
                "$MAVEN_URL/${group.replace(".", "/")}/$artifact/$version/$artifact-$version.jar"
            }
            
            URL(downloadUrl).openStream().use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        if (targetFile.extension == "aar") {
            val extractDir = File(cacheDir, "$artifact-$version")
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
        
        return extractedFiles
    }
}
