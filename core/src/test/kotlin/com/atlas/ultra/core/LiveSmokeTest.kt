package com.atlas.ultra.core

import com.atlas.ultra.core.model.LaunchPlan
import com.atlas.ultra.core.model.Platform
import com.atlas.ultra.core.network.HttpClient
import com.atlas.ultra.core.resolver.LaunchPlanResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * Live smoke test that hits real Mojang and Fabric endpoints.
 * Only runs when RUN_LIVE_TESTS=true environment variable is set.
 * This test verifies the resolver works with live data.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_TESTS", matches = "true")
class LiveSmokeTest {
    
    private val httpClient = HttpClient()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    @Test
    fun `live resolve 1.21.11 with Fabric 0.20.1 for linux-arm64`() = runBlocking {
        val gameDir = File(System.getProperty("user.home"), ".minecraft")
        val assetsDir = File(gameDir, "assets")
        
        val resolver = LaunchPlanResolver(
            httpClient = httpClient,
            gameDir = gameDir,
            assetsDir = assetsDir,
        )
        
        val plan = resolver.resolve("1.21.11", "0.20.1", Platform.LINUX_ARM64)
        
        // Verify basic plan structure
        Assertions.assertEquals("1.21.11", plan.versionId)
        Assertions.assertEquals("0.20.1", plan.fabricLoaderVersion)
        Assertions.assertEquals(Platform.LINUX_ARM64, plan.targetPlatform)
        Assertions.assertEquals(21, plan.requiredJavaMajorVersion)
        Assertions.assertEquals(AssetLayout.MODERN_HASHED, plan.assetLayout)
        Assertions.assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", plan.mainClass)
        
        // Verify downloads are present
        Assertions.assertTrue(plan.downloads.clientJar != null)
        Assertions.assertTrue(plan.downloads.libraries.isNotEmpty())
        Assertions.assertTrue(plan.downloads.natives.isNotEmpty())
        Assertions.assertTrue(plan.downloads.assetIndex != null)
        Assertions.assertTrue(plan.downloads.assetObjects.isNotEmpty())
        Assertions.assertTrue(plan.downloads.fabricLibraries.isNotEmpty())
        
        // Verify classpath is ordered and deduplicated
        Assertions.assertTrue(plan.classpath.isNotEmpty())
        val uniqueClasspath = plan.classpath.distinct()
        Assertions.assertEquals(plan.classpath.size, uniqueClasspath.size)
        
        // Verify arguments are resolved
        Assertions.assertTrue(plan.jvmArguments.isNotEmpty())
        Assertions.assertTrue(plan.gameArguments.isNotEmpty())
        
        // Check for unresolved placeholders
        val unresolved = plan.unresolvedPlaceholders
        Assertions.assertTrue(unresolved.isEmpty() || unresolved.values.all { it.startsWith("UNRESOLVED:") })
        
        // Verify Fabric profile merged
        Assertions.assertNotNull(plan.fabricProfile)
        Assertions.assertEquals("0.20.1", plan.fabricLoaderVersion)
        
        println("Live smoke test passed!")
        println("Version: ${plan.versionId}")
        println("Java: ${plan.requiredJavaMajorVersion}")
        println("Asset Layout: ${plan.assetLayout}")
        println("Main Class: ${plan.mainClass}")
        println("Downloads: ${plan.totalDownloadCount} files, ${plan.totalDownloadSize / 1024 / 1024} MB")
        println("Classpath entries: ${plan.classpath.size}")
        println("JVM args: ${plan.jvmArguments.size}")
        println("Game args: ${plan.gameArguments.size}")
        println("Unresolved placeholders: ${unresolved.size}")
    }
    
    @Test
    fun `live resolve 1.16.5 vanilla`() = runBlocking {
        val gameDir = File(System.getProperty("user.home"), ".minecraft")
        val assetsDir = File(gameDir, "assets")
        
        val resolver = LaunchPlanResolver(
            httpClient = httpClient,
            gameDir = gameDir,
            assetsDir = assetsDir,
        )
        
        val plan = resolver.resolve("1.16.5", null, Platform.LINUX_ARM64)
        
        Assertions.assertEquals("1.16.5", plan.versionId)
        Assertions.assertNull(plan.fabricLoaderVersion)
        Assertions.assertEquals(8, plan.requiredJavaMajorVersion)
        Assertions.assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        Assertions.assertTrue(plan.downloads.libraries.isNotEmpty())
        Assertions.assertTrue(plan.classpath.isNotEmpty())
        
        println("1.16.5 vanilla test passed!")
    }
}

import org.junit.jupiter.api.Assertions