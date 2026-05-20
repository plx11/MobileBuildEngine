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
        
        val cmd = listOf(
            apksignerPath, "sign",
            "--ks", File(workingDir, "debug.keystore").absolutePath,
            "--ks-pass", "pass:android",
            "--out", signedApk.absolutePath,
            unsignedApk.absolutePath
        )

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(2, TimeUnit.MINUTES) && process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
