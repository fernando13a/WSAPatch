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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.CircularRestTimer
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.LabeledValue
import com.ironmind.app.ui.components.SectionTitle
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
                    LabeledValue(stringResource(R.string.session_total_volume), stringResource(R.string.kg_value, state.totalVolume.toInt()))
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
                    exercises = state.availableExercises.map { it.id to it.name },
                    onAddSet = { exerciseId, weight, reps, notes ->
                        viewModel.addSet(exerciseId, weight, reps, notes)
                    },
                )
            }

            item { SectionTitle(stringResource(R.string.session_exercises_title)) }

            items(state.exerciseBlocks, key = { it.exerciseId }) { block ->
                ExerciseBlockCard(
                    block = block,
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
    exercises: List<Pair<Long, String>>,
    onAddSet: (exerciseId: Long, weightKg: Double, reps: Int, notes: String?) -> Unit,
) {
    var selectedId by remember { mutableLongStateOf(0L) }
    var expanded by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Default the selector to the first available exercise once loaded.
    LaunchedEffect(exercises) {
        if (selectedId == 0L && exercises.isNotEmpty()) selectedId = exercises.first().first
    }
    val selectedName = exercises.firstOrNull { it.first == selectedId }?.second ?: stringResource(R.string.select_exercise)

    GlassCard {
        SectionTitle(stringResource(R.string.add_set_title), accent = Gold)
        Spacer(Modifier.height(12.dp))

        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedName, color = Cyan)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                exercises.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        selectedId = id
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
                label = { Text(stringResource(R.string.weight_kg)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    onAddSet(selectedId, weightValue, repsValue, notes)
                    weight = ""
                    reps = ""
                    notes = ""
                }
            },
        )
    }
}

@Composable
private fun ExerciseBlockCard(
    block: ExerciseBlockUi,
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
                        stringResource(R.string.set_line, set.setNumber, set.weightKg.toInt(), set.reps),
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
    onDismiss: () -> Unit,
    onSave: (SetLog) -> Unit,
) {
    var weight by remember { mutableStateOf(set.weightKg.toString()) }
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
                        onSave(set.copy(weightKg = weightValue, reps = repsValue, notes = notes.takeIf { it.isNotBlank() }))
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
                    label = { Text(stringResource(R.string.weight_kg)) },
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
