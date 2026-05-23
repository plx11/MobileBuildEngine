package com.mobilebuildengine.app.core

import com.mobilebuildengine.app.ToolchainManager
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 編譯控制器：串聯核心組件執行編譯流程
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

            // 1. 依賴解析與下載 - 動態解析 build.gradle
            logger?.onProgress(20)
            logger?.onLog("正在解析 build.gradle...")
            val buildGradleCandidates = listOf(
                File(projectDir, "build.gradle"),
                File(projectDir, "app/build.gradle")
            )
            val discoveredBuildGradle = buildGradleCandidates.firstOrNull { it.exists() }

            val dependencies = if (discoveredBuildGradle != null) {
                val gradleParser = GradleParser()
                gradleParser.parseDependencies(discoveredBuildGradle).flatMap { dep ->
                    dependencyResolver.resolveWithTransitives(dep.groupId, dep.artifactId, dep.version)
                }
            } else if (buildScript.isNotBlank()) {
                val tempGradle = File(workspace, "inline-build.gradle")
                tempGradle.writeText(buildScript, StandardCharsets.UTF_8)
                val gradleParser = GradleParser()
                gradleParser.parseDependencies(tempGradle).flatMap { dep ->
                    dependencyResolver.resolveWithTransitives(dep.groupId, dep.artifactId, dep.version)
                }
            } else {
                emptyList()
            }
            val classPath = dependencies.joinToString(File.pathSeparator) { it.absolutePath }

            // 2. Kotlin & Java 編譯
            logger?.onProgress(40)
            logger?.onLog("開始 Kotlin 與 Java 轉字節碼...")
            val javaSrcDir = File(projectDir, "app/src/main/java")
            val kotlinSrcDir = File(projectDir, "app/src/main/kotlin")
            val classDir = File(workspace, "classes")
            classDir.mkdirs()

            // 編譯 Kotlin（優先 app/src/main/kotlin，回退 app/src/main/java）
            val kotlinCompileDir = if (kotlinSrcDir.exists()) kotlinSrcDir else javaSrcDir
            val kotlinEngine = KotlinCompilerEngine(toolchainManager)
            if (!kotlinEngine.compile(kotlinCompileDir, classDir, classPath) { logger?.onLog(it) }) {
                return BuildResult.Failure("Kotlin 編譯失敗")
            }

            // 編譯 Java（僅針對 app/src/main/java）
            if (!JavaCompilerEngine().compile(javaSrcDir, classDir, classPath) { logger?.onLog(it) }) {
                return BuildResult.Failure("Java 編譯失敗")
            }

            // 3. 資源編譯 (AAPT2)
            logger?.onProgress(60)
            logger?.onLog("開始資源編譯與連結 (AAPT2)...")
            val resDir = File(projectDir, "app/src/main/res")
            val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
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

            // 5. APK 打包 (ApkPackager)
            logger?.onProgress(85)
            logger?.onLog("正在打包 APK 檔案...")
            val finalUnsignedApkWithDex = File(workspace, "final-unsigned-with-dex.apk")
            val dexFiles = dexDir.listFiles { file ->
                file.isFile && file.extension == "dex"
            }?.sortedBy { it.name } ?: emptyList()
            if (dexFiles.isEmpty()) {
                return BuildResult.Failure("DEX 檔案未找到")
            }
            val apkPackager = ApkPackager(toolchainManager, workspace)
            if (!apkPackager.packageApk(unsignedApk, dexFiles, finalUnsignedApkWithDex)) {
                return BuildResult.Failure("APK 打包失敗")
            }

            // 6. 簽名打包 (Apksigner)
            logger?.onProgress(90)
            logger?.onLog("執行最後簽名...")
            val finalApk = File(projectDir, "build/final.apk")
            val signer = SigningProcessor(toolchainManager, workspace)
            if (!signer.sign(finalUnsignedApkWithDex, finalApk)) {
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
