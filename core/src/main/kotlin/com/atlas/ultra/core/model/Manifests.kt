package com.atlas.ultra.core.model

import kotlinx.serialization.Serializable

@Serializable
data class VersionManifest(
    val latest: LatestVersions,
    val versions: List<VersionInfo>,
)

@Serializable
data class LatestVersions(
    val release: String,
    val snapshot: String,
)

@Serializable
data class VersionInfo(
    val id: String,
    val type: String,  // "release", "snapshot", "old_alpha", "old_beta"
    val url: String,
    val time: String,
    val releaseTime: String,
    val sha1: String? = null,
    val complianceLevel: Int = 0,
)

@Serializable
data class FabricLoaderVersions(
    val loaders: List<FabricLoaderVersion>,
)

@Serializable
data class FabricLoaderVersion(
    val version: String,
    val stable: Boolean,
    val maven: String,
    val build: Int,
    val separator: String,
)

@Serializable
data class FabricGameVersions(
    val versions: List<FabricGameVersion>,
)

@Serializable
data class FabricGameVersion(
    val version: String,
    val stable: Boolean,
)