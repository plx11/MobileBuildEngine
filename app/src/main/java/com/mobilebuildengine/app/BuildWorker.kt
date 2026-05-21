package com.mobilebuildengine.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mobilebuildengine.app.core.*
import java.io.File

/**
 * 真實實現的後台編譯任務調度器
 */
class BuildWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val projectPath = inputData.getString("project_path") ?: return Result.failure()
        val projectDir = File(projectPath)
        
        val toolchain = ToolchainManager(applicationContext)
        val resolver = EnhancedDependencyResolver(File(applicationContext.filesDir, "maven_cache"))
        
        // 此處不需 UI 回調，由 WorkManager 處理狀態
        val controller = BuildEngineController(toolchain, resolver)
        return when (controller.executeFullBuild(projectDir, "build.gradle")) {
            is BuildEngineController.BuildResult.Success -> Result.success()
            is BuildEngineController.BuildResult.Failure -> Result.failure()
        }
    }
}
