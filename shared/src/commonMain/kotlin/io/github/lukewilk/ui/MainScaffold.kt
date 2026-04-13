package io.github.lukewilk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SidebarItem(
    val label: String,
    val icon: String
)

@Composable
fun MainScaffold(
    hardwareScreen: @Composable () -> Unit,
    headerContent: @Composable (() -> Unit)? = null
) {
    val expandedSidebarWidth = 200.dp
    val collapsedSidebarWidth = 56.dp
    val sidebarItemShape = RoundedCornerShape(8.dp)
    val menuItems = listOf(
        SidebarItem("Hardware", "🔧"),
        SidebarItem("Protocols", "📡"),
        SidebarItem("Electrodes", "⚡"),
        SidebarItem("Signals", "〰️"),
        SidebarItem("Graphs", "📊"),
        SidebarItem("Goals", "🎯"),
        SidebarItem("Training", "🎓")
    )
    var selectedTab by remember { mutableStateOf(0) }
    var isSidebarOpen by remember { mutableStateOf(true) }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(if (isSidebarOpen) expandedSidebarWidth else collapsedSidebarWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clickable { isSidebarOpen = !isSidebarOpen }
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSidebarOpen) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (headerContent != null) {
                            headerContent()
                        } else {
                            Text(
                                "Neuro Rook",
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    Text(
                        "≡",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            menuItems.forEachIndexed { idx, item ->
                val selected = selectedTab == idx
                val itemColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            shape = sidebarItemShape
                        )
                        .clickable { selectedTab = idx }
                        .padding(vertical = 12.dp, horizontal = if (isSidebarOpen) 16.dp else 0.dp),
                    horizontalArrangement = if (isSidebarOpen) Arrangement.Start else Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.icon,
                        color = itemColor,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isSidebarOpen) {
                        Text(
                            item.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = itemColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> hardwareScreen()
                else -> PlaceholderScreen(menuItems[selectedTab].label)
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label screen coming soon...", color = MaterialTheme.colorScheme.onBackground)
    }
}

