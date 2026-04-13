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

@Composable
fun MenuSidebar(
    title: String? = null,
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    collapsedWidth: Int = 56,
    expandedWidth: Int = 220,
    isSystemDark: Boolean? = null
) {
    var expanded by remember { mutableStateOf(true) }
    Row(modifier = modifier.fillMaxHeight()) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            modifier = Modifier
                .width(if (expanded) expandedWidth.dp else collapsedWidth.dp)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "≡",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (expanded) {
                    if (isSystemDark != null) {
                        Text(
                            text = if (isSystemDark) "System: Dark mode" else "System: Light mode",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider(thickness = 1.dp)
                    }
                    items.forEachIndexed { idx, (label, action) ->
                        MenuItem(label = label, onClick = action)
                        if (idx < items.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

