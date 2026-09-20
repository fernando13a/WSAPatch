package com.ironmind.app.ui.progress

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.util.estimateOneRepMax
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.LabeledValue
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.util.weightLabel
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
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val selectedSessionIdA by viewModel.selectedSessionIdA.collectAsStateWithLifecycle()
    val selectedSessionIdB by viewModel.selectedSessionIdB.collectAsStateWithLifecycle()
    val comparisonResult by viewModel.comparisonResult.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(state.exerciseName.ifEmpty { stringResource(R.string.progress_title) }, color = Gold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Cyan)
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
                    SectionTitle(stringResource(R.string.load_evolution), accent = Cyan)
                    Spacer(Modifier.height(8.dp))

                    if (state.points.isEmpty()) {
                        Text(stringResource(R.string.no_chart_data), color = TextMuted)
                    } else {
                        val shown = selectedIndex?.let { state.points.getOrNull(it) } ?: state.points.last()
                        Text(
                            stringResource(R.string.point_label, shown.label, weightLabel(shown.value, weightUnit)),
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
                        LabeledValue(stringResource(R.string.best_mark), weightLabel(state.bestWeight, weightUnit))
                        val e1rmKg = state.history.maxOfOrNull { estimateOneRepMax(it.weightKg, it.reps) } ?: 0.0
                        if (e1rmKg > 0.0) {
                            Spacer(Modifier.height(6.dp))
                            LabeledValue(stringResource(R.string.one_rm_label), weightLabel(e1rmKg, weightUnit))
                        }
                    }
                }
            }

            if (allSessions.size >= 2) {
                item {
                    SessionComparisonCard(
                        sessions = allSessions,
                        selectedIdA = selectedSessionIdA,
                        selectedIdB = selectedSessionIdB,
                        result = comparisonResult,
                        onSelectA = viewModel::selectSessionA,
                        onSelectB = viewModel::selectSessionB,
                        onCompare = viewModel::compareSelectedSessions,
                        onDismiss = viewModel::dismissComparison,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.history_title)) }

            if (state.history.isEmpty()) {
                item {
                    GlassCard { Text(stringResource(R.string.no_records), color = TextMuted) }
                }
            }

            items(state.history, key = { it.id }) { set ->
                HistoryRow(set, weightUnit)
            }
        }
    }
}

@Composable
private fun SessionComparisonCard(
    sessions: List<WorkoutSession>,
    selectedIdA: Long?,
    selectedIdB: Long?,
    result: SuggestionState?,
    onSelectA: (Long) -> Unit,
    onSelectB: (Long) -> Unit,
    onCompare: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard {
        SectionTitle(stringResource(R.string.compare_sessions_title), accent = Gold)
        Spacer(Modifier.height(12.dp))

        SessionPicker(
            label = stringResource(R.string.compare_sessions_first),
            sessions = sessions,
            selectedId = selectedIdA,
            onSelect = onSelectA,
        )
        Spacer(Modifier.height(8.dp))
        SessionPicker(
            label = stringResource(R.string.compare_sessions_second),
            sessions = sessions,
            selectedId = selectedIdB,
            onSelect = onSelectB,
        )
        Spacer(Modifier.height(12.dp))

        when (result) {
            null -> AccentButton(
                text = stringResource(R.string.compare_sessions_compare),
                enabled = selectedIdA != null && selectedIdB != null && selectedIdA != selectedIdB,
                onClick = onCompare,
            )

            SuggestionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.compare_sessions_analyzing), color = TextMuted)
            }

            is SuggestionState.Success -> Column {
                Text(result.suggestion, color = Color.White)
                if (result.isComplete) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = TextMuted) }
                }
            }

            is SuggestionState.Error -> Column {
                Text(result.message, color = Color(0xFFFF6B6B))
                TextButton(onClick = onCompare) { Text(stringResource(R.string.action_retry), color = Cyan) }
            }
        }
    }
}

@Composable
private fun SessionPicker(
    label: String,
    sessions: List<WorkoutSession>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = sessions.firstOrNull { it.id == selectedId }?.let(::sessionLabel)
        ?: stringResource(R.string.compare_sessions_pick)

    Column {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLabel, color = Cyan)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sessions.forEach { session ->
                    DropdownMenuItem(text = { Text(sessionLabel(session)) }, onClick = {
                        onSelect(session.id)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun sessionLabel(session: WorkoutSession): String {
    val title = session.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.session_free_title)
    return "$title (${formatDate(session.startedAt)})"
}

@Composable
private fun HistoryRow(set: SetLog, unit: WeightUnit) {
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatDate(set.performedAt), color = TextMuted, style = MaterialTheme.typography.labelLarge)
            val summary = stringResource(R.string.set_summary, weightLabel(set.weightKg, unit), set.reps)
            val rpe = set.rpe
            val rpeText = if (rpe != null) stringResource(R.string.rpe_suffix, rpe.toString()) else ""
            Text(
                text = summary + rpeText,
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
