package com.mobilebuildengine.app.core

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 修正後的 D8Dexer：處理進程輸出並修復同步問題
 */
class D8Dexer(
    private val toolchainManager: ToolchainManager,
    private val workingDir: File
) {

    fun dex(classFilesDir: File, outputDexDir: File, dependencies: List<File>): Boolean {
        val d8Path = toolchainManager.getBinaryPath("d8")
        if (!File(d8Path).exists()) return false
        
        val cmd = mutableListOf(
            d8Path,
            "--output", outputDexDir.absolutePath,
            classFilesDir.absolutePath
        )
        
        dependencies.forEach { lib ->
            cmd.add("--lib")
            cmd.add(lib.absolutePath)
        }

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            // 必須完整消耗輸出流，否則進程可能阻塞
            process.inputStream.bufferedReader().use { it.readText() }
            
            val finished = process.waitFor(10, TimeUnit.MINUTES)
            finished && process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
