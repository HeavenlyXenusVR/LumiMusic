package com.lumisound.android.ui

/**
 * Screens pushed on top of a tab. A plain stack held by the shell rather than a navigation
 * library: every route here is one level of "open this, then go back", and the system back
 * gesture pops it.
 */
sealed interface Route {
    data object Equalizer : Route
    data object Downloads : Route
    data object Diagnostics : Route
    data object Stats : Route
    data object Rewind : Route
    data object Achievements : Route
    data object Notifications : Route
    data object Scrobbling : Route
    data object Podcasts : Route
    data class Podcast(val feedUrl: String, val title: String, val artworkUrl: String?) : Route
    data class Profile(val userId: String, val name: String) : Route
}
