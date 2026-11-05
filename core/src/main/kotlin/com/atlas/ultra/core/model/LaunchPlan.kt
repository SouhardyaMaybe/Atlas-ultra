package com.atlas.ultra.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LaunchPlan(
    val versionId: String,
    val fabricLoaderVersion: String?,
    val targetPlatform: Platform,
    val requiredJavaMajorVersion: Int,
    val assetLayout: AssetLayout,
    val mainClass: String,
    val classpath: List<String>,  // Ordered, deduplicated paths to jars
    val jvmArguments: List<String>,  // Fully resolved
    val gameArguments: List<String>,  // Fully resolved
    val unresolvedPlaceholders: Map<String, String>,  // placeholder -> reason
    val downloads: Downloads,
    val fabricProfile: FabricProfile? = null,
) {
    val totalDownloadSize: Long
        get() = downloads.all.sumOf { it.size }
    val totalDownloadCount: Int
        get() = downloads.all.size
}

@Serializable
data class Downloads(
    val clientJar: DownloadEntry?,
    val libraries: List<DownloadEntry>,
    val natives: List<DownloadEntry>,
    val assetIndex: DownloadEntry?,
    val assetObjects: List<DownloadEntry>,
    val fabricLibraries: List<DownloadEntry> = emptyList(),
) {
    val all: List<DownloadEntry>
        get() = listOfNotNull(clientJar) + libraries + natives + listOfNotNull(assetIndex) + assetObjects + fabricLibraries
}

enum class AssetLayout {
    MODERN_HASHED,   // assets/objects/<first2>/<hash>
    LEGACY_VIRTUAL,  // assets/virtual/legacy/<path> or direct
}

@Serializable
data class FabricProfile(
    val id: String,
    val inheritsFrom: String,
    val mainClass: String,
    val libraries: List<FabricLibrary>,
    val arguments: FabricArguments,
)

@Serializable
data class FabricLibrary(
    val name: String,  // Maven coordinate string
    val url: String?,  // Maven repo URL
    val md5: String?,
    val sha1: String,
    val sha256: String?,
    val sha512: String?,
    val size: Long?,
) {
    fun toMavenCoordinate(): MavenCoordinate = MavenCoordinate.parse(name)
}

@Serializable
data class FabricArguments(
    val game: List<String>,
    val jvm: List<String>,
)