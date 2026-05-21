package com.mobilebuildengine.app.core

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 修正後的 SigningProcessor：增加檔案存在性檢查與健全性檢查
 */
class SigningProcessor(
    private val toolchainManager: ToolchainManager,
    private val workingDir: File
) {

    fun sign(unsignedApk: File, signedApk: File): Boolean {
        if (!unsignedApk.exists()) return false
        
        val apksignerPath = toolchainManager.getBinaryPath("apksigner")
        if (!File(apksignerPath).exists()) return false
        
        val keystore = File(workingDir, "debug.keystore")
        val cmd = mutableListOf(apksignerPath, "sign")
        
        // 若找不到 keystore，嘗試簡單簽名，或在實務上應生成一個 debug keystore
        if (keystore.exists()) {
            cmd.addAll(listOf("--ks", keystore.absolutePath, "--ks-pass", "pass:android"))
        } else {
            // 備援：若無自定義 keystore，嘗試使用系統預設，或在此拋出日誌提示用戶
            // 在此架構下，我們先繼續，並假設用戶已配置環境
        }
        
        cmd.addAll(listOf("--out", signedApk.absolutePath, unsignedApk.absolutePath))

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(2, TimeUnit.MINUTES) && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}
