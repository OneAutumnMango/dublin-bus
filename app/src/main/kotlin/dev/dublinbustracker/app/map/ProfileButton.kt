package dev.dublinbustracker.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.data.RouteProfile
import kotlinx.coroutines.withTimeoutOrNull

private val BUTTON_SIZE = 44.dp
private val MENU_WIDTH = 220.dp
private val MENU_GAP = 8.dp
private val MENU_ROW_HEIGHT = 48.dp
private const val MENU_MAX_ROWS = 8

/**
 * The map's profiles button. Tap it to open the full profiles list (pick one, delete one, or save what is on the map
 * as a new one). Press and hold to fan the profiles out under it and, without lifting your finger, slide onto one and
 * let go to swap to it; letting go anywhere else just closes the menu.
 */
@Composable
fun ProfileButton(profiles: List<RouteProfile>, onOpen: () -> Unit, onPick: (RouteProfile) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var quickOpen by remember { mutableStateOf(false) }
    var hovered by remember { mutableIntStateOf(-1) }
    val shown = profiles.take(MENU_MAX_ROWS)

    Box(modifier = modifier.size(BUTTON_SIZE)) {
        Surface(
            modifier = Modifier.size(BUTTON_SIZE).pointerInput(shown) {
                val menuLeft = (BUTTON_SIZE - MENU_WIDTH).toPx()
                val menuTop = (BUTTON_SIZE + MENU_GAP).toPx()
                val rowHeight = MENU_ROW_HEIGHT.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    if (tapped != null) {
                        onOpen()
                        return@awaitEachGesture
                    }
                    // Still down after the long-press time. With nothing to fan out, holding is just a slow tap.
                    if (shown.isEmpty()) {
                        onOpen()
                        return@awaitEachGesture
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    quickOpen = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val p = change.position
                        val row = ((p.y - menuTop) / rowHeight).toInt()
                        hovered = if (p.x >= menuLeft && p.x <= BUTTON_SIZE.toPx() && p.y >= menuTop && row in shown.indices) row else -1
                        if (!change.pressed) break
                    }
                    val picked = shown.getOrNull(hovered)
                    quickOpen = false
                    hovered = -1
                    picked?.let(onPick)
                }
            },
            shape = CircleShape,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Bookmarks, contentDescription = "Profiles", modifier = Modifier.size(24.dp))
            }
        }

        if (quickOpen) {
            // Drawn beside the button, not inside it: the finger that opened it stays with the button and slides over these rows.
            Surface(
                // Unbounded and top-left anchored, so it isn't squeezed into (or centred on) the 44dp button it hangs from.
                modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset(x = BUTTON_SIZE - MENU_WIDTH, y = BUTTON_SIZE + MENU_GAP)
                    .width(MENU_WIDTH),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    shown.forEachIndexed { index, profile ->
                        val highlight = if (index == hovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        Box(
                            modifier = Modifier.fillMaxWidth().height(MENU_ROW_HEIGHT).background(highlight).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
