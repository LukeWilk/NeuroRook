package io.github.lukewilk.ui.elements.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
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
import io.github.lukewilk.ui.elements.scroll.VerticalScrollCueBox

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

internal fun menuSidebarToggleIcon(expanded: Boolean): ImageVector =
    if (expanded) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight

internal fun menuSidebarToggleAlignment(expanded: Boolean, title: String?): Alignment =
    if (expanded) Alignment.Center else Alignment.CenterEnd

/** The shell header row now owns the title, so the nested [Menu] never repeats it. */
internal fun menuSidebarMenuTitleForMenu(): String? =
    null

/** Sidebar navigation shell for the desktop scaffold. */
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
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxHeight()
            // Keep the host background aligned with the sidebar surface so no window chrome shows through.
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .width(menuSidebarWidth(isExpanded, collapsedWidth, expandedWidth).dp)
                .fillMaxHeight()
        ) {
            VerticalScrollCueBox(
                scrollState = scrollState,
                modifier = Modifier.fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        IconButton(
                            onClick = { updateExpanded(!isExpanded) },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.align(menuSidebarToggleAlignment(isExpanded, title))
                        ) {
                            Icon(
                                imageVector = menuSidebarToggleIcon(isExpanded),
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
                    }
                    Menu(
                        title = if (isExpanded) menuSidebarMenuTitleForMenu() else null,
                        items = items,
                        icons = icons,
                        selectedIndex = selectedIndex,
                        compact = !isExpanded
                    )
                }
            }
        }
    }
}

