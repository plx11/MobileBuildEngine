package com.mobilebuildengine.app.core

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 嚴格實作的 SigningProcessor：使用進程退出碼與錯誤流監控
 */
class SigningProcessor(
    private val toolchainManager: ToolchainManager,
    private val workingDir: File
) {

    fun sign(unsignedApk: File, signedApk: File): Boolean {
        if (!unsignedApk.exists()) {
            println("簽名失敗: 輸入檔案不存在")
            return false
        }
        
        val apksignerPath = toolchainManager.getBinaryPath("apksigner")
        val cmd = mutableListOf(
            apksignerPath, "sign",
            "--out", signedApk.absolutePath,
            unsignedApk.absolutePath
        )

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true) // 合併輸出與錯誤流以便捕獲
                .start()
            
            // 捕獲並輸出所有日誌（含錯誤資訊）
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }
            
            val finished = process.waitFor(2, TimeUnit.MINUTES)
            
            if (finished && process.exitValue() == 0) {
                true
            } else {
                if (!finished) {
                    process.destroyForcibly()
                    println("簽名超時")
                } else {
                    println("簽名失敗，退出代碼: ${process.exitValue()}")
                    println("錯誤日誌: $output")
                }
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
