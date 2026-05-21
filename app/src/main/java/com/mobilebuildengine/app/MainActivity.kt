package com.mobilebuildengine.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mobilebuildengine.app.core.BuildEngineController
import com.mobilebuildengine.app.core.ToolchainManager
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), BuildEngineController.BuildLogger {

    private lateinit var etProjectPath: EditText
    private lateinit var btnBuild: Button
    private lateinit var tvLogs: TextView
    private lateinit var progressBar: android.widget.ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etProjectPath = findViewById(R.id.etProjectPath)
        btnBuild = findViewById(R.id.btnBuild)
        tvLogs = findViewById(R.id.tvLogs)
        progressBar = findViewById(R.id.progressBar)

        btnBuild.setOnClickListener {
            val path = etProjectPath.text.toString()
            if (path.isNotEmpty()) {
                startBuild(File(path))
            }
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            tvLogs.append(message + "\n")
        }
    }

    override fun onProgress(progress: Int) {
        runOnUiThread {
            progressBar.progress = progress
        }
    }

    private fun startBuild(projectDir: File) {
        tvLogs.text = "開始編譯: ${projectDir.absolutePath}...\n"
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.progress = 0
        btnBuild.isEnabled = false // 禁用按鈕防止重複點擊
        
        thread {
            val toolchain = ToolchainManager(this)
            val cacheDir = File(filesDir, "maven_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val dependencyResolver = EnhancedDependencyResolver(cacheDir)
            
            val controller = BuildEngineController(toolchain, dependencyResolver, this)
            val result = controller.executeFullBuild(projectDir, "build.gradle")
            
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                btnBuild.isEnabled = true // 恢復按鈕
                when (result) {
                    is BuildEngineController.BuildResult.Success -> 
                        tvLogs.append("編譯成功: ${result.apkFile.absolutePath}\n")
                    is BuildEngineController.BuildResult.Failure -> 
                        tvLogs.append("編譯失敗: ${result.message}\n")
                }
            }
        }
    }
}
