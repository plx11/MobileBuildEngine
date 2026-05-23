package com.mobilebuildengine.app

import com.mobilebuildengine.app.core.BuildEngineController
import com.mobilebuildengine.app.core.EnhancedDependencyResolver
import java.io.File

/**
 * 核心編譯流水線：委派給 BuildEngineController，避免雙重流程分岔。
 */
class CompilationPipeline(
    private val toolchainManager: ToolchainManager,
    private val projectDir: File,
    private val dependencyResolver: EnhancedDependencyResolver,
    private val logger: BuildEngineController.BuildLogger? = null,
    private val buildScript: String = ""
) {

    fun execute(): Boolean {
        val controller = BuildEngineController(toolchainManager, dependencyResolver, logger)
        val result = controller.executeFullBuild(projectDir, buildScript)
        return result is BuildEngineController.BuildResult.Success
    }
}
