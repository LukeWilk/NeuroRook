package io.github.lukewilk.ui.elements.buttons

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val ButtonShape = RoundedCornerShape(8.dp)

@Composable
internal fun ButtonLabel(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold)
}

