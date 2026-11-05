package com.atlas.ultra.core.resolver

import com.atlas.ultra.core.model.Library
import com.atlas.ultra.core.model.MavenCoordinate
import com.atlas.ultra.core.model.Platform

object NativeLibraryChecker {
    data class MissingNative(
        val library: Library,
        val coordinate: MavenCoordinate,
        val availableClassifiers: List<String>,
    )
    
    /**
     * Checks if all required libraries have natives for the target platform.
     * Throws an exception with details if any are missing.
     */
    @Throws(MissingNativesException::class)
    fun checkNatives(libraries: List<Library>, platform: Platform): List<MissingNative> {
        val missing = mutableListOf<MissingNative>()
        
        for (lib in libraries) {
            if (lib.natives != null) {
                val classifier = platform.classifier
                val hasNative = lib.downloads?.classifiers?.containsKey(classifier) == true
                
                if (!hasNative) {
                    val available = lib.downloads?.classifiers?.keys.toList() ?: emptyList()
                    missing.add(MissingNative(lib, lib.getMavenCoordinate(), available))
                }
            }
        }
        
        if (missing.isNotEmpty()) {
            throw MissingNativesException(missing)
        }
        
        return missing
    }
}

class MissingNativesException(
    val missing: List<NativeLibraryChecker.MissingNative>
) : Exception(
    "Missing natives for ${missing.first().library.getMavenCoordinate()} on ${missing.first().library.natives?.linux ?: "linux"}. " +
    "Required classifier: ${missing.first().availableClassifiers.firstOrNull() ?: "natives-linux-arm64"}. " +
    "Available: ${missing.map { it.availableClassifiers.joinToString(", ") }.joinToString("; ")}"
) {
    override fun getMessage(): String {
        return missing.map { m ->
            "Library ${m.coordinate} missing natives for linux-arm64. " +
            "Available classifiers: ${m.availableClassifiers.joinToString(", ")}"
        }.joinToString("\n")
    }
}