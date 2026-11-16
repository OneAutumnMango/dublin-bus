package dev.dublinbustracker.app.favourites

import dev.dublinbustracker.app.ui.TestTags
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
fun FavouriteStarButton(isFavourite: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.testTag(TestTags.FAVOURITE_STAR)) {
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
        )
    }
}
