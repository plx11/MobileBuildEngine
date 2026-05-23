package com.mobilebuildengine.app.core

import com.mobilebuildengine.app.ToolchainManager
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
        if (!File(kotlincPath).exists()) {
            System.err.println("Kotlinc binary not found: $kotlincPath")
            return false
        }

        if (!srcDir.exists() || !srcDir.isDirectory) {
            System.err.println("Kotlin source directory not found: ${srcDir.absolutePath}")
            return false
        }

        val kotlinFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.absolutePath }.toList()

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
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            // 正確等待進程結束，waitFor 返回的是 boolean (是否超時)
            val finished = process.waitFor(5, TimeUnit.MINUTES)
            
            if (finished && process.exitValue() == 0) {
                true
            } else {
                if (!finished) process.destroyForcibly()
                if (output.isNotBlank()) {
                    System.err.println("Kotlinc Error: $output")
                }
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
