package com.atlas.ultra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shapes.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.atlas.ultra.core.LaunchPlanApi
import com.atlas.ultra.core.model.LaunchPlan
import com.atlas.ultra.core.model.Platform
import com.atlas.ultra.core.model.StageState
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LaunchPlanScreen(viewModel = viewModel)
                }
            }
        }
    }
}

class MainViewModel(application: AtlasUltraApplication) : ViewModel() {
    
    private val resolver = application.launchPlanResolver
    
    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set
    
    fun resolvePlan(versionId: String, fabricVersion: String?) {
        uiState = UiState.Loading(versionId, fabricVersion)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val plan = resolver.resolve(versionId, fabricVersion, Platform.LINUX_ARM64)
                uiState = UiState.Success(plan)
            } catch (e: Exception) {
                uiState = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun startDryRun(plan: LaunchPlan) {
        uiState = UiState.DryRunning(plan, StageProgress.initial(plan))
        viewModelScope.launch(Dispatchers.IO) {
            // Simulate dry-run with progress updates
            val stages = listOf(
                "Libraries" to plan.downloads.libraries,
                "Natives" to plan.downloads.natives,
                "Assets" to plan.downloads.assetObjects,
                "Fabric" to plan.downloads.fabricLibraries,
            )
            
            var currentProgress = StageProgress.initial(plan)
            
            for ((stageName, entries) in stages) {
                currentProgress = currentProgress.copy(
                    currentStage = stageName,
                    state = StageState.RUNNING,
                )
                uiState = UiState.DryRunning(plan, currentProgress)
                
                // Simulate processing each entry
                for (i in entries.indices) {
                    currentProgress = currentProgress.copy(
                        completedFiles = currentProgress.completedFiles + 1,
                        completedBytes = currentProgress.completedBytes + entries[i].size,
                        currentFile = entries[i].fileName,
                    )
                    uiState = UiState.DryRunning(plan, currentProgress)
                    // Small delay for visible progress
                    kotlinx.coroutines.delay(50)
                }
                
                currentProgress = currentProgress.copy(state = StageState.DONE)
                uiState = UiState.DryRunning(plan, currentProgress)
            }
            
            uiState = UiState.DryRunComplete(plan)
        }
    }
    
    sealed interface UiState {
        data class Idle : UiState
        data class Loading(val versionId: String, val fabricVersion: String?) : UiState
        data class Success(val plan: LaunchPlan) : UiState
        data class DryRunning(val plan: LaunchPlan, val progress: StageProgress) : UiState
        data class DryRunComplete(val plan: LaunchPlan) : UiState
        data class Error(val message: String) : UiState
    }
    
    data class StageProgress(
        val currentStage: String = "",
        val totalFiles: Int = 0,
        val completedFiles: Int = 0,
        val totalBytes: Long = 0,
        val completedBytes: Long = 0,
        val currentFile: String? = null,
        val state: StageState = StageState.IDLE,
        val stageStates: Map<String, StageState> = mapOf(),
    ) {
        companion object {
            fun initial(plan: LaunchPlan): StageProgress {
                val totalFiles = plan.totalDownloadCount
                val totalBytes = plan.totalDownloadSize
                val stageStates = mapOf(
                    "Libraries" to StageState.IDLE,
                    "Natives" to StageState.IDLE,
                    "Assets" to StageState.IDLE,
                    "Fabric" to StageState.IDLE,
                )
                return StageProgress(
                    totalFiles = totalFiles,
                    totalBytes = totalBytes,
                    stageStates = stageStates,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchPlanScreen(viewModel: MainViewModel) {
    val uiState = viewModel.uiState
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top App Bar with version selector
        TopAppBar(
            title = { Text("Atlas Ultra Launcher") },
            actions = {
                VersionSelector(viewModel = viewModel)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        
        // Content
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                is MainViewModel.UiState.Idle -> {
                    Text(
                        text = "Select a version to generate launch plan",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                is MainViewModel.UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                    )
                }
                is MainViewModel.UiState.Success -> {
                    LaunchPlanContent(plan = uiState.plan, viewModel = viewModel)
                }
                is MainViewModel.UiState.DryRunning -> {
                    LaunchPlanContent(plan = uiState.plan, viewModel = viewModel, progress = uiState.progress)
                }
                is MainViewModel.UiState.DryRunComplete -> {
                    LaunchPlanContent(plan = uiState.plan, viewModel = viewModel, progress = StageProgress.initial(uiState.plan).copy(state = StageState.DONE))
                }
                is MainViewModel.UiState.Error -> {
                    Text(
                        text = "Error: ${uiState.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSelector(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val versions = listOf("1.21.11", "1.16.5", "1.12.2", "b1.8.1", "26.4-snapshot-7")
    val fabricVersions = listOf("0.20.1", "0.16.14", "0.14.25")
    
    var selectedVersion by remember { mutableStateOf(versions[0]) }
    var selectedFabric by remember { mutableStateOf(fabricVersions[0]) }
    
    IconButton(onClick = { expanded = !expanded }) {
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select version")
    }
    
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Version") }, onClick = { /* noop */ })
        versions.forEach { version ->
            DropdownMenuItem(
                text = { Text(version) },
                onClick = { selectedVersion = version }
            )
        }
        DropdownMenuItem(text = { Text("Fabric Loader") }, onClick = { /* noop */ })
        fabricVersions.forEach { fabric ->
            DropdownMenuItem(
                text = { Text(fabric) },
                onClick = { selectedFabric = fabric }
            )
        }
        DropdownMenuItem(
            text = { Text("Resolve Plan", color = MaterialTheme.colorScheme.primary) },
            onClick = {
                viewModel.resolvePlan(selectedVersion, selectedFabric)
                expanded = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchPlanContent(
    plan: LaunchPlan,
    viewModel: MainViewModel,
    progress: MainViewModel.StageProgress = MainViewModel.StageProgress.initial(plan),
) {
    val scrollState = rememberScrollState()
    
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Plan summary
        PlanSummaryCard(plan = plan)
        
        // Stages
        StageCard(
            name = "Libraries",
            entries = plan.downloads.libraries,
            progress = progress,
            isFabric = false,
        )
        
        StageCard(
            name = "Natives (linux-arm64)",
            entries = plan.downloads.natives,
            progress = progress,
            isFabric = false,
        )
        
        StageCard(
            name = "Assets",
            entries = plan.downloads.assetObjects,
            progress = progress,
            isFabric = false,
        )
        
        if (plan.fabricLoaderVersion != null) {
            StageCard(
                name = "Fabric",
                entries = plan.downloads.fabricLibraries,
                progress = progress,
                isFabric = true,
            )
        }
        
        // Action buttons
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f))
            TextButton(
                onClick = { /* Cancel */ },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { viewModel.startDryRun(plan) },
                modifier = Modifier.padding(16.dp),
                enabled = progress.state != StageState.RUNNING,
            ) {
                Text(if (progress.state == StageState.RUNNING) "Running..." else "Start Dry Run")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSummaryCard(plan: LaunchPlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Launch Plan: ${plan.versionId}${plan.fabricLoaderVersion?.let { " + Fabric $it" } ?: ""}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Java Version", "Java ${plan.requiredJavaMajorVersion}")
                InfoItem("Asset Layout", plan.assetLayout.name.replace("_", " "))
                InfoItem("Main Class", plan.mainClass.substringAfterLast("."))
            }
            
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Total Files", plan.totalDownloadCount.toString())
                InfoItem("Total Size", formatBytes(plan.totalDownloadSize))
                InfoItem("Classpath Entries", plan.classpath.size.toString())
            }
            
            if (plan.unresolvedPlaceholders.isNotEmpty()) {
                Text(
                    text = "⚠ ${plan.unresolvedPlaceholders.size} unresolved placeholders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageCard(
    name: String,
    entries: List<com.atlas.ultra.core.model.DownloadEntry>,
    progress: MainViewModel.StageProgress,
    isFabric: Boolean = false,
) {
    val stageProgress = progress.stageStates[name] ?: StageState.IDLE
    val isCurrentStage = progress.currentStage == name
    val stageCompleted = progress.completedFiles > 0 && stageProgress == StageState.DONE
    val stageFailed = stageProgress == StageState.FAILED
    
    val stageTotalFiles = entries.size
    val stageTotalBytes = entries.sumOf { it.size }
    val stageCompletedFiles = if (stageProgress == StageState.DONE) stageTotalFiles else if (isCurrentStage) progress.completedFiles else 0
    val stageCompletedBytes = if (stageProgress == StageState.DONE) stageTotalBytes else if (isCurrentStage) progress.completedBytes else 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                
                StageIndicator(state = stageProgress)
            }
            
            // Progress bar
            LinearProgressIndicator(
                progress = if (stageTotalFiles > 0) stageCompletedFiles.toFloat() / stageTotalFiles else 0f,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            
            // Stats
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${stageCompletedFiles}/${stageTotalFiles} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatBytes(stageCompletedBytes)} / ${formatBytes(stageTotalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Current file or error
            if (isCurrentStage && stageProgress == StageState.RUNNING) {
                Text(
                    text = "Verifying... ${progress.currentFile ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (stageFailed) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Failed: ${progress.currentFile ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { /* Retry */ }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun StageIndicator(state: StageState) {
    val color = when (state) {
        StageState.IDLE -> MaterialTheme.colorScheme.outline
        StageState.RUNNING -> MaterialTheme.colorScheme.primary
        StageState.DONE -> MaterialTheme.colorScheme.tertiary
        StageState.FAILED -> MaterialTheme.colorScheme.error
    }
    
    val scale = remember { mutableStateOf(1f) }
    
    // Animate scale for RUNNING state
    androidx.compose.animation.animateFloatAsState(
        targetValue = if (state == StageState.RUNNING) 1.2f else 1f,
        animationSpec = androidx.compose.animation.tween(400)
    ).also { animatedScale ->
        androidx.compose.runtime.LaunchedEffect(animatedScale.value) {
            scale.value = animatedScale.value
        }
    }
    
    androidx.compose.ui.graphics.GraphicsLayerModifier(
        graphicsLayer = {
            scaleX = scale.value
            scaleY = scale.value
        }
    ).also { layer ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(color, CircleShape)
        )
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}