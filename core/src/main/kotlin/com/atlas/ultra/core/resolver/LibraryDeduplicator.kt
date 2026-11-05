package com.atlas.ultra.core.resolver

import com.atlas.ultra.core.model.DownloadEntry
import com.atlas.ultra.core.model.DownloadType
import com.atlas.ultra.core.model.Library
import com.atlas.ultra.core.model.MavenCoordinate
import com.atlas.ultra.core.model.Platform
import com.atlas.ultra.core.model.VersionJson

object LibraryDeduplicator {
    /**
     * Deduplicates libraries by Maven coordinate (group:artifact:version).
     * Conflict resolution: Highest version wins (SemVer comparison).
     * If versions are equal, first encountered wins (vanilla before Fabric).
     */
    fun deduplicate(libraries: List<LibraryEntry>): List<LibraryEntry> {
        val byCoordinate = mutableMapOf<String, LibraryEntry>()
        
        for (entry in libraries) {
            val key = entry.coordinate.withoutClassifier().toString()
            val existing = byCoordinate[key]
            
            if (existing == null) {
                byCoordinate[key] = entry
            } else {
                val comparison = compareVersions(entry.coordinate.version, existing.coordinate.version)
                if (comparison > 0) {
                    // New entry has higher version, replace
                    byCoordinate[key] = entry
                } else if (comparison == 0) {
                    // Same version, keep first (vanilla wins over Fabric)
                    // Already keeping existing
                }
            }
        }
        
        return byCoordinate.values.toList()
    }
    
    /**
     * Compares two version strings using SemVer-like comparison.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = parseVersionParts(v1)
        val parts2 = parseVersionParts(v2)
        
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            if (p1 != p2) return p1.compareTo(p2)
        }
        
        // Handle qualifiers (alpha, beta, rc, etc.)
        val qual1 = extractQualifier(v1)
        val qual2 = extractQualifier(v2)
        if (qual1 != qual2) {
            return compareQualifiers(qual1, qual2)
        }
        
        return 0
    }
    
    private fun parseVersionParts(version: String): List<Int> {
        return version.split('.').mapNotNull { part ->
            val cleaned = part.takeWhile { it.isDigit() }
            cleaned.toIntOrNull()
        }
    }
    
    private fun extractQualifier(version: String): String {
        val idx = version.indexOfFirst { !it.isDigit() && it != '.' }
        return if (idx >= 0) version.substring(idx) else ""
    }
    
    private fun compareQualifiers(q1: String, q2: String): Int {
        val order = listOf("alpha", "beta", "rc", "release", "")
        val i1 = order.indexOfFirst { q1.contains(it, ignoreCase = true) }
        val i2 = order.indexOfFirst { q2.contains(it, ignoreCase = true) }
        return (i1 == -1 ? order.lastIndex : i1).compareTo(i2 == -1 ? order.lastIndex : i2)
    }
}

data class LibraryEntry(
    val coordinate: MavenCoordinate,
    val download: DownloadEntry,
    val source: LibrarySource,
    val platform: Platform,
    val type: DownloadType = DownloadType.LIBRARY,
    val library: Library? = null,
)

enum class LibrarySource {
    VANILLA,
    FABRIC,
}