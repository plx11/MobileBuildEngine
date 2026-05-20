package com.mobilebuildengine.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mobilebuildengine.app.core.*
import java.io.File

/**
 * 實際執行的背景工作者
 * 自動調度 BuildEngineController 進行全自動化編譯
 */
class BuildWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val projectPath = inputData.getString("PROJECT_PATH") ?: return Result.failure()
        val gradleContent = inputData.getString("GRADLE_CONTENT") ?: ""
        
        val projectDir = File(projectPath)
        val cacheDir = File(applicationContext.cacheDir, "dependencies")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val toolchain = ToolchainManager(applicationContext)
        toolchain.initBinaries()

        val resolver = EnhancedDependencyResolver(cacheDir)
        val controller = BuildEngineController(toolchain, resolver)

        val result = controller.executeFullBuild(projectDir, gradleContent)

        return when (result) {
            is BuildEngineController.BuildResult.Success -> {
                Result.success(workDataOf("APK_PATH" to result.apkFile.absolutePath))
            }
            is BuildEngineController.BuildResult.Failure -> {
                Result.failure(workDataOf("ERROR" to result.message))
            }
        }
    }
}
