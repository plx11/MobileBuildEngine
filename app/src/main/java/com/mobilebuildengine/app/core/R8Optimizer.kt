package com.mobilebuildengine.app.core

import com.mobilebuildengine.app.ToolchainManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * R8 代碼混淆與優化器，調用 R8 二進位檔
 */
class R8Optimizer(
    private val toolchainManager: ToolchainManager,
    private val workingDir: File
) {

    fun optimize(inputJar: File, outputJar: File, proguardRules: File): Boolean {
        val r8Path = toolchainManager.getBinaryPath("r8")
        if (!File(r8Path).exists()) {
            System.err.println("R8 binary not found: $r8Path")
            return false
        }

        val cmd = listOf(
            r8Path, "--classfile",
            "--pg-conf", proguardRules.absolutePath,
            "--output", outputJar.absolutePath,
            inputJar.absolutePath
        )

        return try {
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0) true
            else {
                System.err.println("R8 Error: $output")
                false
            }
        } catch (e: Exception) {
            System.err.println("R8 execution exception: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
