package com.ironmind.app.ui.session

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.util.PhotoWeightMode
import com.ironmind.app.domain.util.resolveWeightKg
import com.ironmind.app.domain.util.snapToPlate
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted
import com.ironmind.app.ui.util.suffix
import com.ironmind.app.ui.util.weightLabel

private val ErrorRed = Color(0xFFFF6B6B)

/**
 * Reads a weight off a photo of the plates/dumbbell using on-device OCR, lets the user confirm
 * what was recognized (OCR on worn, angled plates is a best-effort read), and returns the
 * resolved weight in kilograms.
 */
@Composable
fun PhotoWeightDialog(
    displayUnit: WeightUnit,
    onDismiss: () -> Unit,
    onAccept: (weightKg: Double) -> Unit,
    viewModel: PhotoWeightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(PhotoWeightMode.PLATES_PER_SIDE) }
    var plateUnit by remember { mutableStateOf(displayUnit) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var barText by remember { mutableStateOf(defaultBar(displayUnit)) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCaptureUri
        if (saved && uri != null) viewModel.analyze(uri)
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::analyze) }

    // A fresh reading pre-selects everything and adopts any unit the text spelled out.
    LaunchedEffect(state) {
        val ready = state as? PhotoWeightUiState.Ready ?: return@LaunchedEffect
        selected = ready.detected.indices.toSet()
        ready.detected.firstNotNullOfOrNull { it.unit }?.let { detectedUnit ->
            plateUnit = detectedUnit
            barText = defaultBar(detectedUnit)
        }
    }

    val detected = (state as? PhotoWeightUiState.Ready)?.detected.orEmpty()
    val values = detected
        .filterIndexed { index, _ -> index in selected }
        .map { reading ->
            // Only snap in plate mode — a 32.5 kg dumbbell is not a plate denomination.
            if (mode == PhotoWeightMode.PLATES_PER_SIDE) {
                snapToPlate(reading.value, plateUnit) ?: reading.value
            } else {
                reading.value
            }
        }
    val bar = barText.toDoubleOrNull() ?: 0.0
    val totalKg = resolveWeightKg(values, plateUnit, mode, bar)
    val canAccept = values.isNotEmpty() && totalKg > 0.0

    AlertDialog(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        confirmButton = {
            TextButton(
                enabled = canAccept,
                onClick = {
                    viewModel.reset()
                    onAccept(totalKg)
                },
            ) {
                Text(
                    stringResource(R.string.photo_weight_use),
                    color = if (canAccept) Cyan else TextMuted,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.reset()
                onDismiss()
            }) { Text(stringResource(R.string.action_cancel), color = TextMuted) }
        },
        title = { Text(stringResource(R.string.photo_weight_title), color = Gold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.photo_weight_desc), color = TextMuted, style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val uri = viewModel.newCaptureUri()
                            pendingCaptureUri = uri
                            captureLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.photo_weight_take), color = Gold) }
                    OutlinedButton(
                        onClick = {
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.photo_weight_pick), color = Cyan) }
                }

                when (val s = state) {
                    PhotoWeightUiState.Idle -> Unit

                    PhotoWeightUiState.Analyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.photo_weight_analyzing), color = TextMuted)
                    }

                    is PhotoWeightUiState.Error ->
                        Text(stringResource(R.string.photo_weight_error, s.message), color = ErrorRed)

                    is PhotoWeightUiState.Ready -> {
                        if (s.detected.isEmpty()) {
                            Text(stringResource(R.string.photo_weight_none), color = TextMuted)
                        } else {
                            Text(stringResource(R.string.photo_weight_detected), color = TextMuted, style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                s.detected.forEachIndexed { index, reading ->
                                    val isOn = index in selected
                                    SelectableChip(
                                        text = "${trimNumber(reading.value)}${reading.unit?.let { " ${it.name.lowercase()}" } ?: ""}",
                                        selected = isOn,
                                        onClick = {
                                            selected = if (isOn) selected - index else selected + index
                                        },
                                    )
                                }
                            }

                            ModeRow(mode = mode, onSelect = { mode = it })

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.photo_weight_plate_unit), color = TextMuted)
                                Spacer(Modifier.width(8.dp))
                                SelectableChip(
                                    text = stringResource(R.string.unit_kg),
                                    selected = plateUnit == WeightUnit.KG,
                                    onClick = {
                                        plateUnit = WeightUnit.KG
                                        barText = defaultBar(WeightUnit.KG)
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                SelectableChip(
                                    text = stringResource(R.string.unit_lb),
                                    selected = plateUnit == WeightUnit.LB,
                                    onClick = {
                                        plateUnit = WeightUnit.LB
                                        barText = defaultBar(WeightUnit.LB)
                                    },
                                )
                            }

                            if (mode == PhotoWeightMode.PLATES_PER_SIDE) {
                                OutlinedTextField(
                                    value = barText,
                                    onValueChange = { barText = it },
                                    label = { Text(stringResource(R.string.photo_weight_bar, plateUnit.suffix())) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (values.isNotEmpty()) {
                                val other = if (displayUnit == WeightUnit.KG) WeightUnit.LB else WeightUnit.KG
                                Text(
                                    stringResource(R.string.photo_weight_total, weightLabel(totalKg, displayUnit)),
                                    color = Cyan,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.photo_weight_equivalent, weightLabel(totalKg, other)),
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ModeRow(mode: PhotoWeightMode, onSelect: (PhotoWeightMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectableChip(
            text = stringResource(R.string.photo_weight_mode_plates),
            selected = mode == PhotoWeightMode.PLATES_PER_SIDE,
            onClick = { onSelect(PhotoWeightMode.PLATES_PER_SIDE) },
        )
        SelectableChip(
            text = stringResource(R.string.photo_weight_mode_direct),
            selected = mode == PhotoWeightMode.DIRECT,
            onClick = { onSelect(PhotoWeightMode.DIRECT) },
        )
    }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val accent = if (selected) Cyan else TextMuted
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Cyan else TextMuted,
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = if (selected) 0.16f else 0.06f))
            .border(1.dp, accent.copy(alpha = if (selected) 0.6f else 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun defaultBar(unit: WeightUnit): String = if (unit == WeightUnit.LB) "45" else "20"

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
