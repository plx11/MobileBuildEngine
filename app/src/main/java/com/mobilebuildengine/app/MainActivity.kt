package com.mobilebuildengine.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mobilebuildengine.app.core.BuildWorker
import java.io.File

class MainActivity : AppCompatActivity() {

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
            val path = etProjectPath.text.toString().trim()
            if (path.isNotEmpty()) {
                val projectDir = File(path)
                if (!projectDir.exists() || !projectDir.isDirectory) {
                    tvLogs.append("無效專案路徑: ${projectDir.absolutePath}\n")
                } else {
                    startBuild(projectDir)
                }
            }
        }
    }

    private fun startBuild(projectDir: File) {
        tvLogs.text = "提交編譯任務: ${projectDir.absolutePath}...\n"
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.isIndeterminate = true
        btnBuild.isEnabled = false

        // 創建 WorkManager 任務
        val buildRequest = OneTimeWorkRequestBuilder<BuildWorker>()
            .setInputData(workDataOf("project_path" to projectDir.absolutePath))
            .build()

        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(buildRequest)

        // 觀察任務狀態
        workManager.getWorkInfoByIdLiveData(buildRequest.id).observe(this) { workInfo ->
            if (workInfo != null) {
                when (workInfo.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        progressBar.visibility = android.view.View.GONE
                        btnBuild.isEnabled = true
                        tvLogs.append("編譯完成!\n")
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        progressBar.visibility = android.view.View.GONE
                        btnBuild.isEnabled = true
                        tvLogs.append("編譯失敗!\n")
                    }
                    androidx.work.WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt("progress", 0)
                        progressBar.isIndeterminate = false
                        progressBar.progress = progress
                        val log = workInfo.progress.getString("log")
                        if (log != null) tvLogs.append(log + "\n")
                    }
                    else -> {}
                }
            }
        }
    }
}
