package com.mobilebuildengine.app.core

import org.eclipse.jdt.internal.compiler.batch.Main
import java.io.PrintWriter
import java.io.File

/**
 * 升級版 Java 編譯引擎：支援 Java 17，滿足現代 Android SDK 編譯需求
 */
class JavaCompilerEngine {
    fun compile(srcDir: File, classDir: File, classpath: String): Boolean {
        if (!srcDir.exists() || !srcDir.isDirectory) {
            return true
        }

        val javaSources = srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .map { it.absolutePath }
            .toList()

        if (javaSources.isEmpty()) return true

        // -17 指定 Java 17 標準，滿足現代 Android SDK (compileSdk 35+) 的編譯需求
        val args = mutableListOf(
            "-d", classDir.absolutePath,
            "-cp", classpath,
            "-17",
            "-preserveState",
            "-warn:none"
        )
        args.addAll(javaSources)

        return Main.compile(args.toTypedArray(), PrintWriter(System.out), PrintWriter(System.err))
    }
}
