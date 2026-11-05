package com.atlas.ultra.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadEntry(
    val url: String,
    val sha1: String,
    val size: Long,
    val destinationPath: String,
    val type: DownloadType,
    val mavenCoordinate: MavenCoordinate? = null,
) {
    val fileName: String = destinationPath.substringAfterLast('/')
}

enum class DownloadType {
    CLIENT_JAR,
    LIBRARY,
    NATIVE_LIBRARY,
    ASSET_OBJECT,
    ASSET_INDEX,
}

@Serializable
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String? = null,
) {
    override fun toString(): String = "$group:$artifact:$version${classifier?.let { ":$it" } ?: ""}"
    
    fun withoutClassifier(): MavenCoordinate = copy(classifier = null)
    
    companion object {
        fun parse(coord: String): MavenCoordinate {
            val parts = coord.split(':')
            return when (parts.size) {
                3 -> MavenCoordinate(parts[0], parts[1], parts[2])
                4 -> MavenCoordinate(parts[0], parts[1], parts[2], parts[3])
                else -> throw IllegalArgumentException("Invalid Maven coordinate: $coord")
            }
        }
    }
}