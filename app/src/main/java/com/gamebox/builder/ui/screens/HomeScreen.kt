package com.gamebox.builder.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamebox.builder.data.GameProject
import com.gamebox.builder.ui.GameBoxHeader
import com.gamebox.builder.ui.InfoRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    projects: List<GameProject>,
    snackbarHostState: SnackbarHostState,
    onMake2D: () -> Unit,
    onMake3D: () -> Unit,
    onOpenProject: (GameProject) -> Unit,
    onDeleteProject: (GameProject) -> Unit
) {
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
                    title = "GameBox Builder 🎮",
                    subtitle = "Template-based no-code Android game builder. Pick, customize, preview and export."
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeCard(
                        title = "Make 2D Game",
                        subtitle = "Runner + platformer foundation",
                        emoji = "🕹️",
                        modifier = Modifier.weight(1f),
                        onClick = onMake2D
                    )
                    ModeCard(
                        title = "Make 3D Game",
                        subtitle = "3D runner preview foundation",
                        emoji = "🏁",
                        modifier = Modifier.weight(1f),
                        onClick = onMake3D
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Phase 1 included ✅", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        InfoRow("Project system", ".gamebox JSON")
                        InfoRow("Preview", "inside app")
                        InfoRow("Templates", "2D Runner, 2D Platformer, 3D Runner")
                        InfoRow("APK build", "GitHub Actions workflow")
                    }
                }
            }

            item {
                Text(
                    text = "My Projects",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (projects.isEmpty()) {
                item {
                    EmptyProjectsCard()
                }
            } else {
                items(projects, key = { it.projectId }) { project ->
                    ProjectCard(
                        project = project,
                        onOpenProject = { onOpenProject(project) },
                        onDeleteProject = { onDeleteProject(project) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Text(text = title, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyProjectsCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "No projects yet. Start with Make 2D Game or Make 3D Game.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: GameProject,
    onOpenProject: () -> Unit,
    onDeleteProject: () -> Unit
) {
    val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(project.updatedAt))
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(project.gameTitle, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("${project.templateName} • Updated $date", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenProject) { Text("Open") }
                OutlinedButton(onClick = onDeleteProject) { Text("Delete") }
            }
        }
    }
}
