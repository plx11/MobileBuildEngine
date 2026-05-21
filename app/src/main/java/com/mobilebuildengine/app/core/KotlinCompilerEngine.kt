package com.mobilebuildengine.app.core

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * KotlinCompilerEngine: 負責調用 kotlinc 完成 Kotlin 源碼編譯
 * 實現標準 Process 調度與日誌捕獲
 */
class KotlinCompilerEngine(private val toolchainManager: ToolchainManager) {

    fun compile(srcDir: File, outputDir: File, classPath: String): Boolean {
        val kotlincPath = toolchainManager.getBinaryPath("kotlinc")
        val kotlinFiles = srcDir.walkTopDown().filter { it.extension == "kt" }.map { it.absolutePath }.toList()

        if (kotlinFiles.isEmpty()) return true

        val command = mutableListOf(
            kotlincPath,
            "-d", outputDir.absolutePath,
            "-classpath", classPath,
            "-jvm-target", "1.8"
        )
        command.addAll(kotlinFiles)

        return try {
            val process = ProcessBuilder(command)
                .directory(srcDir.parentFile)
                .redirectErrorStream(true)
                .start()

            // 讀取進程輸出日誌
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // 此處可接入 Logger，目前先忽略以確保編譯流暢
            }

            // 正確等待進程結束，waitFor 返回的是 boolean (是否超時)
            val finished = process.waitFor(5, TimeUnit.MINUTES)
            
            if (finished && process.exitValue() == 0) {
                true
            } else {
                if (!finished) process.destroyForcibly()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
