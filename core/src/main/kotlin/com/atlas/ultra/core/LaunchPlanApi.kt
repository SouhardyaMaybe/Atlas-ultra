package com.atlas.ultra.core

import com.atlas.ultra.core.model.LaunchPlan
import com.atlas.ultra.core.model.Platform
import com.atlas.ultra.core.network.HttpClient
import com.atlas.ultra.core.resolver.LaunchPlanResolver
import java.io.File

/**
 * Main API entry point for generating a Launch Plan.
 * 
 * Usage:
 * ```kotlin
 * val plan = LaunchPlanApi.resolve(
 *     versionId = "1.21.11",
 *     fabricLoaderVersion = "0.20.1",
 *     gameDir = File("/path/to/.minecraft"),
 *     assetsDir = File("/path/to/.minecraft/assets"),
 * )
 * ```
 */
object LaunchPlanApi {
    
    @JvmStatic
    fun resolve(
        versionId: String,
        fabricLoaderVersion: String? = null,
        targetPlatform: Platform = Platform.LINUX_ARM64,
        gameDir: File = File(System.getProperty("user.home"), ".minecraft"),
        assetsDir: File = File(System.getProperty("user.home"), ".minecraft/assets"),
        launcherName: String = "Atlas Ultra",
        launcherVersion: String = "0.1.0",
    ): LaunchPlan {
        val httpClient = HttpClient()
        val resolver = LaunchPlanResolver(
            httpClient = httpClient,
            gameDir = gameDir,
            assetsDir = assetsDir,
            launcherName = launcherName,
            launcherVersion = launcherVersion,
        )
        
        return kotlinx.coroutines.runBlocking {
            resolver.resolve(versionId, fabricLoaderVersion, targetPlatform)
        }
    }
    
    /**
     * Asynchronous version for use in coroutine contexts.
     */
    suspend fun resolveAsync(
        versionId: String,
        fabricLoaderVersion: String? = null,
        targetPlatform: Platform = Platform.LINUX_ARM64,
        gameDir: File = File(System.getProperty("user.home"), ".minecraft"),
        assetsDir: File = File(System.getProperty("user.home"), ".minecraft/assets"),
        launcherName: String = "Atlas Ultra",
        launcherVersion: String = "0.1.0",
    ): LaunchPlan {
        val httpClient = HttpClient()
        val resolver = LaunchPlanResolver(
            httpClient = httpClient,
            gameDir = gameDir,
            assetsDir = assetsDir,
            launcherName = launcherName,
            launcherVersion = launcherVersion,
        )
        
        return resolver.resolve(versionId, fabricLoaderVersion, targetPlatform)
    }
}