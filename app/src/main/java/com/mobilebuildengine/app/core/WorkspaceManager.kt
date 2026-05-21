package com.mobilebuildengine.app.core

import java.io.File
import java.util.UUID

/**
 * 工作空間管理器：為每次編譯任務分配獨立的臨時目錄，防止多工衝突
 */
class WorkspaceManager(private val baseDir: File) {

    fun createWorkspace(): File {
        val workspace = File(baseDir, "build_${UUID.randomUUID().toString().take(8)}")
        if (!workspace.exists()) workspace.mkdirs()
        return workspace
    }

    fun cleanWorkspace(workspace: File) {
        workspace.deleteRecursively()
    }
}
