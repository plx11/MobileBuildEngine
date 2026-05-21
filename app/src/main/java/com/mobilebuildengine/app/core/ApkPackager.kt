package com.mobilebuildengine.app.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 實作標準的 APK 打包器，使用 Java ZipOutputStream 插入 DEX
 */
class ApkPackager(
    private val toolchainManager: ToolchainManager,
    private val workingDir: File
) {

    fun packageApk(resApk: File, classesDex: File, outputApk: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
                // 1. 複製資源 APK 的內容
                ZipInputStream(FileInputStream(resApk)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        zos.putNextEntry(ZipEntry(entry.name))
                        zis.copyTo(zos)
                        zos.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                // 2. 插入 classes.dex
                zos.putNextEntry(ZipEntry("classes.dex"))
                FileInputStream(classesDex).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
