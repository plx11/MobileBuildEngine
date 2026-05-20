package com.mobilebuildengine.app.core

import java.io.File

/**
 * 完整整合的編譯引擎控制器
 * 將資源編譯、DEX 轉換、簽名打包封裝為原子化任務序列
 */
class BuildEngineController(
    private val toolchainManager: ToolchainManager,
    private val dependencyResolver: EnhancedDependencyResolver
) {

    fun executeFullBuild(projectDir: File, buildScript: String): BuildResult {
        try {
            // 步驟 1: 依賴解析與下載
            val dependencies = dependencyResolver.resolve(buildScript)
            
            // 步驟 2: 資源編譯 (AAPT2)
            val resDir = File(projectDir, "src/main/res")
            val outputDir = File(projectDir, "build/intermediates")
            if (!outputDir.exists()) outputDir.mkdirs()
            
            val aapt = AAPT2Processor(toolchainManager, projectDir)
            val resResult = aapt.compile(resDir, outputDir)
            if (!resResult.success) return BuildResult.Failure("資源編譯失敗: ${resResult.log}")

            // 步驟 3: 原始碼編譯 (暫時假設已存在 .class)
            // 步驟 4: DEX 轉換 (D8)
            val classDir = File(projectDir, "build/classes")
            val dexDir = File(projectDir, "build/dex")
            if (!dexDir.exists()) dexDir.mkdirs()
            
            val d8 = D8Dexer(toolchainManager, projectDir)
            if (!d8.dex(classDir, dexDir, dependencies)) return BuildResult.Failure("DEX 轉換失敗")

            // 步驟 5: 打包與簽名
            val unsignedApk = File(projectDir, "build/unsigned.apk")
            val finalApk = File(projectDir, "build/final.apk")
            
            val signer = SigningProcessor(toolchainManager, projectDir)
            if (!signer.sign(unsignedApk, finalApk)) return BuildResult.Failure("簽名過程失敗")

            return BuildResult.Success(finalApk)
        } catch (e: Exception) {
            return BuildResult.Failure("執行編譯過程時發生異常: ${e.message}")
        }
    }

    sealed class BuildResult {
        data class Success(val apkFile: File) : BuildResult()
        data class Failure(val message: String) : BuildResult()
    }
}
