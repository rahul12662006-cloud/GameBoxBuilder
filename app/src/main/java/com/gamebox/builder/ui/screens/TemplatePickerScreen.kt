package com.gamebox.builder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamebox.builder.data.GameDimension
import com.gamebox.builder.data.GameTemplate
import com.gamebox.builder.ui.GameBoxHeader

@Composable
fun TemplatePickerScreen(
    dimension: GameDimension,
    templates: List<GameTemplate>,
    onBack: () -> Unit,
    onTemplateSelected: (GameTemplate) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GameBoxHeader(
                title = "Make ${dimension.label} Game",
                subtitle = "Choose a fixed template. Users can customize options without touching code."
            )
        }
        item {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        items(templates, key = { it.id }) { template ->
            TemplateCard(template = template, onClick = { onTemplateSelected(template) })
        }
    }
}

@Composable
private fun TemplateCard(template: GameTemplate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(template.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (template.isPlayableInPhaseOne) "✅" else "🧩")
            }
            Spacer(Modifier.height(6.dp))
            Text(template.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(template.tag, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text("Characters: ${template.characters.size} • Maps: ${template.maps.size} • Packs: ${template.obstaclePacks.size}")
            Spacer(Modifier.height(14.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text("Create project")
            }
        }
    }
}
