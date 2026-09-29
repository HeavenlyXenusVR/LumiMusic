package com.lumisound.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** A value fetched from the bridge, and the states on the way to it. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>

    val valueOrNull: T? get() = (this as? Ready<T>)?.value
}

@Stable
class LoadableHolder<T> internal constructor() {
    var state: Loadable<T> by mutableStateOf<Loadable<T>>(Loadable.Loading)
        internal set
    internal var generation by mutableIntStateOf(0)

    /** Fetch again. The current value stays on screen until the new one arrives. */
    fun reload() {
        generation++
    }
}

/**
 * Runs [fetch] when first composed, again whenever [key] changes, and again on
 * [LoadableHolder.reload]. Failures become a readable [Loadable.Failed] and are logged
 * under [tag], so a dashboard section that silently never loaded leaves a trace.
 */
@Composable
fun <T> rememberLoadable(key: Any?, tag: String, fetch: suspend () -> T): LoadableHolder<T> {
    val holder = remember(key) { LoadableHolder<T>() }
    LaunchedEffect(key, holder.generation) {
        if (holder.state !is Loadable.Ready) holder.state = Loadable.Loading
        holder.state = try {
            Loadable.Ready(fetch())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(tag, "load failed: ${e.javaClass.simpleName}", mapOf("detail" to e.message?.take(160)))
            Loadable.Failed(friendlyError(e))
        }
    }
    return holder
}

/** What to tell a person about a failed bridge call, without a stack trace in it. */
fun friendlyError(e: Throwable): String = when (e) {
    is HttpException -> when (e.code()) {
        401 -> "Your session has expired. Sign in again."
        403 -> "That isn't available to this account."
        404 -> "Nothing was found."
        408, 504 -> "The server took too long to answer."
        429 -> "Too many requests. Try again in a moment."
        in 500..599 -> "The server had a problem (${e.code()})."
        else -> "Request failed (${e.code()})."
    }
    is IOException -> "Couldn't reach the server. Check your connection."
    else -> "Something went wrong."
}

/** The inline loading / error states used inside a section rather than a whole screen. */
@Composable
fun <T> LoadableSection(
    loadable: Loadable<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (loadable) {
        Loadable.Loading -> Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = LocalLumiPalette.current.accent,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(26.dp),
            )
        }
        is Loadable.Failed -> Column(
            modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                loadable.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text("Try again") }
        }
        is Loadable.Ready -> content(loadable.value)
    }
}
