package com.atlas.ultra.core.resolver

import com.atlas.ultra.core.model.LaunchPlan
import com.atlas.ultra.core.model.Platform
import java.io.File

class PlaceholderResolver(
    private val gameDir: File,
    private val assetsDir: File,
    private val launcherName: String = "Atlas Ultra",
    private val launcherVersion: String = "0.1.0",
) {
    private val placeholders = mutableMapOf<String, String>()
    private val unresolved = mutableMapOf<String, String>()
    
    fun resolveAll(plan: LaunchPlan): Pair<List<String>, List<String>> {
        setupPlaceholders(plan)
        val resolvedJvm = plan.jvmArguments.map { resolve(it) }
        val resolvedGame = plan.gameArguments.map { resolve(it) }
        return resolvedJvm to resolvedGame
    }
    
    private fun setupPlaceholders(plan: LaunchPlan) {
        val versionName = plan.versionId
        val nativesDir = File(gameDir, "versions/$versionName/natives")
        val libraryDir = File(gameDir, "libraries")
        val classpath = plan.classpath.joinToString(File.pathSeparator)
        val classpathSeparator = File.pathSeparator
        
        placeholders["auth_player_name"] = "Player"
        placeholders["version_name"] = versionName
        placeholders["game_directory"] = gameDir.absolutePath
        placeholders["assets_root"] = assetsDir.absolutePath
        placeholders["assets_index_name"] = versionName
        placeholders["auth_uuid"] = "00000000-0000-0000-0000-000000000000"
        placeholders["auth_access_token"] = "dummy_token"
        placeholders["user_type"] = "msa"
        placeholders["version_type"] = when {
            plan.versionId.contains("snapshot", ignoreCase = true) -> "snapshot"
            plan.versionId.startsWith("b") -> "old_beta"
            plan.versionId.startsWith("a") -> "old_alpha"
            else -> "release"
        }
        placeholders["classpath"] = classpath
        placeholders["natives_directory"] = nativesDir.absolutePath
        placeholders["launcher_name"] = launcherName
        placeholders["launcher_version"] = launcherVersion
        placeholders["classpath_separator"] = classpathSeparator
        placeholders["library_directory"] = libraryDir.absolutePath
        placeholders["auth_session"] = "dummy_session"
        
        // Fabric placeholders
        plan.fabricLoaderVersion?.let { fabricVersion ->
            placeholders["fabric_loader_version"] = fabricVersion
            placeholders["fabric_mc_version"] = plan.versionId
        }
    }
    
    private fun resolve(arg: String): String {
        var result = arg
        val placeholderRegex = "\\$\\{([^}]+)}".toRegex()
        val matches = placeholderRegex.findAll(result).toList()
        
        for (match in matches) {
            val placeholder = match.groupValues[1]
            val replacement = placeholders[placeholder]
                ?: unresolved.getOrPut(placeholder) { "UNRESOLVED:$placeholder" }
            result = result.replace("\${$placeholder}", replacement)
        }
        return result
    }
    
    fun getUnresolved(): Map<String, String> = unresolved.toMap()
}