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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050512))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
    val horizonY = h * 0.12f
    val trackBottomY = h * 1.02f
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
        moveTo(centerX - w * 0.13f, horizonY)
        lineTo(centerX + w * 0.13f, horizonY)
        lineTo(w * 1.06f, trackBottomY)
        lineTo(-w * 0.06f, trackBottomY)
        close()
    }
    val roadShoulder = Path().apply {
        moveTo(centerX - w * 0.17f, horizonY + h * 0.015f)
        lineTo(centerX + w * 0.17f, horizonY + h * 0.015f)
        lineTo(w * 1.18f, trackBottomY)
        lineTo(-w * 0.18f, trackBottomY)
        close()
    }
    drawPath(roadShoulder, map.accent.copy(alpha = 0.18f))
    drawPath(road, Brush.verticalGradient(listOf(map.roadTop.copy(alpha = 0.98f), map.roadBottom)))
    drawPath(road, primary.copy(alpha = 0.38f), style = Stroke(width = 5f))

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
    val leftEdgeTop = centerX - w * 0.13f
    val rightEdgeTop = centerX + w * 0.13f
    val leftEdgeBottom = -w * 0.06f
    val rightEdgeBottom = w * 1.06f

    fun roadPoint(edgeFraction: Float, depth: Float): Offset {
        val leftX = lerp(leftEdgeTop, leftEdgeBottom, depth)
        val rightX = lerp(rightEdgeTop, rightEdgeBottom, depth)
        return Offset(lerp(leftX, rightX, edgeFraction), lerp(horizonY, bottomY, depth))
    }

    // Depth strips make the road feel like it is moving toward the player.
    repeat(9) { index ->
        val raw = ((distance * 1.7f + index / 9f) % 1f)
        val depth = raw.pow(1.38f).coerceIn(0.02f, 1f)
        val left = roadPoint(0f, depth)
        val right = roadPoint(1f, depth)
        drawLine(Color.White.copy(alpha = 0.055f + depth * 0.055f), left, right, strokeWidth = 1.5f + depth * 3.2f)
    }

    // Lane dividers.
    listOf(1f / 3f, 2f / 3f).forEach { laneFraction ->
        val start = roadPoint(laneFraction, 0.02f)
        val end = roadPoint(laneFraction, 1.0f)
        drawLine(Color.White.copy(alpha = 0.20f), start, end, strokeWidth = 2.2f)
    }

    // Moving dash markers in center lane.
    repeat(12) { index ->
        val raw = ((distance * 2.35f + index / 12f) % 1f)
        val depth = raw.pow(1.55f).coerceIn(0.03f, 1f)
        val point = roadPoint(0.50f, depth)
        val dashW = w * (0.010f + depth * 0.030f)
        val dashH = 5f + depth * 11f
        val color = if (mapName == "Cyber Track") primary.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.42f)
        drawRoundRect(
            color = color,
            topLeft = Offset(point.x - dashW / 2f, point.y - dashH / 2f),
            size = Size(dashW, dashH),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }

    // Edge glow.
    drawLine(primary.copy(alpha = 0.34f), roadPoint(0f, 0.03f), roadPoint(0f, 1f), strokeWidth = 3f)
    drawLine(primary.copy(alpha = 0.34f), roadPoint(1f, 0.03f), roadPoint(1f, 1f), strokeWidth = 3f)
}

private fun DrawScope.drawMapBackdrop(mapName: String, distance: Float, w: Float, h: Float, primary: Color, palette: ThreeDMapPalette) {
    val slowOffset = (distance * w * 0.16f) % (w * 0.36f)
    val fastOffset = (distance * w * 0.34f) % (w * 0.42f)

    // Far glow / horizon haze.
    drawCircle(
        color = palette.accent.copy(alpha = 0.10f),
        radius = w * 0.52f,
        center = Offset(w * 0.50f, h * 0.16f)
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, palette.accent.copy(alpha = 0.08f), Color.Transparent),
            startY = h * 0.10f,
            endY = h * 0.56f
        ),
        size = Size(w, h)
    )

    when (mapName) {
        "Jungle Temple" -> {
            repeat(10) { i ->
                val x = i * w * 0.18f - slowOffset - w * 0.10f
                val trunkH = h * (0.25f + (i % 3) * 0.035f)
                drawRoundRect(
                    color = Color(0xFF143528).copy(alpha = 0.75f),
                    topLeft = Offset(x, h * 0.48f - trunkH * 0.15f),
                    size = Size(24f + (i % 2) * 8f, trunkH),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                drawCircle(Color(0xFF2FB363).copy(alpha = 0.48f), radius = 42f + (i % 3) * 12f, center = Offset(x + 18f, h * 0.43f - (i % 2) * 28f))
            }
        }
        "Desert Ruins" -> {
            repeat(8) { i ->
                val x = i * w * 0.22f - slowOffset - w * 0.08f
                val pillarH = h * (0.19f + (i % 3) * 0.035f)
                drawRoundRect(
                    color = Color(0xFFB7783A).copy(alpha = 0.36f),
                    topLeft = Offset(x, h * 0.54f - pillarH),
                    size = Size(52f + (i % 2) * 18f, pillarH),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                drawRoundRect(
                    color = Color(0xFFE0A15A).copy(alpha = 0.18f),
                    topLeft = Offset(x - 12f, h * 0.54f - pillarH - 12f),
                    size = Size(76f + (i % 2) * 18f, 14f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
            repeat(3) { i ->
                val y = h * (0.55f + i * 0.05f)
                drawOval(
                    color = Color(0xFFB7783A).copy(alpha = 0.13f),
                    topLeft = Offset(-w * 0.08f + i * w * 0.22f, y),
                    size = Size(w * 0.90f, h * 0.20f)
                )
            }
        }
        "Snow Bridge" -> {
            repeat(7) { i ->
                val x = i * w * 0.24f - slowOffset - w * 0.16f
                val peak = h * (0.30f + (i % 2) * 0.05f)
                val path = Path().apply {
                    moveTo(x, h * 0.62f)
                    lineTo(x + 92f, peak)
                    lineTo(x + 184f, h * 0.62f)
                    close()
                }
                drawPath(path, Color(0xFFD7ECFF).copy(alpha = 0.34f))
                drawLine(Color.White.copy(alpha = 0.28f), Offset(x + 92f, peak), Offset(x + 124f, h * 0.62f), strokeWidth = 3f)
            }
            repeat(18) { i ->
                val x = (i * 53f + fastOffset) % (w + 80f) - 40f
                val y = h * (0.16f + (i % 8) * 0.045f)
                drawCircle(Color.White.copy(alpha = 0.26f), radius = 2f + (i % 3), center = Offset(x, y))
            }
        }
        "Cyber Track" -> {
            repeat(9) { i ->
                val x = i * w * 0.18f - fastOffset
                drawLine(primary.copy(alpha = 0.38f), Offset(x, h * 0.10f), Offset(x + 110f, h * 0.72f), strokeWidth = 3.5f)
                drawLine(Color(0xFFFF4DFF).copy(alpha = 0.20f), Offset(x + 100f, h * 0.12f), Offset(x - 10f, h * 0.72f), strokeWidth = 2.5f)
            }
            repeat(5) { i ->
                val y = h * (0.22f + i * 0.10f)
                drawLine(primary.copy(alpha = 0.14f), Offset(0f, y), Offset(w, y), strokeWidth = 2f)
            }
        }
        "Rail Track" -> {
            drawLine(palette.accent.copy(alpha = 0.42f), Offset(w * 0.18f, h * 0.46f), Offset(w * 0.00f, h * 0.94f), strokeWidth = 8f)
            drawLine(palette.accent.copy(alpha = 0.42f), Offset(w * 0.82f, h * 0.46f), Offset(w * 1.00f, h * 0.94f), strokeWidth = 8f)
            repeat(8) { i ->
                val x = i * w * 0.20f - fastOffset
                drawRoundRect(Color(0xFF27334A).copy(alpha = 0.55f), Offset(x, h * 0.38f), Size(42f, h * 0.32f), CornerRadius(8f, 8f))
            }
        }
        else -> {
            repeat(10) { i ->
                val x = i * w * 0.18f - slowOffset - w * 0.10f
                val buildingH = h * (0.18f + (i % 4) * 0.04f)
                drawRoundRect(
                    color = Color(0xFF1D2544).copy(alpha = 0.62f),
                    topLeft = Offset(x, h * 0.55f - buildingH),
                    size = Size(52f + (i % 2) * 28f, buildingH),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                repeat(3) { j ->
                    drawRoundRect(
                        color = primary.copy(alpha = 0.18f),
                        topLeft = Offset(x + 12f + j * 16f, h * 0.57f - buildingH + 20f),
                        size = Size(7f, 12f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }

    // Foreground speed panels on the sides.
    repeat(6) { i ->
        val y = h * (0.34f + i * 0.12f) + (fastOffset % 54f)
        val alpha = (0.10f + i * 0.012f).coerceAtMost(0.22f)
        drawRoundRect(primary.copy(alpha = alpha), Offset(-18f, y), Size(44f, 48f), CornerRadius(8f, 8f))
        drawRoundRect(primary.copy(alpha = alpha), Offset(w - 26f, y + 20f), Size(44f, 48f), CornerRadius(8f, 8f))
    }
}

private fun DrawScope.draw3DObstacle(obstacle: ThreeDObstacle, w: Float, horizonY: Float, bottomY: Float, centerX: Float, project: GameProject) {
    val depth = depthFromZ(obstacle.z)
    if (depth <= 0f) return
    val x = laneX(obstacle.lane.toFloat(), depth, w, centerX)
    val y = lerp(horizonY, bottomY, depth.pow(1.34f))
    val scale = 0.18f + depth * 1.52f
    drawOvalShadow(x, y + 4f * scale, 42f * scale, scale)

    when (obstacle.type) {
        ThreeDObstacleType.LOW_BLOCK -> {
            drawPseudoBox(
                centerX = x,
                baseY = y,
                width = 48f * scale,
                height = 42f * scale,
                depth = 18f * scale,
                front = Color(0xFFFF4D5E),
                top = Color(0xFFFF8A92),
                side = Color(0xFFBA2536)
            )
        }
        ThreeDObstacleType.ROAD_BLOCK -> {
            drawPseudoBox(
                centerX = x,
                baseY = y,
                width = 70f * scale,
                height = 48f * scale,
                depth = 22f * scale,
                front = Color(0xFFFF8A3D),
                top = Color(0xFFFFBD76),
                side = Color(0xFFB85624)
            )
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x - 26f * scale, y - 30f * scale), Offset(x + 26f * scale, y - 12f * scale), strokeWidth = 4f * scale)
        }
        ThreeDObstacleType.HIGH_GATE -> {
            val gateColor = if (project.selectedMap == "Cyber Track") Color(0xFF00E5FF) else Color(0xFFFFD54F)
            drawCircle(gateColor.copy(alpha = 0.14f), radius = 64f * scale, center = Offset(x, y - 60f * scale))
            drawRoundRect(
                color = gateColor,
                topLeft = Offset(x - 50f * scale, y - 122f * scale),
                size = Size(100f * scale, 22f * scale),
                cornerRadius = CornerRadius(8f * scale, 8f * scale)
            )
            drawRoundRect(gateColor.copy(alpha = 0.86f), Offset(x - 50f * scale, y - 120f * scale), Size(9f * scale, 124f * scale), CornerRadius(4f, 4f))
            drawRoundRect(gateColor.copy(alpha = 0.86f), Offset(x + 41f * scale, y - 120f * scale), Size(9f * scale, 124f * scale), CornerRadius(4f, 4f))
        }
        ThreeDObstacleType.RAMP -> {
            val path = Path().apply {
                moveTo(x - 52f * scale, y)
                lineTo(x + 52f * scale, y)
                lineTo(x + 30f * scale, y - 54f * scale)
                lineTo(x - 32f * scale, y - 30f * scale)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFB39CFF), Color(0xFF6D4CFF))))
            drawPath(path, Color.White.copy(alpha = 0.23f), style = Stroke(width = 3f * scale))
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
    val groundY = lerp(horizonY, trackBottomY, depth.pow(1.34f))
    val liftPx = jumpLift * h * 0.30f
    val scale = 1.12f
    val playerColor = threeDCharacterColor(project.selectedCharacter, primary)
    val yBottom = groundY - liftPx
    val crouch = slideProgress
    val torsoH = (58f - 22f * crouch) * scale
    val torsoW = (38f + 20f * crouch) * scale

    drawOvalShadow(x, groundY + 8f, 58f * scale, scale)

    if (shieldActive) {
        drawCircle(Color(0xFF64FFDA).copy(alpha = 0.18f), radius = 70f, center = Offset(x, yBottom - torsoH * 0.60f))
        drawCircle(Color(0xFF64FFDA).copy(alpha = 0.68f), radius = 70f, center = Offset(x, yBottom - torsoH * 0.60f), style = Stroke(width = 5f))
    }

    // Legs / motion streaks.
    val legColor = playerColor.copy(alpha = 0.82f)
    if (crouch < 0.5f) {
        drawLine(legColor, Offset(x - 10f * scale, yBottom - 4f), Offset(x - 22f * scale, yBottom + 22f * scale), strokeWidth = 8f * scale)
        drawLine(legColor.copy(alpha = 0.72f), Offset(x + 9f * scale, yBottom - 4f), Offset(x + 20f * scale, yBottom + 18f * scale), strokeWidth = 8f * scale)
    }

    // Body.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(playerColor.copy(alpha = 1f), playerColor.copy(alpha = 0.68f))),
        topLeft = Offset(x - torsoW / 2f, yBottom - torsoH - 14f * scale),
        size = Size(torsoW, torsoH),
        cornerRadius = CornerRadius(18f * scale, 18f * scale)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(x - torsoW / 2f + 6f, yBottom - torsoH - 8f * scale),
        size = Size(torsoW * 0.42f, torsoH * 0.45f),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Head / visor.
    if (crouch < 0.65f) {
        drawCircle(playerColor.copy(alpha = 0.95f), radius = 19f * scale, center = Offset(x, yBottom - torsoH - 32f * scale))
        drawCircle(Color.White.copy(alpha = 0.90f), radius = 6f * scale, center = Offset(x + 7f * scale, yBottom - torsoH - 37f * scale))
    }

    // Speed glow under player.
    drawRoundRect(
        color = primary.copy(alpha = 0.28f),
        topLeft = Offset(x - 30f * scale, yBottom + 18f * scale),
        size = Size(60f * scale, 6f * scale),
        cornerRadius = CornerRadius(8f, 8f)
    )
}

private fun DrawScope.drawPseudoBox(
    centerX: Float,
    baseY: Float,
    width: Float,
    height: Float,
    depth: Float,
    front: Color,
    top: Color,
    side: Color
) {
    val left = centerX - width / 2f
    val topY = baseY - height
    val frontRectTop = Offset(left, topY)
    val frontSize = Size(width, height)

    val topFace = Path().apply {
        moveTo(left, topY)
        lineTo(left + depth, topY - depth)
        lineTo(left + width + depth, topY - depth)
        lineTo(left + width, topY)
        close()
    }
    val sideFace = Path().apply {
        moveTo(left + width, topY)
        lineTo(left + width + depth, topY - depth)
        lineTo(left + width + depth, baseY - depth)
        lineTo(left + width, baseY)
        close()
    }

    drawPath(topFace, top)
    drawPath(sideFace, side)
    drawRoundRect(front, frontRectTop, frontSize, CornerRadius(9f, 9f))
    drawPath(topFace, Color.White.copy(alpha = 0.16f), style = Stroke(width = 2f))
    drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(left, baseY - height * 0.20f), Size(width, height * 0.20f), CornerRadius(8f, 8f))
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
    val depth = 1f - (z / 2.05f)
    return depth.coerceIn(0.02f, 1.08f)
}

private fun laneX(lane: Float, depth: Float, w: Float, centerX: Float): Float {
    val laneOffset = lane - 1f
    val nearSpread = w * 0.38f
    val farSpread = w * 0.055f
    val spread = lerp(farSpread, nearSpread, depth.pow(1.10f))
    return centerX + laneOffset * spread
}

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1.1f)
