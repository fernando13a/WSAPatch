package com.ironmind.app.ui.model

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.ModelDownloadState
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.GlowProgressBar
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onBack: () -> Unit,
    viewModel: ModelDownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(viewModel.defaultUrl) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFromFile) }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_title), color = Gold, fontWeight = FontWeight.Bold) },
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
                SectionTitle(stringResource(R.string.model_ondevice_title), accent = Cyan)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.model_desc), color = TextMuted)
            }

            GlassCard {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.model_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                when (val s = state) {
                    ModelDownloadState.Idle -> Column {
                        AccentButton(
                            text = stringResource(R.string.ai_download_model),
                            enabled = url.isNotBlank(),
                            onClick = { viewModel.download(url) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.model_resume_hint),
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    is ModelDownloadState.Downloading -> Column {
                        Text(
                            s.progress?.let { stringResource(R.string.model_downloading_pct, (it * 100).toInt()) }
                                ?: stringResource(R.string.model_downloading),
                            color = Cyan,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlowProgressBar(progress = s.progress ?: 0f)
                    }

                    ModelDownloadState.Ready -> Column {
                        Text(stringResource(R.string.model_ready), color = Cyan, fontWeight = FontWeight.SemiBold)
                        val mb = viewModel.modelSizeBytes() / (1024 * 1024)
                        if (mb > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.model_size_on_disk, mb),
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        AccentButton(text = stringResource(R.string.action_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.model_delete), color = Color(0xFFFF6B6B))
                        }
                    }

                    is ModelDownloadState.Error -> Column {
                        Text(stringResource(R.string.model_error, s.message), color = Color(0xFFFF6B6B))
                        Spacer(Modifier.height(8.dp))
                        AccentButton(
                            text = stringResource(R.string.action_retry),
                            enabled = url.isNotBlank(),
                            onClick = { viewModel.download(url) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Alternative to downloading: pick a model file already on the device.
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.model_or_import), color = TextMuted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = state !is ModelDownloadState.Downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.model_import_file), color = Cyan)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteModel()
                }) { Text(stringResource(R.string.model_delete), color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = TextMuted)
                }
            },
            title = { Text(stringResource(R.string.model_delete_confirm_title), color = Gold) },
            text = { Text(stringResource(R.string.model_delete_confirm_body), color = TextMuted) },
        )
    }
}
