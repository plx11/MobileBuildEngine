package com.mobilebuildengine.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        setContentView(textView)

        val toolchain = ToolchainManager(this)
        
        try {
            toolchain.initBinaries()
            val binPath = toolchain.getBinaryPath("test_bin")
            
            val process = ProcessBuilder(binPath).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            
            textView.text = "Execution Result: $output"
        } catch (e: Exception) {
            textView.text = "Error: ${e.message}"
        }
    }
}
