package com.mobilebuildengine.app.core

import java.io.File

/**
 * 最終 APK 封裝器
 * 處理檔案對齊 (zipalign) 與資源壓縮
 */
class ApkPackager(
    private val toolchain: ToolchainManager,
    private val workingDir: File
) {

    fun alignAndFinalize(inputApk: File, outputApk: File): Boolean {
        val zipalign = toolchain.getBinaryPath("zipalign")
        
        val cmd = listOf(
            zipalign, "-f", "-p", "4",
            inputApk.absolutePath,
            outputApk.absolutePath
        )

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
