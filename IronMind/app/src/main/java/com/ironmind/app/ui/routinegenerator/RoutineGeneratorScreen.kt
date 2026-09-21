package com.ironmind.app.ui.routinegenerator

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.Chip
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted
import com.ironmind.app.ui.util.displayName
import com.ironmind.app.ui.util.label

private val ErrorRed = Color(0xFFFF6B6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineGeneratorScreen(
    onBack: () -> Unit,
    onRoutineSaved: (routineId: Long) -> Unit,
    viewModel: RoutineGeneratorViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val draftState by viewModel.draftState.collectAsStateWithLifecycle()
    val draftRows by viewModel.draftRows.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routine_generator_title), color = Gold, fontWeight = FontWeight.Bold) },
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
                FormCard(
                    split = ui.split,
                    goal = ui.goal,
                    availableEquipment = ui.availableEquipment,
                    canGenerate = ui.canGenerate,
                    isGenerating = draftState == RoutineDraftState.Loading,
                    onSplitChange = viewModel::setSplit,
                    onGoalChange = viewModel::setGoal,
                    onToggleEquipment = viewModel::toggleEquipment,
                    onGenerate = viewModel::generate,
                )
            }

            when (val state = draftState) {
                null -> Unit

                RoutineDraftState.Loading -> item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.routine_generator_generating), color = TextMuted)
                        }
                    }
                }

                is RoutineDraftState.Error -> item {
                    GlassCard {
                        Text(state.message, color = ErrorRed)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::generate) {
                            Text(stringResource(R.string.action_retry), color = Cyan)
                        }
                    }
                }

                is RoutineDraftState.Success -> {
                    item {
                        GlassCard {
                            SectionTitle(stringResource(R.string.routine_generator_proposed_title), accent = Cyan)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = ui.routineName,
                                onValueChange = viewModel::setRoutineName,
                                label = { Text(stringResource(R.string.routine_generator_name_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    items(draftRows, key = { it.exerciseId }) { row ->
                        DraftRowCard(
                            exerciseName = ui.exercisesById[row.exerciseId]?.displayName() ?: row.exerciseId.toString(),
                            row = row,
                            onUpdate = { sets, reps, rest -> viewModel.updateRow(row.exerciseId, sets, reps, rest) },
                            onRemove = { viewModel.removeRow(row.exerciseId) },
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AccentButton(
                                text = stringResource(R.string.routine_generator_save),
                                enabled = draftRows.isNotEmpty(),
                                onClick = { viewModel.save(onRoutineSaved) },
                            )
                            TextButton(onClick = viewModel::generate) {
                                Text(stringResource(R.string.routine_generator_regenerate), color = Cyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormCard(
    split: RoutineSplit,
    goal: TrainingGoal,
    availableEquipment: Set<Equipment>,
    canGenerate: Boolean,
    isGenerating: Boolean,
    onSplitChange: (RoutineSplit) -> Unit,
    onGoalChange: (TrainingGoal) -> Unit,
    onToggleEquipment: (Equipment) -> Unit,
    onGenerate: () -> Unit,
) {
    GlassCard(
        borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Gold.copy(alpha = 0.55f), Cyan.copy(alpha = 0.35f)),
        ),
    ) {
        SectionTitle(stringResource(R.string.routine_generator_hint), accent = Gold)
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.routine_generator_split_label), color = TextMuted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        var splitExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { splitExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(split.label(), color = Cyan)
            }
            DropdownMenu(expanded = splitExpanded, onDismissRequest = { splitExpanded = false }) {
                RoutineSplit.entries.forEach { candidate ->
                    DropdownMenuItem(text = { Text(candidate.label()) }, onClick = {
                        onSplitChange(candidate)
                        splitExpanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.routine_generator_goal_label), color = TextMuted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        var goalExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { goalExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(goal.label(), color = Cyan)
            }
            DropdownMenu(expanded = goalExpanded, onDismissRequest = { goalExpanded = false }) {
                TrainingGoal.entries.forEach { candidate ->
                    DropdownMenuItem(text = { Text(candidate.label()) }, onClick = {
                        onGoalChange(candidate)
                        goalExpanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.routine_generator_equipment_label), color = TextMuted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowChips(
            equipment = Equipment.entries,
            selected = availableEquipment,
            onToggle = onToggleEquipment,
        )

        Spacer(Modifier.height(16.dp))
        AccentButton(
            text = stringResource(R.string.routine_generator_generate),
            enabled = canGenerate && !isGenerating,
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!canGenerate) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.routine_generator_no_equipment), color = ErrorRed, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Simple wrapping row of toggleable equipment chips (no Compose FlowRow dependency needed at this count). */
@Composable
private fun FlowChips(equipment: List<Equipment>, selected: Set<Equipment>, onToggle: (Equipment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        equipment.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    val isSelected = item in selected
                    Chip(
                        text = item.label(),
                        accent = if (isSelected) Gold else TextMuted,
                        modifier = Modifier.clickable { onToggle(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftRowCard(
    exerciseName: String,
    row: RoutineDraftExercise,
    onUpdate: (sets: Int, reps: Int, restSeconds: Int) -> Unit,
    onRemove: () -> Unit,
) {
    var sets by remember(row.exerciseId) { mutableStateOf(row.sets.toString()) }
    var reps by remember(row.exerciseId) { mutableStateOf(row.reps.toString()) }
    var rest by remember(row.exerciseId) { mutableStateOf(row.restSeconds.toString()) }

    fun pushUpdate() {
        val s = sets.toIntOrNull() ?: row.sets
        val r = reps.toIntOrNull() ?: row.reps
        val t = rest.toIntOrNull() ?: row.restSeconds
        onUpdate(s, r, t)
    }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete), tint = TextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = sets,
                onValueChange = { sets = it; pushUpdate() },
                label = { Text(stringResource(R.string.routine_generator_sets_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it; pushUpdate() },
                label = { Text(stringResource(R.string.reps)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = rest,
                onValueChange = { rest = it; pushUpdate() },
                label = { Text(stringResource(R.string.routine_generator_rest_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
