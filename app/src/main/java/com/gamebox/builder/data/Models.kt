package com.gamebox.builder.data

import java.util.UUID

enum class GameDimension(val label: String) {
    TWO_D("2D"),
    THREE_D("3D")
}

data class GameTemplate(
    val id: String,
    val name: String,
    val dimension: GameDimension,
    val description: String,
    val tag: String,
    val characters: List<String>,
    val maps: List<String>,
    val obstaclePacks: List<String>,
    val controlModes: List<String>,
    val cameraModes: List<String>,
    val isPlayableInPhaseOne: Boolean
)

data class GameProject(
    val projectId: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = 1,
    val gameTitle: String,
    val dimension: GameDimension,
    val templateId: String,
    val templateName: String,
    val selectedCharacter: String,
    val selectedMap: String,
    val selectedObstaclePack: String,
    val controlMode: String,
    val cameraMode: String,
    val gameSpeed: Int = 3,
    val difficulty: Int = 2,
    val coinsEnabled: Boolean = true,
    val powerupsEnabled: Boolean = true,
    val uiTheme: String = "Neon Purple",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun withUpdatedTime(): GameProject = copy(updatedAt = System.currentTimeMillis())
}
