package com.atlas.ultra.core.model

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class VersionJson(
    val id: String,
    val inheritsFrom: String? = null,
    val releaseTime: String,
    val time: String,
    val type: String,
    val mainClass: String,
    val javaVersion: JavaVersion? = null,
    val libraries: List<Library> = emptyList(),
    val arguments: Arguments? = null,
    val minecraftArguments: String? = null,  // Legacy pre-1.13
    val assetIndex: AssetIndex? = null,
    val downloads: VersionDownloads? = null,
    val logging: Logging? = null,
    val complianceLevel: Int = 0,  // 0 = legacy, 1+ = modern
) {
    val isModernFormat: Boolean
        get() = complianceLevel > 0 || arguments != null
    
    val resolvedMainClass: String
        get() = mainClass
    
    val resolvedJavaMajorVersion: Int
        get() = javaVersion?.majorVersion ?: when {
            this.id.startsWith("1.") && this.id.substringAfter("1.").substringBefore(".").toIntOrNull() ?: 0 >= 18 -> 17
            this.id.startsWith("1.") && this.id.substringAfter("1.").substringBefore(".").toIntOrNull() ?: 0 >= 17 -> 16
            else -> 8
        }
}

@Serializable
data class JavaVersion(
    val component: String,
    val majorVersion: Int,
)

@Serializable
data class Arguments(
    val game: List<ArgumentEntry>,
    val jvm: List<ArgumentEntry>,
)

@Serializable
sealed class ArgumentEntry {
    @Serializable
    @ContextualSerializer(ArgumentValueSerializer::class)
    data class Value(val value: String) : ArgumentEntry()
    
    @Serializable
    data class Rules(val rules: List<Rule>, val value: List<String>?) : ArgumentEntry()
}

object ArgumentValueSerializer : KSerializer<ArgumentEntry.Value> {
    override val descriptor: SerialDescriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("ArgumentValue", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: ArgumentEntry.Value) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): ArgumentEntry.Value {
        return ArgumentEntry.Value(decoder.decodeString())
    }
}

@Serializable
data class Rule(
    val action: String,  // "allow" or "disallow"
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null,
)

@Serializable
data class OsRule(
    val name: String?,  // "windows", "linux", "osx"
    val arch: String?,  // "x86", "x64", "arm64", etc.
    val version: String? = null,
)

@Serializable
data class Library(
    val name: String,  // Maven coordinate
    val url: String? = null,
    val downloads: LibraryDownloads? = null,
    val natives: Natives? = null,
    val rules: List<Rule>? = null,
    val extract: ExtractRules? = null,
) {
    fun getMavenCoordinate(): MavenCoordinate = MavenCoordinate.parse(name)
    
    fun isApplicableTo(platform: Platform): Boolean {
        return rules?.all { rule -> rule.appliesTo(platform) } ?: true
    }
}

@Serializable
data class LibraryDownloads(
    val artifact: ArtifactDownload? = null,
    val classifiers: Map<String, ArtifactDownload>? = null,
)

@Serializable
data class ArtifactDownload(
    val url: String,
    val sha1: String,
    val size: Long,
)

@Serializable
data class Natives(
    val linux: String? = null,
    val windows: String? = null,
    val osx: String? = null,
    // Modern: classifiers like "natives-linux-arm64"
)

@Serializable
data class ExtractRules(
    val exclude: List<String> = emptyList(),
)

@Serializable
data class AssetIndex(
    val id: String,
    val url: String,
    val sha1: String,
    val size: Long,
    val totalSize: Long,
    val mapToResources: Boolean = true,  // Modern = true, Legacy = false
)

@Serializable
data class VersionDownloads(
    val client: ArtifactDownload? = null,
    val server: ArtifactDownload? = null,
)

@Serializable
data class Logging(
    val client: LoggingClient? = null,
)

@Serializable
data class LoggingClient(
    val file: LoggingFile? = null,
    val argument: String? = null,
    val type: String? = null,
)

@Serializable
data class LoggingFile(
    val id: String,
    val sha1: String,
    val size: Long,
    val url: String,
)

extension Rule {
    fun appliesTo(platform: Platform): Boolean {
        return when {
            action == "disallow" -> {
                os?.let { osRule ->
                    (osRule.name == null || osRule.name == platform.os) &&
                    (osRule.arch == null || osRule.arch == platform.arch) &&
                    (osRule.version == null || System.getProperty("os.version").startsWith(osRule.version))
                } ?: true
            }
            action == "allow" -> {
                os?.let { osRule ->
                    (osRule.name == platform.os) &&
                    (osRule.arch == null || osRule.arch == platform.arch)
                } ?: false
            }
            else -> true
        }
    }
}