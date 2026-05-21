package com.mobilebuildengine.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 修正後的 ToolchainManager：確保權限配置無誤，並增加安全性檢查
 */
class ToolchainManager(private val context: Context) {

    private val binDir = File(context.filesDir, "bin")

    /**
     * 初始化工具鏈，若發生錯誤則拋出 IOException
     * @throws IOException 當 asset 讀取或檔案寫入失敗時
     */
    fun initBinaries() {
        if (!binDir.exists() && !binDir.mkdirs()) {
            throw IOException("無法創建工具鏈目錄: ${binDir.absolutePath}")
        }

        val binaries = context.assets.list("binaries") ?: throw IOException("未找到 binaries 目錄")
        for (bin in binaries) {
            val outFile = File(binDir, bin)
            if (!outFile.exists()) {
                context.assets.open("binaries/$bin").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // 增加完整性檢查：檢查檔案是否存在且大小大於 0
            if (!outFile.exists() || outFile.length() == 0L) {
                throw IOException("工具鏈檔案損壞或缺失: $bin")
            }

            if (!outFile.setExecutable(true, false)) {
                throw IOException("無法設定工具鏈執行權限: ${outFile.absolutePath}")
            }
        }
    }

    fun getBinaryPath(name: String): String {
        return File(binDir, name).absolutePath
    }
}
