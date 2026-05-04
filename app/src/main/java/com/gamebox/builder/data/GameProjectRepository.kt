package com.gamebox.builder.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class GameProjectRepository(private val context: Context) {
    private val projectsDir: File = File(context.filesDir, "gamebox_projects").apply { mkdirs() }
    private val exportDir: File = File(context.cacheDir, "exports").apply { mkdirs() }

    fun listProjects(): List<GameProject> {
        return projectsDir
            .listFiles { file -> file.extension == "gamebox" }
            ?.mapNotNull { file -> runCatching { fromJson(file.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
    }

    fun saveProject(project: GameProject): GameProject {
        val updated = project.withUpdatedTime()
        val file = File(projectsDir, "${updated.projectId}.gamebox")
        file.writeText(toJson(updated).toString(2))
        return updated
    }

    fun deleteProject(project: GameProject) {
        File(projectsDir, "${project.projectId}.gamebox").delete()
    }

    fun exportProject(project: GameProject): File {
        exportDir.mkdirs()
        val safeName = project.gameTitle
            .trim()
            .ifBlank { "GameBoxProject" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(exportDir, "$safeName.gamebox")
        file.writeText(toJson(project.withUpdatedTime()).toString(2))
        return file
    }

    fun toJson(project: GameProject): JSONObject = JSONObject().apply {
        put("schemaVersion", project.schemaVersion)
        put("projectId", project.projectId)
        put("gameTitle", project.gameTitle)
        put("dimension", project.dimension.name)
        put("templateId", project.templateId)
        put("templateName", project.templateName)
        put("selectedCharacter", project.selectedCharacter)
        put("selectedMap", project.selectedMap)
        put("selectedObstaclePack", project.selectedObstaclePack)
        put("controlMode", project.controlMode)
        put("cameraMode", project.cameraMode)
        put("gameSpeed", project.gameSpeed)
        put("difficulty", project.difficulty)
        put("coinsEnabled", project.coinsEnabled)
        put("powerupsEnabled", project.powerupsEnabled)
        put("uiTheme", project.uiTheme)
        put("createdAt", project.createdAt)
        put("updatedAt", project.updatedAt)
    }

    private fun fromJson(rawJson: String): GameProject {
        val json = JSONObject(rawJson)
        return GameProject(
            projectId = json.optString("projectId"),
            schemaVersion = json.optInt("schemaVersion", 1),
            gameTitle = json.optString("gameTitle", "Untitled Game"),
            dimension = runCatching { GameDimension.valueOf(json.optString("dimension")) }.getOrDefault(GameDimension.TWO_D),
            templateId = json.optString("templateId", "2d_endless_runner"),
            templateName = json.optString("templateName", "2D Endless Runner"),
            selectedCharacter = json.optString("selectedCharacter", "Runner Boy"),
            selectedMap = json.optString("selectedMap", "City Night"),
            selectedObstaclePack = json.optString("selectedObstaclePack", "Mixed Starter Pack"),
            controlMode = json.optString("controlMode", "Tap Jump"),
            cameraMode = json.optString("cameraMode", "Side Camera"),
            gameSpeed = json.optInt("gameSpeed", 3),
            difficulty = json.optInt("difficulty", 2),
            coinsEnabled = json.optBoolean("coinsEnabled", true),
            powerupsEnabled = json.optBoolean("powerupsEnabled", true),
            uiTheme = json.optString("uiTheme", "Neon Purple"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
