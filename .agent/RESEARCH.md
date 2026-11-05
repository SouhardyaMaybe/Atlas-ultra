# RESEARCH

## Version Research (from real metadata URLs)

### Gradle
- **Latest stable: 9.8.2** (released Oct 19, 2026)
- Source URL: https://gradle.org/releases/
- Also available: 8.14.6 (maintenance branch)

### Kotlin
- **Latest stable: 2.4.21** (released Oct 8, 2026)
- Source URL: https://kotlinlang.org/docs/releases.html
- Language release line 2.4 supported until Dec 3, 2027

### Android Gradle Plugin (AGP)
- **Latest stable: 9.5.0** (released ~Nov 2026)
- Requires: Gradle 9.6.0, JDK 17
- Source URL: https://developer.android.com/build/releases/gradle-plugin

### KSP (Kotlin Symbol Processing)
- **Latest stable: 2.3.13** (released Oct 30, 2026)
- Compatible with Kotlin 2.3.x
- Source URL: https://github.com/google/ksp/releases
- Note: For Kotlin 2.4.x, need KSP 2.4.x (not yet released as of research date)

### Compose Compiler / Compose BOM
- **Compose Compiler latest stable: 1.5.15** (as of Aug 2024 per AndroidX releases page)
- **Compose BOM latest stable: 2026.11.00** (Nov 4, 2026 per AndroidX releases)
  - compose.material3: 1.4.0 stable, 1.5.0-rc01 RC
  - compose.foundation: 1.12.1 stable, 1.13.0-rc01 RC
  - compose.runtime: 1.12.1 stable, 1.13.0-rc01 RC
  - compose.ui: 1.12.1 stable, 1.13.0-rc01 RC
  - compose.animation: 1.12.1 stable, 1.13.0-rc01 RC
- Source URL: https://developer.android.com/jetpack/androidx/releases/compose-bom

### Material 3 Expressive
- Part of compose.material3 1.4.0+ (stable as of Nov 2026)
- Source URL: https://developer.android.com/jetpack/androidx/releases/compose-material3

### JVM Target Decision
- **Chosen: JVM 17 (Java 17)**
- Reasoning: 
  - AGP 9.5.0 requires JDK 17 (minimum and default)
  - Kotlin 2.4.x supports JVM 17+
  - Modern Minecraft versions (1.18+) require Java 17+
  - CI will use JDK 17 (Temurin/Eclipse Adoptium)
- CI JDK: 17 (Temurin distribution)

---

## Mojang Official Endpoints

### Version Manifest (v1 - legacy)
- URL: `https://launchermeta.mojang.com/mc/game/version_manifest.json`
- **Note**: This endpoint appears to serve archived data (last updated ~2022). Does not contain versions newer than 1.19.x.

### Version Manifest (v2 - current)
- URL: `https://launchermeta.mojang.com/mc/game/version_manifest_v2.json`
- Same data as v1 but with `sha1` and `complianceLevel` fields
- **Note**: Also appears to serve archived data.

### Version JSON
- URL pattern: `https://piston-meta.mojang.com/v1/packages/<hash>/<version>.json`
- Hash and URL obtained from version manifest
- **Critical finding**: The piston-meta endpoints for newer versions (1.20+) return 404/BlobNotFound. Mojang appears to have migrated to a new CDN or the manifest is stale.

### Asset Index
- URL pattern: From version JSON `assetIndex.url` (e.g., `https://piston-meta.mojang.com/v1/packages/<hash>/<version>.json`)
- Schema: `{ "objects": { "path/to/asset": { "hash": "", "size": 0 } }, "map_to_resources": true/false }`

### Library Downloads
- Maven Central: `https://libraries.minecraft.net/<group>/<artifact>/<version>/<artifact>-<version>.jar`
- Mojang libraries: `https://libraries.minecraft.net/` (same structure)
- Native classifiers: `-natives-<os>-<arch>` (e.g., `-natives-linux-arm64`)

### Client JAR Download
- URL: From version JSON `downloads.client.url`
- SHA1: From version JSON `downloads.client.sha1`
- Size: From version JSON `downloads.client.size`

---

## Fabric Official Endpoints

### Loader Versions
- URL: `https://meta.fabricmc.net/v2/versions/loader`
- Returns array of `{ "version": "", "stable": true/false, "maven": "", "build": 0, "separator": "." }`
- Latest stable as of research: 0.20.1

### Game Versions (Minecraft versions supported by Fabric)
- URL: `https://meta.fabricmc.net/v2/versions/game`
- Returns array of `{ "version": "", "stable": true/false }`
- Includes modern versions: 26.4-snapshot-7, 26.3, 1.21.11, 1.21.10, etc.
- Includes old versions down to 1.14.4

### Loader Profile (for merging)
- URL: `https://meta.fabricmc.net/v2/versions/loader/<minecraft_version>/<loader_version>/profile/json`
- Returns Fabric profile JSON with:
  - `id`: e.g., "fabric-loader-0.20.1-1.21.11"
  - `inheritsFrom`: base Minecraft version
  - `mainClass`: "net.fabricmc.loader.impl.launch.knot.KnotClient"
  - `arguments`: { "game": [], "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "] }
  - `libraries`: Array of library objects with name, url, sha1, sha256, size

### Fabric Maven Repo
- URL: `https://maven.fabricmc.net/`
- Hosts fabric-loader, fabric-api, intermediary, sponge-mixin, etc.

---

## ManifestResolverKit Investigation

- **Status: Does not exist as a maintained library**
- Search results: No such library found in Maven Central, GitHub, or common Minecraft launcher projects
- The name appears to be fictional or a misunderstanding
- **Decision: Will not use. Implement custom resolver.**

---

## Version JSON Format Differences (Pre-1.13 vs Modern)

### Modern (1.13+) - `arguments` object
```json
{
  "arguments": {
    "game": [...],
    "jvm": [...]
  }
}
```

### Legacy (pre-1.13) - `minecraftArguments` string
```json
{
  "minecraftArguments": "--username ${auth_player_name} --version ${version_name} ..."
}
```

### Verification
- **Assumption to verify**: "Assume all version JSONs use the same modern `arguments` format."
- **Finding**: FALSE. Pre-1.13 versions use `minecraftArguments` string format. Must handle both.
- ComplianceLevel field in v2 manifest: 0 = legacy, 1+ = modern (needs verification)

---

## Placeholder Variables to Resolve

### Standard Placeholders (from Mojang launcher)
| Placeholder | Description |
|-------------|-------------|
| `${auth_player_name}` | Player username |
| `${version_name}` | Version ID |
| `${game_directory}` | Game directory |
| `${assets_root}` | Assets root directory |
| `${assets_index_name}` | Asset index name (version ID) |
| `${auth_uuid}` | Player UUID |
| `${auth_access_token}` | Access token |
| `${user_type}` | User type (msa/mojang/legacy) |
| `${version_type}` | Version type (release/snapshot/old_alpha/old_beta) |
| `${classpath}` | Classpath string |
| `${natives_directory}` | Natives directory |
| `${launcher_name}` | Launcher name |
| `${launcher_version}` | Launcher version |
| `${classpath_separator}` | Classpath separator (; on Windows, : on Unix) |
| `${library_directory}` | Library directory |
| `${auth_session}` | Session ID (deprecated) |

### Fabric-specific placeholders
- `${fabric_loader_version}` - Fabric loader version
- `${fabric_mc_version}` - Minecraft version for Fabric
- Handled via `-DFabricMcEmu=` JVM argument

---

## Duplicate Library Conflict Resolution Strategy

### Options Considered:
1. **First wins** (vanilla first, then Fabric)
2. **Last wins** (Fabric overrides vanilla)
3. **Highest version wins** (compare version strings)
4. **Explicit error** on conflict

### Decision: **Highest version wins (SemVer comparison)**
- Rationale: Fabric libraries are often newer/patched versions of vanilla dependencies (e.g., ASM, Mixin)
- Implementation: Parse Maven coordinates (group:artifact:version), compare versions using SemVer, keep highest
- Exception: If versions are identical, first wins (vanilla)
- Document in ARCHITECTURE.md

---

## Missing Natives for linux-arm64 Strategy

### Options:
1. **Fail fast** with explicit error listing missing natives
2. **Skip silently** (FORBIDDEN by requirements)
3. **Download x86_64 and try emulation** (not reliable)
4. **Allow fallback to generic natives** if available

### Decision: **Fail fast with explicit error**
- Scan all libraries for `natives-linux-arm64` classifier
- If any required library (marked with `rules` allowing linux-arm64) lacks natives, throw detailed error
- Error message includes: library name, required classifier, available classifiers
- Never skip silently

---

## Asset Layout Types

### Modern (hashed) - `map_to_resources: true`
- Assets stored at `assets/objects/<first2>/<hash>`
- Referenced by hash in asset index
- Used by versions 1.6+

### Legacy/Virtual - `map_to_resources: false` or pre-1.6
- Assets stored at `assets/virtual/legacy/<path>`
- Or direct path mapping
- Used by old alpha/beta versions

### Decision: Parse `assetIndex.map_to_resources` and version to determine
- If `map_to_resources == true` → modern hashed layout
- If `map_to_resources == false` or missing → legacy/virtual layout
- Pre-1.6 versions typically use legacy

---

## Test Version Matrix (for JUnit fixtures)

| Category | Version | Type | Fabric Test |
|----------|---------|------|-------------|
| Pre-1.13 | 1.12.2 | release | Yes (0.14.25) |
| 1.13-1.20 | 1.16.5 | release | Yes (0.14.25) |
| Latest release | 1.21.11 | release | Yes (0.20.1) |
| Snapshot | 26w46a | snapshot | No (too new) |
| Old alpha/beta | b1.7.3 | old_beta | No (not supported) |
| Fabric test | 1.21.11 + 0.20.1 | release + loader | Primary test |

### Fixture Recording
- Fetch date recorded in test resources
- Commit real JSON responses as test fixtures
- One live smoke test marked with `@Tag("live")`

---

## Android Compose Dependencies (from AndroidX releases)

```kotlin
// Compose BOM 2026.11.00
implementation(platform("androidx.compose:compose-bom:2026.11.00"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.activity:activity-compose:1.13.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
```