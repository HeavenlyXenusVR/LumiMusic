package com.lumisound.android.ui.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lumisound.android.gallery.GalleryPhoto
import com.lumisound.android.gallery.GallerySettings
import com.lumisound.android.gallery.GalleryTransition
import com.lumisound.android.ui.aura.LocalMotion
import kotlinx.coroutines.delay

/**
 * How one photo is placed at a point in a transition. [progress] runs 0..1 as the new
 * photo arrives; the outgoing photo is drawn with [incoming] false at the same progress.
 * Translations are fractions of the page, so the numbers hold at any size.
 */
data class PhotoLayer(
    val alpha: Float = 1f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    /** Blur added on top of the settings' blur, in dp, for the blur-based transitions. */
    val extraBlur: Float = 0f,
)

fun photoLayer(transition: GalleryTransition, progress: Float, incoming: Boolean): PhotoLayer {
    val p = progress.coerceIn(0f, 1f)
    val shown = if (incoming) p else 1f - p
    return when (transition) {
        GalleryTransition.Fade -> PhotoLayer(alpha = shown)
        GalleryTransition.SlideLeft -> PhotoLayer(translateX = if (incoming) 1f - p else -p)
        GalleryTransition.SlideRight -> PhotoLayer(translateX = if (incoming) p - 1f else p)
        GalleryTransition.SlideUp -> PhotoLayer(translateY = if (incoming) 1f - p else -p)
        GalleryTransition.SlideDown -> PhotoLayer(translateY = if (incoming) p - 1f else p)
        GalleryTransition.ZoomIn -> PhotoLayer(alpha = shown, scale = if (incoming) 0.8f + 0.2f * p else 1f + 0.2f * p)
        GalleryTransition.ZoomOut -> PhotoLayer(alpha = shown, scale = if (incoming) 1.2f - 0.2f * p else 1f - 0.2f * p)
        GalleryTransition.ZoomBlur -> PhotoLayer(
            alpha = shown,
            scale = if (incoming) 1.15f - 0.15f * p else 1f + 0.15f * p,
            extraBlur = 24f * (1f - shown),
        )
        // The outgoing photo turns away over the first half; the new one turns in over the second.
        GalleryTransition.Flip -> if (incoming) {
            PhotoLayer(alpha = if (p < 0.5f) 0f else 1f, rotationY = if (p < 0.5f) 90f else 90f * (1f - p) * 2f)
        } else {
            PhotoLayer(alpha = if (p < 0.5f) 1f else 0f, rotationY = if (p < 0.5f) -90f * p * 2f else -90f)
        }
        GalleryTransition.Twist -> PhotoLayer(
            alpha = shown,
            rotationZ = if (incoming) -12f * (1f - p) else 12f * p,
            scale = 0.9f + 0.1f * shown,
        )
        GalleryTransition.BlurIn -> PhotoLayer(alpha = shown, extraBlur = 28f * (1f - shown))
        GalleryTransition.Cut -> PhotoLayer(alpha = if (incoming) 1f else 0f)
    }
}

/**
 * The account's gallery photos as a slow slideshow behind every screen: one photo at a
 * time, at the synced opacity and blur, moving to the next every interval with the synced
 * transition. With Ken Burns on, each photo also drifts and zooms while it is up.
 *
 * Blur needs Android 12; on Android 11 the photo shows sharp, still at its opacity.
 * With motion off (screenshot renders), the first photo is held still.
 */
@Composable
fun GalleryBackdrop(photos: List<GalleryPhoto>, settings: GallerySettings, modifier: Modifier = Modifier) {
    if (!settings.enabled || photos.isEmpty()) return
    val motion = LocalMotion.current
    var index by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf<GalleryPhoto?>(null) }
    val progress = remember { Animatable(1f) }
    val current = photos[index.coerceIn(0, photos.lastIndex)]

    if (motion && photos.size > 1) {
        LaunchedEffect(photos, settings.intervalSeconds, settings.transition) {
            while (true) {
                delay(settings.intervalSeconds * 1_000L)
                previous = photos[index.coerceIn(0, photos.lastIndex)]
                index = (index + 1) % photos.size
                if (settings.transition == GalleryTransition.Cut) {
                    progress.snapTo(1f)
                } else {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(TRANSITION_MS, easing = FastOutSlowInEasing))
                }
                previous = null
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().graphicsLayer { alpha = settings.opacity }) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val p = progress.value
        previous?.let { outgoing ->
            key(outgoing.id) {
                Photo(outgoing, settings, photoLayer(settings.transition, p, incoming = false), widthPx, heightPx, motion)
            }
        }
        key(current.id) {
            val layer = if (previous == null) PhotoLayer() else photoLayer(settings.transition, p, incoming = true)
            Photo(current, settings, layer, widthPx, heightPx, motion)
        }
    }
}

@Composable
private fun Photo(
    photo: GalleryPhoto,
    settings: GallerySettings,
    layer: PhotoLayer,
    widthPx: Float,
    heightPx: Float,
    motion: Boolean,
) {
    // Ken Burns: one slow zoom-and-drift per photo, reversing so it never jumps.
    val drift = if (motion && settings.kenBurns) {
        rememberInfiniteTransition(label = "kenBurns").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween((settings.intervalSeconds * 1_000).coerceAtLeast(8_000), easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "kenBurnsDrift",
        ).value
    } else {
        0f
    }
    val blur = settings.blurRadius + layer.extraBlur
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = layer.alpha
                translationX = layer.translateX * widthPx + drift * widthPx * 0.03f
                translationY = layer.translateY * heightPx - drift * heightPx * 0.02f
                val zoom = layer.scale * (1f + 0.10f * drift)
                scaleX = zoom
                scaleY = zoom
                rotationY = layer.rotationY
                rotationZ = layer.rotationZ
                cameraDistance = 12f * density
            }
            .then(if (blur > 0.5f) Modifier.blur(blur.dp) else Modifier)
    ) {
        // A heavily blurred photo loses nothing at half resolution, and costs a quarter as much.
        GalleryImage(photo, if (blur >= 8f) 720 else 1440, Modifier.fillMaxSize())
    }
}

/**
 * Stands in for the network in screenshot renders: given a photo, the painter to draw.
 * Unset in the app, where photos load through Coil with the session token attached.
 */
val LocalGalleryPhotoPainter = staticCompositionLocalOf<(@Composable (GalleryPhoto) -> Painter)?> { null }

/** One gallery photo, cropped to fill. Used by the backdrop and the settings screen. */
@Composable
fun GalleryImage(photo: GalleryPhoto, sizePx: Int, modifier: Modifier = Modifier) {
    val override = LocalGalleryPhotoPainter.current
    if (override != null) {
        Image(override(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(photo.url, sizePx) {
        ImageRequest.Builder(context).data(photo.url).size(sizePx).crossfade(false).build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

private const val TRANSITION_MS = 900
