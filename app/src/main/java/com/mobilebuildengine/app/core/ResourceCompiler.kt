package com.mobilebuildengine.app.core

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Android 資源編譯與合併處理器
 * 處理 AAPT2 資源衝突與編譯流水線
 */
class ResourceCompiler(
    private val toolchain: ToolchainManager,
    private val workingDir: File
) {

    fun compileResources(resDir: File, assetsDir: File, manifest: File, outputApk: File): Boolean {
        val aapt2 = toolchain.getBinaryPath("aapt2")
        
        // 狀態標記：編譯 -> 連結
        val compiledDir = File(workingDir, "build/compiled_res")
        if (!compiledDir.exists()) compiledDir.mkdirs()

        // 1. AAPT2 Compile
        val compileProcess = ProcessBuilder(
            aapt2, "compile", "--dir", resDir.absolutePath, "-o", compiledDir.absolutePath
        ).directory(workingDir).start()
        
        if (compileProcess.waitFor() != 0) return false

        // 2. AAPT2 Link (資源合併)
        val linkCmd = mutableListOf(
            aapt2, "link",
            "--manifest", manifest.absolutePath,
            "-o", outputApk.absolutePath,
            "-I", toolchain.getBinaryPath("android.jar") // 必須引用系統 jar
        )
        // 將 compiledDir 下的所有 .flat 檔案加入連結指令
        compiledDir.walk().filter { it.extension == "flat" }.forEach {
            linkCmd.add(it.absolutePath)
        }

        val linkProcess = ProcessBuilder(linkCmd).directory(workingDir).start()
        if (!(linkProcess.waitFor(5, TimeUnit.MINUTES) && linkProcess.exitValue() == 0)) return false

        // 3. Zipalign 對齊
        val zipalign = tool.getBinaryPath("zipalign")
        val alignedApk = File(outputApk.parentFile, "aligned_${outputApk.name}")
        val alignProcess = ProcessBuilder(zipalign, "-p", "-f", "4", outputApk.absolutePath, alignedApk.absolutePath).start()
        
        return alignProcess.waitFor() == 0
    }
}
