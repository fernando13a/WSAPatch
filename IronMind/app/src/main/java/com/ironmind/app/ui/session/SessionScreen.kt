package com.ironmind.app.ui.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.util.computePlatePlan
import com.ironmind.app.domain.util.displayUnitToKg
import com.ironmind.app.domain.util.toDisplayUnit
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.CircularRestTimer
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.LabeledValue
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.util.displayName
import com.ironmind.app.ui.util.label
import com.ironmind.app.ui.util.suffix
import com.ironmind.app.ui.util.weightLabel
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit = {},
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemaining.collectAsStateWithLifecycle()
    val restTotal by viewModel.restTotal.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val recoveryAdvice by viewModel.recoveryAdvice.collectAsStateWithLifecycle()

    // Hoisted so the Recovery Coach card (below AddSetCard) knows which muscle group is in focus.
    var selectedExerciseId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.availableExercises) {
        if (selectedExerciseId == 0L) {
            state.availableExercises.firstOrNull()?.let { selectedExerciseId = it.id }
        }
    }
    val selectedMuscleGroup = state.availableExercises.firstOrNull { it.id == selectedExerciseId }?.muscleGroup
    // Stale advice for a different muscle group should never linger once the selection changes.
    LaunchedEffect(selectedMuscleGroup) { viewModel.dismissRecoveryAdvice() }

    // Keep the screen awake during a workout.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Ask for notification permission (Android 13+) so the rest-timer alert can reach the user when
    // the app is backgrounded. Denial is fine — the in-app countdown keeps working either way.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored; the notifier guards on areNotificationsEnabled() */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Live session timer (ticks every second once the session has started).
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSeconds = if (state.startedAt > 0) ((nowMillis - state.startedAt) / 1_000).toInt() else 0

    var editingSet by remember { mutableStateOf<SetLog?>(null) }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: stringResource(R.string.session_free_title), color = Gold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Cyan)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.finishSession(onBack) }) {
                        Text(stringResource(R.string.session_finish), color = Cyan, fontWeight = FontWeight.SemiBold)
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
                    val totalSets = state.exerciseBlocks.sumOf { it.sets.size }
                    LabeledValue(stringResource(R.string.session_time), formatMmSs(elapsedSeconds))
                    Spacer(Modifier.height(6.dp))
                    LabeledValue(stringResource(R.string.session_total_volume), weightLabel(state.totalVolume, weightUnit))
                    Spacer(Modifier.height(6.dp))
                    LabeledValue(stringResource(R.string.session_sets_logged), "$totalSets")
                }
            }

            item {
                RestTimerCard(
                    restRemaining = restRemaining,
                    restTotal = restTotal,
                    onStart = viewModel::startRest,
                    onStop = viewModel::stopRest,
                )
            }

            item {
                AddSetCard(
                    exercises = state.availableExercises,
                    selectedId = selectedExerciseId,
                    onSelectedIdChange = { selectedExerciseId = it },
                    unit = weightUnit,
                    onAddSet = { exerciseId, weight, reps, notes ->
                        viewModel.addSet(exerciseId, weight, reps, notes)
                    },
                )
            }

            if (selectedMuscleGroup != null) {
                item {
                    RecoveryCoachCard(
                        muscleGroup = selectedMuscleGroup,
                        advice = recoveryAdvice,
                        onGenerate = { viewModel.generateRecoveryAdvice(selectedMuscleGroup) },
                        onDismiss = viewModel::dismissRecoveryAdvice,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.session_exercises_title)) }

            items(state.exerciseBlocks, key = { it.exerciseId }) { block ->
                ExerciseBlockCard(
                    block = block,
                    unit = weightUnit,
                    onEditSet = { editingSet = it },
                    onDeleteSet = viewModel::deleteSet,
                    onOpen = { onOpenExercise(block.exerciseId) },
                )
            }
        }
    }

    editingSet?.let { set ->
        EditSetDialog(
            set = set,
            unit = weightUnit,
            onDismiss = { editingSet = null },
            onSave = { updated ->
                viewModel.updateSet(updated)
                editingSet = null
            },
        )
    }
}

@Composable
private fun RestTimerCard(
    restRemaining: Int,
    restTotal: Int,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
) {
    GlassCard {
        SectionTitle(stringResource(R.string.rest_title), accent = Cyan)
        Spacer(Modifier.height(16.dp))
        CircularRestTimer(
            remaining = restRemaining,
            total = restTotal,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(60, 90, 120).forEach { seconds ->
                OutlinedButton(onClick = { onStart(seconds) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.rest_seconds, seconds), color = Gold)
                }
            }
        }
        if (restRemaining > 0) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onStop,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.rest_stop), color = TextMuted) }
        }
    }
}

@Composable
private fun AddSetCard(
    exercises: List<Exercise>,
    selectedId: Long,
    onSelectedIdChange: (Long) -> Unit,
    unit: WeightUnit,
    onAddSet: (exerciseId: Long, weightKg: Double, reps: Int, notes: String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showPlates by remember { mutableStateOf(false) }
    var showPhotoWeight by remember { mutableStateOf(false) }

    val selectedName = exercises.firstOrNull { it.id == selectedId }?.displayName() ?: stringResource(R.string.select_exercise)

    GlassCard {
        SectionTitle(stringResource(R.string.add_set_title), accent = Gold)
        Spacer(Modifier.height(12.dp))

        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedName, color = Cyan)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                exercises.forEach { exercise ->
                    DropdownMenuItem(text = { Text(exercise.displayName()) }, onClick = {
                        onSelectedIdChange(exercise.id)
                        expanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text(stringResource(R.string.weight_input_label, unit.suffix())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    IconButton(onClick = { showPhotoWeight = true }) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.photo_weight_title),
                            tint = Cyan,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                label = { Text(stringResource(R.string.reps)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.notes_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        val weightValue = weight.toDoubleOrNull()
        val repsValue = reps.toIntOrNull()
        AccentButton(
            text = stringResource(R.string.add_set_title),
            enabled = selectedId != 0L && weightValue != null && repsValue != null,
            onClick = {
                if (weightValue != null && repsValue != null) {
                    onAddSet(selectedId, weightValue.displayUnitToKg(unit), repsValue, notes)
                    weight = ""
                    reps = ""
                    notes = ""
                }
            },
        )

        TextButton(
            onClick = { showPlates = true },
            enabled = weightValue != null,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(stringResource(R.string.plate_calc_open), color = Cyan) }
    }

    val plateTargetKg = weight.toDoubleOrNull()?.displayUnitToKg(unit)
    if (showPlates && plateTargetKg != null) {
        PlateDialog(targetKg = plateTargetKg, unit = unit, onDismiss = { showPlates = false })
    }

    if (showPhotoWeight) {
        PhotoWeightDialog(
            displayUnit = unit,
            onDismiss = { showPhotoWeight = false },
            onAccept = { kg ->
                weight = formatEditableWeight(kg.toDisplayUnit(unit))
                showPhotoWeight = false
            },
        )
    }
}

@Composable
private fun RecoveryCoachCard(
    muscleGroup: MuscleGroup,
    advice: SuggestionState?,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(
        borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Cyan.copy(alpha = 0.55f), Gold.copy(alpha = 0.35f)),
        ),
    ) {
        SectionTitle(stringResource(R.string.recovery_title), accent = Cyan)
        Spacer(Modifier.height(12.dp))

        when (advice) {
            null -> {
                Text(stringResource(R.string.recovery_hint, muscleGroup.label()), color = TextMuted)
                Spacer(Modifier.height(12.dp))
                AccentButton(text = stringResource(R.string.recovery_generate), onClick = onGenerate)
            }

            SuggestionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recovery_analyzing), color = TextMuted)
            }

            is SuggestionState.Success -> Column {
                Text(advice.suggestion, color = Color.White)
                if (advice.isComplete) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close), color = TextMuted)
                    }
                }
            }

            is SuggestionState.Error -> Column {
                Text(advice.message, color = Color(0xFFFF6B6B))
                TextButton(onClick = onGenerate) {
                    Text(stringResource(R.string.action_retry), color = Cyan)
                }
            }
        }
    }
}

@Composable
private fun PlateDialog(targetKg: Double, unit: WeightUnit, onDismiss: () -> Unit) {
    // Compute in the display unit with unit-appropriate bar + plate inventory.
    val target = targetKg.toDisplayUnit(unit)
    val barWeight = if (unit == WeightUnit.LB) 45.0 else 20.0
    val plateSet = if (unit == WeightUnit.LB) {
        listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    } else {
        listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    }
    val plan = remember(target, unit) { computePlatePlan(target, barWeight, plateSet) }
    val suffix = unit.suffix()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Cyan) }
        },
        title = { Text(stringResource(R.string.plate_calc_title, "${target.toInt()} $suffix"), color = Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.plate_calc_bar, "${barWeight.toInt()} $suffix"), color = TextMuted)
                if (plan.perSide.isEmpty()) {
                    Text(stringResource(R.string.plate_calc_bar_only), color = Color.White)
                } else {
                    val perSide = plan.perSide.joinToString(" + ") { formatPlate(it) }
                    Text(stringResource(R.string.plate_calc_per_side, "$perSide $suffix"), color = Color.White)
                }
                Text(stringResource(R.string.plate_calc_total, "${plan.achievable.toInt()} $suffix"), color = Cyan)
                if (plan.leftover > 0.01) {
                    Text(stringResource(R.string.plate_calc_leftover, "${formatPlate(plan.leftover)} $suffix"), color = TextMuted)
                }
            }
        },
    )
}

private fun formatPlate(kg: Double): String =
    if (kg % 1.0 == 0.0) "${kg.toInt()}" else kg.toString()

/** A clean, editable representation of a weight value (integer if whole, else one decimal).
 *  Forces a '.' decimal (Locale.US) so the field re-parses regardless of the device locale. */
private fun formatEditableWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

@Composable
private fun ExerciseBlockCard(
    block: ExerciseBlockUi,
    unit: WeightUnit,
    onEditSet: (SetLog) -> Unit,
    onDeleteSet: (SetLog) -> Unit,
    onOpen: () -> Unit,
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpen() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.exerciseName,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpen) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.instructions_title), tint = Cyan)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (block.sets.isEmpty()) {
            Text(stringResource(R.string.no_sets_yet), color = TextMuted)
        } else {
            block.sets.forEach { set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.set_line, set.setNumber, weightLabel(set.weightKg, unit), set.reps),
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEditSet(set) },
                    )
                    TextButton(onClick = { onEditSet(set) }) { Text(stringResource(R.string.action_edit), color = Cyan) }
                    IconButton(onClick = { onDeleteSet(set) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete), tint = TextMuted)
                    }
                }
                if (!set.notes.isNullOrBlank()) {
                    Text("  ${set.notes}", color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun EditSetDialog(
    set: SetLog,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (SetLog) -> Unit,
) {
    var weight by remember { mutableStateOf(formatEditableWeight(set.weightKg.toDisplayUnit(unit))) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var notes by remember { mutableStateOf(set.notes ?: "") }
    val weightValue = weight.toDoubleOrNull()
    val repsValue = reps.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = weightValue != null && repsValue != null,
                onClick = {
                    if (weightValue != null && repsValue != null) {
                        onSave(set.copy(weightKg = weightValue.displayUnitToKg(unit), reps = repsValue, notes = notes.takeIf { it.isNotBlank() }))
                    }
                },
            ) { Text(stringResource(R.string.action_save), color = Cyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextMuted) } },
        title = { Text(stringResource(R.string.edit_set_title, set.setNumber), color = Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text(stringResource(R.string.weight_input_label, unit.suffix())) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text(stringResource(R.string.reps)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

private fun formatMmSs(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
