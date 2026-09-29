package com.lumisound.android.ui.aura

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.lumisound.android.ui.components.fallbackPaletteFor
import kotlinx.coroutines.CancellationException
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The colours of whatever is playing, which the whole app takes on.
 *
 * This is the central idea of the redesign: instead of one fixed accent painted on a fixed
 * navy page, the page itself glows with the music -- sampled from the cover when there is
 * one, from the generated cover's hue when there is not -- and cross-fades when the track
 * changes. The account accent still marks interactive things; the aura is the room they sit in.
 */
@Immutable
data class Aura(val primary: Color, val secondary: Color) {
    companion object {
        fun fromPair(pair: Pair<Int, Int>) = Aura(Color(pair.first), Color(pair.second))

        /** The aura of a generated cover, so a track with no artwork still has its own. */
        fun forKey(key: String): Aura = fromPair(AuraColors.fromHue(fallbackPaletteFor(key).hue))

        /** The resting aura, built from the account accent when nothing is playing. */
        fun forAccent(accent: Color): Aura {
            val hsv = AuraColors.toHsv(accent.toArgb())
            return fromPair(AuraColors.fromHue(hsv[0]))
        }
    }
}

val LocalAura = staticCompositionLocalOf { Aura(Color(0xFFEC4079), Color(0xFF7F5AF0)) }

/**
 * Whether ambient motion runs: the drifting glow, the turning record, the dancing bars.
 * On in the app. Off in screenshot renders, where a loop that never settles makes the
 * renderer step frames without end -- one Now Playing render ran out of memory doing it.
 */
val LocalMotion = staticCompositionLocalOf { true }

/**
 * The aura for a track: the generated one at once, replaced by the cover's real colours as
 * soon as a small copy of the cover has loaded. The cover is fetched at 40px through the
 * app's own image loader, so it is a cache hit whenever the artwork is already on screen.
 */
@Composable
fun rememberTrackAura(artworkModel: Any?, fallbackKey: String, idle: Aura): Aura {
    val context = LocalContext.current
    var sampled by remember(artworkModel, fallbackKey) { mutableStateOf<Aura?>(null) }
    LaunchedEffect(artworkModel) {
        if (artworkModel == null) return@LaunchedEffect
        sampled = try {
            val request = ImageRequest.Builder(context)
                .data(artworkModel)
                .size(40)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            result?.image?.toBitmap()?.let { bitmap ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                AuraColors.extract(pixels)?.let(Aura::fromPair)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    val target = when {
        sampled != null -> sampled!!
        fallbackKey.isBlank() -> idle
        else -> Aura.forKey(fallbackKey)
    }
    val primary by animateColorAsState(target.primary, tween(1_200), label = "auraPrimary")
    val secondary by animateColorAsState(target.secondary, tween(1_400), label = "auraSecondary")
    return Aura(primary, secondary)
}

/** The page every screen sits on: near-black, with the aura as two slowly drifting glows. */
val AuraBase = Color(0xFF07080F)

@Composable
fun AuraBackdrop(
    aura: Aura,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // One slow loop drives both glows; at 40 seconds a cycle the motion is felt more than seen.
    val phase = if (LocalMotion.current) {
        rememberInfiniteTransition(label = "auraDrift").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
            label = "auraPhase",
        ).value
    } else {
        0.6f
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(AuraBase)
            val w = size.width
            val h = size.height
            val big = max(w, h)
            val a = Offset(w * (0.18f + 0.10f * cos(phase)), h * (0.10f + 0.05f * sin(phase)))
            val b = Offset(w * (0.92f + 0.08f * sin(phase * 2)), h * (0.34f + 0.06f * cos(phase)))
            val c = Offset(w * (0.40f + 0.12f * sin(phase)), h * (0.86f + 0.04f * cos(phase * 2)))
            drawCircle(
                Brush.radialGradient(
                    listOf(aura.primary.copy(alpha = 0.42f * intensity), Color.Transparent),
                    center = a,
                    radius = big * 0.62f,
                ),
                radius = big * 0.62f,
                center = a,
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(aura.secondary.copy(alpha = 0.30f * intensity), Color.Transparent),
                    center = b,
                    radius = big * 0.52f,
                ),
                radius = big * 0.52f,
                center = b,
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(aura.primary.copy(alpha = 0.14f * intensity), Color.Transparent),
                    center = c,
                    radius = big * 0.5f,
                ),
                radius = big * 0.5f,
                center = c,
            )
            // A soft vignette so text at the bottom always sits on something dark.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to AuraBase.copy(alpha = 0.35f),
                    1f to AuraBase.copy(alpha = 0.85f),
                )
            )
        }
        content()
    }
}
