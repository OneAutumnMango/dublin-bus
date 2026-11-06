package dev.dublinbustracker.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Cards line up with the screen's 16dp margin, with a small gap between them. */
private val CARD_MARGIN = 16.dp
private val CARD_GAP = 3.dp

/** A tappable rounded card holding a row of your own layout. */
@Composable
fun AppRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = CARD_MARGIN, vertical = CARD_GAP),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_MARGIN, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A card with a headline, optional smaller lines beneath it, and something at the end. */
@Composable
fun AppListItem(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    AppRow(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headlineContent() }
            if (supportingContent != null) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    supportingContent()
                }
            }
        }
        trailingContent?.invoke()
    }
}
