package com.ironmind.app.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.Chip
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.GlowProgressBar
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.util.label
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

private val ErrorRed = Color(0xFFFF6B6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartSession: (routineId: Long) -> Unit,
    onOpenProgress: (exerciseId: Long) -> Unit,
    onNewRoutine: () -> Unit,
    onGenerateRoutine: () -> Unit,
    onEditRoutine: (routineId: Long) -> Unit,
    onDownloadModel: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val modelAvailable by viewModel.modelAvailable.collectAsStateWithLifecycle()
    val showModelPrompt by viewModel.showModelPrompt.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshModelAvailability() }

    Scaffold(
        containerColor = Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), color = Gold, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenBackup) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.backup_title),
                            tint = Cyan,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Black),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("dashboardList"),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DashboardHeader() }

            item { StatsRow(streak = state.streak, totalSessions = state.totalSessions) }

            item {
                AiSuggestionPanel(
                    modelAvailable = modelAvailable,
                    focusExerciseId = state.focusExerciseId,
                    focusExerciseName = state.focusExerciseName,
                    suggestion = suggestion,
                    onGenerate = viewModel::generateSuggestion,
                    onDismiss = viewModel::dismissSuggestion,
                    onOpenProgress = onOpenProgress,
                    onDownloadModel = onDownloadModel,
                )
            }

            if (modelAvailable) {
                item {
                    TrainingInsightsPanel(
                        insights = insights,
                        onGenerate = viewModel::generateInsights,
                        onDismiss = viewModel::dismissInsights,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(stringResource(R.string.routines_title))
                    Row {
                        TextButton(onClick = onGenerateRoutine) { Text(stringResource(R.string.routines_generate), color = Gold) }
                        TextButton(onClick = onNewRoutine) { Text(stringResource(R.string.routines_new), color = Cyan) }
                    }
                }
            }

            if (state.routines.isEmpty()) {
                item {
                    GlassCard {
                        Text(stringResource(R.string.routines_empty), color = TextMuted)
                    }
                }
            }

            items(state.routines, key = { it.routineId }) { routine ->
                RoutineCard(
                    routine = routine,
                    onStart = { onStartSession(routine.routineId) },
                    onEdit = { onEditRoutine(routine.routineId) },
                )
            }

            item {
                AccentButton(
                    text = stringResource(R.string.start_free_session),
                    onClick = { onStartSession(0L) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showModelPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelPrompt() },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissModelPrompt()
                    onDownloadModel()
                }) { Text(stringResource(R.string.ai_download_model), color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissModelPrompt() }) {
                    Text(stringResource(R.string.model_prompt_later), color = TextMuted)
                }
            },
            title = { Text(stringResource(R.string.model_prompt_title), color = Gold) },
            text = { Text(stringResource(R.string.model_prompt_body), color = TextMuted) },
        )
    }
}

@Composable
private fun DashboardHeader() {
    Column {
        Text(
            text = stringResource(R.string.dash_engine_label),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = Gold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dash_tagline),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(text = stringResource(R.string.badge_on_device_ai), accent = Cyan)
            Chip(text = stringResource(R.string.badge_offline), accent = Gold)
        }
    }
}

@Composable
private fun StatsRow(streak: Int, totalSessions: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatBadgeTile(
            badge = "🔥",
            value = "$streak",
            label = stringResource(R.string.dash_streak_label),
            accent = Gold,
            modifier = Modifier.weight(1f),
        )
        StatBadgeTile(
            badge = "🏆",
            value = "$totalSessions",
            label = stringResource(R.string.dash_sessions_label),
            accent = Cyan,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatBadgeTile(
    badge: String,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.15f)),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(badge, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = value,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun AiSuggestionPanel(
    modelAvailable: Boolean,
    focusExerciseId: Long?,
    focusExerciseName: String?,
    suggestion: SuggestionState?,
    onGenerate: (Long) -> Unit,
    onDismiss: () -> Unit,
    onOpenProgress: (Long) -> Unit,
    onDownloadModel: () -> Unit,
) {
    GlassCard(
        borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Cyan.copy(alpha = 0.55f), Gold.copy(alpha = 0.35f)),
        ),
    ) {
        SectionTitle(stringResource(R.string.ai_coach_title), accent = Cyan)
        Spacer(Modifier.height(12.dp))

        if (!modelAvailable) {
            Text(stringResource(R.string.ai_model_missing), color = TextMuted)
            Spacer(Modifier.height(12.dp))
            AccentButton(text = stringResource(R.string.ai_download_model), onClick = onDownloadModel)
            return@GlassCard
        }

        if (focusExerciseId == null) {
            Text(stringResource(R.string.ai_need_history), color = TextMuted)
            return@GlassCard
        }

        val focusName = focusExerciseName ?: stringResource(R.string.ai_focus_fallback)
        Text(stringResource(R.string.ai_focus, focusName), color = Gold, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        when (val s = suggestion) {
            null -> AccentButton(
                text = stringResource(R.string.ai_generate),
                onClick = { onGenerate(focusExerciseId) },
            )

            SuggestionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_analyzing), color = TextMuted)
            }

            is SuggestionState.Success -> Column {
                Text(s.suggestion, color = Color.White)
                if (s.isComplete) {
                    Row {
                        TextButton(onClick = { onGenerate(focusExerciseId) }) {
                            Text(stringResource(R.string.ai_regenerate), color = Cyan)
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_close), color = TextMuted)
                        }
                    }
                }
            }

            is SuggestionState.Error -> Column {
                Text(s.message, color = ErrorRed)
                TextButton(onClick = { onGenerate(focusExerciseId) }) {
                    Text(stringResource(R.string.action_retry), color = Cyan)
                }
            }
        }

        TextButton(onClick = { onOpenProgress(focusExerciseId) }) {
            Text(stringResource(R.string.ai_view_progress), color = Cyan)
        }
    }
}

@Composable
private fun TrainingInsightsPanel(
    insights: SuggestionState?,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(
        borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Gold.copy(alpha = 0.55f), Cyan.copy(alpha = 0.35f)),
        ),
    ) {
        SectionTitle(stringResource(R.string.insights_title), accent = Gold)
        Spacer(Modifier.height(12.dp))

        when (val s = insights) {
            null -> {
                Text(stringResource(R.string.insights_hint), color = TextMuted)
                Spacer(Modifier.height(12.dp))
                AccentButton(text = stringResource(R.string.insights_generate), onClick = onGenerate)
            }

            SuggestionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.insights_analyzing), color = TextMuted)
            }

            is SuggestionState.Success -> Column {
                Text(s.suggestion, color = Color.White)
                if (s.isComplete) {
                    Row {
                        TextButton(onClick = onGenerate) {
                            Text(stringResource(R.string.ai_regenerate), color = Cyan)
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_close), color = TextMuted)
                        }
                    }
                }
            }

            is SuggestionState.Error -> Column {
                Text(s.message, color = ErrorRed)
                TextButton(onClick = onGenerate) {
                    Text(stringResource(R.string.action_retry), color = Cyan)
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: RoutineProgressUi, onStart: () -> Unit, onEdit: () -> Unit) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                SplitChip(label = routine.split.label())
            }
            Text(stringResource(R.string.routine_exercise_count, routine.exerciseCount), color = TextMuted)
        }
        Spacer(Modifier.height(12.dp))
        GlowProgressBar(progress = routine.progress)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.routine_week_progress, (routine.progress * 100).toInt()),
            color = TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentButton(text = stringResource(R.string.routine_start), onClick = onStart)
            TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit), color = Cyan) }
        }
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
