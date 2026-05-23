package com.mobilebuildengine.app.core

import java.io.File

/**
 * GradleParser: 使用正則表達式解析 Gradle 依賴
 * 兼容 implementation("group:artifact:version") 及 implementation 'group:artifact:version'
 */
class GradleParser {

    // 支援 implementation/api，括號可選，單雙引號，以及變量 (例如 'implementation group:artifact:version' 或 implementation("group:artifact:version"))
    private val dependencyRegex = Regex("""(implementation|api)\s*\(?['"]([^'":\s]+):([^'":\s]+):([^'":\s]+)['"]\)?|\s*[\$]?\{?[\w\.]+\}?[\s]*""")

    data class Dependency(val groupId: String, val artifactId: String, val version: String)

    fun parseDependencies(buildGradle: File): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        
        if (!buildGradle.exists()) return dependencies

        buildGradle.forEachLine { line ->
            val trimLine = line.trim()
            // 嘗試匹配標準格式
            val match = Regex("""(implementation|api)\s*\(?['"]([^'":]+):([^'":]+):([^'":]+)['"]\)?""").find(trimLine)
            if (match != null) {
                dependencies.add(Dependency(match.groupValues[2], match.groupValues[3], match.groupValues[4]))
            }
        }
        
        return dependencies
    }
}
