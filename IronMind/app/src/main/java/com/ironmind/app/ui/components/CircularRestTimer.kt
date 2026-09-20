package com.ironmind.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironmind.app.R
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

/**
 * A hero countdown dial for the rest timer: a gold→cyan gradient arc that shrinks as the rest
 * elapses, a faint cyan atmospheric glow while running, and the remaining mm:ss in the centre.
 * Follows the "Kinetic Glass Obsidian" telemetry look (cyan == live timer feedback).
 */
@Composable
fun CircularRestTimer(
    remaining: Int,
    total: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 208.dp,
    stroke: Dp = 14.dp,
) {
    val fraction = if (total > 0) (remaining.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(targetValue = fraction, label = "restFraction")
    val running = remaining > 0
    val arcBrush = Brush.sweepGradient(listOf(Cyan, Gold, Cyan))

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Atmospheric glow behind the dial while the timer runs.
            if (running) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Cyan.copy(alpha = 0.18f), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2f,
                    ),
                    radius = size.minDimension / 2f,
                    center = center,
                )
            }

            // Idle track ring.
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Remaining-time arc, drawn from the top and shrinking as rest elapses.
            if (animated > 0f) {
                drawArc(
                    brush = arcBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatMmSs(remaining),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = if (running) Cyan else TextMuted,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(if (running) R.string.rest_dial_active else R.string.rest_dial_ready),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
