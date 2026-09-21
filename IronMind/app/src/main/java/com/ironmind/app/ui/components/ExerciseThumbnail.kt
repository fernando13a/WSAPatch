package com.ironmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.TextMuted
import com.ironmind.app.ui.util.displayName
import java.io.File

/**
 * Small square image for an exercise in a list. Prefers the athlete's own attached photo, then the
 * reference image (bundled in the APK for the starter exercises, fetched on demand for the rest).
 * The exercise's initial shows underneath, so a row reads correctly while the image loads, when
 * there's no image, and when a remote one can't be fetched offline.
 *
 * Uses [AsyncImage] with the app-wide loader from
 * [com.ironmind.app.IronMindApplication.newImageLoader] rather than SubcomposeAsyncImage: these
 * render in long lists, where subcomposition costs far more than the plain layout this needs.
 */
@Composable
fun ExerciseThumbnail(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    /**
     * `false` where the whole catalog composes at once (the session's exercise picker renders all
     * ~890 rows eagerly): only images already on the device are shown, so opening it can't kick
     * off hundreds of downloads. Everything else keeps the initial.
     */
    allowRemote: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    // An attached photo can outlive its file (cache cleared, or a backup restored onto a device
    // where that absolute path means nothing), so fall through to the reference image instead of
    // letting a dead path mask it.
    val attached = exercise.imagePath?.takeIf { File(it).exists() }?.let { File(it) }
    val reference = exercise.imageUrl?.takeIf { allowRemote || !it.startsWith("http") }
    val model = attached ?: reference

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Cyan.copy(alpha = 0.08f))
            .border(1.dp, Cyan.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = exercise.displayName().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
        )
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )
        }
    }
}
