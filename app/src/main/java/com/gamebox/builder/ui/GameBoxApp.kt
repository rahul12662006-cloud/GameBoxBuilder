package com.gamebox.builder.ui

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.gamebox.builder.data.DefaultLibrary
import com.gamebox.builder.data.GameDimension
import com.gamebox.builder.data.GameProject
import com.gamebox.builder.data.GameProjectRepository
import com.gamebox.builder.ui.screens.EditorScreen
import com.gamebox.builder.ui.screens.HomeScreen
import com.gamebox.builder.ui.screens.PreviewScreen
import com.gamebox.builder.ui.screens.TemplatePickerScreen
import kotlinx.coroutines.launch

private sealed interface GameBoxScreen {
    data object Home : GameBoxScreen
    data class TemplatePicker(val dimension: GameDimension) : GameBoxScreen
    data class Editor(val project: GameProject) : GameBoxScreen
    data class Preview(val project: GameProject) : GameBoxScreen
}

@Composable
fun GameBoxApp() {
    val context = LocalContext.current
    val repository = remember { GameProjectRepository(context.applicationContext) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<GameBoxScreen>(GameBoxScreen.Home) }
    var projects by remember { mutableStateOf(emptyList<GameProject>()) }

    fun refreshProjects() {
        projects = repository.listProjects()
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    when (val current = screen) {
        GameBoxScreen.Home -> HomeScreen(
            projects = projects,
            snackbarHostState = snackbarHostState,
            onMake2D = { screen = GameBoxScreen.TemplatePicker(GameDimension.TWO_D) },
            onMake3D = { screen = GameBoxScreen.TemplatePicker(GameDimension.THREE_D) },
            onOpenProject = { screen = GameBoxScreen.Editor(it) },
            onDeleteProject = { project ->
                repository.deleteProject(project)
                refreshProjects()
                scope.launch { snackbarHostState.showSnackbar("Project deleted") }
            }
        )

        is GameBoxScreen.TemplatePicker -> TemplatePickerScreen(
            dimension = current.dimension,
            templates = DefaultLibrary.templatesFor(current.dimension),
            onBack = { screen = GameBoxScreen.Home },
            onTemplateSelected = { template ->
                val project = repository.saveProject(DefaultLibrary.createProject(template))
                refreshProjects()
                screen = GameBoxScreen.Editor(project)
            }
        )

        is GameBoxScreen.Editor -> EditorScreen(
            project = current.project,
            template = DefaultLibrary.templateById(current.project.templateId),
            snackbarHostState = snackbarHostState,
            onBack = {
                refreshProjects()
                screen = GameBoxScreen.Home
            },
            onSave = { updatedProject ->
                val saved = repository.saveProject(updatedProject)
                refreshProjects()
                screen = GameBoxScreen.Editor(saved)
                scope.launch { snackbarHostState.showSnackbar("Project saved") }
            },
            onPreview = { updatedProject ->
                val saved = repository.saveProject(updatedProject)
                refreshProjects()
                screen = GameBoxScreen.Preview(saved)
            },
            onExport = { updatedProject ->
                val saved = repository.saveProject(updatedProject)
                val file = repository.exportProject(saved)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "${saved.gameTitle}.gamebox")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export .gamebox project"))
            }
        )

        is GameBoxScreen.Preview -> PreviewScreen(
            project = current.project,
            onBack = { screen = GameBoxScreen.Editor(current.project) }
        )
    }
}
