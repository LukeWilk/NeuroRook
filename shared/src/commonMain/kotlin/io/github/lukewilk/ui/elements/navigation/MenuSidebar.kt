package io.github.lukewilk.ui.elements.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun menuSidebarWidth(expanded: Boolean, collapsedWidth: Int, expandedWidth: Int): Int =
    if (expanded) expandedWidth else collapsedWidth

internal fun menuSidebarSystemModeLabel(isSystemDark: Boolean?): String? = when (isSystemDark) {
    true -> "System: Dark mode"
    false -> "System: Light mode"
    null -> null
}

internal fun menuSidebarItemHasDivider(index: Int, lastIndex: Int): Boolean = index < lastIndex

@Composable
fun MenuSidebar(
    title: String? = null,
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    collapsedWidth: Int = 56,
    expandedWidth: Int = 220,
    isSystemDark: Boolean? = null,
    icons: List<String?> = emptyList(),
    selectedIndex: Int = -1,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null
) {
    var internalExpanded by remember { mutableStateOf(true) }
    val isExpanded = expanded ?: internalExpanded
    val updateExpanded: (Boolean) -> Unit = onExpandedChange ?: { internalExpanded = it }

    Row(modifier = modifier.fillMaxHeight()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            modifier = Modifier
                .width(menuSidebarWidth(isExpanded, collapsedWidth, expandedWidth).dp)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { updateExpanded(!isExpanded) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = "≡",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (isExpanded) {
                    val systemModeLabel = menuSidebarSystemModeLabel(isSystemDark)
                    if (systemModeLabel != null) {
                        Text(
                            text = systemModeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    if (headerContent != null) {
                        headerContent()
                        HorizontalDivider(thickness = 1.dp)
                    }
                    Menu(
                        title = if (headerContent == null) title else null,
                        items = items,
                        icons = icons,
                        selectedIndex = selectedIndex
                    )
                }
            }
        }
    }
}

