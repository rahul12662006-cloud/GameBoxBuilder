package com.gamebox.builder.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gamebox.builder.data.GameProject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val PLATFORMER_GROUND_Y = 0.80f
private const val PLATFORMER_PLAYER_HALF_WIDTH = 0.046f
private const val PLATFORMER_PLAYER_HEIGHT = 0.145f
private const val PLATFORMER_WORLD_LENGTH = 9.05f
private const val PLATFORMER_COYOTE_TIME = 0.12f
private const val PLATFORMER_JUMP_BUFFER = 0.13f

private data class PlatformerPlatform(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val moving: Boolean = false,
    val moveRange: Float = 0f,
    val movePhase: Float = 0f
)

private data class PlatformerCoin(
    val id: Int,
    val x: Float,
    val y: Float,
    val collected: Boolean = false
)

private data class PlatformerEnemy(
    val id: Int,
    val homeX: Float,
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

private data class PlatformerLevel(
    val platforms: List<PlatformerPlatform>,
    val coins: List<PlatformerCoin>,
    val enemies: List<PlatformerEnemy>,
    val spikes: List<PlatformerSpike>,
    val pits: List<PlatformerPit>,
    val checkpointX: Float,
    val flagX: Float
)

private fun animatedPlatformY(platform: PlatformerPlatform, gameClock: Float): Float {
    return if (platform.moving && platform.moveRange > 0f) {
        platform.y + sin((gameClock * 1.55f + platform.movePhase).toDouble()).toFloat() * platform.moveRange
    } else {
        platform.y
    }
}

private fun platformerMapFrictionMultiplier(map: String): Float = when (map) {
    "Snow Hills" -> 0.58f
    "Space Base" -> 0.82f
    else -> 1f
}

private fun platformerMapGravityBonus(map: String): Float = when (map) {
    "Space Base" -> -0.34f
    "Lava Zone" -> 0.08f
    else -> 0f
}

private fun platformerMapJumpBoost(map: String): Float = when (map) {
    "Space Base" -> 0.08f
    "Snow Hills" -> 0.02f
    else -> 0f
}

@Composable
fun PlatformerGameScreen(
    project: GameProject,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scorePrefs = remember(project.projectId) { context.getSharedPreferences("gamebox_platformer_scores", 0) }
    val bestScoreKey = "best_platformer_${project.projectId}"

    var runSeed by remember(project.projectId, project.updatedAt) { mutableIntStateOf(1) }
    var isRunning by remember(runSeed) { mutableStateOf(true) }
    var isGameOver by remember(runSeed) { mutableStateOf(false) }
    var isLevelComplete by remember(runSeed) { mutableStateOf(false) }
    var playerX by remember(runSeed) { mutableFloatStateOf(0.18f) }
    var playerY by remember(runSeed) { mutableFloatStateOf(PLATFORMER_GROUND_Y) }
    var velocityX by remember(runSeed) { mutableFloatStateOf(0f) }
    var velocityY by remember(runSeed) { mutableFloatStateOf(0f) }
    var moveInput by remember(runSeed) { mutableFloatStateOf(0f) }
    var checkpointX by remember(runSeed) { mutableFloatStateOf(0.18f) }
    var lastFrameNanos by remember(runSeed) { mutableLongStateOf(0L) }
    var score by remember(runSeed) { mutableIntStateOf(0) }
    var coins by remember(runSeed) { mutableIntStateOf(0) }
    var lives by remember(runSeed) { mutableIntStateOf(3) }
    var bestScore by remember(project.projectId) { mutableIntStateOf(scorePrefs.getInt(bestScoreKey, 0)) }
    var isOnSurface by remember(runSeed) { mutableStateOf(true) }
    var damageCooldown by remember(runSeed) { mutableFloatStateOf(0f) }
    var coyoteTimer by remember(runSeed) { mutableFloatStateOf(PLATFORMER_COYOTE_TIME) }
    var jumpBufferTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var jumpsUsed by remember(runSeed) { mutableIntStateOf(0) }
    var fallingIntoPit by remember(runSeed) { mutableStateOf(false) }
    var gameClock by remember(runSeed) { mutableFloatStateOf(0f) }

    val level = remember(project.selectedMap, project.selectedObstaclePack, project.difficulty) {
        buildPlatformerLevel(project)
    }
    val coinsList = remember(runSeed, project.coinsEnabled, project.selectedObstaclePack, project.difficulty) {
        mutableStateListOf<PlatformerCoin>().apply {
            if (project.coinsEnabled) addAll(level.coins)
        }
    }
    val enemies = remember(runSeed, project.selectedObstaclePack, project.difficulty) {
        mutableStateListOf<PlatformerEnemy>().apply { addAll(level.enemies) }
    }

    fun isPitUnderFeet(x: Float): Boolean {
        val leftFoot = x - PLATFORMER_PLAYER_HALF_WIDTH * 0.62f
        val rightFoot = x + PLATFORMER_PLAYER_HALF_WIDTH * 0.62f
        return level.pits.any { pit ->
            rightFoot > pit.startX + 0.006f && leftFoot < pit.endX - 0.006f
        }
    }

    fun pathTouchesPit(fromX: Float, toX: Float): Boolean {
        val left = min(fromX, toX) - PLATFORMER_PLAYER_HALF_WIDTH * 0.34f
        val right = max(fromX, toX) + PLATFORMER_PLAYER_HALF_WIDTH * 0.34f
        return level.pits.any { pit ->
            right > pit.startX + 0.008f && left < pit.endX - 0.008f
        }
    }

    fun requestJump() {
        if (!isGameOver && !isLevelComplete) {
            jumpBufferTimer = PLATFORMER_JUMP_BUFFER
            isRunning = true
        }
    }

    fun respawnAtCheckpoint() {
        playerX = checkpointX.coerceAtLeast(0.18f)
        playerY = PLATFORMER_GROUND_Y
        velocityX = 0f
        velocityY = 0f
        moveInput = 0f
        damageCooldown = 0.75f
        isOnSurface = true
        fallingIntoPit = false
        coyoteTimer = PLATFORMER_COYOTE_TIME
        jumpsUsed = 0
        isRunning = true
    }

    fun takeDamage() {
        if (damageCooldown > 0f || isGameOver || isLevelComplete) return
        lives = (lives - 1).coerceAtLeast(0)
        if (lives <= 0) {
            isGameOver = true
            isRunning = false
            moveInput = 0f
            velocityX = 0f
        } else {
            respawnAtCheckpoint()
        }
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

    LaunchedEffect(runSeed, project.gameSpeed, project.difficulty, project.selectedObstaclePack, project.coinsEnabled, project.powerupsEnabled) {
        while (true) {
            val frameTime = withFrameNanos { it }
            if (lastFrameNanos == 0L) {
                lastFrameNanos = frameTime
                continue
            }
            val dt = ((frameTime - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.045f)
            lastFrameNanos = frameTime

            gameClock += dt
            if (damageCooldown > 0f) damageCooldown = (damageCooldown - dt).coerceAtLeast(0f)
            if (jumpBufferTimer > 0f) jumpBufferTimer = (jumpBufferTimer - dt).coerceAtLeast(0f)
            if (coyoteTimer > 0f) coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)

            if (!isRunning || isGameOver || isLevelComplete) continue

            // Smooth horizontal movement: hold buttons now feel like a real platformer instead of tiny nudges.
            val mapFriction = platformerMapFrictionMultiplier(project.selectedMap)
            val maxRunSpeed = (0.82f + project.gameSpeed * 0.065f) * if (project.selectedMap == "Snow Hills") 1.05f else 1f
            val acceleration = 8.5f * if (project.selectedMap == "Snow Hills") 0.90f else 1f
            val friction = 10.0f * mapFriction
            if (moveInput != 0f) {
                velocityX += moveInput * acceleration * dt
                velocityX = velocityX.coerceIn(-maxRunSpeed, maxRunSpeed)
            } else {
                val slowAmount = friction * dt
                velocityX = when {
                    velocityX > slowAmount -> velocityX - slowAmount
                    velocityX < -slowAmount -> velocityX + slowAmount
                    else -> 0f
                }
            }

            val previousX = playerX
            val previousY = playerY
            playerX = (playerX + velocityX * dt).coerceIn(0.10f, PLATFORMER_WORLD_LENGTH - 0.20f)

            // Prevent the player from skating across pits while simply holding Right.
            // Once the player walks off ground into a pit, ground collision stays disabled
            // until they respawn or actively jump across it.
            val walkingOnGround = isOnSurface && abs(previousY - PLATFORMER_GROUND_Y) < 0.045f && velocityY >= -0.02f
            if (walkingOnGround && (isPitUnderFeet(playerX) || pathTouchesPit(previousX, playerX))) {
                fallingIntoPit = true
                isOnSurface = false
                coyoteTimer = PLATFORMER_COYOTE_TIME
            }

            fun feetOnPlatform(platform: PlatformerPlatform, x: Float): Boolean {
                val leftFoot = x - PLATFORMER_PLAYER_HALF_WIDTH
                val rightFoot = x + PLATFORMER_PLAYER_HALF_WIDTH
                return rightFoot > platform.x - 0.035f && leftFoot < platform.x + platform.width + 0.035f
            }

            fun surfaceUnderPlayer(x: Float, previousBottom: Float, currentBottom: Float, vy: Float): Float? {
                val possibleSurfaces = mutableListOf<Float>()

                level.platforms.forEach { platform ->
                    val platformY = animatedPlatformY(platform, gameClock)
                    val standingOnSamePlatform = abs(previousBottom - platformY) < 0.048f && feetOnPlatform(platform, x)
                    val crossedPlatformTop = vy >= -0.02f && feetOnPlatform(platform, x) &&
                        previousBottom <= platformY + 0.034f && currentBottom >= platformY - 0.032f
                    if (standingOnSamePlatform || crossedPlatformTop) possibleSurfaces.add(platformY)
                }

                val groundAvailable = !fallingIntoPit && !isPitUnderFeet(x)
                val standingOnGround = abs(previousBottom - PLATFORMER_GROUND_Y) < 0.050f && groundAvailable
                val crossedGround = vy >= -0.02f && groundAvailable &&
                    previousBottom <= PLATFORMER_GROUND_Y + 0.035f && currentBottom >= PLATFORMER_GROUND_Y - 0.030f
                if (standingOnGround || crossedGround) possibleSurfaces.add(PLATFORMER_GROUND_Y)

                return possibleSurfaces.minOrNull()
            }

            val gravity = (2.12f + project.difficulty * 0.035f + platformerMapGravityBonus(project.selectedMap)).coerceAtLeast(1.62f)
            velocityY += gravity * dt
            playerY += velocityY * dt

            val landingSurface = if (velocityY >= -0.02f) {
                surfaceUnderPlayer(playerX, previousY, playerY, velocityY)
            } else null

            if (landingSurface != null) {
                playerY = landingSurface
                velocityY = 0f
                isOnSurface = true
                fallingIntoPit = false
                coyoteTimer = PLATFORMER_COYOTE_TIME
                jumpsUsed = 0
            } else {
                // If the player walked off a platform, coyoteTimer gives a small fair jump window.
                if (isOnSurface && abs(previousX - playerX) > 0.0001f) coyoteTimer = PLATFORMER_COYOTE_TIME
                isOnSurface = false
            }

            val canDoubleJump = project.powerupsEnabled
            if (jumpBufferTimer > 0f) {
                val canGroundJump = isOnSurface || coyoteTimer > 0f
                val canAirJump = canDoubleJump && jumpsUsed < 1 && !canGroundJump
                if (canGroundJump || canAirJump) {
                    velocityY = -1.16f - project.gameSpeed * 0.020f - platformerMapJumpBoost(project.selectedMap)
                    playerY -= 0.010f
                    isOnSurface = false
                    fallingIntoPit = false
                    coyoteTimer = 0f
                    jumpBufferTimer = 0f
                    jumpsUsed += 1
                }
            }

            if (playerY > 1.09f) {
                takeDamage()
                continue
            }

            val newCoins = coinsList.map { coin ->
                if (!coin.collected && abs(playerX - coin.x) < 0.080f && abs(playerY - coin.y) < 0.17f) {
                    coins += 1
                    score += 125
                    coin.copy(collected = true)
                } else coin
            }.filter { !it.collected }
            coinsList.clear()
            coinsList.addAll(newCoins)

            val enemySpeed = 0.13f + project.difficulty * 0.014f
            val newEnemies = enemies.map { enemy ->
                if (!enemy.alive) enemy else {
                    var nextX = enemy.x + enemy.walkDir * enemySpeed * dt
                    var nextDir = enemy.walkDir
                    if (nextX < enemy.homeX - 0.20f || nextX > enemy.homeX + 0.20f) {
                        nextDir *= -1f
                        nextX = nextX.coerceIn(enemy.homeX - 0.20f, enemy.homeX + 0.20f)
                    }
                    enemy.copy(x = nextX, walkDir = nextDir)
                }
            }
            enemies.clear()
            enemies.addAll(newEnemies)

            val touchedSpike = level.spikes.any { spike ->
                abs(playerX - spike.x) < 0.034f && playerY > spike.y - 0.055f && playerY < spike.y + 0.030f && velocityY >= -0.10f
            }
            if (touchedSpike) {
                takeDamage()
                continue
            }

            val touchedEnemy = enemies.firstOrNull { enemy ->
                enemy.alive && abs(playerX - enemy.x) < 0.070f && abs(playerY - enemy.y) < 0.13f
            }
            if (touchedEnemy != null) {
                if (velocityY > 0.18f && playerY < touchedEnemy.y - 0.030f) {
                    enemies.remove(touchedEnemy)
                    velocityY = -0.72f
                    score += 180
                } else {
                    takeDamage()
                    continue
                }
            }

            if (playerX > level.checkpointX && checkpointX < level.checkpointX) {
                checkpointX = level.checkpointX
                score += 100
            }
            score = max(score, (playerX * 80).toInt() + coins * 100)
            if (playerX > level.flagX) {
                isLevelComplete = true
                isRunning = false
                moveInput = 0f
                velocityX = 0f
                score += 500 + lives * 150
            }
        }
    }

    val cameraTarget = if (project.cameraMode.contains("Fixed", ignoreCase = true)) {
        0f
    } else {
        (playerX - 0.24f).coerceIn(0f, PLATFORMER_WORLD_LENGTH - 1f)
    }
    val cameraX by animateFloatAsState(
        targetValue = cameraTarget,
        animationSpec = tween(70),
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
            Text("Platformer Playtest", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
                Text("Camera: ${project.cameraMode}  •  Effect: ${platformerMapEffectLabel(project.selectedMap)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (project.powerupsEnabled) {
                    Text("Powerup: double jump enabled", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
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
                gameClock = gameClock,
                coins = coinsList,
                enemies = enemies,
                checkpointX = checkpointX,
                damageCooldown = damageCooldown,
                modifier = Modifier.fillMaxSize()
            )

            if (isGameOver || isLevelComplete) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xEE080914), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(if (isLevelComplete) "Level Complete" else "Game Over", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Score $score • Best $bestScore • Coins $coins", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { fullRestart() }) { Text(if (isLevelComplete) "Replay" else "Restart") }
                        if (isLevelComplete) {
                            OutlinedButton(onClick = onBack) { Text("Edit") }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HoldControlButton(
                label = "Left",
                enabled = !isGameOver && !isLevelComplete,
                modifier = Modifier.weight(1f),
                onDown = { moveInput = -1f; isRunning = true },
                onUp = { if (moveInput < 0f) moveInput = 0f }
            )
            Button(onClick = { requestJump() }, modifier = Modifier.weight(1f).height(58.dp), enabled = !isGameOver && !isLevelComplete) { Text("Jump") }
            HoldControlButton(
                label = "Right",
                enabled = !isGameOver && !isLevelComplete,
                modifier = Modifier.weight(1f),
                onDown = { moveInput = 1f; isRunning = true },
                onUp = { if (moveInput > 0f) moveInput = 0f }
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun HoldControlButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    val background = if (enabled) MaterialTheme.colorScheme.primary else Color.DarkGray
    Box(
        modifier = modifier
            .height(58.dp)
            .background(background, RoundedCornerShape(22.dp))
            .then(
                if (enabled) Modifier.pointerInput(label) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onDown()
                        waitForUpOrCancellation()
                        onUp()
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

private fun buildPlatformerLevel(project: GameProject): PlatformerLevel {
    val difficulty = project.difficulty.coerceIn(1, 5)
    val pack = project.selectedObstaclePack
    val map = project.selectedMap
    val isEasy = difficulty <= 2
    val isHard = difficulty >= 4
    val movingPack = pack.contains("Moving", ignoreCase = true)
    val enemyPack = pack.contains("Enemy", ignoreCase = true)
    val coinPack = pack.contains("Coin", ignoreCase = true) || pack.contains("Key", ignoreCase = true)
    val mixedPack = pack.contains("Mixed", ignoreCase = true)
    val pitSpikePack = pack.contains("Pit", ignoreCase = true) || pack.contains("Spike", ignoreCase = true)
    val lavaMap = map == "Lava Zone"
    val caveMap = map == "Cave"
    val snowMap = map == "Snow Hills"
    val spaceMap = map == "Space Base"

    val widthBoost = when {
        isEasy -> 0.08f
        isHard -> -0.035f
        else -> 0f
    }
    val heightShift = when {
        spaceMap -> -0.025f
        caveMap -> 0.015f
        snowMap -> -0.005f
        else -> 0f
    }

    fun p(id: Int, x: Float, y: Float, width: Float, moving: Boolean = false): PlatformerPlatform {
        val shouldMove = movingPack && moving
        return PlatformerPlatform(
            id = id,
            x = x,
            y = (y + heightShift).coerceIn(0.46f, 0.72f),
            width = (width + widthBoost).coerceIn(0.34f, 0.64f),
            moving = shouldMove,
            moveRange = if (shouldMove) if (snowMap) 0.026f else 0.036f else 0f,
            movePhase = id * 0.72f
        )
    }

    val platforms = listOf(
        p(1, 0.54f, 0.67f, 0.52f, moving = false),
        p(2, 1.34f, 0.58f, 0.50f, moving = true),
        p(3, 2.16f, 0.66f, 0.52f, moving = false),
        p(4, 2.98f, 0.56f, 0.50f, moving = true),
        p(5, 3.78f, 0.68f, 0.50f, moving = false),
        p(6, 4.58f, 0.55f, 0.52f, moving = true),
        p(7, 5.42f, 0.66f, 0.54f, moving = false),
        p(8, 6.24f, 0.57f, 0.48f, moving = true),
        p(9, 7.04f, 0.68f, 0.52f, moving = false),
        p(10, 7.82f, 0.59f, 0.46f, moving = movingPack)
    )

    val coinLift = if (spaceMap) -0.025f else 0f
    val baseCoins = listOf(
        PlatformerCoin(1, 0.74f, 0.57f + coinLift),
        PlatformerCoin(2, 1.52f, 0.48f + coinLift),
        PlatformerCoin(3, 2.38f, 0.56f + coinLift),
        PlatformerCoin(4, 3.22f, 0.46f + coinLift),
        PlatformerCoin(5, 4.02f, 0.58f + coinLift),
        PlatformerCoin(6, 4.78f, 0.45f + coinLift),
        PlatformerCoin(7, 5.66f, 0.55f + coinLift),
        PlatformerCoin(8, 6.44f, 0.48f + coinLift),
        PlatformerCoin(9, 7.20f, 0.58f + coinLift),
        PlatformerCoin(10, 7.96f, 0.50f + coinLift),
        PlatformerCoin(11, 8.36f, 0.68f + coinLift)
    )
    val bonusCoins = listOf(
        PlatformerCoin(12, 1.02f, 0.48f + coinLift),
        PlatformerCoin(13, 2.78f, 0.48f + coinLift),
        PlatformerCoin(14, 5.05f, 0.46f + coinLift),
        PlatformerCoin(15, 6.86f, 0.49f + coinLift)
    )
    val coins = if (coinPack) baseCoins + bonusCoins else baseCoins

    val enemies = buildList {
        val shouldUseEnemies = enemyPack || mixedPack || difficulty >= 3
        if (shouldUseEnemies) add(PlatformerEnemy(1, 1.94f, 1.94f, PLATFORMER_GROUND_Y))
        if (shouldUseEnemies && difficulty >= 2) add(PlatformerEnemy(2, 3.52f, 3.52f, PLATFORMER_GROUND_Y))
        if (enemyPack || difficulty >= 4) add(PlatformerEnemy(3, 5.10f, 5.10f, PLATFORMER_GROUND_Y))
        if (enemyPack && difficulty >= 4) add(PlatformerEnemy(4, 6.72f, 6.72f, PLATFORMER_GROUND_Y))
        if (difficulty >= 5) add(PlatformerEnemy(5, 7.48f, 7.48f, PLATFORMER_GROUND_Y))
    }

    val spikes = buildList {
        val shouldUseSpikes = pitSpikePack || mixedPack || lavaMap || difficulty >= 3
        if (shouldUseSpikes) add(PlatformerSpike(1, 2.80f, PLATFORMER_GROUND_Y))
        if (shouldUseSpikes && difficulty >= 2) add(PlatformerSpike(2, 4.38f, PLATFORMER_GROUND_Y))
        if ((pitSpikePack || lavaMap || difficulty >= 4) && !coinPack) add(PlatformerSpike(3, 5.96f, PLATFORMER_GROUND_Y))
        if ((isHard || lavaMap) && !coinPack) add(PlatformerSpike(4, 7.42f, PLATFORMER_GROUND_Y))
    }

    val pits = buildList {
        val shouldUsePits = pitSpikePack || mixedPack || lavaMap || difficulty >= 2
        if (shouldUsePits) {
            val gapA = if (isEasy) 0.25f else if (isHard || lavaMap) 0.40f else 0.32f
            val gapB = if (isEasy) 0.28f else if (isHard || lavaMap) 0.42f else 0.35f
            add(PlatformerPit(1.10f, 1.10f + gapA))
            add(PlatformerPit(3.28f, 3.28f + gapB))
            if (!isEasy || lavaMap) add(PlatformerPit(5.18f, 5.18f + gapB))
            if (isHard || lavaMap) add(PlatformerPit(6.92f, 6.92f + 0.38f))
        }
    }

    val checkpoint = if (isEasy) 3.28f else 3.52f
    val flag = if (isHard) 8.50f else 8.36f
    return PlatformerLevel(platforms, coins, enemies, spikes, pits, checkpointX = checkpoint, flagX = flag)
}

@Composable
private fun PlatformerCanvas(
    project: GameProject,
    level: PlatformerLevel,
    playerX: Float,
    playerY: Float,
    cameraX: Float,
    gameClock: Float,
    coins: List<PlatformerCoin>,
    enemies: List<PlatformerEnemy>,
    checkpointX: Float,
    damageCooldown: Float,
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
                gameClock = gameClock,
                coins = coins,
                enemies = enemies,
                checkpointX = checkpointX,
                damageCooldown = damageCooldown
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
    gameClock: Float,
    coins: List<PlatformerCoin>,
    enemies: List<PlatformerEnemy>,
    checkpointX: Float,
    damageCooldown: Float
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

    repeat(10) { index ->
        val x = (index * 0.40f - (cameraX * 0.22f % 0.40f)) * w
        drawCircle(primary.copy(alpha = 0.10f), radius = 30f + index % 3 * 7f, center = Offset(x, h * (0.16f + (index % 4) * 0.065f)))
    }

    var segmentStart = cameraX - 0.10f
    level.pits.sortedBy { it.startX }.forEach { pit ->
        val left = sx(pit.startX)
        val right = sx(pit.endX)
        if (pit.startX > segmentStart) {
            drawLine(primary.copy(alpha = 0.92f), Offset(sx(segmentStart), sy(PLATFORMER_GROUND_Y)), Offset(left, sy(PLATFORMER_GROUND_Y)), strokeWidth = 8f)
        }
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.70f),
            topLeft = Offset(left, sy(PLATFORMER_GROUND_Y)),
            size = Size(right - left, h * 0.17f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        segmentStart = pit.endX
    }
    drawLine(primary.copy(alpha = 0.92f), Offset(sx(segmentStart), sy(PLATFORMER_GROUND_Y)), Offset(w, sy(PLATFORMER_GROUND_Y)), strokeWidth = 8f)
    drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, sy(0.84f)), Offset(w, sy(0.84f)), strokeWidth = 3f)

    level.platforms.forEach { platform ->
        val left = sx(platform.x)
        val top = sy(animatedPlatformY(platform, gameClock))
        val width = platform.width * w
        val color = if (platform.moving) Color(0xFF00E5FF) else primary
        drawRoundRect(
            color = color.copy(alpha = 0.96f),
            topLeft = Offset(left, top),
            size = Size(width, 18f),
            cornerRadius = CornerRadius(9f, 9f)
        )
        drawRoundRect(
            color = color.copy(alpha = 0.20f),
            topLeft = Offset(left + 7f, top + 18f),
            size = Size(width - 14f, 38f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        if (platform.moving) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(left + 12f, top + 6f), Offset(left + width - 12f, top + 6f), strokeWidth = 3f)
        }
    }

    coins.forEach { coin ->
        drawCircle(Color(0xFFFFD54F), radius = 13f, center = Offset(sx(coin.x), sy(coin.y)))
        drawCircle(Color.White.copy(alpha = 0.45f), radius = 5f, center = Offset(sx(coin.x) - 4f, sy(coin.y) - 4f))
    }

    level.spikes.forEach { spike ->
        val x = sx(spike.x)
        val y = sy(spike.y)
        val path = Path().apply {
            moveTo(x - 24f, y)
            lineTo(x - 11f, y - 38f)
            lineTo(x + 1f, y)
            lineTo(x + 13f, y - 38f)
            lineTo(x + 26f, y)
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
                topLeft = Offset(x - 21f, bottom - 44f),
                size = Size(42f, 44f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 7f, center = Offset(x + 8f, bottom - 29f))
        }
    }

    val checkpointScreenX = sx(level.checkpointX)
    drawLine(Color(0xFFFFD54F), Offset(checkpointScreenX, sy(PLATFORMER_GROUND_Y)), Offset(checkpointScreenX, sy(0.56f)), strokeWidth = 6f)
    drawRoundRect(
        color = if (checkpointX >= level.checkpointX) Color(0xFF64FFDA) else Color(0xFFFFD54F),
        topLeft = Offset(checkpointScreenX, sy(0.56f)),
        size = Size(56f, 32f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    val flagX = sx(level.flagX)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(flagX, sy(PLATFORMER_GROUND_Y)), Offset(flagX, sy(0.45f)), strokeWidth = 7f)
    drawRoundRect(
        color = primary,
        topLeft = Offset(flagX, sy(0.45f)),
        size = Size(70f, 40f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    val playerScreenX = sx(playerX)
    val playerBottom = sy(playerY)
    val playerHeight = PLATFORMER_PLAYER_HEIGHT * h
    val playerWidth = PLATFORMER_PLAYER_HALF_WIDTH * 2f * w
    val alpha = if (damageCooldown > 0f) 0.55f else 1f
    drawRoundRect(
        color = platformerCharacterColor(project.selectedCharacter, primary).copy(alpha = alpha),
        topLeft = Offset(playerScreenX - playerWidth / 2f, playerBottom - playerHeight),
        size = Size(playerWidth, playerHeight),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawCircle(Color.White.copy(alpha = 0.90f), radius = 10f, center = Offset(playerScreenX + playerWidth * 0.18f, playerBottom - playerHeight * 0.72f))

    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(20f, 20f),
        size = Size(w - 40f, h - 40f),
        cornerRadius = CornerRadius(28f, 28f),
        style = Stroke(width = 2.5f)
    )
}


private fun platformerMapEffectLabel(map: String): String = when (map) {
    "Cave" -> "darker hazards"
    "Snow Hills" -> "slippery movement"
    "Lava Zone" -> "hard lava gaps"
    "Space Base" -> "low gravity jump"
    else -> "balanced"
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
