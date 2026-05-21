package com.mobilebuildengine.app.core

import java.io.File

/**
 * GradleParser: 使用正則表達式解析 Gradle 依賴
 * 兼容 implementation("group:artifact:version") 及 implementation 'group:artifact:version'
 */
class GradleParser {

    private val dependencyRegex = Regex("""implementation\s*\(?['"]([^'"]+):([^'"]+):([^'"]+)['"]\)?""")

    data class Dependency(val groupId: String, val artifactId: String, val version: String)

    fun parseDependencies(buildGradle: File): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        
        if (!buildGradle.exists()) return dependencies

        buildGradle.forEachLine { line ->
            val match = dependencyRegex.find(line)
            if (match != null) {
                val (group, artifact, version) = match.destructured
                dependencies.add(Dependency(group, artifact, version))
            }
        }
        
        return dependencies
    }
}
