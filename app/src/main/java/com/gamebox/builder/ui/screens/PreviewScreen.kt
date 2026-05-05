package com.gamebox.builder.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamebox.builder.data.GameDimension
import com.gamebox.builder.data.GameProject
import com.gamebox.builder.ui.GameBoxHeader
import com.gamebox.builder.ui.InfoRow

@Composable
fun PreviewScreen(
    project: GameProject,
    onBack: () -> Unit
) {
    if (project.templateId == "2d_endless_runner") {
        RunnerGameScreen(project = project, onBack = onBack)
        return
    }

    if (project.templateId == "2d_platformer") {
        PlatformerGameScreen(project = project, onBack = onBack)
        return
    }

    var lane by remember { mutableIntStateOf(1) }
    var actionTick by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GameBoxHeader(
            title = "Preview Mode ▶️",
            subtitle = "Phase 3 connects editor rules to the real runner: speed, difficulty, obstacle pack, coins and powerups. Other templates still use safe preview."
        )
        OutlinedButton(onClick = onBack) { Text("Back to editor") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(project.gameTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                InfoRow("Template", project.templateName)
                InfoRow("Character", project.selectedCharacter)
                InfoRow("Map", project.selectedMap)
                InfoRow("Speed", project.gameSpeed.toString())
                InfoRow("Difficulty", project.difficulty.toString())
            }
        }

        GamePreviewCanvas(
            project = project,
            lane = lane,
            actionTick = actionTick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                if (project.dimension == GameDimension.TWO_D) actionTick++ else lane = (lane + 2) % 3
            }, modifier = Modifier.weight(1f)) { Text(if (project.dimension == GameDimension.TWO_D) "Jump" else "Lane Left") }
            Button(onClick = {
                if (project.dimension == GameDimension.TWO_D) actionTick += 2 else lane = (lane + 1) % 3
            }, modifier = Modifier.weight(1f)) { Text(if (project.dimension == GameDimension.TWO_D) "Slide" else "Lane Right") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GamePreviewCanvas(project: GameProject, lane: Int, actionTick: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "preview-loop")
    val loop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (2600 - project.gameSpeed * 250).coerceAtLeast(900), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "track-offset"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080914))
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val primary = when (project.uiTheme) {
                    "Cyber Blue" -> Color(0xFF00E5FF)
                    "Lava Orange" -> Color(0xFFFF8A3D)
                    "Forest Green" -> Color(0xFF35E66B)
                    "Mono Dark" -> Color(0xFFEDEBFF)
                    else -> Color(0xFF8E6BFF)
                }
                val secondary = Color(0xFFFFD54F)

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF15182A), Color(0xFF070812))
                    ),
                    cornerRadius = CornerRadius(32f, 32f),
                    size = size
                )

                if (project.dimension == GameDimension.THREE_D) {
                    val horizonY = h * 0.18f
                    drawLine(primary.copy(alpha = 0.7f), Offset(w * 0.15f, horizonY), Offset(w * 0.85f, horizonY), strokeWidth = 5f)
                    drawLine(primary.copy(alpha = 0.6f), Offset(w * 0.50f, horizonY), Offset(w * 0.16f, h * 0.92f), strokeWidth = 5f)
                    drawLine(primary.copy(alpha = 0.6f), Offset(w * 0.50f, horizonY), Offset(w * 0.84f, h * 0.92f), strokeWidth = 5f)
                    drawLine(Color.White.copy(alpha = 0.18f), Offset(w * 0.39f, h * 0.26f), Offset(w * 0.34f, h * 0.92f), strokeWidth = 3f)
                    drawLine(Color.White.copy(alpha = 0.18f), Offset(w * 0.61f, h * 0.26f), Offset(w * 0.66f, h * 0.92f), strokeWidth = 3f)

                    repeat(5) { index ->
                        val progress = (loop + index * 0.2f) % 1f
                        val y = horizonY + progress * h * 0.75f
                        val scale = progress.coerceAtLeast(0.12f)
                        val x = w * (0.31f + (index % 3) * 0.19f)
                        drawRoundRect(
                            color = Color(0xFFFF5252),
                            topLeft = Offset(x - 18f * scale, y),
                            size = Size(36f * scale + 12f, 52f * scale + 12f),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }

                    val playerX = w * (0.31f + lane.coerceIn(0, 2) * 0.19f)
                    drawCircle(primary, radius = 38f, center = Offset(playerX, h * 0.76f))
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = 15f, center = Offset(playerX, h * 0.74f))
                } else {
                    val groundY = h * 0.78f
                    drawLine(primary.copy(alpha = 0.9f), Offset(0f, groundY), Offset(w, groundY), strokeWidth = 8f)

                    repeat(6) { index ->
                        val x = w - ((loop * w) + index * w / 5f) % (w + 80f)
                        drawRoundRect(
                            color = Color(0xFFFF5252),
                            topLeft = Offset(x, groundY - 56f - (project.difficulty * 4f)),
                            size = Size(46f, 56f + project.difficulty * 4f),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        if (project.coinsEnabled) {
                            drawCircle(secondary, radius = 13f, center = Offset(x + 80f, groundY - 115f))
                        }
                    }

                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(w * 0.17f, groundY - 78f - if (actionTick % 3 == 1) 54f else 0f),
                        size = Size(58f, if (actionTick % 3 == 2) 44f else 78f),
                        cornerRadius = CornerRadius(18f, 18f)
                    )
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = 12f, center = Offset(w * 0.17f + 29f, groundY - 58f - if (actionTick % 3 == 1) 54f else 0f))
                }

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(20f, 20f),
                    size = Size(w - 40f, h - 40f),
                    cornerRadius = CornerRadius(26f, 26f),
                    style = Stroke(width = 2.5f)
                )
            }
        }
    }
}
