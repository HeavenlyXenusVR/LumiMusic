package com.lumisound.android.ui.components

/**
 * Builds a track's secondary line without repeating its title.
 *
 * Most of this cloud library is uploads whose artist tag is the title again, and whose
 * "album" is the per-track folder the server falls back to when a file has no album tag.
 * Rendered naively that produced rows reading "Some Track" over "Some Track" -- twice the
 * ink for no information, on every row in the list.
 */
fun trackSubtitle(title: String, artist: String?, album: String?, fallback: String = "Unknown artist"): String {
    val parts = listOfNotNull(artist, album)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.equals("unknown artist", ignoreCase = true) }
        // Anything that is just the title again, or contains it, adds nothing.
        .filterNot { it.similarTo(title) }
        .distinctBy { it.lowercase() }
    return if (parts.isEmpty()) fallback else parts.joinToString(" · ")
}

private fun String.similarTo(title: String): Boolean {
    val a = normalise()
    val b = title.normalise()
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || a.startsWith(b) || b.startsWith(a)
}

private fun String.normalise(): String = lowercase().filter { it.isLetterOrDigit() }
