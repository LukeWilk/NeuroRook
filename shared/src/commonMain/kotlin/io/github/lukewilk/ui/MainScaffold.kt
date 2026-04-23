package io.github.lukewilk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import io.github.lukewilk.ui.elements.navigation.MenuSidebar

internal enum class MainScaffoldDestination(
    val label: String,
    val icon: ImageVector
) {
    Hardware(label = "Hardware", icon = Icons.Outlined.Memory),
    TestSignalNoise(label = "Test Signal Noise", icon = Icons.Outlined.ShowChart),
    RawData(label = "Raw Data", icon = Icons.Outlined.Storage),
    Protocols(label = "Protocols", icon = Icons.Outlined.Article),
    Baseline(label = "Baseline", icon = Icons.Outlined.QueryStats),
    AggregationAndReward(label = "Aggregation & Reward", icon = Icons.Outlined.EmojiEvents),
    Electrodes(label = "Electrodes", icon = Icons.Outlined.Bolt),
    Signals(label = "Signals", icon = Icons.Outlined.GraphicEq),
    Graphs(label = "Graphs", icon = Icons.Outlined.BarChart),
    Goals(label = "Goals", icon = Icons.Outlined.TrackChanges),
    Training(label = "Training", icon = Icons.Outlined.School),
    About(label = "About", icon = Icons.Outlined.Info)
}

internal data class MainScaffoldMenuItem(
    val destination: MainScaffoldDestination,
    val selected: Boolean
) {
    val label: String get() = destination.label
    val icon: ImageVector get() = destination.icon
}

internal data class MainScaffoldUiState(
    val sidebarWidth: Int,
    val menuItems: List<MainScaffoldMenuItem>,
    val selectedDestination: MainScaffoldDestination?
)

internal fun mainScaffoldMenuItems(): List<MainScaffoldDestination> = listOf(
    MainScaffoldDestination.Hardware,
    MainScaffoldDestination.TestSignalNoise,
    MainScaffoldDestination.RawData,
    MainScaffoldDestination.Protocols,
    MainScaffoldDestination.Baseline,
    MainScaffoldDestination.AggregationAndReward,
    MainScaffoldDestination.Electrodes,
    MainScaffoldDestination.Signals,
    MainScaffoldDestination.Graphs,
    MainScaffoldDestination.Goals,
    MainScaffoldDestination.Training,
    MainScaffoldDestination.About
)

internal fun mainScaffoldSelectedDestination(
    selectedTab: Int,
    menuItems: List<MainScaffoldDestination> = mainScaffoldMenuItems()
): MainScaffoldDestination? = menuItems.getOrNull(selectedTab)

internal fun mainScaffoldPlaceholderLabel(
    selectedTab: Int,
    menuItems: List<MainScaffoldDestination> = mainScaffoldMenuItems()
): String? = when (val destination = mainScaffoldSelectedDestination(selectedTab, menuItems)) {
    null,
    MainScaffoldDestination.Hardware,
    MainScaffoldDestination.Graphs,
    MainScaffoldDestination.About -> null
    else -> destination.label
}

internal fun mainScaffoldUiState(
    selectedTab: Int,
    isSidebarOpen: Boolean,
    menuItems: List<MainScaffoldDestination> = mainScaffoldMenuItems()
): MainScaffoldUiState = MainScaffoldUiState(
    sidebarWidth = if (isSidebarOpen) 220 else 56,
    menuItems = menuItems.mapIndexed { index, destination ->
        MainScaffoldMenuItem(destination = destination, selected = index == selectedTab)
    },
    selectedDestination = mainScaffoldSelectedDestination(selectedTab, menuItems)
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
            menuItems = menuItems
        )
    }
    val navigationItems = mainScaffoldNavigationItems(uiState.menuItems) { selectedTab = it }

    Row(Modifier.fillMaxSize()) {
        MenuSidebar(
            title = null,
            items = navigationItems,
            modifier = Modifier.fillMaxHeight(),
            collapsedWidth = 56,
            expandedWidth = 220,
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
            when (val selectedDestination = uiState.selectedDestination) {
                null, MainScaffoldDestination.Hardware -> hardwareScreen()
                MainScaffoldDestination.Graphs -> GraphsScreen()
                MainScaffoldDestination.About -> AboutScreen()
                else -> PlaceholderScreen(selectedDestination.label)
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

