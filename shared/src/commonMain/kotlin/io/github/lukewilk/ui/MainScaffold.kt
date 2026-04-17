package io.github.lukewilk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import io.github.lukewilk.ui.elements.navigation.MenuSidebar

internal data class MainScaffoldMenuItem(
    val label: String,
    val icon: String,
    val selected: Boolean
)

internal data class MainScaffoldUiState(
    val sidebarWidth: Int,
    val defaultHeaderTitle: String?,
    val menuItems: List<MainScaffoldMenuItem>,
    val placeholderLabel: String?
)

internal fun mainScaffoldMenuItems(): List<Pair<String, String>> = listOf(
    "Hardware" to "🔧",
    "Protocols" to "📡",
    "Electrodes" to "⚡",
    "Signals" to "〰️",
    "Graphs" to "📊",
    "Goals" to "🎯",
    "Training" to "🎓"
)

internal fun mainScaffoldPlaceholderLabel(selectedTab: Int, menuItems: List<Pair<String, String>> = mainScaffoldMenuItems()): String? {
    if (selectedTab == 0) return null

    val selectedItem = menuItems.getOrNull(selectedTab) ?: return null
    return selectedItem.first
}

internal fun mainScaffoldUiState(
    selectedTab: Int,
    isSidebarOpen: Boolean,
    hasCustomHeader: Boolean,
    menuItems: List<Pair<String, String>> = mainScaffoldMenuItems()
): MainScaffoldUiState = MainScaffoldUiState(
    sidebarWidth = if (isSidebarOpen) 200 else 56,
    defaultHeaderTitle = if (isSidebarOpen && !hasCustomHeader) "Neuro Rook" else null,
    menuItems = menuItems.mapIndexed { index, (label, icon) ->
        MainScaffoldMenuItem(label = label, icon = icon, selected = index == selectedTab)
    },
    placeholderLabel = mainScaffoldPlaceholderLabel(selectedTab, menuItems)
)

internal fun mainScaffoldNavigationItems(
    menuItems: List<MainScaffoldMenuItem>,
    onSelectTab: (Int) -> Unit
): List<Pair<String, () -> Unit>> =
    menuItems.mapIndexed { index, item ->
        item.label to { onSelectTab(index) }
    }

@Composable
fun MainScaffold(
    hardwareScreen: @Composable () -> Unit,
    headerContent: @Composable (() -> Unit)? = null
) {
    val menuItems = mainScaffoldMenuItems()
    var selectedTab by remember { mutableStateOf(0) }
    var isSidebarOpen by remember { mutableStateOf(true) }
    val uiState = remember(selectedTab, isSidebarOpen, headerContent) {
        mainScaffoldUiState(
            selectedTab = selectedTab,
            isSidebarOpen = isSidebarOpen,
            hasCustomHeader = headerContent != null,
            menuItems = menuItems
        )
    }
    val navigationItems = mainScaffoldNavigationItems(uiState.menuItems) { selectedTab = it }

    Row(Modifier.fillMaxSize()) {
        MenuSidebar(
            title = uiState.defaultHeaderTitle,
            items = navigationItems,
            modifier = Modifier.fillMaxHeight(),
            collapsedWidth = 56,
            expandedWidth = 200,
            icons = uiState.menuItems.map { it.icon },
            selectedIndex = uiState.menuItems.indexOfFirst { it.selected },
            expanded = isSidebarOpen,
            onExpandedChange = { isSidebarOpen = it },
            headerContent = headerContent
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val placeholderLabel = uiState.placeholderLabel
            if (placeholderLabel == null) {
                hardwareScreen()
            } else {
                PlaceholderScreen(placeholderLabel)
            }
        }
    }
}

@Composable
internal fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "$label screen coming soon...",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp
        )
    }
}

