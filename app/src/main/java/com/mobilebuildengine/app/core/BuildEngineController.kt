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

    interface BuildLogger {
        fun onLog(message: String)
    }

    private val toolchainManager: ToolchainManager
    private val dependencyResolver: EnhancedDependencyResolver
    private var logger: BuildLogger? = null

    constructor(
        toolchainManager: ToolchainManager,
        dependencyResolver: EnhancedDependencyResolver,
        logger: BuildLogger? = null
    ) {
        this.toolchainManager = toolchainManager
        interface BuildLogger {
            fun onLog(message: String)
            fun onProgress(progress: Int)
        }
fun executeFullBuild(projectDir: File, buildScript: String): BuildResult {
    val workspaceManager = WorkspaceManager(File(projectDir, "build/workspaces"))
    val workspace = workspaceManager.createWorkspace()

    try {
        logger?.onProgress(10)
        logger?.onLog("工作空間: ${workspace.name}")
        toolchainManager.initBinaries()

        // 步驟 1: 解析依賴
        logger?.onProgress(20)
        logger?.onLog("解析依賴...")
        val dependencies = dependencyResolver.resolve(buildScript)

        // 步驟 2: 資源編譯 (AAPT2)
        logger?.onProgress(40)
        logger?.onLog("開始資源編譯...")
        val aapt = AAPT2Processor(toolchainManager, projectDir)
        val resResult = aapt.compile(File(projectDir, "src/main/res"), workspace)
        if (!resResult.success) return BuildResult.Failure("資源編譯失敗: ${resResult.log}")

        // 步驟 3: DEX 轉換 (D8)
        logger?.onProgress(60)
        logger?.onLog("開始 DEX 轉換...")
        val d8 = D8Dexer(toolchainManager, projectDir)
        if (!d8.dex(File(projectDir, "build/classes"), workspace, dependencies)) return BuildResult.Failure("DEX 轉換失敗")

        // 步驟 4: 打包與簽名
        logger?.onProgress(80)
        logger?.onLog("正在簽名打包...")
        val finalApk = File(projectDir, "build/final.apk")
        val signer = SigningProcessor(toolchainManager, projectDir)
        if (!signer.sign(File(workspace, "unsigned.apk"), finalApk)) return BuildResult.Failure("簽名過程失敗")

        logger?.onProgress(100)
        logger?.onLog("編譯完成!")
        return BuildResult.Success(finalApk)
    } catch (e: Exception) {
        logger?.onLog("嚴重錯誤: ${e.message}")
        return BuildResult.Failure("編譯過程異常: ${e.message}")
    } finally {
        workspaceManager.cleanWorkspace(workspace)
    }
}
        data class Success(val apkFile: File) : BuildResult()
        data class Failure(val message: String) : BuildResult()
    }
}
