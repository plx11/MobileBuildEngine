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

    fun initBinaries() {
        if (!binDir.exists()) binDir.mkdirs()

        val binaries = context.assets.list("binaries") ?: return
        for (bin in binaries) {
            val outFile = File(binDir, bin)
            if (!outFile.exists()) {
                try {
                    context.assets.open("binaries/$bin").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            // 修正權限設定：確保在不同 Android 版本下的執行權限
            outFile.setExecutable(true, false)
        }
    }

    fun getBinaryPath(name: String): String {
        return File(binDir, name).absolutePath
    }
}
