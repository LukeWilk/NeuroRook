package io.github.lukewilk.ui.elements.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun menuShowsTitle(title: String?): Boolean = title != null

internal fun menuItemHasDivider(index: Int, lastIndex: Int): Boolean = index < lastIndex

@Composable
fun Menu(
    title: String? = null,
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    icons: List<String?> = emptyList(),
    selectedIndex: Int = -1
) {
    val menuItems = items.mapIndexed { index, (label, action) ->
        MenuItemUiState(
            label = label,
            onClick = action,
            icon = icons.getOrNull(index),
            selected = index == selectedIndex
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (menuShowsTitle(title)) {
            Text(
                text = title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(thickness = 1.dp)
        }
        menuItems.forEachIndexed { idx, item ->
            MenuItem(item)
            if (menuItemHasDivider(idx, menuItems.lastIndex)) HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

