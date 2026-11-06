package dev.dublinbustracker.app.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

private const val PREFS = "map_ui"

/** A map option that is remembered between visits: the current value and a way to change it. */
class MapFlag(private val context: Context, private val key: String, initial: Boolean) {
    var value by mutableStateOf(initial)
        private set

    fun set(on: Boolean) {
        value = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(key, on) }
    }
}

@Composable
fun rememberMapFlag(key: String, default: Boolean): MapFlag {
    val context = LocalContext.current
    return remember(key) { MapFlag(context, key, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, default)) }
}
