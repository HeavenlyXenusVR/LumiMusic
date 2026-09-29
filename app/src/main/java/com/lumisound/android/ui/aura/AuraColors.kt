package com.lumisound.android.ui.aura

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The two colours an image "is", for tinting everything around it.
 *
 * Averaging the pixels is the obvious approach and the wrong one: a mostly-black cover with
 * a hot pink logo averages to a muddy maroon that appears nowhere in it. Instead every pixel
 * votes for its hue, weighted by how vivid it is (saturation times brightness), so a small
 * bright element outvotes a large dull field -- which is what a person would call the
 * cover's colour. The second colour is the strongest hue at least [MIN_HUE_GAP] degrees from
 * the first; when there is none (a one-colour cover), it is the first rotated, so the glow
 * still has two lobes rather than one flat wash.
 *
 * Plain ARGB ints and HSV maths only, so it is testable on the JVM with no Android types.
 */
object AuraColors {

    private const val BUCKETS = 36
    private const val MIN_HUE_GAP = 40f

    /** Returns (primary, secondary) as ARGB, or null when the image has no usable colour. */
    fun extract(pixels: IntArray): Pair<Int, Int>? {
        val weight = FloatArray(BUCKETS)
        val satSum = FloatArray(BUCKETS)
        val valSum = FloatArray(BUCKETS)
        for (argb in pixels) {
            if ((argb ushr 24) < 128) continue
            val hsv = toHsv(argb)
            // Near-greys carry no hue worth voting for; near-blacks carry nothing at all.
            if (hsv[1] < 0.18f || hsv[2] < 0.18f) continue
            val bucket = ((hsv[0] / 360f) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            val w = hsv[1] * hsv[2]
            weight[bucket] += w
            satSum[bucket] += hsv[1] * w
            valSum[bucket] += hsv[2] * w
        }
        val first = weight.indices.maxByOrNull { weight[it] } ?: return null
        if (weight[first] <= 0f) return null
        val firstHue = (first + 0.5f) * 360f / BUCKETS
        val second = weight.indices
            .filter { hueDistance((it + 0.5f) * 360f / BUCKETS, firstHue) >= MIN_HUE_GAP && weight[it] > 0f }
            .maxByOrNull { weight[it] }
        val primary = vivid(firstHue, satSum[first] / weight[first], valSum[first] / weight[first])
        val secondaryColor = if (second != null) {
            vivid((second + 0.5f) * 360f / BUCKETS, satSum[second] / weight[second], valSum[second] / weight[second])
        } else {
            vivid((firstHue + 48f) % 360f, satSum[first] / weight[first], valSum[first] / weight[first])
        }
        return primary to secondaryColor
    }

    /**
     * A background glow wants colour, not the exact pixel: saturation and brightness are
     * pulled into a band that reads as light on a near-black page without turning neon.
     */
    fun vivid(hue: Float, saturation: Float, value: Float): Int =
        fromHsv(hue, saturation.coerceIn(0.55f, 0.85f), value.coerceIn(0.62f, 0.92f))

    /** The same kind of pair for something with no artwork, from its generated cover hue. */
    fun fromHue(hue: Float): Pair<Int, Int> =
        vivid(hue, 0.72f, 0.82f) to vivid((hue + 52f) % 360f, 0.66f, 0.76f)

    fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    fun toHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val hue = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val sat = if (maxC == 0f) 0f else delta / maxC
        return floatArrayOf(hue, sat, maxC)
    }

    fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
        val c = value * saturation
        val h = (hue % 360f) / 60f
        val x = c * (1 - abs(h % 2f - 1))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = value - c
        fun channel(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}
