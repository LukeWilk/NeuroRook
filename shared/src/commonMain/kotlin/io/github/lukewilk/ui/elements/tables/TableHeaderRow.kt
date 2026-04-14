package io.github.lukewilk.ui.elements.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal fun shouldUseWeightedHeaderCell(cellModifier: Modifier?): Boolean =
    cellModifier == null || cellModifier == Modifier

internal fun resolvedHeaderCellModifier(cellModifier: Modifier?, weightedCellModifier: Modifier): Modifier =
    when {
        cellModifier == null -> weightedCellModifier
        cellModifier == Modifier -> weightedCellModifier
        else -> cellModifier
    }

@Composable
fun TableHeaderRow(
    headers: List<String>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    cellModifiers: List<Modifier> = emptyList()
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        headers.forEachIndexed { index, header ->
            val cellModifier = cellModifiers.getOrNull(index)
            Box(
                modifier = resolvedHeaderCellModifier(cellModifier, Modifier.weight(1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = header,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

