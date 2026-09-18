package com.ironmind.app.ui.routineedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditScreen(
    onDone: () -> Unit,
    onOpenExercise: (Long) -> Unit = {},
    viewModel: RoutineEditViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showNewExercise by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.routineId == 0L) stringResource(R.string.routine_new_title) else stringResource(R.string.routine_edit_title),
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Cyan)
                    }
                },
                actions = {
                    TextButton(enabled = state.canSave, onClick = { viewModel.save(onDone) }) {
                        Text(stringResource(R.string.action_save), color = if (state.canSave) Cyan else TextMuted, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.routine_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.split_label), color = TextMuted, style = MaterialTheme.typography.labelLarge)
                EnumDropdown(
                    current = state.split.name,
                    options = RoutineSplit.entries.map { it.name },
                    onSelect = { viewModel.setSplit(RoutineSplit.valueOf(it)) },
                )
            }

            SectionTitle(stringResource(R.string.routine_exercises_title))

            GlassCard {
                if (state.selected.isEmpty()) {
                    Text(stringResource(R.string.add_from_catalog_hint), color = TextMuted)
                } else {
                    state.selected.forEachIndexed { index, exercise ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.numbered_exercise, index + 1, exercise.name),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenExercise(exercise.id) },
                            )
                            IconButton(onClick = { viewModel.move(index, index - 1) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up), tint = Cyan)
                            }
                            IconButton(onClick = { viewModel.move(index, index + 1) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down), tint = Cyan)
                            }
                            IconButton(onClick = { viewModel.removeExercise(exercise) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete), tint = TextMuted)
                            }
                        }
                    }
                }
            }

            GlassCard {
                SectionTitle(stringResource(R.string.add_exercise_title), accent = Cyan)
                Spacer(Modifier.height(12.dp))
                if (state.addable.isEmpty()) {
                    Text(stringResource(R.string.no_more_catalog), color = TextMuted)
                } else {
                    EnumDropdown(
                        current = stringResource(R.string.select_from_catalog),
                        options = state.addable.map { it.name },
                        onSelect = { name -> state.addable.firstOrNull { it.name == name }?.let(viewModel::addExercise) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showNewExercise = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.create_new_exercise), color = Gold)
                }
            }

            AccentButton(
                text = stringResource(R.string.save_routine),
                onClick = { viewModel.save(onDone) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showNewExercise) {
        NewExerciseDialog(
            onDismiss = { showNewExercise = false },
            onCreate = { name, mg, eq ->
                viewModel.createExercise(name, mg, eq)
                showNewExercise = false
            },
        )
    }
}

@Composable
private fun EnumDropdown(current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current, color = Cyan)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun NewExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (String, MuscleGroup, Equipment) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, muscle, equipment) }) {
                Text(stringResource(R.string.action_create), color = if (name.isNotBlank()) Cyan else TextMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextMuted) } },
        title = { Text(stringResource(R.string.new_exercise_title), color = Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.muscle_group_label), color = TextMuted, style = MaterialTheme.typography.labelLarge)
                EnumDropdown(
                    current = muscle.name,
                    options = MuscleGroup.entries.map { it.name },
                    onSelect = { muscle = MuscleGroup.valueOf(it) },
                )
                Text(stringResource(R.string.equipment_label), color = TextMuted, style = MaterialTheme.typography.labelLarge)
                EnumDropdown(
                    current = equipment.name,
                    options = Equipment.entries.map { it.name },
                    onSelect = { equipment = Equipment.valueOf(it) },
                )
            }
        },
    )
}
