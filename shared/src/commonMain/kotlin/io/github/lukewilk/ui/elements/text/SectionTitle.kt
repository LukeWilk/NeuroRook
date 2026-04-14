package io.github.lukewilk.ui.elements.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal fun sectionTitleFontSizeSp(): Int = 14

internal fun sectionTitleFontWeight(): FontWeight = FontWeight.Medium

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = sectionTitleFontSizeSp().sp,
        fontWeight = sectionTitleFontWeight(),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}
