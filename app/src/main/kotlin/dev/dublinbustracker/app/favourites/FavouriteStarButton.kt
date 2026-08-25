package dev.dublinbustracker.app.favourites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
fun FavouriteStarButton(isFavourite: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
        )
    }
}
