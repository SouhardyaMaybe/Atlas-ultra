# DESIGN

## Compose Screen Design - Launch Plan Viewer

### Overview
Single screen displaying the resolved Launch Plan for a selected Minecraft version (+ optional Fabric loader) as staged sections with real counts/byte totals, plus a real dry-run download/verification into app cache with true progress.

### Screen Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Atlas Ultra Launcher                    [Version Selector] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Libraries                                          │   │
│  │  ████████████░░░░░░░░░░░░░  47/52  124.3 MB/142.1 MB │   │
│  │  [●●○○] Verifying...  fabric-loader-0.20.1.jar      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Natives (linux-arm64)                              │   │
│  │  ████████████████████████  8/8  12.4 MB/12.4 MB     │   │
│  │  [●●●●] Complete                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Assets                                              │   │
│  │  ████████████████░░░░░░░░  1,234/2,156  45.2/120.8 MB │   │
│  │  [●●○○] Downloading...  textures/block/stone.png     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Fabric                                              │   │
│  │  ████████████████████████  12/12  8.7 MB/8.7 MB     │   │
│  │  [●●●●] Complete                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  [Cancel]                                    [Launch]       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### State Machine

```
┌─────────┐     start      ┌──────────┐     complete     ┌─────┐
│  IDLE   │ ──────────────► │ RUNNING  │ ───────────────► │ DONE │
└─────────┘                 └──────────┘                  └─────┘
                                  │
                                  │ failure
                                  ▼
                            ┌──────────┐
                            │  FAILED  │
                            └──────────┘
                                  │
                                  │ retry
                                  ▼
                            ┌──────────┐
                            │ RUNNING  │
                            └──────────┘
```

**States:**
- `IDLE` - Initial state, showing version selector, no plan loaded
- `RUNNING` - Dry-run in progress, showing progress per stage
- `DONE` - All stages complete, verified, ready to launch
- `FAILED` - One or more files failed verification/download, shows which file and why

### Motion Tokens (Custom)

```kotlin
// Custom motion spec for state morph
val launchPlanMorph = tween(
    durationMillis = 400,
    easing = { t -> if (t < 0.5f) 2 * t * t else 1 - pow(-2 * t + 2, 2) / 2 } // easeInOutQuad
)

// Stage entrance stagger
val stageStagger = tween(durationMillis = 150, delayMillis = 50 * index)

// Progress bar animation
val progressAnimation = tween(durationMillis = 200, easing = LinearEasing)

// Reduced motion: all durations * 0.1, no stagger
```

**Reduced Motion Support:**
```kotlin
val reducedMotion = LocalReduceMotion.current
val effectiveDuration = if (reducedMotion) 40 else 400
val effectiveStagger = if (reducedMotion) 0 else 50
```

### Stage Data Model

```kotlin
data class StageProgress(
    val name: String,           // "Libraries", "Natives", "Assets", "Fabric"
    val totalFiles: Int,
    val completedFiles: Int,
    val totalBytes: Long,
    val completedBytes: Long,
    val currentFile: String?,   // Currently processing file name
    val state: StageState,      // IDLE, RUNNING, DONE, FAILED
    val error: String?          // Error message if FAILED
)

enum class StageState { IDLE, RUNNING, DONE, FAILED }
```

### Color Scheme (Material 3 Expressive)

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `primary` | #6750A4 | #D0BCFF | Progress bar fill, active indicators |
| `primaryContainer` | #EADDFF | #4F378B | Stage card background |
| `secondary` | #625B71 | #CCC2DC | Progress bar track |
| `tertiary` | #7D5260 | #EFB8C8 | Fabric accent |
| `error` | #B3261E | #F2B8B5 | Failed state |
| `surface` | #FFFBFE | #1C1B1F | Background |
| `onSurface` | #1C1B1F | #E6E1E5 | Primary text |
| `outline` | #79747E | #938F99 | Dividers, inactive indicators |

### Component Breakdown

#### 1. Version Selector (Top App Bar)
- Dropdown for version selection (populated from version manifest)
- Optional Fabric loader version dropdown (enabled when version supports Fabric)
- "Resolve Plan" button triggers dry-run

#### 2. Stage Card (Reusable)
```kotlin
@Composable
fun StageCard(
    progress: StageProgress,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row {
                Text(progress.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                StateIndicator(progress.state)
            }
            
            // Progress bar
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row {
                    Text("${progress.completedFiles}/${progress.totalFiles}")
                    Spacer(Modifier.weight(1f))
                    Text(formatBytes(progress.completedBytes) + " / " + formatBytes(progress.totalBytes))
                }
                LinearProgressIndicator(
                    progress = progress.completedFiles.toFloat() / progress.totalFiles,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Current file / error
            if (progress.state == StageState.RUNNING) {
                Text("Verifying... ${progress.currentFile}", 
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (progress.state == StageState.FAILED) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Failed: ${progress.error}", 
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}
```

#### 3. State Indicator (Morphing)
```kotlin
@Composable
fun StateIndicator(state: StageState) {
    val color = when(state) {
        StageState.IDLE -> MaterialTheme.colorScheme.outline
        StageState.RUNNING -> MaterialTheme.colorScheme.primary
        StageState.DONE -> MaterialTheme.colorScheme.tertiary
        StageState.FAILED -> MaterialTheme.colorScheme.error
    }
    
    val scale = animateFloatAsState(
        targetValue = if (state == StageState.RUNNING) 1.2f else 1f,
        animationSpec = launchPlanMorph
    )
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(color, CircleShape)
    )
}
```

#### 4. Dry-Run Engine
```kotlin
class DryRunEngine(
    private val cacheDir: File,
    private val okHttpClient: OkHttpClient
) {
    suspend fun runDryRun(
        plan: LaunchPlan,
        onStageUpdate: (StageProgress) -> Unit
    ): DryRunResult {
        val stages = listOf(
            StageTask("Libraries", plan.libraries),
            StageTask("Natives", plan.natives),
            StageTask("Assets", plan.assetObjects),
            StageTask("Fabric", plan.fabricLibraries)
        )
        
        for (stage in stages) {
            onStageUpdate(stage.progress.copy(state = StageState.RUNNING))
            val result = stage.execute()
            onStageUpdate(stage.progress.copy(
                state = if (result.success) StageState.DONE else StageState.FAILED,
                error = result.error
            ))
            if (!result.success) return DryRunResult.Failed(stage.name, result.error!!)
        }
        return DryRunResult.Success
    }
}
```

### Data Flow

```
User selects version + Fabric
        │
        ▼
   LaunchPlanResolver.resolve(version, fabricVersion, Platform.LINUX_ARM64)
        │
        ▼
   LaunchPlan (download list, classpath, arguments, java version, asset layout)
        │
        ▼
   UI observes plan → creates StageProgress for each category
        │
        ▼
   DryRunEngine runs verification/download to cache
        │
        ├── Progress updates → UI (recomposition)
        │
        ▼
   Result: DONE or FAILED
```

### Accessibility
- Semantic roles for progress indicators
- Content descriptions for state icons
- Live region announcements for state changes
- Minimum touch target 48dp
- Color contrast WCAG AA

### Reduced Motion Implementation
```kotlin
@Composable
fun rememberReducedMotionSpec(): AnimationSpec<Float> = {
    val reduced = LocalReduceMotion.current
    if (reduced) {
        tween(durationMillis = 40, easing = LinearEasing)
    } else {
        launchPlanMorph
    }
}
```

### No Placeholder Data
- All counts, byte totals, file names come from actual `LaunchPlan`
- Version selector populated from live (or cached) version manifest
- Progress reflects real HTTP HEAD/GET requests to cache
- Error messages from actual verification failures

### Single Purposeful State Morph
The only morphing animation is the **state indicator** (●) transitioning between IDLE→RUNNING→DONE/FAILED with scale + color change. No other decorative animations.