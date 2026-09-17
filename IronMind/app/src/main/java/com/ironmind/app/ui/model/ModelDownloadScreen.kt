package com.ironmind.app.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("Modelo de IA", color = Gold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Cyan)
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
                SectionTitle("IA on-device", accent = Cyan)
                Spacer(Modifier.height(8.dp))
                Text(
                    "El coach de IA corre 100% en tu dispositivo. Descarga una vez un modelo " +
                        "compatible con MediaPipe (p. ej. Gemma). Después funciona sin conexión.",
                    color = TextMuted,
                )
            }

            GlassCard {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL del modelo (.bin / .task)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                when (val s = state) {
                    ModelDownloadState.Idle -> AccentButton(
                        text = "Descargar modelo",
                        enabled = url.isNotBlank(),
                        onClick = { viewModel.download(url) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    is ModelDownloadState.Downloading -> Column {
                        Text(
                            s.progress?.let { "Descargando… ${(it * 100).toInt()}%" } ?: "Descargando…",
                            color = Cyan,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlowProgressBar(progress = s.progress ?: 0f)
                    }

                    ModelDownloadState.Ready -> Column {
                        Text("Modelo listo ✓", color = Cyan, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        AccentButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    }

                    is ModelDownloadState.Error -> Column {
                        Text("Error: ${s.message}", color = Color(0xFFFF6B6B))
                        Spacer(Modifier.height(8.dp))
                        AccentButton(
                            text = "Reintentar",
                            enabled = url.isNotBlank(),
                            onClick = { viewModel.download(url) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
