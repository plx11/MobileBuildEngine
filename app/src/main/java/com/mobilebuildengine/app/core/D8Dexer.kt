package com.mobilebuildengine.app.core

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 完整實作的 D8 字節碼轉碼器，調用真實 D8 二進位檔
 */
class D8Dexer(
    private val toolchainManager: ToolchainManager,
    private val workingDir: File
) {

    fun dex(classFilesDir: File, outputDexDir: File, dependencies: List<File>): Boolean {
        val d8Path = toolchainManager.getBinaryPath("d8")
        if (!File(d8Path).exists()) return false
        
        val cmd = mutableListOf(d8Path)
        
        // 輸出設定
        cmd.addAll(listOf("--output", outputDexDir.absolutePath))
        
        // 加入依賴庫 (庫通常是已編譯的 JAR)
        dependencies.forEach { lib ->
            if (lib.exists()) {
                cmd.add("--lib")
                cmd.add(lib.absolutePath)
            }
        }
        
        // 加入類別檔案
        cmd.add(classFilesDir.absolutePath)

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            // 真實處理日誌流
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(10, TimeUnit.MINUTES)
            
            if (!finished || process.exitValue() != 0) {
                System.err.println("D8 Execution Error: $output")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
