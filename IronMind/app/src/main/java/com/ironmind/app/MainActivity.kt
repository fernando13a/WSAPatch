package com.ironmind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.GlassFill
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.IronMindTheme
import com.ironmind.app.ui.theme.TextMuted
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. The real navigation and screens land in Stage 3; for now this shows
 * a themed placeholder so the app builds, runs, and previews the design language.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IronMindTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    StageOnePlaceholder()
                }
            }
        }
    }
}

@Composable
private fun StageOnePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Frosted "glass" card with a subtle gold→cyan glowing edge.
        Column(
            modifier = Modifier
                .background(GlassFill, RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Gold.copy(alpha = 0.6f), Cyan.copy(alpha = 0.4f)),
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "IronMind",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            Text(
                text = "Etapa 1 · Capa de datos lista",
                style = MaterialTheme.typography.titleLarge,
                color = Cyan,
            )
            Text(
                text = "Room · Clean Architecture · Hilt\n100% offline · IA on-device (próximamente)",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StageOnePlaceholderPreview() {
    IronMindTheme {
        StageOnePlaceholder()
    }
}
