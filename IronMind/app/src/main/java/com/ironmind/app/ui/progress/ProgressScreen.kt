package com.ironmind.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.LabeledValue
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(state.exerciseName.ifEmpty { "Progreso" }, color = Gold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GlassCard {
                    SectionTitle("Evolución de carga", accent = Cyan)
                    Spacer(Modifier.height(8.dp))

                    if (state.points.isEmpty()) {
                        Text("Aún no hay datos suficientes para graficar.", color = TextMuted)
                    } else {
                        val shown = selectedIndex?.let { state.points.getOrNull(it) } ?: state.points.last()
                        Text(
                            "${shown.label} · ${shown.value.toInt()} kg",
                            color = Cyan,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        LoadChart(
                            points = state.points,
                            selectedIndex = selectedIndex,
                            onSelect = { selectedIndex = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        LabeledValue("Mejor marca", "${state.bestWeight.toInt()} kg")
                    }
                }
            }

            item { SectionTitle("Historial") }

            if (state.history.isEmpty()) {
                item {
                    GlassCard { Text("Sin registros todavía.", color = TextMuted) }
                }
            }

            items(state.history, key = { it.id }) { set ->
                HistoryRow(set)
            }
        }
    }
}

@Composable
private fun HistoryRow(set: SetLog) {
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatDate(set.performedAt), color = TextMuted, style = MaterialTheme.typography.labelLarge)
            Text(
                buildString {
                    append("${set.weightKg.toInt()} kg × ${set.reps}")
                    set.rpe?.let { append("  ·  RPE ${it}") }
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (!set.notes.isNullOrBlank()) {
            Text(set.notes, color = TextMuted, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val historyFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun formatDate(millis: Long): String =
    historyFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
