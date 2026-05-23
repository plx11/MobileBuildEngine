package com.mobilebuildengine.app.core

import com.mobilebuildengine.app.ToolchainManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * AAPT2 資源編譯器，執行二進位調用
 */
class ResourceCompiler(
    private val toolchain: ToolchainManager,
    private val workingDir: File
) {

    fun compileResources(resDir: File, manifest: File, outputApk: File, androidJar: File): Boolean {
        val aapt2 = toolchain.getBinaryPath("aapt2")
        val zipalign = toolchain.getBinaryPath("zipalign")
        
        val flatDir = File(workingDir, "build/flat")
        if (!flatDir.exists()) flatDir.mkdirs()

        // 1. AAPT2 Compile: 將 res 編譯為 .flat 檔案
        val compileProcess = ProcessBuilder(
            aapt2, "compile", "--dir", resDir.absolutePath, "-o", flatDir.absolutePath
        ).directory(workingDir).redirectErrorStream(true).start()
        
        val compileOutput = compileProcess.inputStream.bufferedReader().use { it.readText() }
        if (!(compileProcess.waitFor(2, TimeUnit.MINUTES) && compileProcess.exitValue() == 0)) {
            System.err.println("AAPT2 Compile Error: $compileOutput")
            return false
        }

        // 2. AAPT2 Link: 合併資源並產生 APK
        val flatFiles = flatDir.listFiles { _, name -> name.endsWith(".flat") }?.map { it.absolutePath } ?: emptyList()
        val linkCmd = mutableListOf(
            aapt2, "link",
            "--manifest", manifest.absolutePath,
            "-o", outputApk.absolutePath,
            "-I", androidJar.absolutePath
        )
        linkCmd.addAll(flatFiles)

        val linkProcess = ProcessBuilder(linkCmd).directory(workingDir).redirectErrorStream(true).start()
        val linkOutput = linkProcess.inputStream.bufferedReader().use { it.readText() }
        if (!(linkProcess.waitFor(2, TimeUnit.MINUTES) && linkProcess.exitValue() == 0)) {
            System.err.println("AAPT2 Link Error: $linkOutput")
            return false
        }

        // 3. Zipalign: 對齊
        val alignedApk = File(outputApk.parentFile, "aligned_${outputApk.name}")
        val alignProcess = ProcessBuilder(
            zipalign, "-p", "-f", "4", outputApk.absolutePath, alignedApk.absolutePath
        ).directory(workingDir).redirectErrorStream(true).start()
        val alignOutput = alignProcess.inputStream.bufferedReader().use { it.readText() }

        if (!(alignProcess.waitFor(2, TimeUnit.MINUTES) && alignProcess.exitValue() == 0)) {
            System.err.println("Zipalign Error: $alignOutput")
            return false
        }

        if (!alignedApk.renameTo(outputApk)) {
            System.err.println("Zipalign Error: 無法覆蓋輸出 APK")
            return false
        }

        true
    }
}
