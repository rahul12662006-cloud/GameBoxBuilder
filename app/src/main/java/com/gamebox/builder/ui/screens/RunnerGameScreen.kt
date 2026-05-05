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
import kotlin.random.Random

private enum class RunnerObstacleType { BOX, TALL_GATE, SPIKES }
private enum class RunnerPowerupType { SHIELD, MAGNET, DOUBLE_COIN }

private data class RunnerObstacle(
    val id: Int,
    val type: RunnerObstacleType,
    val x: Float,
    val scored: Boolean = false
)

private data class RunnerCoin(
    val id: Int,
    val x: Float,
    val y: Float,
    val collected: Boolean = false
)

private data class RunnerPowerup(
    val id: Int,
    val type: RunnerPowerupType,
    val x: Float,
    val y: Float,
    val collected: Boolean = false
)

@Composable
fun RunnerGameScreen(
    project: GameProject,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scorePrefs = remember(project.projectId) { context.getSharedPreferences("gamebox_runner_scores", 0) }
    val bestScoreKey = "best_score_${project.projectId}"

    var runSeed by remember(project.projectId, project.updatedAt) { mutableIntStateOf(1) }
    var isRunning by remember(runSeed) { mutableStateOf(true) }
    var isGameOver by remember(runSeed) { mutableStateOf(false) }
    var playerLift by remember(runSeed) { mutableFloatStateOf(0f) }
    var jumpVelocity by remember(runSeed) { mutableFloatStateOf(0f) }
    var slideTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var shieldTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var magnetTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var doubleCoinTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var score by remember(runSeed) { mutableIntStateOf(0) }
    var bestScore by remember(project.projectId) { mutableIntStateOf(scorePrefs.getInt(bestScoreKey, 0)) }
    var coins by remember(runSeed) { mutableIntStateOf(0) }
    var distance by remember(runSeed) { mutableFloatStateOf(0f) }
    var nextId by remember(runSeed) { mutableIntStateOf(10) }
    var lastFrameNanos by remember(runSeed) { mutableLongStateOf(0L) }

    val canSlide = project.controlMode != "Tap Jump"

    val obstacles = remember(runSeed, project.selectedObstaclePack) {
        mutableStateListOf(
            RunnerObstacle(1, obstacleForPack(project.selectedObstaclePack, 1), 1.55f),
            RunnerObstacle(2, obstacleForPack(project.selectedObstaclePack, 2), 2.44f),
            RunnerObstacle(3, obstacleForPack(project.selectedObstaclePack, 3), 3.35f)
        )
    }
    val coinList = remember(runSeed, project.coinsEnabled) {
        mutableStateListOf<RunnerCoin>().apply {
            if (project.coinsEnabled) {
                add(RunnerCoin(4, 1.82f, 0.58f))
                add(RunnerCoin(5, 2.70f, 0.48f))
                add(RunnerCoin(6, 3.62f, 0.58f))
            }
        }
    }
    val powerups = remember(runSeed, project.powerupsEnabled) {
        mutableStateListOf<RunnerPowerup>().apply {
            if (project.powerupsEnabled) add(RunnerPowerup(7, RunnerPowerupType.SHIELD, 2.05f, 0.50f))
        }
    }

    fun jump() {
        if (!isGameOver && playerLift <= 0.01f) {
            jumpVelocity = 1.40f + project.gameSpeed * 0.03f
            isRunning = true
        }
    }

    fun slide() {
        if (!isGameOver && canSlide) {
            slideTimer = 0.58f
            isRunning = true
        }
    }

    fun restart() {
        runSeed += 1
    }

    LaunchedEffect(score) {
        if (score > bestScore) {
            bestScore = score
            scorePrefs.edit().putInt(bestScoreKey, score).apply()
        }
    }

    LaunchedEffect(runSeed, project.gameSpeed, project.difficulty, project.selectedObstaclePack, project.coinsEnabled, project.powerupsEnabled) {
        while (true) {
            val frameTime = withFrameNanos { it }
            if (lastFrameNanos == 0L) {
                lastFrameNanos = frameTime
                continue
            }

            val dt = ((frameTime - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = frameTime

            if (!isRunning || isGameOver) continue

            val worldSpeed = 0.34f + project.gameSpeed * 0.050f + project.difficulty * 0.020f
            distance += worldSpeed * dt
            score = max(score, (distance * 100).toInt() + coins * 75)

            if (slideTimer > 0f) slideTimer = (slideTimer - dt).coerceAtLeast(0f)
            if (shieldTimer > 0f) shieldTimer = (shieldTimer - dt).coerceAtLeast(0f)
            if (magnetTimer > 0f) magnetTimer = (magnetTimer - dt).coerceAtLeast(0f)
            if (doubleCoinTimer > 0f) doubleCoinTimer = (doubleCoinTimer - dt).coerceAtLeast(0f)

            if (jumpVelocity > 0f || playerLift > 0f) {
                playerLift += jumpVelocity * dt
                jumpVelocity -= 3.00f * dt
                if (playerLift <= 0f) {
                    playerLift = 0f
                    jumpVelocity = 0f
                }
            }

            val playerX = 0.20f
            val newObstacles = obstacles.map { obstacle ->
                val moved = obstacle.copy(x = obstacle.x - worldSpeed * dt)
                if (!moved.scored && moved.x < playerX - 0.10f) moved.copy(scored = true) else moved
            }.filter { it.x > -0.20f }
            obstacles.clear()
            obstacles.addAll(newObstacles)

            val newCoins = coinList.map { coin ->
                var moved = coin.copy(x = coin.x - worldSpeed * dt)
                val collectRangeX = if (magnetTimer > 0f) 0.20f else 0.060f
                val collectRangeY = if (magnetTimer > 0f) 0.35f else 0.16f
                if (!moved.collected && abs(moved.x - playerX) < collectRangeX && abs((0.78f - playerLift) - moved.y) < collectRangeY) {
                    moved = moved.copy(collected = true)
                    coins += if (doubleCoinTimer > 0f) 2 else 1
                    score += if (doubleCoinTimer > 0f) 200 else 100
                }
                moved
            }.filter { it.x > -0.15f && !it.collected }
            coinList.clear()
            coinList.addAll(newCoins)

            val newPowerups = powerups.map { powerup ->
                var moved = powerup.copy(x = powerup.x - worldSpeed * dt)
                if (!moved.collected && abs(moved.x - playerX) < 0.070f && abs((0.78f - playerLift) - moved.y) < 0.20f) {
                    moved = moved.copy(collected = true)
                    when (moved.type) {
                        RunnerPowerupType.SHIELD -> shieldTimer = 5.0f
                        RunnerPowerupType.MAGNET -> magnetTimer = 5.0f
                        RunnerPowerupType.DOUBLE_COIN -> doubleCoinTimer = 5.0f
                    }
                    score += 150
                }
                moved
            }.filter { it.x > -0.15f && !it.collected }
            powerups.clear()
            powerups.addAll(newPowerups)

            val farthestObstacle = obstacles.maxOfOrNull { it.x } ?: 0f
            val difficultyGapPenalty = project.difficulty * 0.040f
            val minimumGap = (0.86f - difficultyGapPenalty).coerceAtLeast(0.62f)
            if (farthestObstacle < 1.68f) {
                val random = Random(nextId + score + project.difficulty * 13 + project.gameSpeed * 17)
                val type = obstacleForPack(project.selectedObstaclePack, random.nextInt(100))
                val spawnX = max(2.02f, farthestObstacle + minimumGap + random.nextFloat() * 0.30f)
                obstacles.add(RunnerObstacle(nextId, type, spawnX))
                nextId += 1

                if (project.coinsEnabled && random.nextInt(100) > 12) {
                    coinList.add(
                        RunnerCoin(
                            nextId,
                            spawnX + 0.28f + random.nextFloat() * 0.20f,
                            if (type == RunnerObstacleType.TALL_GATE) 0.52f else 0.58f
                        )
                    )
                    nextId += 1
                }

                if (project.powerupsEnabled && random.nextInt(100) < 18) {
                    val powerupType = when (random.nextInt(3)) {
                        0 -> RunnerPowerupType.SHIELD
                        1 -> RunnerPowerupType.MAGNET
                        else -> RunnerPowerupType.DOUBLE_COIN
                    }
                    powerups.add(RunnerPowerup(nextId, powerupType, spawnX + 0.50f, 0.50f))
                    nextId += 1
                }
            }

            val hitObstacle = obstacles.firstOrNull { obstacle ->
                val xGap = abs(obstacle.x - playerX)
                when (obstacle.type) {
                    RunnerObstacleType.BOX -> xGap < 0.040f && playerLift < 0.12f
                    RunnerObstacleType.SPIKES -> xGap < 0.044f && playerLift < 0.11f
                    RunnerObstacleType.TALL_GATE -> xGap < 0.046f && slideTimer <= 0.08f
                }
            }
            if (hitObstacle != null) {
                if (shieldTimer > 0f) {
                    shieldTimer = 0f
                    obstacles.remove(hitObstacle)
                    score += 120
                } else {
                    isGameOver = true
                    isRunning = false
                }
            }
        }
    }

    val slideProgress by animateFloatAsState(
        targetValue = if (slideTimer > 0f) 1f else 0f,
        animationSpec = tween(120),
        label = "slide-progress"
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
            Text("2D Runner Playtest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            OutlinedButton(onClick = { isRunning = !isRunning }, enabled = !isGameOver) {
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
                    Text("Coins: $coins")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Speed: ${project.gameSpeed}")
                    Text("Difficulty: ${project.difficulty}")
                    Text("Pack: ${project.selectedObstaclePack}")
                }
                val active = buildList {
                    if (shieldTimer > 0f) add("Shield ${shieldTimer.toInt()}s")
                    if (magnetTimer > 0f) add("Magnet ${magnetTimer.toInt()}s")
                    if (doubleCoinTimer > 0f) add("2x Coins ${doubleCoinTimer.toInt()}s")
                }
                Text(
                    text = if (active.isEmpty()) "Character: ${project.selectedCharacter}  •  Map: ${project.selectedMap}" else active.joinToString("  •  "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            RunnerCanvas(
                project = project,
                playerLift = playerLift,
                slideProgress = slideProgress,
                obstacles = obstacles,
                coins = coinList,
                powerups = powerups,
                distance = distance,
                shieldActive = shieldTimer > 0f,
                modifier = Modifier.fillMaxSize()
            )

            if (isGameOver) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xDD080914), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Game Over", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Score $score • Best $bestScore • Coins $coins", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { restart() }) { Text("Restart") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = ::jump, modifier = Modifier.weight(1f), enabled = !isGameOver) { Text("Jump") }
            Button(onClick = ::slide, modifier = Modifier.weight(1f), enabled = !isGameOver && canSlide) { Text(if (canSlide) "Slide" else "Slide Off") }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun RunnerCanvas(
    project: GameProject,
    playerLift: Float,
    slideProgress: Float,
    obstacles: List<RunnerObstacle>,
    coins: List<RunnerCoin>,
    powerups: List<RunnerPowerup>,
    distance: Float,
    shieldActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080914))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            drawRunnerWorld(
                project = project,
                playerLift = playerLift,
                slideProgress = slideProgress,
                obstacles = obstacles,
                coins = coins,
                powerups = powerups,
                distance = distance,
                shieldActive = shieldActive
            )
        }
    }
}

private fun DrawScope.drawRunnerWorld(
    project: GameProject,
    playerLift: Float,
    slideProgress: Float,
    obstacles: List<RunnerObstacle>,
    coins: List<RunnerCoin>,
    powerups: List<RunnerPowerup>,
    distance: Float,
    shieldActive: Boolean
) {
    val w = size.width
    val h = size.height
    val groundY = h * 0.78f
    val primary = themePrimary(project.uiTheme)
    val coinColor = Color(0xFFFFD54F)
    val bgTop = when (project.selectedMap) {
        "Forest" -> Color(0xFF102A24)
        "Desert" -> Color(0xFF382514)
        "Snow Road" -> Color(0xFF152433)
        "Cyber Tunnel" -> Color(0xFF120A2D)
        else -> Color(0xFF14182C)
    }
    val bgBottom = Color(0xFF070812)

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(bgTop, bgBottom)),
        cornerRadius = CornerRadius(34f, 34f),
        size = size
    )

    drawParallaxLayer(distance, w, h, primary)

    drawLine(primary.copy(alpha = 0.95f), Offset(0f, groundY), Offset(w, groundY), strokeWidth = 8f)
    drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, groundY + 24f), Offset(w, groundY + 24f), strokeWidth = 3f)

    coins.forEach { coin ->
        drawCircle(coinColor, radius = 13f, center = Offset(coin.x * w, coin.y * h))
        drawCircle(Color.White.copy(alpha = 0.45f), radius = 5f, center = Offset(coin.x * w - 4f, coin.y * h - 4f))
    }

    powerups.forEach { powerup ->
        val x = powerup.x * w
        val y = powerup.y * h
        val color = when (powerup.type) {
            RunnerPowerupType.SHIELD -> Color(0xFF64FFDA)
            RunnerPowerupType.MAGNET -> Color(0xFFFF80AB)
            RunnerPowerupType.DOUBLE_COIN -> Color(0xFFFFD54F)
        }
        drawCircle(color.copy(alpha = 0.22f), radius = 26f, center = Offset(x, y))
        drawCircle(color, radius = 17f, center = Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.75f), radius = 6f, center = Offset(x - 5f, y - 5f))
    }

    obstacles.forEach { obstacle ->
        val x = obstacle.x * w
        when (obstacle.type) {
            RunnerObstacleType.BOX -> {
                drawRoundRect(
                    color = Color(0xFFFF5252),
                    topLeft = Offset(x - 24f, groundY - 58f),
                    size = Size(48f, 58f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
            }
            RunnerObstacleType.SPIKES -> {
                val path = Path().apply {
                    moveTo(x - 34f, groundY)
                    lineTo(x - 18f, groundY - 48f)
                    lineTo(x - 2f, groundY)
                    lineTo(x + 14f, groundY - 48f)
                    lineTo(x + 30f, groundY)
                    close()
                }
                drawPath(path, Color(0xFFFF7043))
            }
            RunnerObstacleType.TALL_GATE -> {
                drawRoundRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(x - 28f, groundY - 150f),
                    size = Size(56f, 34f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                drawLine(Color(0xFF00E5FF), Offset(x - 30f, groundY - 150f), Offset(x - 30f, groundY), strokeWidth = 6f)
                drawLine(Color(0xFF00E5FF), Offset(x + 30f, groundY - 150f), Offset(x + 30f, groundY), strokeWidth = 6f)
            }
        }
    }

    val playerX = w * 0.20f
    val standingHeight = 82f
    val crouchHeight = 48f
    val playerHeight = standingHeight - (standingHeight - crouchHeight) * slideProgress
    val playerWidth = 52f + 12f * slideProgress
    val playerBottom = groundY - playerLift * h * 0.42f
    val playerTop = playerBottom - playerHeight

    if (shieldActive) {
        drawCircle(
            color = Color(0xFF64FFDA).copy(alpha = 0.23f),
            radius = 54f,
            center = Offset(playerX, playerTop + playerHeight / 2f)
        )
        drawCircle(
            color = Color(0xFF64FFDA).copy(alpha = 0.65f),
            radius = 54f,
            center = Offset(playerX, playerTop + playerHeight / 2f),
            style = Stroke(width = 5f)
        )
    }

    drawRoundRect(
        color = characterColor(project.selectedCharacter, primary),
        topLeft = Offset(playerX - playerWidth / 2f, playerTop),
        size = Size(playerWidth, playerHeight),
        cornerRadius = CornerRadius(18f, 18f)
    )
    drawCircle(Color.White.copy(alpha = 0.9f), radius = 12f, center = Offset(playerX + playerWidth * 0.14f, playerTop + 22f))

    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(20f, 20f),
        size = Size(w - 40f, h - 40f),
        cornerRadius = CornerRadius(28f, 28f),
        style = Stroke(width = 2.5f)
    )
}

private fun obstacleForPack(pack: String, seed: Int): RunnerObstacleType {
    return when (pack) {
        "Rocks + Boxes" -> if (seed % 5 == 0) RunnerObstacleType.TALL_GATE else RunnerObstacleType.BOX
        "Spikes + Pits" -> if (seed % 3 == 0) RunnerObstacleType.BOX else RunnerObstacleType.SPIKES
        "Barrels + Gates" -> if (seed % 2 == 0) RunnerObstacleType.TALL_GATE else RunnerObstacleType.BOX
        "Traffic Cones" -> if (seed % 4 == 0) RunnerObstacleType.SPIKES else RunnerObstacleType.BOX
        else -> when (seed % 3) {
            0 -> RunnerObstacleType.BOX
            1 -> RunnerObstacleType.SPIKES
            else -> RunnerObstacleType.TALL_GATE
        }
    }
}

private fun DrawScope.drawParallaxLayer(distance: Float, w: Float, h: Float, primary: Color) {
    val offset = (distance * w * 0.65f) % (w / 3f)
    val baseY = h * 0.64f
    repeat(8) { index ->
        val x = index * w / 3f - offset
        drawRoundRect(
            color = primary.copy(alpha = 0.10f),
            topLeft = Offset(x, baseY - 80f - (index % 3) * 24f),
            size = Size(70f + (index % 3) * 16f, 110f + (index % 2) * 34f),
            cornerRadius = CornerRadius(10f, 10f)
        )
    }
}

private fun themePrimary(theme: String): Color = when (theme) {
    "Cyber Blue" -> Color(0xFF00E5FF)
    "Lava Orange" -> Color(0xFFFF8A3D)
    "Forest Green" -> Color(0xFF35E66B)
    "Mono Dark" -> Color(0xFFEDEBFF)
    else -> Color(0xFF8E6BFF)
}

private fun characterColor(character: String, fallback: Color): Color = when {
    character.contains("Ninja", ignoreCase = true) -> Color(0xFF2C2F48)
    character.contains("Robot", ignoreCase = true) -> Color(0xFF00E5FF)
    character.contains("Girl", ignoreCase = true) -> Color(0xFFFF80AB)
    character.contains("Biker", ignoreCase = true) -> Color(0xFFFFB74D)
    else -> fallback
}
