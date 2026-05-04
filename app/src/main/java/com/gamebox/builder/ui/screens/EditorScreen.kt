package com.gamebox.builder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamebox.builder.data.DefaultLibrary
import com.gamebox.builder.data.GameProject
import com.gamebox.builder.data.GameTemplate
import com.gamebox.builder.ui.GameBoxHeader
import com.gamebox.builder.ui.SelectablePill
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ColumnScope

@Composable
fun EditorScreen(
    project: GameProject,
    template: GameTemplate?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: (GameProject) -> Unit,
    onPreview: (GameProject) -> Unit,
    onExport: (GameProject) -> Unit
) {
    var edited by remember(project.projectId, project.updatedAt) { mutableStateOf(project) }
    val safeTemplate = template

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GameBoxHeader(
                    title = "No-Code Editor 🧩",
                    subtitle = "Only safe fixed options are allowed, so users do not create code errors."
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Button(onClick = { onPreview(edited) }) { Text("Preview") }
                }
            }

            item {
                EditorCard(title = "Project") {
                    TextField(
                        value = edited.gameTitle,
                        onValueChange = { value -> edited = edited.copy(gameTitle = value.take(36)) },
                        label = { Text("Game name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Template: ${edited.templateName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (safeTemplate != null) {
                item {
                    EditorCard(title = "Character") {
                        OptionRow(
                            options = safeTemplate.characters,
                            selected = edited.selectedCharacter,
                            onSelected = { edited = edited.copy(selectedCharacter = it) }
                        )
                    }
                }

                item {
                    EditorCard(title = "Map") {
                        OptionRow(
                            options = safeTemplate.maps,
                            selected = edited.selectedMap,
                            onSelected = { edited = edited.copy(selectedMap = it) }
                        )
                    }
                }

                item {
                    EditorCard(title = "Obstacles / Assets") {
                        OptionRow(
                            options = safeTemplate.obstaclePacks,
                            selected = edited.selectedObstaclePack,
                            onSelected = { edited = edited.copy(selectedObstaclePack = it) }
                        )
                    }
                }

                item {
                    EditorCard(title = "Controls") {
                        OptionRow(
                            options = safeTemplate.controlModes,
                            selected = edited.controlMode,
                            onSelected = { edited = edited.copy(controlMode = it) }
                        )
                    }
                }

                item {
                    EditorCard(title = "Camera") {
                        OptionRow(
                            options = safeTemplate.cameraModes,
                            selected = edited.cameraMode,
                            onSelected = { edited = edited.copy(cameraMode = it) }
                        )
                    }
                }
            }

            item {
                EditorCard(title = "Game Rules") {
                    Text("Speed: ${edited.gameSpeed}", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = edited.gameSpeed.toFloat(),
                        onValueChange = { edited = edited.copy(gameSpeed = it.roundToInt().coerceIn(1, 5)) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    Text("Difficulty: ${edited.difficulty}", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = edited.difficulty.toFloat(),
                        onValueChange = { edited = edited.copy(difficulty = it.roundToInt().coerceIn(1, 5)) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    ToggleRow(
                        title = "Coins enabled",
                        checked = edited.coinsEnabled,
                        onCheckedChange = { edited = edited.copy(coinsEnabled = it) }
                    )
                    ToggleRow(
                        title = "Powerups enabled",
                        checked = edited.powerupsEnabled,
                        onCheckedChange = { edited = edited.copy(powerupsEnabled = it) }
                    )
                }
            }

            item {
                EditorCard(title = "UI Theme") {
                    OptionRow(
                        options = DefaultLibrary.uiThemes,
                        selected = edited.uiTheme,
                        onSelected = { edited = edited.copy(uiTheme = it) }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onSave(edited) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Project")
                    }
                    OutlinedButton(onClick = { onExport(edited) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export .gamebox")
                    }
                }
            }
        }
    }
}

@Composable
 private fun EditorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun OptionRow(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options) { option ->
            SelectablePill(
                text = option,
                selected = option == selected,
                onClick = { onSelected(option) }
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
