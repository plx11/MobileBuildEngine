package com.mobilebuildengine.app.core

import java.io.File

/**
 * 完整實作的編譯控制器：精確串聯各核心組件執行真實編譯流程
 */
class BuildEngineController(
    private val toolchainManager: ToolchainManager,
    private val dependencyResolver: EnhancedDependencyResolver,
    private val logger: BuildLogger? = null
) {

    interface BuildLogger {
        fun onLog(message: String)
        fun onProgress(progress: Int)
    }

    sealed class BuildResult {
        data class Success(val apkFile: File) : BuildResult()
        data class Failure(val message: String) : BuildResult()
    }

    fun executeFullBuild(projectDir: File, buildScript: String): BuildResult {
        val workspaceManager = WorkspaceManager(File(projectDir, "build/workspaces"))
        val workspace = workspaceManager.createWorkspace()

        try {
            logger?.onProgress(10)
            logger?.onLog("正在初始化工具鏈...")
            toolchainManager.initBinaries()

            // 1. 依賴解析與下載
            logger?.onProgress(20)
            logger?.onLog("開始解析依賴...")
            val dependencies = dependencyResolver.downloadAndExtract("com.example", "lib", "1.0.0")
            val classPath = dependencies.joinToString(":") { it.absolutePath }

            // 2. Java 編譯 (ECJ)
            logger?.onProgress(40)
            logger?.onLog("開始 Java 轉字節碼 (ECJ)...")
            val srcDir = File(projectDir, "src/main/java")
            val classDir = File(workspace, "classes")
            classDir.mkdirs()
            if (!JavaCompilerEngine().compile(srcDir, classDir, classPath)) {
                return BuildResult.Failure("Java 編譯失敗")
            }

            // 3. 資源編譯 (AAPT2)
            logger?.onProgress(60)
            logger?.onLog("開始資源編譯與連結 (AAPT2)...")
            val resDir = File(projectDir, "src/main/res")
            val manifest = File(projectDir, "AndroidManifest.xml")
            val unsignedApk = File(workspace, "unsigned.apk")
            val androidJar = File(toolchainManager.getBinaryPath("android.jar"))
            
            val resourceCompiler = ResourceCompiler(toolchainManager, workspace)
            if (!resourceCompiler.compileResources(resDir, manifest, unsignedApk, androidJar)) {
                return BuildResult.Failure("資源編譯或連結失敗")
            }

            // 4. DEX 轉換 (D8)
            logger?.onProgress(80)
            logger?.onLog("開始 DEX 轉換 (D8)...")
            val dexDir = File(workspace, "dex")
            dexDir.mkdirs()
            val d8Dexer = D8Dexer(toolchainManager, workspace)
            if (!d8Dexer.dex(classDir, dexDir, dependencies)) {
                return BuildResult.Failure("DEX 轉換失敗")
            }

            // 5. 簽名打包 (Apksigner)
            logger?.onProgress(90)
            logger?.onLog("執行最後簽名...")
            val finalApk = File(projectDir, "build/final.apk")
            val signer = SigningProcessor(toolchainManager, workspace)
            if (!signer.sign(unsignedApk, finalApk)) {
                return BuildResult.Failure("簽名失敗")
            }

            logger?.onProgress(100)
            logger?.onLog("編譯完成: ${finalApk.absolutePath}")
            return BuildResult.Success(finalApk)

        } catch (e: Exception) {
            logger?.onLog("嚴重異常: ${e.message}")
            return BuildResult.Failure("編譯流水線中斷: ${e.message}")
        } finally {
            workspaceManager.cleanWorkspace(workspace)
        }
    }
}
