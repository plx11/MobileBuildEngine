package com.mobilebuildengine.app

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mobilebuildengine.app.core.*
import java.io.File

/**
 * 真實實現的後台編譯任務調度器
 */
class BuildWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val projectPath = inputData.getString("project_path")
            ?: return failureWithMessage("missing project_path")
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return failureWithMessage("invalid project_path: $projectPath")
        }

        return try {
            val toolchain = ToolchainManager(applicationContext)
            val resolver = EnhancedDependencyResolver(File(applicationContext.filesDir, "maven_cache"))
            val buildScriptContent = listOf(
                File(projectDir, "build.gradle"),
                File(projectDir, "app/build.gradle")
            ).firstOrNull { it.exists() }?.readText().orEmpty()

            // 此處不需 UI 回調，由 WorkManager 處理狀態
            val controller = BuildEngineController(toolchain, resolver)
            val buildResult = controller.executeFullBuild(projectDir, buildScriptContent)
            when (buildResult) {
                is BuildEngineController.BuildResult.Success -> Result.success()
                is BuildEngineController.BuildResult.Failure -> failureWithMessage(buildResult.message)
            }
        } catch (e: Exception) {
            failureWithMessage("worker exception: ${e.message}")
        }
    }

    private fun failureWithMessage(message: String): Result {
        val errorData = Data.Builder()
            .putString("error_message", message)
            .build()
        return Result.failure(errorData)
    }
}
