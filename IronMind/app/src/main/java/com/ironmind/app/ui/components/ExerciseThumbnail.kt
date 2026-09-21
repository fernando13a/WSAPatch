package com.ironmind.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.TextMuted
import java.io.File

/**
 * Coil loader with GIF/animated-WebP support, so an attached GIF actually plays. Remembered per
 * call site; Coil's own memory/disk cache is shared underneath, so lists don't refetch.
 */
@Composable
fun rememberExerciseImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

/**
 * Small square image for an exercise in a list. Prefers the athlete's own attached photo, then the
 * reference image (bundled in the APK for the starter exercises, fetched on demand for the rest).
 * Falls back to the exercise's initial rather than a broken-image box, so a row never looks empty
 * while offline.
 */
@Composable
fun ExerciseThumbnail(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(10.dp)
    val boxModifier = modifier
        .size(size)
        .clip(shape)
        .background(Cyan.copy(alpha = 0.08f))
        .border(1.dp, Cyan.copy(alpha = 0.25f), shape)

    val model = exercise.imagePath?.let { File(it) } ?: exercise.imageUrl
    if (model == null) {
        Box(boxModifier, contentAlignment = Alignment.Center) { Initial(exercise) }
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(true).build(),
        imageLoader = rememberExerciseImageLoader(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = boxModifier,
        loading = { Box(Modifier, contentAlignment = Alignment.Center) { Initial(exercise) } },
        error = { Box(Modifier, contentAlignment = Alignment.Center) { Initial(exercise) } },
    )
}

@Composable
private fun Initial(exercise: Exercise) {
    Text(
        text = exercise.name.firstOrNull()?.uppercase() ?: "?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
    )
}
