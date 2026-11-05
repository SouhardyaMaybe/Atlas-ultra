package com.atlas.ultra.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AssetIndexJson(
    val objects: Map<String, AssetObject>,
    val mapToResources: Boolean = true,
)

@Serializable
data class AssetObject(
    val hash: String,
    val size: Long,
)

extension AssetIndexJson {
    val layout: AssetLayout
        get() = if (mapToResources) AssetLayout.MODERN_HASHED else AssetLayout.LEGACY_VIRTUAL
    
    fun getAssetDownloadEntries(assetsRoot: String): List<DownloadEntry> {
        return objects.map { (path, obj) ->
            val hash = obj.hash
            val (firstTwo, rest) = hash.splitAt(2)
            val destinationPath = when (layout) {
                AssetLayout.MODERN_HASHED -> "$assetsRoot/objects/$firstTwo/$hash"
                AssetLayout.LEGACY_VIRTUAL -> "$assetsRoot/virtual/legacy/$path"
            }
            DownloadEntry(
                url = "https://resources.download.minecraft.net/$firstTwo/$hash",
                sha1 = hash,
                size = obj.size,
                destinationPath = destinationPath,
                type = DownloadType.ASSET_OBJECT,
            )
        }.toList()
    }
}