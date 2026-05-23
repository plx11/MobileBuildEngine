package com.mobilebuildengine.app.core

import java.io.File

/**
 * GradleParser: 使用正則表達式解析 Gradle 依賴
 * 兼容 implementation("group:artifact:version") 及 implementation 'group:artifact:version'
 */
class GradleParser {

    private val dependencyRegex = Regex("""(implementation|api)\s*\(?['"]([^'"]+):([^'"]+):([^'"]+)['"]\)?""")

    data class Dependency(val groupId: String, val artifactId: String, val version: String)

    fun parseDependencies(buildGradle: File): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        
        if (!buildGradle.exists()) return dependencies

        buildGradle.forEachLine { line ->
            val match = dependencyRegex.find(line)
            if (match != null) {
                // match.groupValues[0] 是整個匹配，[1] 是關鍵字，[2,3,4] 是 GAV
                dependencies.add(Dependency(match.groupValues[2], match.groupValues[3], match.groupValues[4]))
            }
        }
        
        return dependencies
    }
}
