package com.mobilebuildengine.app.core

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * R8 混淆與優化處理器
 * 處理生產級代碼優化與混淆任務
 */
class R8Optimizer(
    private val toolchain: ToolchainManager,
    private val workingDir: File
) {

    fun optimize(dexDir: File, outputDexDir: File, proguardRules: File?): Boolean {
        val r8Path = toolchain.getBinaryPath("r8")
        
        val cmd = mutableListOf(
            r8Path,
            "--release",
            "--output", outputDexDir.absolutePath,
            dexDir.absolutePath
        )
        
        proguardRules?.let {
            cmd.add("--pg-conf")
            cmd.add(it.absolutePath)
        }

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(15, TimeUnit.MINUTES) && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}
