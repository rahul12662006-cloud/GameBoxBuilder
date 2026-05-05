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
import kotlin.math.pow
import kotlin.random.Random

private enum class ThreeDObstacleType { LOW_BLOCK, HIGH_GATE, ROAD_BLOCK, RAMP }
private enum class ThreeDPowerupType { SHIELD, MAGNET, DOUBLE_COIN }

private data class ThreeDObstacle(
    val id: Int,
    val type: ThreeDObstacleType,
    val lane: Int,
    val z: Float,
    val scored: Boolean = false
)

private data class ThreeDCoin(
    val id: Int,
    val lane: Int,
    val z: Float,
    val height: Float = 0.0f,
    val collected: Boolean = false
)

private data class ThreeDPowerup(
    val id: Int,
    val type: ThreeDPowerupType,
    val lane: Int,
    val z: Float,
    val collected: Boolean = false
)

@Composable
fun ThreeDRunnerGameScreen(
    project: GameProject,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(project.projectId) { context.getSharedPreferences("gamebox_3d_runner_scores", 0) }
    val bestScoreKey = "best_3d_runner_${project.projectId}"

    var runSeed by remember(project.projectId, project.updatedAt) { mutableIntStateOf(1) }
    var isRunning by remember(runSeed) { mutableStateOf(true) }
    var isGameOver by remember(runSeed) { mutableStateOf(false) }
    var lane by remember(runSeed) { mutableIntStateOf(1) }
    var targetLane by remember(runSeed) { mutableIntStateOf(1) }
    var jumpLift by remember(runSeed) { mutableFloatStateOf(0f) }
    var jumpVelocity by remember(runSeed) { mutableFloatStateOf(0f) }
    var slideTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var shieldTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var magnetTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var doubleCoinTimer by remember(runSeed) { mutableFloatStateOf(0f) }
    var distance by remember(runSeed) { mutableFloatStateOf(0f) }
    var score by remember(runSeed) { mutableIntStateOf(0) }
    var bestScore by remember(project.projectId) { mutableIntStateOf(prefs.getInt(bestScoreKey, 0)) }
    var coins by remember(runSeed) { mutableIntStateOf(0) }
    var nextId by remember(runSeed) { mutableIntStateOf(100) }
    var lastFrameNanos by remember(runSeed) { mutableLongStateOf(0L) }

    val obstacles = remember(runSeed, project.selectedObstaclePack, project.selectedMap) {
        mutableStateListOf(
            ThreeDObstacle(1, obstacleFor3DPack(project.selectedObstaclePack, 1), 0, 1.28f),
            ThreeDObstacle(2, obstacleFor3DPack(project.selectedObstaclePack, 2), 2, 1.78f),
            ThreeDObstacle(3, ThreeDObstacleType.LOW_BLOCK, 1, 2.24f)
        )
    }
    val coinList = remember(runSeed, project.coinsEnabled) {
        mutableStateListOf<ThreeDCoin>().apply {
            if (project.coinsEnabled) {
                add(ThreeDCoin(10, 1, 1.10f, 0.08f))
                add(ThreeDCoin(11, 0, 1.55f, 0.00f))
                add(ThreeDCoin(12, 2, 2.05f, 0.10f))
            }
        }
    }
    val powerups = remember(runSeed, project.powerupsEnabled) {
        mutableStateListOf<ThreeDPowerup>().apply {
            if (project.powerupsEnabled) add(ThreeDPowerup(20, ThreeDPowerupType.SHIELD, 1, 2.62f))
        }
    }

    val visualLane by animateFloatAsState(
        targetValue = targetLane.toFloat(),
        animationSpec = tween(durationMillis = 150),
        label = "3d-lane-animation"
    )
    val slideProgress by animateFloatAsState(
        targetValue = if (slideTimer > 0f) 1f else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "3d-slide-animation"
    )

    fun laneLeft() {
        if (!isGameOver) {
            targetLane = (targetLane - 1).coerceAtLeast(0)
            lane = targetLane
            isRunning = true
        }
    }

    fun laneRight() {
        if (!isGameOver) {
            targetLane = (targetLane + 1).coerceAtMost(2)
            lane = targetLane
            isRunning = true
        }
    }

    fun jump() {
        if (!isGameOver && jumpLift <= 0.02f) {
            val mapBoost = if (project.selectedMap == "Snow Bridge") 0.04f else 0f
            jumpVelocity = 1.45f + project.gameSpeed * 0.025f + mapBoost
            isRunning = true
        }
    }

    fun slide() {
        if (!isGameOver) {
            slideTimer = 0.52f
            isRunning = true
        }
    }

    fun restart() {
        runSeed += 1
    }

    LaunchedEffect(score) {
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt(bestScoreKey, score).apply()
        }
    }

    LaunchedEffect(runSeed, project.gameSpeed, project.difficulty, project.selectedObstaclePack, project.selectedMap, project.coinsEnabled, project.powerupsEnabled) {
        while (true) {
            val frameTime = withFrameNanos { it }
            if (lastFrameNanos == 0L) {
                lastFrameNanos = frameTime
                continue
            }
            val dt = ((frameTime - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = frameTime

            if (!isRunning || isGameOver) continue

            val mapSpeed = when (project.selectedMap) {
                "Cyber Track" -> 0.035f
                "Snow Bridge" -> 0.015f
                else -> 0f
            }
            val worldSpeed = 0.34f + project.gameSpeed * 0.052f + project.difficulty * 0.022f + mapSpeed
            distance += worldSpeed * dt
            score = max(score, (distance * 140).toInt() + coins * 100)

            if (slideTimer > 0f) slideTimer = (slideTimer - dt).coerceAtLeast(0f)
            if (shieldTimer > 0f) shieldTimer = (shieldTimer - dt).coerceAtLeast(0f)
            if (magnetTimer > 0f) magnetTimer = (magnetTimer - dt).coerceAtLeast(0f)
            if (doubleCoinTimer > 0f) doubleCoinTimer = (doubleCoinTimer - dt).coerceAtLeast(0f)

            if (jumpVelocity > 0f || jumpLift > 0f) {
                val gravity = if (project.selectedMap == "Snow Bridge") 2.65f else 3.05f
                jumpLift += jumpVelocity * dt
                jumpVelocity -= gravity * dt
                if (jumpLift <= 0f) {
                    jumpLift = 0f
                    jumpVelocity = 0f
                }
            }

            val movedObstacles = obstacles.map { obstacle ->
                val moved = obstacle.copy(z = obstacle.z - worldSpeed * dt)
                if (!moved.scored && moved.z < -0.04f) moved.copy(scored = true) else moved
            }.filter { it.z > -0.22f }
            obstacles.clear()
            obstacles.addAll(movedObstacles)

            val movedCoins = coinList.map { coin ->
                var moved = coin.copy(z = coin.z - worldSpeed * dt)
                val laneMatch = if (magnetTimer > 0f) abs(moved.lane - lane) <= 1 else moved.lane == lane
                val zRange = if (magnetTimer > 0f) 0.17f else 0.085f
                val heightOk = magnetTimer > 0f || abs(jumpLift - moved.height) < 0.30f
                if (!moved.collected && laneMatch && abs(moved.z) < zRange && heightOk) {
                    moved = moved.copy(collected = true)
                    coins += if (doubleCoinTimer > 0f) 2 else 1
                    score += if (doubleCoinTimer > 0f) 220 else 120
                }
                moved
            }.filter { it.z > -0.22f && !it.collected }
            coinList.clear()
            coinList.addAll(movedCoins)

            val movedPowerups = powerups.map { powerup ->
                var moved = powerup.copy(z = powerup.z - worldSpeed * dt)
                if (!moved.collected && moved.lane == lane && abs(moved.z) < 0.095f) {
                    moved = moved.copy(collected = true)
                    when (moved.type) {
                        ThreeDPowerupType.SHIELD -> shieldTimer = 5.0f
                        ThreeDPowerupType.MAGNET -> magnetTimer = 5.0f
                        ThreeDPowerupType.DOUBLE_COIN -> doubleCoinTimer = 5.0f
                    }
                    score += 200
                }
                moved
            }.filter { it.z > -0.22f && !it.collected }
            powerups.clear()
            powerups.addAll(movedPowerups)

            val farthest = max(
                obstacles.maxOfOrNull { it.z } ?: 0f,
                coinList.maxOfOrNull { it.z } ?: 0f
            )
            val minimumGap = (0.56f - project.difficulty * 0.026f).coerceAtLeast(0.38f)
            if (farthest < 1.42f) {
                val random = Random(nextId + score + project.selectedMap.hashCode())
                val spawnZ = max(1.80f, farthest + minimumGap + random.nextFloat() * 0.32f)
                val obstacleLane = random.nextInt(3)
                val type = obstacleFor3DPack(project.selectedObstaclePack, random.nextInt(100))
                obstacles.add(ThreeDObstacle(nextId, type, obstacleLane, spawnZ))
                nextId += 1

                if (project.coinsEnabled) {
                    val coinLane = if (random.nextInt(100) < 35) obstacleLane else random.nextInt(3)
                    coinList.add(ThreeDCoin(nextId, coinLane, spawnZ + 0.22f + random.nextFloat() * 0.25f, if (random.nextBoolean()) 0.07f else 0f))
                    nextId += 1
                }

                if (project.powerupsEnabled && random.nextInt(100) < 18) {
                    val powerupType = when (random.nextInt(3)) {
                        0 -> ThreeDPowerupType.SHIELD
                        1 -> ThreeDPowerupType.MAGNET
                        else -> ThreeDPowerupType.DOUBLE_COIN
                    }
                    powerups.add(ThreeDPowerup(nextId, powerupType, random.nextInt(3), spawnZ + 0.45f))
                    nextId += 1
                }
            }

            val hit = obstacles.firstOrNull { obstacle ->
                val sameLane = obstacle.lane == lane
                val closeEnough = obstacle.z in -0.030f..0.095f
                if (!sameLane || !closeEnough) return@firstOrNull false
                when (obstacle.type) {
                    ThreeDObstacleType.LOW_BLOCK -> jumpLift < 0.24f
                    ThreeDObstacleType.ROAD_BLOCK -> jumpLift < 0.20f
                    ThreeDObstacleType.HIGH_GATE -> slideTimer <= 0.08f
                    ThreeDObstacleType.RAMP -> false
                }
            }
            if (hit != null) {
                if (shieldTimer > 0f) {
                    shieldTimer = 0f
                    obstacles.remove(hit)
                    score += 160
                } else {
                    isGameOver = true
                    isRunning = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("3D Runner Playtest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
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
                    Text("Lane: ${lane + 1}")
                    Text("Speed: ${project.gameSpeed}")
                    Text("Difficulty: ${project.difficulty}")
                }
                val active = buildList {
                    if (shieldTimer > 0f) add("Shield ${shieldTimer.toInt()}s")
                    if (magnetTimer > 0f) add("Magnet ${magnetTimer.toInt()}s")
                    if (doubleCoinTimer > 0f) add("2x Coins ${doubleCoinTimer.toInt()}s")
                }
                Text(
                    text = if (active.isEmpty()) "Map: ${project.selectedMap}  •  ${project.selectedCharacter}" else active.joinToString("  •  "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ThreeDRunnerCanvas(
                project = project,
                visualLane = visualLane,
                jumpLift = jumpLift,
                slideProgress = slideProgress,
                obstacles = obstacles,
                coins = coinList,
                powerups = powerups,
                distance = distance,
                shieldActive = shieldTimer > 0f,
                magnetActive = magnetTimer > 0f,
                modifier = Modifier.fillMaxSize()
            )

            if (isGameOver) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xE6080914), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Game Over", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Score $score • Best $bestScore • Coins $coins", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = ::restart) { Text("Restart") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = ::laneLeft, modifier = Modifier.weight(1f), enabled = !isGameOver) { Text("Lane Left") }
            Button(onClick = ::laneRight, modifier = Modifier.weight(1f), enabled = !isGameOver) { Text("Lane Right") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = ::jump, modifier = Modifier.weight(1f), enabled = !isGameOver) { Text("Jump") }
            Button(onClick = ::slide, modifier = Modifier.weight(1f), enabled = !isGameOver) { Text("Slide") }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ThreeDRunnerCanvas(
    project: GameProject,
    visualLane: Float,
    jumpLift: Float,
    slideProgress: Float,
    obstacles: List<ThreeDObstacle>,
    coins: List<ThreeDCoin>,
    powerups: List<ThreeDPowerup>,
    distance: Float,
    shieldActive: Boolean,
    magnetActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080914))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            drawThreeDRunnerWorld(
                project = project,
                visualLane = visualLane,
                jumpLift = jumpLift,
                slideProgress = slideProgress,
                obstacles = obstacles,
                coins = coins,
                powerups = powerups,
                distance = distance,
                shieldActive = shieldActive,
                magnetActive = magnetActive
            )
        }
    }
}

private fun DrawScope.drawThreeDRunnerWorld(
    project: GameProject,
    visualLane: Float,
    jumpLift: Float,
    slideProgress: Float,
    obstacles: List<ThreeDObstacle>,
    coins: List<ThreeDCoin>,
    powerups: List<ThreeDPowerup>,
    distance: Float,
    shieldActive: Boolean,
    magnetActive: Boolean
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.20f
    val trackBottomY = h * 0.90f
    val centerX = w * 0.50f
    val primary = threeDThemePrimary(project.uiTheme)
    val map = threeDMapPalette(project.selectedMap)

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(map.skyTop, map.skyMid, map.groundBottom)),
        cornerRadius = CornerRadius(34f, 34f),
        size = size
    )

    drawMapBackdrop(project.selectedMap, distance, w, h, primary, map)

    val road = Path().apply {
        moveTo(centerX - w * 0.10f, horizonY)
        lineTo(centerX + w * 0.10f, horizonY)
        lineTo(w * 0.95f, trackBottomY)
        lineTo(w * 0.05f, trackBottomY)
        close()
    }
    drawPath(road, Brush.verticalGradient(listOf(map.roadTop, map.roadBottom)))
    drawPath(road, primary.copy(alpha = 0.22f), style = Stroke(width = 4f))

    drawRoadLines(distance, w, horizonY, trackBottomY, centerX, primary, project.selectedMap)

    val sortedDrawables = buildList {
        obstacles.forEach { add(Triple(0, it.z, it)) }
        coins.forEach { add(Triple(1, it.z, it)) }
        powerups.forEach { add(Triple(2, it.z, it)) }
    }.sortedByDescending { it.second }

    sortedDrawables.forEach { (_, _, item) ->
        when (item) {
            is ThreeDObstacle -> draw3DObstacle(item, w, horizonY, trackBottomY, centerX, project)
            is ThreeDCoin -> draw3DCoin(item, w, h, horizonY, trackBottomY, centerX, magnetActive)
            is ThreeDPowerup -> draw3DPowerup(item, w, h, horizonY, trackBottomY, centerX)
        }
    }

    draw3DPlayer(
        project = project,
        lane = visualLane,
        jumpLift = jumpLift,
        slideProgress = slideProgress,
        shieldActive = shieldActive,
        w = w,
        h = h,
        horizonY = horizonY,
        trackBottomY = trackBottomY,
        centerX = centerX,
        primary = primary
    )

    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(20f, 20f),
        size = Size(w - 40f, h - 40f),
        cornerRadius = CornerRadius(28f, 28f),
        style = Stroke(width = 2.5f)
    )
}

private fun DrawScope.drawRoadLines(
    distance: Float,
    w: Float,
    horizonY: Float,
    bottomY: Float,
    centerX: Float,
    primary: Color,
    mapName: String
) {
    val leftEdgeTop = centerX - w * 0.10f
    val rightEdgeTop = centerX + w * 0.10f
    val leftEdgeBottom = w * 0.05f
    val rightEdgeBottom = w * 0.95f

    repeat(4) { i ->
        val t = (i + 1) / 4f
        val top = Offset(lerp(leftEdgeTop, leftEdgeBottom, t), lerp(horizonY, bottomY, t))
        val bottom = Offset(lerp(rightEdgeTop, rightEdgeBottom, t), lerp(horizonY, bottomY, t))
        if (i == 1 || i == 2) {
            val laneT = i / 3f
            drawLine(
                Color.White.copy(alpha = 0.20f),
                Offset(lerp(leftEdgeTop, rightEdgeTop, laneT), horizonY + 12f),
                Offset(lerp(leftEdgeBottom, rightEdgeBottom, laneT), bottomY),
                strokeWidth = 4f
            )
        }
        drawLine(Color.White.copy(alpha = 0.06f), top, bottom, strokeWidth = 2f)
    }

    repeat(10) { index ->
        val raw = ((distance * 2.0f + index / 10f) % 1f)
        val t = raw.pow(1.25f)
        val y = lerp(horizonY + 8f, bottomY, t)
        val scale = 0.15f + t * 1.25f
        val lineWidth = w * 0.030f * scale
        val color = if (mapName == "Cyber Track") primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.34f)
        drawRoundRect(
            color = color,
            topLeft = Offset(centerX - lineWidth / 2f, y),
            size = Size(lineWidth, 8f * scale),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

private fun DrawScope.drawMapBackdrop(mapName: String, distance: Float, w: Float, h: Float, primary: Color, palette: ThreeDMapPalette) {
    val offset = (distance * w * 0.20f) % (w * 0.30f)
    when (mapName) {
        "Jungle Temple" -> {
            repeat(8) { i ->
                val x = i * w * 0.20f - offset
                drawRoundRect(
                    color = Color(0xFF1C5A3E).copy(alpha = 0.55f),
                    topLeft = Offset(x, h * 0.42f - (i % 2) * 34f),
                    size = Size(42f, h * 0.35f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                drawCircle(Color(0xFF36B66D).copy(alpha = 0.45f), radius = 45f + (i % 3) * 10f, center = Offset(x + 20f, h * 0.38f - (i % 2) * 30f))
            }
        }
        "Desert Ruins" -> {
            repeat(7) { i ->
                val x = i * w * 0.24f - offset
                drawRoundRect(
                    color = Color(0xFFB77A3E).copy(alpha = 0.45f),
                    topLeft = Offset(x, h * 0.44f - (i % 2) * 18f),
                    size = Size(62f, h * 0.22f + (i % 3) * 18f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        "Snow Bridge" -> {
            repeat(6) { i ->
                val x = i * w * 0.28f - offset
                val peak = h * (0.32f + (i % 2) * 0.04f)
                val path = Path().apply {
                    moveTo(x, h * 0.62f)
                    lineTo(x + 80f, peak)
                    lineTo(x + 160f, h * 0.62f)
                    close()
                }
                drawPath(path, Color(0xFFD7ECFF).copy(alpha = 0.35f))
            }
        }
        "Cyber Track" -> {
            repeat(6) { i ->
                val x = i * w * 0.24f - offset
                drawLine(primary.copy(alpha = 0.35f), Offset(x, h * 0.18f), Offset(x + 80f, h * 0.62f), strokeWidth = 3f)
                drawLine(primary.copy(alpha = 0.22f), Offset(x + 80f, h * 0.18f), Offset(x, h * 0.62f), strokeWidth = 3f)
            }
        }
        "Rail Track" -> {
            drawLine(palette.accent.copy(alpha = 0.35f), Offset(w * 0.18f, h * 0.52f), Offset(w * 0.03f, h * 0.88f), strokeWidth = 8f)
            drawLine(palette.accent.copy(alpha = 0.35f), Offset(w * 0.82f, h * 0.52f), Offset(w * 0.97f, h * 0.88f), strokeWidth = 8f)
        }
        else -> {
            repeat(8) { i ->
                val x = i * w * 0.20f - offset
                drawRoundRect(
                    color = primary.copy(alpha = 0.11f),
                    topLeft = Offset(x, h * 0.38f - (i % 3) * 32f),
                    size = Size(56f + (i % 2) * 25f, h * 0.28f + (i % 3) * 20f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
            }
        }
    }
}

private fun DrawScope.draw3DObstacle(obstacle: ThreeDObstacle, w: Float, horizonY: Float, bottomY: Float, centerX: Float, project: GameProject) {
    val depth = depthFromZ(obstacle.z)
    if (depth <= 0f) return
    val x = laneX(obstacle.lane.toFloat(), depth, w, centerX)
    val y = lerp(horizonY, bottomY, depth.pow(1.40f))
    val scale = 0.20f + depth * 1.42f
    val shadowW = 34f * scale
    drawOvalShadow(x, y, shadowW, scale)

    when (obstacle.type) {
        ThreeDObstacleType.LOW_BLOCK -> {
            drawRoundRect(
                color = Color(0xFFFF5252),
                topLeft = Offset(x - 22f * scale, y - 28f * scale),
                size = Size(44f * scale, 36f * scale),
                cornerRadius = CornerRadius(8f * scale, 8f * scale)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(x - 15f * scale, y - 24f * scale),
                size = Size(14f * scale, 9f * scale),
                cornerRadius = CornerRadius(4f * scale, 4f * scale)
            )
        }
        ThreeDObstacleType.ROAD_BLOCK -> {
            drawRoundRect(
                color = Color(0xFFFF8A3D),
                topLeft = Offset(x - 30f * scale, y - 34f * scale),
                size = Size(60f * scale, 42f * scale),
                cornerRadius = CornerRadius(10f * scale, 10f * scale)
            )
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x - 22f * scale, y - 20f * scale), Offset(x + 22f * scale, y - 4f * scale), strokeWidth = 4f * scale)
        }
        ThreeDObstacleType.HIGH_GATE -> {
            val gateColor = if (project.selectedMap == "Cyber Track") Color(0xFF00E5FF) else Color(0xFFFFD54F)
            drawRoundRect(
                color = gateColor,
                topLeft = Offset(x - 42f * scale, y - 110f * scale),
                size = Size(84f * scale, 24f * scale),
                cornerRadius = CornerRadius(7f * scale, 7f * scale)
            )
            drawLine(gateColor, Offset(x - 42f * scale, y - 108f * scale), Offset(x - 42f * scale, y), strokeWidth = 7f * scale)
            drawLine(gateColor, Offset(x + 42f * scale, y - 108f * scale), Offset(x + 42f * scale, y), strokeWidth = 7f * scale)
        }
        ThreeDObstacleType.RAMP -> {
            val path = Path().apply {
                moveTo(x - 44f * scale, y)
                lineTo(x + 44f * scale, y)
                lineTo(x + 28f * scale, y - 48f * scale)
                lineTo(x - 28f * scale, y - 28f * scale)
                close()
            }
            drawPath(path, Color(0xFF8E6BFF))
            drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(width = 3f * scale))
        }
    }
}

private fun DrawScope.draw3DCoin(coin: ThreeDCoin, w: Float, h: Float, horizonY: Float, bottomY: Float, centerX: Float, magnetActive: Boolean) {
    val depth = depthFromZ(coin.z)
    if (depth <= 0f) return
    val x = laneX(coin.lane.toFloat(), depth, w, centerX)
    val y = lerp(horizonY, bottomY, depth.pow(1.40f)) - (42f + coin.height * h * 0.50f) * (0.30f + depth)
    val scale = 0.24f + depth * 1.05f
    val radius = 10f * scale
    if (magnetActive) {
        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.13f), radius = radius * 2.3f, center = Offset(x, y))
    }
    drawCircle(Color(0xFFFFD54F), radius = radius, center = Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.55f), radius = radius * 0.32f, center = Offset(x - radius * 0.35f, y - radius * 0.35f))
}

private fun DrawScope.draw3DPowerup(powerup: ThreeDPowerup, w: Float, h: Float, horizonY: Float, bottomY: Float, centerX: Float) {
    val depth = depthFromZ(powerup.z)
    if (depth <= 0f) return
    val x = laneX(powerup.lane.toFloat(), depth, w, centerX)
    val y = lerp(horizonY, bottomY, depth.pow(1.40f)) - 58f * (0.30f + depth)
    val scale = 0.24f + depth * 1.12f
    val color = when (powerup.type) {
        ThreeDPowerupType.SHIELD -> Color(0xFF64FFDA)
        ThreeDPowerupType.MAGNET -> Color(0xFFFF80AB)
        ThreeDPowerupType.DOUBLE_COIN -> Color(0xFFFFD54F)
    }
    drawCircle(color.copy(alpha = 0.23f), radius = 22f * scale, center = Offset(x, y))
    drawCircle(color, radius = 14f * scale, center = Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.72f), radius = 5f * scale, center = Offset(x - 4f * scale, y - 4f * scale))
}

private fun DrawScope.draw3DPlayer(
    project: GameProject,
    lane: Float,
    jumpLift: Float,
    slideProgress: Float,
    shieldActive: Boolean,
    w: Float,
    h: Float,
    horizonY: Float,
    trackBottomY: Float,
    centerX: Float,
    primary: Color
) {
    val depth = 0.94f
    val x = laneX(lane, depth, w, centerX)
    val groundY = lerp(horizonY, trackBottomY, depth.pow(1.40f))
    val liftPx = jumpLift * h * 0.28f
    val scale = 1.22f
    val playerColor = threeDCharacterColor(project.selectedCharacter, primary)
    val bodyH = (76f - 26f * slideProgress) * scale
    val bodyW = (50f + 16f * slideProgress) * scale
    val yBottom = groundY - liftPx
    drawOvalShadow(x, groundY, 44f * scale, scale)

    if (shieldActive) {
        drawCircle(Color(0xFF64FFDA).copy(alpha = 0.22f), radius = 58f, center = Offset(x, yBottom - bodyH * 0.45f))
        drawCircle(Color(0xFF64FFDA).copy(alpha = 0.72f), radius = 58f, center = Offset(x, yBottom - bodyH * 0.45f), style = Stroke(width = 5f))
    }

    drawRoundRect(
        color = playerColor,
        topLeft = Offset(x - bodyW / 2f, yBottom - bodyH),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(17f, 17f)
    )
    drawCircle(Color.White.copy(alpha = 0.88f), radius = 11f, center = Offset(x + bodyW * 0.16f, yBottom - bodyH + 24f))
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(x - bodyW / 2f, yBottom - 8f),
        size = Size(bodyW, 8f),
        cornerRadius = CornerRadius(5f, 5f)
    )
}

private fun DrawScope.drawOvalShadow(x: Float, y: Float, width: Float, scale: Float) {
    drawOval(
        color = Color.Black.copy(alpha = 0.24f),
        topLeft = Offset(x - width / 2f, y - 3f * scale),
        size = Size(width, 12f * scale)
    )
}

private fun obstacleFor3DPack(pack: String, seed: Int): ThreeDObstacleType = when (pack) {
    "Rocks + Barrels" -> if (seed % 4 == 0) ThreeDObstacleType.ROAD_BLOCK else ThreeDObstacleType.LOW_BLOCK
    "Gates + Ramps" -> if (seed % 3 == 0) ThreeDObstacleType.RAMP else ThreeDObstacleType.HIGH_GATE
    "Cars + Cones" -> if (seed % 5 == 0) ThreeDObstacleType.HIGH_GATE else ThreeDObstacleType.ROAD_BLOCK
    "Boxes + Fences" -> if (seed % 4 == 0) ThreeDObstacleType.HIGH_GATE else ThreeDObstacleType.LOW_BLOCK
    else -> when (seed % 4) {
        0 -> ThreeDObstacleType.LOW_BLOCK
        1 -> ThreeDObstacleType.HIGH_GATE
        2 -> ThreeDObstacleType.ROAD_BLOCK
        else -> ThreeDObstacleType.RAMP
    }
}

private data class ThreeDMapPalette(
    val skyTop: Color,
    val skyMid: Color,
    val groundBottom: Color,
    val roadTop: Color,
    val roadBottom: Color,
    val accent: Color
)

private fun threeDMapPalette(map: String): ThreeDMapPalette = when (map) {
    "Jungle Temple" -> ThreeDMapPalette(
        Color(0xFF15382E), Color(0xFF0D241D), Color(0xFF06100D), Color(0xFF243A2E), Color(0xFF0E1814), Color(0xFF36E66F)
    )
    "Desert Ruins" -> ThreeDMapPalette(
        Color(0xFF4B2E1A), Color(0xFF28180F), Color(0xFF0E0907), Color(0xFF4A3424), Color(0xFF1B1110), Color(0xFFFFB45B)
    )
    "Snow Bridge" -> ThreeDMapPalette(
        Color(0xFF17324A), Color(0xFF0E1B2C), Color(0xFF070B12), Color(0xFF24384E), Color(0xFF0B111D), Color(0xFFD7ECFF)
    )
    "Cyber Track" -> ThreeDMapPalette(
        Color(0xFF1A0D3D), Color(0xFF0D0A26), Color(0xFF050510), Color(0xFF17102F), Color(0xFF080611), Color(0xFF00E5FF)
    )
    "Rail Track" -> ThreeDMapPalette(
        Color(0xFF1B2435), Color(0xFF101522), Color(0xFF070812), Color(0xFF252B36), Color(0xFF10121A), Color(0xFFE0C07A)
    )
    else -> ThreeDMapPalette(
        Color(0xFF171B34), Color(0xFF101326), Color(0xFF070812), Color(0xFF1C2035), Color(0xFF090B16), Color(0xFF8E6BFF)
    )
}

private fun threeDThemePrimary(theme: String): Color = when (theme) {
    "Cyber Blue" -> Color(0xFF00E5FF)
    "Lava Orange" -> Color(0xFFFF8A3D)
    "Forest Green" -> Color(0xFF35E66B)
    "Mono Dark" -> Color(0xFFEDEBFF)
    else -> Color(0xFF8E6BFF)
}

private fun threeDCharacterColor(character: String, fallback: Color): Color = when {
    character.contains("Robot", ignoreCase = true) -> Color(0xFF00E5FF)
    character.contains("Soldier", ignoreCase = true) -> Color(0xFF6AE27A)
    character.contains("Ninja", ignoreCase = true) -> Color(0xFF242741)
    character.contains("Alien", ignoreCase = true) -> Color(0xFFBAFF5A)
    else -> fallback
}

private fun depthFromZ(z: Float): Float {
    val depth = 1f - (z / 1.92f)
    return depth.coerceIn(0.02f, 1.08f)
}

private fun laneX(lane: Float, depth: Float, w: Float, centerX: Float): Float {
    val laneOffset = (lane - 1f) / 1f
    val spread = w * 0.34f * depth.pow(1.15f)
    return centerX + laneOffset * spread
}

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1.1f)
