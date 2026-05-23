package com.mobilebuildengine.app.core

import com.mobilebuildengine.app.ToolchainManager
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

    init {
        // keep constructor contract explicit and avoid misleading unused dependencies
        require(workingDir.exists() || workingDir.mkdirs()) { "workingDir 無法建立: ${workingDir.absolutePath}" }
        val zipalignPath = toolchainManager.getBinaryPath("zipalign")
        require(zipalignPath.isNotBlank() && File(zipalignPath).exists()) { "zipalign 未初始化: $zipalignPath" }
    }

    fun packageApk(resApk: File, classesDex: File, outputApk: File): Boolean {
        return packageApk(resApk, listOf(classesDex), outputApk)
    }

    fun packageApk(resApk: File, dexFiles: List<File>, outputApk: File): Boolean {
        val tempApk = File(outputApk.absolutePath + ".tmp")
        val success = try {
            ZipOutputStream(FileOutputStream(tempApk)).use { zos ->
                // 1. 複製資源
                ZipInputStream(FileInputStream(resApk)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        // 資源保留原始壓縮格式
                        zos.putNextEntry(ZipEntry(entry.name))
                        zis.copyTo(zos)
                        zos.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                // 2. 插入 dex (STORED 模式)
                dexFiles.sortedBy { it.name }.forEach { dexFile ->
                    val entry = ZipEntry(dexFile.name)
                    entry.method = ZipEntry.STORED
                    entry.size = dexFile.length()
                    
                    // 計算 CRC-32
                    val crc = java.util.zip.CRC32()
                    FileInputStream(dexFile).use { fis ->
                        val buffer = ByteArray(8192)
                        var read = fis.read(buffer)
                        while(read != -1) {
                            crc.update(buffer, 0, read)
                            read = fis.read(buffer)
                        }
                    }
                    entry.crc = crc.value
                    
                    zos.putNextEntry(entry)
                    FileInputStream(dexFile).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (success) {
            // 3. Zipalign
            val zipalign = toolchainManager.getBinaryPath("zipalign")
            val process = ProcessBuilder(zipalign, "-v", "4", tempApk.absolutePath, outputApk.absolutePath).start()
            val result = process.waitFor() == 0
            tempApk.delete()
            return result
        }
        return false
    }
}
