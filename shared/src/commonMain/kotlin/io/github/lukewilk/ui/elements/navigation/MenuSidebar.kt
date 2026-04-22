package io.github.lukewilk.ui.elements.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal fun menuSidebarWidth(expanded: Boolean, collapsedWidth: Int, expandedWidth: Int): Int =
    if (expanded) expandedWidth else collapsedWidth

internal fun menuSidebarSystemModeLabel(isSystemDark: Boolean?): String? = when (isSystemDark) {
    true -> "System: Dark mode"
    false -> "System: Light mode"
    null -> null
}

internal fun menuSidebarItemHasDivider(index: Int, lastIndex: Int): Boolean = index < lastIndex

internal fun menuSidebarToggleContentDescription(expanded: Boolean): String =
    if (expanded) "Collapse sidebar" else "Expand sidebar"

/** The shell header row now owns the title, so the nested [Menu] never repeats it. */
internal fun menuSidebarMenuTitleForMenu(hasHeaderContent: Boolean, title: String?): String? =
    null

/**
 * Sidebar navigation shell that paints its host background behind the rounded surface so desktop window chrome never bleeds through.
 */
@Composable
fun MenuSidebar(
    title: String? = null,
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    collapsedWidth: Int = 56,
    expandedWidth: Int = 220,
    isSystemDark: Boolean? = null,
    icons: List<ImageVector?> = emptyList(),
    selectedIndex: Int = -1,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null
) {
    var internalExpanded by remember { mutableStateOf(true) }
    val isExpanded = expanded ?: internalExpanded
    val updateExpanded: (Boolean) -> Unit = onExpandedChange ?: { internalExpanded = it }
    val hasHeaderContent = headerContent != null

    Row(
        modifier = modifier
            .fillMaxHeight()
            // Match the shell background behind the rounded surface so transparent corners never reveal a white window backdrop.
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            modifier = Modifier
                .width(menuSidebarWidth(isExpanded, collapsedWidth, expandedWidth).dp)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (isExpanded && !hasHeaderContent && title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(
                        onClick = { updateExpanded(!isExpanded) },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Outlined.Close else Icons.Outlined.Menu,
                            contentDescription = menuSidebarToggleContentDescription(isExpanded),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp)
                if (isExpanded) {
                    val systemModeLabel = menuSidebarSystemModeLabel(isSystemDark)
                    if (systemModeLabel != null) {
                        Text(
                            text = systemModeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    if (hasHeaderContent) {
                        headerContent()
                        HorizontalDivider(thickness = 1.dp)
                    }
                    Menu(
                        title = menuSidebarMenuTitleForMenu(hasHeaderContent, title),
                        items = items,
                        icons = icons,
                        selectedIndex = selectedIndex
                    )
                }
            }
        }
    }
}

