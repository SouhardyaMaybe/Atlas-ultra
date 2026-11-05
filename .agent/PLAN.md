# PLAN

## Phase 0: Repository Setup & Research
- [ ] Run `git init` in `/root/test/atlas-ultra`
- [ ] Research latest stable Gradle, Kotlin, AGP, KSP, Compose versions from real metadata (with source URLs)
- [ ] Research Mojang official endpoints (version manifest, version JSON, asset index, library downloads)
- [ ] Research Fabric official endpoints (loader versions, profile JSONs, maven repo)
- [ ] Research "ManifestResolverKit" - verify existence, maintenance status, fitness
- [ ] Decide JVM target version (compatible with latest stable AGP + Kotlin)
- [ ] Document all findings in `.agent/RESEARCH.md` with source URLs
- [ ] Create GitHub repo `atlas-ultra` via `gh` and push initial commit

## Phase 1: Gradle Project Structure & CI
- [ ] Create root `settings.gradle.kts` with `:core` and `:app` modules
- [ ] Create root `build.gradle.kts` with plugins, repositories, version catalog
- [ ] Create `:core/build.gradle.kts` (pure Kotlin/JVM, JUnit, HTTP client, JSON parsing)
- [ ] Create `:app/build.gradle.kts` (Android, Compose, Material3 Expressive, minSdk 26)
- [ ] Create GitHub Actions workflow for CI (runs `./gradlew :core:test`)
- [ ] Push and verify diagnostic workflow runs (`./gradlew help`)

## Phase 2: Core Module - Mojang Data Fetching & Parsing
- [ ] Implement HTTP client with caching (OkHttp + Kotlinx Serialization)
- [ ] Fetch and parse version manifest (`https://launchermeta.mojang.com/mc/game/version_manifest.json`)
- [ ] Fetch and parse version JSON for given version ID
- [ ] Parse libraries (maven coordinates, natives classifiers, download URLs, SHA1, size)
- [ ] Parse asset index (URL, SHA1, size, object map)
- [ ] Parse arguments (JVM + game) with placeholder resolution
- [ ] Determine required Java major version from version JSON
- [ ] Determine asset layout type (modern hashed vs legacy/virtual)
- [ ] Handle pre-1.13 version JSON format differences (verify assumption)

## Phase 3: Core Module - Fabric Integration
- [ ] Fetch Fabric loader versions from `https://meta.fabricmc.net/v2/versions/loader`
- [ ] Fetch Fabric profile JSON for given loader version + Minecraft version
- [ ] Merge Fabric profile libraries into vanilla plan
- [ ] Document and implement duplicate library conflict resolution (Maven coordinates)
- [ ] Handle Fabric-specific main class and arguments

## Phase 4: Core Module - Launch Plan Assembly
- [ ] Build complete download list (client jar, libraries, natives, assets)
- [ ] Build ordered, deduplicated classpath
- [ ] Resolve all placeholders in JVM and game arguments
- [ ] Report any unresolved placeholders explicitly
- [ ] Handle missing natives for linux-arm64 (explicit error)
- [ ] Create data classes for LaunchPlan, DownloadEntry, ResolvedArguments, etc.

## Phase 5: Core Module - Tests
- [ ] Write JUnit tests with committed real fixtures (fetch date recorded)
- [ ] Test cases: pre-1.13, 1.13-1.20, latest release, snapshot, old alpha/beta, Fabric on chosen version
- [ ] One live smoke test (marked as such)
- [ ] Verify `./gradlew :core:test` passes in CI

## Phase 6: App Module - Compose Screen
- [ ] Write `.agent/DESIGN.md` first (UI design, motion tokens, state machine)
- [ ] Implement single Compose screen with staged display
- [ ] Libraries/Natives/Assets/Fabric sections with real counts/byte totals
- [ ] Dry-run download/verification into app cache with true progress
- [ ] Custom motion tokens, state morph (idle→running→done/failed)
- [ ] Reduced motion respected
- [ ] States: running, failed (which file, why), done
- [ ] No placeholder data

## Phase 7: Final Verification & Report
- [ ] Run full CI, verify all tests pass
- [ ] Compare result against GOAL.md (especially OUT OF SCOPE)
- [ ] Write final report: verified with evidence, not verified, real data vs assumptions contradictions