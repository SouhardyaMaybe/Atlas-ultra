package com.atlas.ultra.core.resolver

import com.atlas.ultra.core.model.*
import com.atlas.ultra.core.network.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LaunchPlanResolver(
    private val httpClient: HttpClient,
    private val gameDir: File,
    private val assetsDir: File,
    private val launcherName: String = "Atlas Ultra",
    private val launcherVersion: String = "0.1.0",
) {
    private val versionCache = ConcurrentHashMap<String, VersionJson>()
    private val assetIndexCache = ConcurrentHashMap<String, AssetIndexJson>()
    private val fabricProfileCache = ConcurrentHashMap<String, FabricProfile>()
    
    suspend fun resolve(
        versionId: String,
        fabricLoaderVersion: String?,
        targetPlatform: Platform = Platform.LINUX_ARM64,
    ): LaunchPlan {
        // 1. Fetch and parse version JSON
        val versionJson = fetchVersionJson(versionId)
        
        // 2. Fetch Fabric profile if requested
        val fabricProfile = fabricLoaderVersion?.let { loaderVersion ->
            fetchFabricProfile(versionId, loaderVersion)
        }
        
        // 3. Determine required Java version
        val javaVersion = fabricProfile?.let { it.libraries
            .firstOrNull { it.name.contains("java") }
            ?.name?.substringAfterLast(':')?.substringBefore(':')?.toIntOrNull()
        } ?: versionJson.resolvedJavaMajorVersion
        
        // 4. Determine asset layout
        val assetIndex = fetchAssetIndex(versionJson)
        val assetLayout = assetIndex.layout
        
        // 5. Collect all libraries (vanilla + fabric)
        val allLibraries = collectLibraries(versionJson, fabricProfile, targetPlatform)
        
        // 6. Deduplicate libraries
        val deduplicated = LibraryDeduplicator.deduplicate(allLibraries)
        
        // 7. Check natives
        NativeLibraryChecker.checkNatives(deduplicated.map { it.library }, targetPlatform)
        
        // 8. Build download list
        val downloads = buildDownloads(versionJson, assetIndex, fabricProfile, deduplicated, targetPlatform)
        
        // 9. Build classpath
        val classpath = buildClasspath(deduplicated, versionJson, fabricProfile)
        
        // 10. Resolve arguments
        val (jvmArgs, gameArgs) = resolveArguments(versionJson, fabricProfile)
        
        // 11. Build placeholder resolver and resolve
        val placeholderResolver = PlaceholderResolver(gameDir, assetsDir, launcherName, launcherVersion)
        val (resolvedJvm, resolvedGame) = placeholderResolver.resolveAll(
            LaunchPlan(
                versionId = versionId,
                fabricLoaderVersion = fabricLoaderVersion,
                targetPlatform = targetPlatform,
                requiredJavaMajorVersion = javaVersion,
                assetLayout = assetLayout,
                mainClass = fabricProfile?.mainClass ?: versionJson.resolvedMainClass,
                classpath = emptyList(),
                jvmArguments = jvmArgs,
                gameArguments = gameArgs,
                unresolvedPlaceholders = emptyMap(),
                downloads = downloads,
                fabricProfile = fabricProfile,
            )
        )
        
        return LaunchPlan(
            versionId = versionId,
            fabricLoaderVersion = fabricLoaderVersion,
            targetPlatform = targetPlatform,
            requiredJavaMajorVersion = javaVersion,
            assetLayout = assetLayout,
            mainClass = fabricProfile?.mainClass ?: versionJson.resolvedMainClass,
            classpath = classpath,
            jvmArguments = resolvedJvm,
            gameArguments = resolvedGame,
            unresolvedPlaceholders = placeholderResolver.getUnresolved(),
            downloads = downloads,
            fabricProfile = fabricProfile,
        )
    }
    
    private suspend fun fetchVersionJson(versionId: String): VersionJson {
        return versionCache.computeIfAbsent(versionId) {
            runBlocking {
                // First get manifest to find the version URL
                val manifest = fetchVersionManifest()
                val versionInfo = manifest.versions.firstOrNull { it.id == versionId }
                    ?: throw IllegalArgumentException("Version not found: $versionId")
                
                val json = httpClient.getJson<VersionJson>(versionInfo.url)
                json
            }
        }
    }
    
    private suspend fun fetchVersionManifest(): VersionManifest {
        val url = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
        return httpClient.getJson<VersionManifest>(url)
    }
    
    private suspend fun fetchFabricProfile(mcVersion: String, loaderVersion: String): FabricProfile {
        val key = "$mcVersion:$loaderVersion"
        return fabricProfileCache.computeIfAbsent(key) {
            runBlocking {
                val url = "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVersion/profile/json"
                httpClient.getJson<FabricProfile>(url)
            }
        }
    }
    
    private suspend fun fetchAssetIndex(versionJson: VersionJson): AssetIndexJson {
        val assetIndexUrl = versionJson.assetIndex?.url
            ?: throw IllegalStateException("No asset index in version JSON")
        
        return assetIndexCache.computeIfAbsent(assetIndexUrl) {
            runBlocking {
                httpClient.getJson<AssetIndexJson>(assetIndexUrl)
            }
        }
    }
    
    private fun collectLibraries(
        versionJson: VersionJson,
        fabricProfile: FabricProfile?,
        platform: Platform,
    ): List<LibraryEntry> {
        val entries = mutableListOf<LibraryEntry>()
        
        // Vanilla libraries
        for (lib in versionJson.libraries) {
            if (lib.isApplicableTo(platform)) {
                val downloads = lib.downloads
                val artifact = downloads?.artifact
                val classifiers = downloads?.classifiers ?: emptyMap()
                
                // Main artifact
                artifact?.let { art ->
                    entries.add(LibraryEntry(
                        coordinate = lib.getMavenCoordinate(),
                        download = DownloadEntry(
                            url = art.url,
                            sha1 = art.sha1,
                            size = art.size,
                            destinationPath = "libraries/${mavenPath(lib.getMavenCoordinate())}",
                            type = DownloadType.LIBRARY,
                            mavenCoordinate = lib.getMavenCoordinate(),
                        ),
                        source = LibrarySource.VANILLA,
                        platform = platform,
                        library = lib,
                    ))
                }
                
                // Native classifiers
                val nativeClassifier = platform.classifier
                classifiers[nativeClassifier]?.let { nativeArt ->
                    entries.add(LibraryEntry(
                        coordinate = lib.getMavenCoordinate().copy(classifier = nativeClassifier),
                        download = DownloadEntry(
                            url = nativeArt.url,
                            sha1 = nativeArt.sha1,
                            size = nativeArt.size,
                            destinationPath = "libraries/${mavenPath(lib.getMavenCoordinate().copy(classifier = nativeClassifier))}",
                            type = DownloadType.NATIVE_LIBRARY,
                            mavenCoordinate = lib.getMavenCoordinate().copy(classifier = nativeClassifier),
                        ),
                        source = LibrarySource.VANILLA,
                        platform = platform,
                        library = lib,
                    ))
                }
            }
        }
        
        // Fabric libraries
        fabricProfile?.libraries?.forEach { fabricLib ->
            val coord = fabricLib.toMavenCoordinate()
            val repoUrl = fabricLib.url ?: "https://maven.fabricmc.net/"
            val artifactUrl = "$repoUrl${mavenPath(coord)}"
            
            entries.add(LibraryEntry(
                coordinate = coord,
                download = DownloadEntry(
                    url = artifactUrl,
                    sha1 = fabricLib.sha1,
                    size = fabricLib.size ?: 0,
                    destinationPath = "libraries/${mavenPath(coord)}",
                    type = DownloadType.LIBRARY,
                    mavenCoordinate = coord,
                ),
                source = LibrarySource.FABRIC,
                platform = platform,
            ))
        }
        
        return entries
    }
    
    private fun buildDownloads(
        versionJson: VersionJson,
        assetIndex: AssetIndexJson,
        fabricProfile: FabricProfile?,
        deduplicated: List<LibraryEntry>,
        platform: Platform,
    ): Downloads {
        val libraries = deduplicated.filter { it.type != DownloadType.NATIVE_LIBRARY }.map { it.download }
        val natives = deduplicated.filter { it.type == DownloadType.NATIVE_LIBRARY }.map { it.download }
        val fabricLibs = deduplicated.filter { it.source == LibrarySource.FABRIC }.map { it.download }
        
        val clientJar = versionJson.downloads?.client?.let { client ->
            DownloadEntry(
                url = client.url,
                sha1 = client.sha1,
                size = client.size,
                destinationPath = "versions/${versionJson.id}/${versionJson.id}.jar",
                type = DownloadType.CLIENT_JAR,
            )
        }
        
        val assetIndexEntry = versionJson.assetIndex?.let { ai ->
            DownloadEntry(
                url = ai.url,
                sha1 = ai.sha1,
                size = ai.size,
                destinationPath = "assets/indexes/${ai.id}.json",
                type = DownloadType.ASSET_INDEX,
            )
        }
        
        val assetObjects = assetIndex.getAssetDownloadEntries(assetsDir.absolutePath)
        
        return Downloads(
            clientJar = clientJar,
            libraries = libraries,
            natives = natives,
            assetIndex = assetIndexEntry,
            assetObjects = assetObjects,
            fabricLibraries = fabricLibs,
        )
    }
    
    private fun buildClasspath(
        deduplicated: List<LibraryEntry>,
        versionJson: VersionJson,
        fabricProfile: FabricProfile?,
    ): List<String> {
        // Order: vanilla libraries first, then fabric, then client jar
        val libPaths = deduplicated
            .filter { it.type == DownloadType.LIBRARY }
            .map { "libraries/${it.download.destinationPath.substringAfter("libraries/")}" }
            .distinct()
        
        val clientJarPath = "versions/${versionJson.id}/${versionJson.id}.jar"
        
        return libPaths + clientJarPath
    }
    
    private fun resolveArguments(
        versionJson: VersionJson,
        fabricProfile: FabricProfile?,
    ): Pair<List<String>, List<String>> {
        var jvmArgs = mutableListOf<String>()
        var gameArgs = mutableListOf<String>()
        
        if (versionJson.isModernFormat) {
            versionJson.arguments?.jvm?.forEach { entry ->
                when (entry) {
                    is Arguments.Value -> jvmArgs.add(entry.value)
                    is Arguments.Rules -> if (entry.rules.all { it.appliesTo(Platform.LINUX_ARM64) }) {
                        entry.value?.forEach { jvmArgs.add(it) }
                    }
                }
            }
            
            versionJson.arguments?.game?.forEach { entry ->
                when (entry) {
                    is Arguments.Value -> gameArgs.add(entry.value)
                    is Arguments.Rules -> if (entry.rules.all { it.appliesTo(Platform.LINUX_ARM64) }) {
                        entry.value?.forEach { gameArgs.add(it) }
                    }
                }
            }
        } else {
            // Legacy format: parse minecraftArguments string
            versionJson.minecraftArguments?.split(" ")?.forEach { gameArgs.add(it) }
            // Legacy JVM args are typically hardcoded or minimal
        }
        
        // Add Fabric arguments
        fabricProfile?.arguments?.jvm?.forEach { jvmArgs.add(it) }
        fabricProfile?.arguments?.game?.forEach { gameArgs.add(it) }
        
        return jvmArgs to gameArgs
    }
    
    private fun mavenPath(coord: MavenCoordinate): String {
        return "${coord.group.replace('.', '/')}/${coord.artifact}/${coord.version}/${coord.artifact}-${coord.version}${coord.classifier?.let { "-$it" } ?: ""}.jar"
    }
}