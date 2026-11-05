# GOAL

## One-paragraph Goal
Build a pure Kotlin/JVM library module `:core` that, given a Minecraft Java Edition version ID and optional Fabric loader version, produces a complete verified Launch Plan (download list with URLs/SHA1/size/destination, ordered deduplicated classpath + main class, fully resolved JVM/game argument lists, required Java major version, asset layout type) for target platform `linux-arm64` using live Mojang and Fabric metadata endpoints. Then build an Android app module `:app` (minSdk 26, Jetpack Compose Material 3 Expressive) that displays the resolved plan as staged sections with real counts/byte totals and performs a real dry-run download/verification into the app cache with true progress, using custom motion tokens and a single purposeful state morph (idle→running→done/failed) respecting reduced motion.

## IN SCOPE
- `:core` Kotlin/JVM module (no Android dependencies)
  - Fetch and parse Mojang version manifest + version JSONs
  - Fetch and parse Fabric loader metadata + profile JSONs
  - Merge Fabric profile into vanilla plan correctly
  - Resolve all `${placeholder}` variables in arguments (game directory, assets directory, version, etc.)
  - Handle pre-1.13 (legacy), 1.13-1.20, latest release, snapshot, old alpha/beta versions
  - Handle missing natives for linux-arm64 (explicit error, not silent skip)
  - Deduplicate libraries by Maven coordinates (group:artifact:version), document conflict resolution
  - JUnit tests with committed real fixtures (fetch date recorded) + 1 live smoke test
  - CI runs `./gradlew :core:test` and shows results
- `:app` Android module (minSdk 26)
  - Single Compose screen showing plan stages: Libraries, Natives, Assets, Fabric
  - Real counts and byte totals per stage
  - Real dry-run download/verification into app cache with true progress
  - Material 3 Expressive only
  - Custom motion tokens, one state morph (idle→running→done/failed)
  - Reduced motion respected
  - States: running, failed (which file, why), done
  - No placeholder data, no extra screens/features
- GitHub repo `atlas-ultra` public, CI via GitHub Actions
- All builds in GitHub Actions only (no local builds)
- Documentation in `.agent/` files (RESEARCH.md, DESIGN.md, LOG.md, FAILURES.md, ARCHITECTURE.md)

## OUT OF SCOPE
- Actual game launch (only launch plan generation)
- Multiple screens or navigation
- Settings/preferences UI
- Mod loaders other than Fabric (Forge, Quilt, etc.)
- Non-Android Compose targets (Desktop, Web)
- Background download manager / persistence across app restarts
- Authentication / Microsoft login
- Multiplayer server integration
- Resource pack / shader support
- Custom Java runtime management (only version detection)
- Any "ManifestResolverKit" unless verified to exist, be maintained, and fit (report findings)
- Guessing schemas - must inspect real JSON from official endpoints