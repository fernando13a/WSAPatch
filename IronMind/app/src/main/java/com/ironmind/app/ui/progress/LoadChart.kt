package com.ironmind.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironmind.app.R
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import kotlin.math.roundToInt

/**
 * Load-evolution chart: a cyan curve (with glow + area fill) over a warm gold field, with faint
 * gridlines. Tapping selects the nearest point (reported via [onSelect]) so the caller can show
 * its value — making the chart interactive.
 */
@Composable
fun LoadChart(
    points: List<LoadPoint>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxV = points.maxOfOrNull { it.value } ?: 0f
    val minV = points.minOfOrNull { it.value } ?: 0f
    val range = (maxV - minV).takeIf { it > 0f } ?: 1f
    val chartDescription = stringResource(R.string.chart_cd)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = chartDescription }
            .pointerInput(points.size) {
            detectTapGestures { offset ->
                if (points.isEmpty()) return@detectTapGestures
                val idx = if (points.size == 1) {
                    0
                } else {
                    ((offset.x / size.width) * (points.size - 1)).roundToInt()
                        .coerceIn(0, points.size - 1)
                }
                onSelect(idx)
            }
        },
    ) {
        val padX = 16.dp.toPx()
        val padY = 16.dp.toPx()
        val chartW = size.width - padX * 2
        val chartH = size.height - padY * 2

        // Warm gold field.
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Gold.copy(alpha = 0.30f), Gold.copy(alpha = 0.06f))),
            cornerRadius = CornerRadius(28f, 28f),
        )

        // Faint gridlines.
        val gridColor = Color.White.copy(alpha = 0.08f)
        for (i in 0..4) {
            val y = padY + chartH * i / 4f
            drawLine(gridColor, Offset(padX, y), Offset(size.width - padX, y), strokeWidth = 1f)
        }

        if (points.isEmpty()) return@Canvas

        fun x(i: Int): Float =
            padX + if (points.size == 1) chartW / 2f else chartW * i / (points.size - 1)

        fun y(v: Float): Float = padY + chartH * (1f - (v - minV) / range)

        // Curve path.
        val line = Path()
        points.forEachIndexed { i, p ->
            val px = x(i); val py = y(p.value)
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }

        // Cyan area fill under the curve.
        val area = Path().apply {
            addPath(line)
            lineTo(x(points.lastIndex), padY + chartH)
            lineTo(x(0), padY + chartH)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.35f), Cyan.copy(alpha = 0f))))

        // Glow underlay + crisp cyan line.
        drawPath(line, color = Cyan.copy(alpha = 0.25f), style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, color = Cyan, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Points, with the selected one emphasized.
        points.forEachIndexed { i, p ->
            val center = Offset(x(i), y(p.value))
            val selected = i == selectedIndex
            drawCircle(color = Cyan, radius = if (selected) 9f else 5f, center = center)
            if (selected) drawCircle(color = Color.White, radius = 4f, center = center)
        }
    }
}
