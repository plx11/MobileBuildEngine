package com.mobilebuildengine.app.core

import java.io.File

/**
 * GradleParser: 使用正則表達式解析 Gradle 依賴
 * 兼容 implementation("group:artifact:version") 及 implementation 'group:artifact:version'
 */
class GradleParser {

    // 增強解析器，處理更複雜的依賴宣告格式
    // 支援: implementation("g:a:v"), implementation 'g:a:v', implementation group: 'g', name: 'a', version: 'v' 等
    fun parseDependencies(buildGradle: File): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        if (!buildGradle.exists()) return dependencies

        // 收集所有屬性定義 (簡易版變數解析)
        val properties = mutableMapOf<String, String>()
        val propRegex = Regex("""def\s+(\w+)\s*=\s*['"]([^'"]+)['"]""")

        buildGradle.forEachLine { line ->
            val pMatch = propRegex.find(line)
            if (pMatch != null) properties[pMatch.groupValues[1]] = pMatch.groupValues[2]
        }

        // 核心解析邏輯：捕獲括號內的三段式格式，或處理 $ 變數
        val dependencyRegex = Regex("""(implementation|api)\s*\(?['"]([^'":\s]+):([^'":\s]+):([^'":\s\$]+|[^\s\)]+)['"]\)?""")

        buildGradle.forEachLine { line ->
            val cleanLine = line.trim()
            val match = dependencyRegex.find(cleanLine)
            if (match != null) {
                var v = match.groupValues[4]
                // 簡易變數替換
                if (v.startsWith("$")) {
                    val varName = v.substring(1).removeSurrounding("{", "}")
                    v = properties[varName] ?: v
                }
                dependencies.add(Dependency(match.groupValues[2], match.groupValues[3], v))
            }
        }
        return dependencies
    }

    data class Dependency(val groupId: String, val artifactId: String, val version: String)
}
