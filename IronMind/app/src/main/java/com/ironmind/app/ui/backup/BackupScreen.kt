package com.ironmind.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val backupFilename = stringResource(R.string.backup_filename)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title), color = Gold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Cyan)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard {
                SectionTitle(stringResource(R.string.settings_units_title), accent = Cyan)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_units_desc), color = TextMuted)
                Spacer(Modifier.height(12.dp))
                UnitOption(
                    name = stringResource(R.string.unit_kg_name),
                    selected = weightUnit == WeightUnit.KG,
                    onClick = { viewModel.setWeightUnit(WeightUnit.KG) },
                )
                UnitOption(
                    name = stringResource(R.string.unit_lb_name),
                    selected = weightUnit == WeightUnit.LB,
                    onClick = { viewModel.setWeightUnit(WeightUnit.LB) },
                )
            }

            GlassCard {
                SectionTitle(stringResource(R.string.backup_export_title), accent = Cyan)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.backup_export_desc), color = TextMuted)
                Spacer(Modifier.height(16.dp))
                AccentButton(
                    text = stringResource(R.string.backup_export_action),
                    onClick = { exportLauncher.launch(backupFilename) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            GlassCard {
                SectionTitle(stringResource(R.string.backup_import_title), accent = Gold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.backup_import_desc), color = TextMuted)
                Spacer(Modifier.height(16.dp))
                AccentButton(
                    text = stringResource(R.string.backup_import_action),
                    onClick = { showRestoreConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (val s = state) {
                BackupUiState.Idle -> Unit
                BackupUiState.Working -> Text(stringResource(R.string.backup_working), color = Cyan)
                BackupUiState.Exported -> Text(
                    stringResource(R.string.backup_exported),
                    color = Cyan,
                    fontWeight = FontWeight.SemiBold,
                )
                is BackupUiState.Imported -> Text(
                    stringResource(
                        R.string.backup_imported,
                        s.result.exercises,
                        s.result.routines,
                        s.result.sessions,
                        s.result.setLogs,
                    ),
                    color = Cyan,
                    fontWeight = FontWeight.SemiBold,
                )
                is BackupUiState.Error -> Text(stringResource(R.string.backup_error, s.message), color = Color(0xFFFF6B6B))
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text(stringResource(R.string.backup_import_action), color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = TextMuted)
                }
            },
            title = { Text(stringResource(R.string.backup_restore_confirm_title), color = Gold) },
            text = { Text(stringResource(R.string.backup_restore_confirm_body), color = TextMuted) },
        )
    }
}

@Composable
private fun UnitOption(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = TextMuted),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, color = if (selected) Color.White else TextMuted)
    }
}
