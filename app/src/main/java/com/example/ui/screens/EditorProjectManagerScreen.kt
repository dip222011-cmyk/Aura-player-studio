package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.EditorProjectEntity
import com.example.domain.model.AspectRatioPreset
import com.example.ui.components.AuraTopHeader
import com.example.ui.components.GlassStudioCard
import com.example.ui.components.StudioActionPill
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import java.text.DateFormat
import java.util.Date

@Composable
fun EditorProjectManagerScreen(
    projects: List<EditorProjectEntity>,
    onCreateProject: (String, AspectRatioPreset) -> Unit,
    onOpenProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var selectedAspect by remember { mutableStateOf(AspectRatioPreset.RATIO_16_9) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("editor_project_manager_screen")
    ) {
        AuraTopHeader(
            title = "Pro Video Editor",
            subtitle = "${projects.size} saved & autosaved studio projects",
            trailing = {
                StudioActionPill(
                    label = "New Project",
                    icon = Icons.Rounded.Add,
                    accent = LuminousCyan,
                    onClick = { showCreateDialog = true },
                    testTag = "create_project_button"
                )
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (projects.isEmpty()) {
                item {
                    GlassStudioCard(accentColor = LuminousCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("Start Your First Edit", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Create a multi-track timeline with Media3 GPU color grading, keyframes, voice-over, and 4K export.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Text("Create Studio Project")
                        }
                    }
                }
            }

            items(projects, key = { it.id }) { project ->
                val formattedDate = remember(project.updatedAtMs) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(project.updatedAtMs))
                }
                GlassStudioCard(
                    accentColor = ElectricViolet,
                    onClick = { onOpenProject(project.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_card_${project.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.MovieCreation,
                            contentDescription = null,
                            tint = LuminousCyan,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${project.aspectRatio.removePrefix("RATIO_").replace("_", ":")} • " +
                                    "${project.clipCount} clips • ${formatTimeMs(project.durationMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = LuminousCyan
                            )
                            Text(
                                "Autosaved: $formattedDate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onOpenProject(project.id) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Open project", tint = LuminousCyan)
                        }
                        IconButton(onClick = { onDuplicateProject(project.id) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate project")
                        }
                        IconButton(onClick = { onDeleteProject(project.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete project", tint = CrimsonAlert)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Video Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        placeholder = { Text("Cyberpunk Reel") },
                        singleLine = true,
                        modifier = Modifier.testTag("new_project_name_input")
                    )
                    Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AspectRatioPreset.entries.take(3).forEach { ratio ->
                            FilterChip(
                                selected = selectedAspect == ratio,
                                onClick = { selectedAspect = ratio },
                                label = { Text(ratio.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AspectRatioPreset.entries.drop(3).forEach { ratio ->
                            FilterChip(
                                selected = selectedAspect == ratio,
                                onClick = { selectedAspect = ratio },
                                label = { Text(ratio.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateProject(projectName.ifBlank { "Studio Project" }, selectedAspect)
                        projectName = ""
                        showCreateDialog = false
                    },
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Open Timeline")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
