package com.mobilebuildengine.app.core

import org.eclipse.jdt.internal.compiler.batch.Main
import java.io.PrintWriter

/**
 * 整合 ECJ 編譯器，實現 Android 沙箱內的 Java 轉 Bytecode
 */
class JavaCompilerEngine {
    fun compile(srcDir: java.io.File, classDir: java.io.File, classpath: String): Boolean {
        // ECJ 是 Eclipse Java Compiler，不需要完整 JDK 即可將 .java 編譯為 .class
        val args = arrayOf(
            "-d", classDir.absolutePath,
            "-cp", classpath,
            "-1.8", // 指定 Java 8 語言標準
            srcDir.absolutePath
        )
        return Main.compile(args, PrintWriter(System.out), PrintWriter(System.err))
    }
}
