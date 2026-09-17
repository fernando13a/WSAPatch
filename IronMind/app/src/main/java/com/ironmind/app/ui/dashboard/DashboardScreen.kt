package com.ironmind.app.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.GlowProgressBar
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.components.StatTile
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted
import androidx.compose.ui.graphics.Color

private val ErrorRed = Color(0xFFFF6B6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartSession: (routineId: Long) -> Unit,
    onOpenProgress: (exerciseId: Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IronMind", color = Gold, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Black),
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
            item { StatsRow(streak = state.streak, totalSessions = state.totalSessions) }

            item {
                AiSuggestionPanel(
                    focusExerciseId = state.focusExerciseId,
                    focusExerciseName = state.focusExerciseName,
                    suggestion = suggestion,
                    onGenerate = viewModel::generateSuggestion,
                    onDismiss = viewModel::dismissSuggestion,
                    onOpenProgress = onOpenProgress,
                )
            }

            item { SectionTitle("Rutinas") }

            if (state.routines.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            "Aún no tienes rutinas. Empieza una sesión libre y registra tus ejercicios.",
                            color = TextMuted,
                        )
                    }
                }
            }

            items(state.routines, key = { it.routineId }) { routine ->
                RoutineCard(routine = routine, onStart = { onStartSession(routine.routineId) })
            }

            item {
                AccentButton(
                    text = "Iniciar sesión libre",
                    onClick = { onStartSession(0L) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatsRow(streak: Int, totalSessions: Int) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatTile(value = "🔥 $streak", label = "Racha (días)", modifier = Modifier.weight(1f), valueColor = Gold)
            StatTile(value = "$totalSessions", label = "Sesiones", modifier = Modifier.weight(1f), valueColor = Cyan)
        }
    }
}

@Composable
private fun AiSuggestionPanel(
    focusExerciseId: Long?,
    focusExerciseName: String?,
    suggestion: SuggestionState?,
    onGenerate: (Long) -> Unit,
    onDismiss: () -> Unit,
    onOpenProgress: (Long) -> Unit,
) {
    GlassCard(
        borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Cyan.copy(alpha = 0.55f), Gold.copy(alpha = 0.35f)),
        ),
    ) {
        SectionTitle("Coach IA", accent = Cyan)
        Spacer(Modifier.height(12.dp))

        if (focusExerciseId == null) {
            Text(
                "Registra un entrenamiento para recibir sugerencias de sobrecarga progresiva generadas en tu dispositivo.",
                color = TextMuted,
            )
            return@GlassCard
        }

        Text("Enfoque: ${focusExerciseName ?: "tu último ejercicio"}", color = Gold, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        when (val s = suggestion) {
            null -> AccentButton(
                text = "Generar sugerencia",
                onClick = { onGenerate(focusExerciseId) },
            )

            SuggestionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                Spacer(Modifier.height(0.dp))
                Text("  Analizando tu progreso…", color = TextMuted)
            }

            is SuggestionState.Success -> Column {
                Text(s.suggestion, color = Color.White)
                if (s.isComplete) {
                    Row {
                        TextButton(onClick = { onGenerate(focusExerciseId) }) { Text("Regenerar", color = Cyan) }
                        TextButton(onClick = onDismiss) { Text("Cerrar", color = TextMuted) }
                    }
                }
            }

            is SuggestionState.Error -> Column {
                Text(s.message, color = ErrorRed)
                TextButton(onClick = { onGenerate(focusExerciseId) }) { Text("Reintentar", color = Cyan) }
            }
        }

        TextButton(onClick = { onOpenProgress(focusExerciseId) }) {
            Text("Ver progreso de este ejercicio", color = Cyan)
        }
    }
}

@Composable
private fun RoutineCard(routine: RoutineProgressUi, onStart: () -> Unit) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                SplitChip(label = routine.split.name)
            }
            Text("${routine.exerciseCount} ejercicios", color = TextMuted)
        }
        Spacer(Modifier.height(12.dp))
        GlowProgressBar(progress = routine.progress)
        Spacer(Modifier.height(4.dp))
        Text(
            "${(routine.progress * 100).toInt()}% entrenado esta semana",
            color = TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(12.dp))
        AccentButton(text = "Iniciar", onClick = onStart)
    }
}

@Composable
private fun SplitChip(label: String) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .border(1.dp, Cyan.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(label, color = Cyan, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}
