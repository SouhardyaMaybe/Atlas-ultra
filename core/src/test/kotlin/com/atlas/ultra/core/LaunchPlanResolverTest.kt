package com.atlas.ultra.core

import com.atlas.ultra.core.model.*
import com.atlas.ultra.core.network.HttpClient
import com.atlas.ultra.core.resolver.LaunchPlanResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class LaunchPlanResolverTest {
    
    @TempDir
    lateinit var tempDir: File
    
    private lateinit var mockServer: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var resolver: LaunchPlanResolver
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        
        val okHttpClient = OkHttpClient.Builder().build()
        httpClient = HttpClient(okHttpClient, moshi, kotlinx.coroutines.Dispatchers.Unconfined)
        
        val gameDir = File(tempDir, "game")
        val assetsDir = File(tempDir, "assets")
        gameDir.mkdirs()
        assetsDir.mkdirs()
        
        resolver = LaunchPlanResolver(
            httpClient = httpClient,
            gameDir = gameDir,
            assetsDir = assetsDir,
        )
    }
    
    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }
    
    @Test
    fun `resolve pre-1.13 version (1.7.10) using fixture`() = runBlocking {
        // Load fixture
        val fixture = Files.readString(File("core/src/test/resources/fixtures/1.7.10.json").toPath())
        val versionJson = moshi.adapter(VersionJson::class.java).fromJson(fixture)!!
        
        // Verify it's legacy format
        Assertions.assertFalse(versionJson.isModernFormat)
        Assertions.assertNotNull(versionJson.minecraftArguments)
        Assertions.assertEquals(8, versionJson.resolvedJavaMajorVersion)
        Assertions.assertEquals("net.minecraft.client.main.Main", versionJson.resolvedMainClass)
    }
    
    @Test
    fun `resolve modern version (1.16.5) using fixture`() = runBlocking {
        val fixture = Files.readString(File("core/src/test/resources/fixtures/1.16.5.json").toPath())
        val versionJson = moshi.adapter(VersionJson::class.java).fromJson(fixture)!!
        
        Assertions.assertTrue(versionJson.isModernFormat)
        Assertions.assertNotNull(versionJson.arguments)
        Assertions.assertEquals(8, versionJson.resolvedJavaMajorVersion)
        Assertions.assertEquals("net.minecraft.client.main.Main", versionJson.resolvedMainClass)
    }
    
    @Test
    fun `resolve latest release (1.21.11) using fixture`() = runBlocking {
        val fixture = Files.readString(File("core/src/test/resources/fixtures/1.21.11.json").toPath())
        val versionJson = moshi.adapter(VersionJson::class.java).fromJson(fixture)!!
        
        Assertions.assertTrue(versionJson.isModernFormat)
        Assertions.assertEquals(21, versionJson.resolvedJavaMajorVersion)
        Assertions.assertEquals("net.minecraft.client.main.Main", versionJson.resolvedMainClass)
    }
    
    @Test
    fun `resolve snapshot (26.4-snapshot-7) using fixture`() = runBlocking {
        val fixture = Files.readString(File("core/src/test/resources/fixtures/26.4-snapshot-7.json").toPath())
        val versionJson = moshi.adapter(VersionJson::class.java).fromJson(fixture)!!
        
        Assertions.assertTrue(versionJson.isModernFormat)
        Assertions.assertEquals(25, versionJson.resolvedJavaMajorVersion)
        Assertions.assertEquals("net.minecraft.client.main.Main", versionJson.resolvedMainClass)
    }
    
    @Test
    fun `resolve old beta (b1.8.1) using fixture`() = runBlocking {
        val fixture = Files.readString(File("core/src/test/resources/fixtures/b1.8.1.json").toPath())
        val versionJson = moshi.adapter(VersionJson::class.java).fromJson(fixture)!!
        
        Assertions.assertFalse(versionJson.isModernFormat)
        Assertions.assertEquals(8, versionJson.resolvedJavaMajorVersion)
        Assertions.assertEquals("net.minecraft.launchwrapper.Launch", versionJson.resolvedMainClass)
    }
    
    @Test
    fun `parse fabric profile from fixture`() = runBlocking {
        val fixture = Files.readString(File("core/src/test/resources/fixtures/fabric-1.21.11-0.20.1.json").toPath())
        val fabricProfile = moshi.adapter(FabricProfile::class.java).fromJson(fixture)!!
        
        Assertions.assertEquals("fabric-loader-0.20.1-1.21.11", fabricProfile.id)
        Assertions.assertEquals("1.21.11", fabricProfile.inheritsFrom)
        Assertions.assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", fabricProfile.mainClass)
        Assertions.assertEquals(7, fabricProfile.libraries.size)
    }
    
    @Test
    fun `deduplicate libraries - highest version wins`() {
        val entries = listOf(
            LibraryEntry(
                coordinate = MavenCoordinate("org.example", "lib", "1.0.0"),
                download = DownloadEntry("", "", 0, "", DownloadType.LIBRARY),
                source = LibrarySource.VANILLA,
                platform = Platform.LINUX_ARM64,
            ),
            LibraryEntry(
                coordinate = MavenCoordinate("org.example", "lib", "2.0.0"),
                download = DownloadEntry("", "", 0, "", DownloadType.LIBRARY),
                source = LibrarySource.FABRIC,
                platform = Platform.LINUX_ARM64,
            ),
        )
        
        val result = LibraryDeduplicator.deduplicate(entries)
        
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals("2.0.0", result.first().coordinate.version)
    }
    
    @Test
    fun `deduplicate libraries - same version keeps first`() {
        val entries = listOf(
            LibraryEntry(
                coordinate = MavenCoordinate("org.example", "lib", "1.0.0"),
                download = DownloadEntry("", "", 0, "", DownloadType.LIBRARY),
                source = LibrarySource.VANILLA,
                platform = Platform.LINUX_ARM64,
            ),
            LibraryEntry(
                coordinate = MavenCoordinate("org.example", "lib", "1.0.0"),
                download = DownloadEntry("", "", 0, "", DownloadType.LIBRARY),
                source = LibrarySource.FABRIC,
                platform = Platform.LINUX_ARM64,
            ),
        )
        
        val result = LibraryDeduplicator.deduplicate(entries)
        
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals(LibrarySource.VANILLA, result.first().source)
    }
}