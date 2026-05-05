package com.gamebox.builder.data

object DefaultLibrary {
    val templates: List<GameTemplate> = listOf(
        GameTemplate(
            id = "2d_endless_runner",
            name = "2D Endless Runner",
            dimension = GameDimension.TWO_D,
            description = "Build a fast side-runner with coins, obstacles, speed, difficulty and arcade controls.",
            tag = "Best first template",
            characters = listOf("Runner Boy", "Runner Girl", "Ninja", "Robot", "Biker Mascot"),
            maps = listOf("City Night", "Forest", "Desert", "Snow Road", "Cyber Tunnel"),
            obstaclePacks = listOf("Rocks + Boxes", "Spikes + Pits", "Barrels + Gates", "Traffic Cones", "Mixed Starter Pack"),
            controlModes = listOf("Tap Jump", "Jump + Slide", "Swipe"),
            cameraModes = listOf("Side Camera", "Zoomed Side Camera"),
            isPlayableInPhaseOne = true
        ),
        GameTemplate(
            id = "2d_platformer",
            name = "2D Platformer",
            dimension = GameDimension.TWO_D,
            description = "Create a Mario-style platform game with tiles, enemies, coins, checkpoints and levels.",
            tag = "Playable in Phase 4",
            characters = listOf("Pixel Hero", "Explorer", "Knight", "Robot", "Alien"),
            maps = listOf("Grass Land", "Cave", "Snow Hills", "Lava Zone", "Space Base"),
            obstaclePacks = listOf("Pits + Spikes", "Moving Platforms", "Enemy Walkers", "Coins + Keys", "Mixed Starter Pack"),
            controlModes = listOf("Left/Right + Jump", "Joystick + Jump"),
            cameraModes = listOf("Follow Camera", "Fixed Level Camera"),
            isPlayableInPhaseOne = true
        ),
        GameTemplate(
            id = "3d_endless_runner",
            name = "3D Endless Runner",
            dimension = GameDimension.THREE_D,
            description = "Make a lane-based 3D runner with maps, characters, coins, powerups and obstacles.",
            tag = "Phase 1 visual preview",
            characters = listOf("Street Runner", "Robot", "Soldier Arcade", "Ninja", "Alien Mascot"),
            maps = listOf("City Road", "Jungle Temple", "Rail Track", "Snow Bridge", "Cyber Track"),
            obstaclePacks = listOf("Rocks + Barrels", "Gates + Ramps", "Cars + Cones", "Boxes + Fences", "Mixed Starter Pack"),
            controlModes = listOf("Swipe Lanes", "Button Lanes", "Tilt Later"),
            cameraModes = listOf("Third Person", "Low Chase Camera"),
            isPlayableInPhaseOne = true
        )
    )

    val uiThemes = listOf("Neon Purple", "Cyber Blue", "Lava Orange", "Forest Green", "Mono Dark")

    fun templatesFor(dimension: GameDimension): List<GameTemplate> = templates.filter { it.dimension == dimension }

    fun templateById(id: String): GameTemplate? = templates.firstOrNull { it.id == id }

    fun createProject(template: GameTemplate): GameProject = GameProject(
        gameTitle = template.name.replace(" ", "") + " Game",
        dimension = template.dimension,
        templateId = template.id,
        templateName = template.name,
        selectedCharacter = template.characters.first(),
        selectedMap = template.maps.first(),
        selectedObstaclePack = template.obstaclePacks.first(),
        controlMode = template.controlModes.first(),
        cameraMode = template.cameraModes.first(),
        gameSpeed = 3,
        difficulty = 2,
        coinsEnabled = true,
        powerupsEnabled = true,
        uiTheme = uiThemes.first()
    )
}
