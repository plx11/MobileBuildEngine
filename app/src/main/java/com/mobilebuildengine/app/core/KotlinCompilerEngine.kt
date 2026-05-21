package com.mobilebuildengine.app.core

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * KotlinCompilerEngine: 負責調用 kotlinc 完成 Kotlin 源碼編譯
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

            val exitCode = process.waitFor(5, TimeUnit.MINUTES)
            if (!exitCode) {
                process.destroy()
                return false
            }

            process.exitValue() == 0
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}
