package com.gamebox.builder.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamebox.builder.data.GameProject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private data class PlatformerPlatform(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val moving: Boolean = false
)

private data class PlatformerCoin(
    val id: Int,
    val x: Float,
    val y: Float,
    val collected: Boolean = false
)

private data class PlatformerEnemy(
    val id: Int,
    val x: Float,
    val y: Float,
    val alive: Boolean = true,
    val walkDir: Float = -1f
)

private data class PlatformerSpike(
    val id: Int,
    val x: Float,
    val y: Float
)

private data class PlatformerPit(
    val startX: Float,
    val endX: Float
)

@Composable
fun PlatformerGameScreen(
    project: GameProject,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scorePrefs = remember(project.projectId) { context.getSharedPreferences("gamebox_platformer_scores", 0) }
    val bestScoreKey = "best_platformer_${project.projectId}"
    val levelLength = 5.8f
    val groundY = 0.80f

    var runSeed by remember(project.projectId, project.updatedAt) { mutableIntStateOf(1) }
    var isRunning by remember(runSeed) { mutableStateOf(true) }
    var isGameOver by remember(runSeed) { mutableStateOf(false) }
    var isLevelComplete by remember(runSeed) { mutableStateOf(false) }
    var playerX by remember(runSeed) { mutableFloatStateOf(0.20f) }
    var playerY by remember(runSeed) { mutableFloatStateOf(groundY) }
    var velocityY by remember(runSeed) { mutableFloatStateOf(0f) }
    var moveInput by remember(runSeed) { mutableFloatStateOf(0f) }
    var moveTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var checkpointX by remember(runSeed) { mutableFloatStateOf(0.20f) }
    var lastFrameNanos by remember(runSeed) { mutableLongStateOf(0L) }
    var score by remember(runSeed) { mutableIntStateOf(0) }
    var coins by remember(runSeed) { mutableIntStateOf(0) }
    var lives by remember(runSeed) { mutableIntStateOf(3) }
    var bestScore by remember(project.projectId) { mutableIntStateOf(scorePrefs.getInt(bestScoreKey, 0)) }

    val level = remember(project.selectedMap, project.selectedObstaclePack, project.difficulty) {
        buildPlatformerLevel(project, groundY)
    }
    val coinsList = remember(runSeed, project.coinsEnabled, project.selectedObstaclePack) {
        mutableStateListOf<PlatformerCoin>().apply {
            if (project.coinsEnabled) addAll(level.coins)
        }
    }
    val enemies = remember(runSeed, project.selectedObstaclePack, project.difficulty) {
        mutableStateListOf<PlatformerEnemy>().apply { addAll(level.enemies) }
    }

    val isOverPit: (Float) -> Boolean = { x -> level.pits.any { x > it.startX && x < it.endX } }

    fun nudge(direction: Float) {
        if (!isGameOver && !isLevelComplete) {
            moveInput = direction
            moveTimer = 0.22f
            isRunning = true
        }
    }

    fun jump() {
        val onSurface = abs(playerY - groundY) < 0.010f || level.platforms.any { platform ->
            abs(playerY - platform.y) < 0.012f && playerX > platform.x - 0.06f && playerX < platform.x + platform.width + 0.06f
        }
        if (!isGameOver && !isLevelComplete && onSurface) {
            velocityY = -1.06f - project.difficulty * 0.02f
            isRunning = true
        }
    }

    fun restartFromCheckpoint() {
        playerX = checkpointX
        playerY = groundY
        velocityY = 0f
        moveInput = 0f
        moveTimer = 0f
        isRunning = true
        isGameOver = false
        isLevelComplete = false
    }

    fun fullRestart() {
        runSeed += 1
    }

    LaunchedEffect(score) {
        if (score > bestScore) {
            bestScore = score
            scorePrefs.edit().putInt(bestScoreKey, score).apply()
        }
    }

    LaunchedEffect(runSeed, project.gameSpeed, project.difficulty, project.selectedObstaclePack, project.coinsEnabled) {
        while (true) {
            val frameTime = withFrameNanos { it }
            if (lastFrameNanos == 0L) {
                lastFrameNanos = frameTime
                continue
            }
            val dt = ((frameTime - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = frameTime

            if (!isRunning || isGameOver || isLevelComplete) continue

            if (moveTimer > 0f) {
                val moveSpeed = 0.72f + project.gameSpeed * 0.050f
                playerX = (playerX + moveInput * moveSpeed * dt).coerceIn(0.10f, levelLength - 0.20f)
                moveTimer = (moveTimer - dt).coerceAtLeast(0f)
            }

            val previousY = playerY
            val gravity = 2.35f + project.difficulty * 0.05f
            velocityY += gravity * dt
            playerY += velocityY * dt

            var landed = false
            level.platforms.forEach { platform ->
                val effectiveY = platform.y
                val withinX = playerX > platform.x - 0.045f && playerX < platform.x + platform.width + 0.045f
                if (velocityY >= 0f && withinX && previousY <= effectiveY && playerY >= effectiveY) {
                    playerY = effectiveY
                    velocityY = 0f
                    landed = true
                }
            }
            if (!landed && !isOverPit(playerX) && velocityY >= 0f && previousY <= groundY && playerY >= groundY) {
                playerY = groundY
                velocityY = 0f
            }
            if (playerY > 1.05f) {
                lives -= 1
                if (lives <= 0) {
                    isGameOver = true
                    isRunning = false
                } else {
                    restartFromCheckpoint()
                }
            }

            val newCoins = coinsList.map { coin ->
                if (!coin.collected && abs(playerX - coin.x) < 0.060f && abs(playerY - coin.y) < 0.18f) {
                    coins += 1
                    score += 125
                    coin.copy(collected = true)
                } else coin
            }.filter { !it.collected }
            coinsList.clear()
            coinsList.addAll(newCoins)

            val enemySpeed = 0.12f + project.difficulty * 0.015f
            val newEnemies = enemies.map { enemy ->
                if (!enemy.alive) enemy else {
                    var nextX = enemy.x + enemy.walkDir * enemySpeed * dt
                    var nextDir = enemy.walkDir
                    if (nextX < enemy.x - 0.18f || nextX > enemy.x + 0.18f) nextDir *= -1f
                    nextX = nextX.coerceIn(enemy.x - 0.20f, enemy.x + 0.20f)
                    enemy.copy(x = nextX, walkDir = nextDir)
                }
            }
            enemies.clear()
            enemies.addAll(newEnemies)

            val touchedSpike = level.spikes.any { spike -> abs(playerX - spike.x) < 0.045f && abs(playerY - spike.y) < 0.08f }
            val touchedEnemy = enemies.firstOrNull { enemy -> enemy.alive && abs(playerX - enemy.x) < 0.065f && abs(playerY - enemy.y) < 0.12f }
            if (touchedEnemy != null) {
                if (velocityY > 0.22f && playerY < touchedEnemy.y - 0.03f) {
                    enemies.remove(touchedEnemy)
                    velocityY = -0.62f
                    score += 180
                } else {
                    lives -= 1
                    if (lives <= 0) {
                        isGameOver = true
                        isRunning = false
                    } else {
                        restartFromCheckpoint()
                    }
                }
            }
            if (touchedSpike) {
                lives -= 1
                if (lives <= 0) {
                    isGameOver = true
                    isRunning = false
                } else {
                    restartFromCheckpoint()
                }
            }

            if (playerX > level.checkpointX && checkpointX < level.checkpointX) {
                checkpointX = level.checkpointX
                score += 100
            }
            score = max(score, (playerX * 70).toInt() + coins * 100)
            if (playerX > level.flagX) {
                isLevelComplete = true
                isRunning = false
                score += 500 + lives * 150
            }
        }
    }

    val cameraX by animateFloatAsState(
        targetValue = (playerX - 0.24f).coerceIn(0f, levelLength - 1f),
        animationSpec = tween(140),
        label = "platformer-camera"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("2D Platformer Playtest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            OutlinedButton(onClick = { isRunning = !isRunning }, enabled = !isGameOver && !isLevelComplete) {
                Text(if (isRunning) "Pause" else "Play")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(project.gameTitle, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Score: $score")
                    Text("Best: $bestScore")
                    Text("Lives: $lives")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Coins: $coins")
                    Text("Map: ${project.selectedMap}")
                    Text("Difficulty: ${project.difficulty}")
                }
                Text("Pack: ${project.selectedObstaclePack}  •  Character: ${project.selectedCharacter}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PlatformerCanvas(
                project = project,
                level = level,
                playerX = playerX,
                playerY = playerY,
                cameraX = cameraX,
                coins = coinsList,
                enemies = enemies,
                checkpointX = checkpointX,
                modifier = Modifier.fillMaxSize()
            )

            if (isGameOver || isLevelComplete) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xDD080914), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(if (isLevelComplete) "Level Complete" else "Game Over", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Score $score • Best $bestScore • Coins $coins", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { restartFromCheckpoint() }) { Text("Checkpoint") }
                        Button(onClick = { fullRestart() }) { Text("Restart") }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { nudge(-1f) }, modifier = Modifier.weight(1f), enabled = !isGameOver && !isLevelComplete) { Text("Left") }
            Button(onClick = { jump() }, modifier = Modifier.weight(1f), enabled = !isGameOver && !isLevelComplete) { Text("Jump") }
            Button(onClick = { nudge(1f) }, modifier = Modifier.weight(1f), enabled = !isGameOver && !isLevelComplete) { Text("Right") }
        }
        Spacer(Modifier.height(2.dp))
    }
}

private data class PlatformerLevel(
    val platforms: List<PlatformerPlatform>,
    val coins: List<PlatformerCoin>,
    val enemies: List<PlatformerEnemy>,
    val spikes: List<PlatformerSpike>,
    val pits: List<PlatformerPit>,
    val checkpointX: Float,
    val flagX: Float
)

private fun buildPlatformerLevel(project: GameProject, groundY: Float): PlatformerLevel {
    val extraEnemy = project.difficulty >= 3 || project.selectedObstaclePack.contains("Enemy", ignoreCase = true)
    val movingPlatforms = project.selectedObstaclePack.contains("Moving", ignoreCase = true)
    val moreSpikes = project.selectedObstaclePack.contains("Spike", ignoreCase = true) || project.difficulty >= 4
    val platforms = listOf(
        PlatformerPlatform(1, 0.64f, 0.66f, 0.28f),
        PlatformerPlatform(2, 1.18f, 0.55f, 0.26f, movingPlatforms),
        PlatformerPlatform(3, 1.72f, 0.64f, 0.34f),
        PlatformerPlatform(4, 2.48f, 0.58f, 0.32f, movingPlatforms),
        PlatformerPlatform(5, 3.16f, 0.69f, 0.30f),
        PlatformerPlatform(6, 3.84f, 0.54f, 0.34f),
        PlatformerPlatform(7, 4.58f, 0.64f, 0.30f)
    )
    val coins = listOf(
        PlatformerCoin(1, 0.74f, 0.58f),
        PlatformerCoin(2, 1.28f, 0.47f),
        PlatformerCoin(3, 1.88f, 0.56f),
        PlatformerCoin(4, 2.62f, 0.50f),
        PlatformerCoin(5, 3.28f, 0.61f),
        PlatformerCoin(6, 3.98f, 0.46f),
        PlatformerCoin(7, 4.72f, 0.56f),
        PlatformerCoin(8, 5.18f, 0.68f)
    )
    val enemies = buildList {
        add(PlatformerEnemy(1, 1.58f, groundY))
        add(PlatformerEnemy(2, 3.04f, groundY))
        if (extraEnemy) add(PlatformerEnemy(3, 4.30f, groundY))
    }
    val spikes = buildList {
        add(PlatformerSpike(1, 2.20f, groundY))
        add(PlatformerSpike(2, 3.58f, groundY))
        if (moreSpikes) add(PlatformerSpike(3, 4.92f, groundY))
    }
    val pits = listOf(
        PlatformerPit(0.98f, 1.08f),
        PlatformerPit(2.92f, 3.04f),
        PlatformerPit(4.34f, 4.48f)
    )
    return PlatformerLevel(platforms, coins, enemies, spikes, pits, checkpointX = 2.74f, flagX = 5.30f)
}

@Composable
private fun PlatformerCanvas(
    project: GameProject,
    level: PlatformerLevel,
    playerX: Float,
    playerY: Float,
    cameraX: Float,
    coins: List<PlatformerCoin>,
    enemies: List<PlatformerEnemy>,
    checkpointX: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080914))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            drawPlatformerWorld(
                project = project,
                level = level,
                playerX = playerX,
                playerY = playerY,
                cameraX = cameraX,
                coins = coins,
                enemies = enemies,
                checkpointX = checkpointX
            )
        }
    }
}

private fun DrawScope.drawPlatformerWorld(
    project: GameProject,
    level: PlatformerLevel,
    playerX: Float,
    playerY: Float,
    cameraX: Float,
    coins: List<PlatformerCoin>,
    enemies: List<PlatformerEnemy>,
    checkpointX: Float
) {
    val w = size.width
    val h = size.height
    val primary = platformerThemePrimary(project.uiTheme)
    val bgTop = when (project.selectedMap) {
        "Cave" -> Color(0xFF171225)
        "Snow Hills" -> Color(0xFF162B3D)
        "Lava Zone" -> Color(0xFF35110D)
        "Space Base" -> Color(0xFF080C23)
        else -> Color(0xFF102A24)
    }

    fun sx(worldX: Float): Float = (worldX - cameraX) * w
    fun sy(worldY: Float): Float = worldY * h

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(bgTop, Color(0xFF070812))),
        cornerRadius = CornerRadius(34f, 34f),
        size = size
    )

    repeat(8) { index ->
        val x = (index * 0.42f - (cameraX * 0.22f % 0.42f)) * w
        drawCircle(primary.copy(alpha = 0.10f), radius = 34f + index % 3 * 8f, center = Offset(x, h * (0.18f + (index % 4) * 0.06f)))
    }

    level.pits.forEach { pit ->
        val left = sx(pit.startX)
        val right = sx(pit.endX)
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(left, sy(0.80f)),
            size = Size(right - left, h * 0.14f),
            cornerRadius = CornerRadius(8f, 8f)
        )
    }

    drawLine(primary.copy(alpha = 0.9f), Offset(0f, sy(0.80f)), Offset(w, sy(0.80f)), strokeWidth = 8f)
    drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, sy(0.84f)), Offset(w, sy(0.84f)), strokeWidth = 3f)

    level.platforms.forEach { platform ->
        val left = sx(platform.x)
        val top = sy(platform.y)
        val width = platform.width * w
        val color = if (platform.moving) Color(0xFF00E5FF) else primary
        drawRoundRect(
            color = color.copy(alpha = 0.88f),
            topLeft = Offset(left, top),
            size = Size(width, 18f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = color.copy(alpha = 0.20f),
            topLeft = Offset(left + 6f, top + 18f),
            size = Size(width - 12f, 42f),
            cornerRadius = CornerRadius(8f, 8f)
        )
    }

    coins.forEach { coin ->
        drawCircle(Color(0xFFFFD54F), radius = 13f, center = Offset(sx(coin.x), sy(coin.y)))
        drawCircle(Color.White.copy(alpha = 0.45f), radius = 5f, center = Offset(sx(coin.x) - 4f, sy(coin.y) - 4f))
    }

    level.spikes.forEach { spike ->
        val x = sx(spike.x)
        val y = sy(spike.y)
        val path = Path().apply {
            moveTo(x - 28f, y)
            lineTo(x - 13f, y - 45f)
            lineTo(x + 1f, y)
            lineTo(x + 15f, y - 45f)
            lineTo(x + 30f, y)
            close()
        }
        drawPath(path, Color(0xFFFF7043))
    }

    enemies.forEach { enemy ->
        if (enemy.alive) {
            val x = sx(enemy.x)
            val bottom = sy(enemy.y)
            drawRoundRect(
                color = Color(0xFFFF5252),
                topLeft = Offset(x - 23f, bottom - 48f),
                size = Size(46f, 48f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 7f, center = Offset(x + 8f, bottom - 31f))
        }
    }

    val checkpointScreenX = sx(level.checkpointX)
    drawLine(Color(0xFFFFD54F), Offset(checkpointScreenX, sy(0.80f)), Offset(checkpointScreenX, sy(0.56f)), strokeWidth = 6f)
    drawRoundRect(
        color = if (checkpointX >= level.checkpointX) Color(0xFF64FFDA) else Color(0xFFFFD54F),
        topLeft = Offset(checkpointScreenX, sy(0.56f)),
        size = Size(56f, 32f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    val flagX = sx(level.flagX)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(flagX, sy(0.80f)), Offset(flagX, sy(0.45f)), strokeWidth = 7f)
    drawRoundRect(
        color = primary,
        topLeft = Offset(flagX, sy(0.45f)),
        size = Size(70f, 40f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    val playerScreenX = sx(playerX)
    val playerBottom = sy(playerY)
    val playerHeight = 74f
    val playerWidth = 50f
    drawRoundRect(
        color = platformerCharacterColor(project.selectedCharacter, primary),
        topLeft = Offset(playerScreenX - playerWidth / 2f, playerBottom - playerHeight),
        size = Size(playerWidth, playerHeight),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawCircle(Color.White.copy(alpha = 0.90f), radius = 11f, center = Offset(playerScreenX + 9f, playerBottom - 54f))

    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(20f, 20f),
        size = Size(w - 40f, h - 40f),
        cornerRadius = CornerRadius(28f, 28f),
        style = Stroke(width = 2.5f)
    )
}

private fun platformerThemePrimary(theme: String): Color = when (theme) {
    "Cyber Blue" -> Color(0xFF00E5FF)
    "Lava Orange" -> Color(0xFFFF8A3D)
    "Forest Green" -> Color(0xFF35E66B)
    "Mono Dark" -> Color(0xFFEDEBFF)
    else -> Color(0xFF8E6BFF)
}

private fun platformerCharacterColor(character: String, fallback: Color): Color = when {
    character.contains("Knight", ignoreCase = true) -> Color(0xFFB0BEC5)
    character.contains("Robot", ignoreCase = true) -> Color(0xFF00E5FF)
    character.contains("Alien", ignoreCase = true) -> Color(0xFF35E66B)
    character.contains("Explorer", ignoreCase = true) -> Color(0xFFFFB74D)
    else -> fallback
}
