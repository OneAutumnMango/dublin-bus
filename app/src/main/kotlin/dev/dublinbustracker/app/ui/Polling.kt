package dev.dublinbustracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Calls [tick] every [intervalMs] while this screen is on show. The loop stops when the screen
 * leaves composition or the app goes to the background, so nothing keeps polling the server
 * unseen (a ViewModel outlives its screen, so a loop in one would never stop).
 */
@Composable
fun PollWhileVisible(intervalMs: Long, tick: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestTick by rememberUpdatedState(tick)
    LaunchedEffect(intervalMs, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(intervalMs)
                latestTick()
            }
        }
    }
}
