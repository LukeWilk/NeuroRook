package io.github.lukewilk.ui.elements.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Menu(
    title: String? = null,
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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

