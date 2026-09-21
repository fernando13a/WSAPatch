package com.ironmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

/** A screen/section heading with an accent tick. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, accent: androidx.compose.ui.graphics.Color = Gold) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "▍",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A big number over a caption — used for the streak and quick stats. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = Cyan,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextMuted,
        )
    }
}

/** The primary call-to-action button in brand gold — tall, rounded, with a soft gold glow. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = modifier
            .height(56.dp)
            .then(if (enabled) Modifier.shadow(18.dp, shape, spotColor = Gold, ambientColor = Gold) else Modifier),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Black,
            disabledContainerColor = Gold.copy(alpha = 0.3f),
            disabledContentColor = Black.copy(alpha = 0.5f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A small rounded tag/pill — routine splits, "ON-DEVICE AI", status labels, etc. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = Cyan,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A label/value row used inside detail cards. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
