package io.github.lukewilk.ui.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import io.github.lukewilk.ui.elements.cards.CardHeader
import io.github.lukewilk.ui.elements.cards.PanelCard
import io.github.lukewilk.ui.elements.layout.VerticalSpacer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SystemLogCard(
    logs: List<SystemLogEntry>,
    modifier: Modifier = Modifier,
    onCopyAllLogs: ((String) -> Unit)? = null
) {
    val isEmpty = logs.isEmpty()
    val newestFirstLogs = remember(logs) { logs.asReversed() }
    val scrollState = rememberScrollState()
    val copyAllLogs = onCopyAllLogs ?: rememberClipboardCopyHandler()

    LaunchedEffect(newestFirstLogs.size) {
        if (newestFirstLogs.isNotEmpty()) {
            scrollState.scrollTo(0)
        }
    }

    PanelCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardHeader(
                icon = "\uD83D\uDCC4",
                iconColor = MaterialTheme.colorScheme.tertiary,
                title = "System Log"
            )
            if (!isEmpty) {
                TextButton(onClick = { copyAllLogs(systemLogClipboardText(logs)) }) {
                    Text("Copy all")
                }
            }
        }
        VerticalSpacer(12.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    color = if (isEmpty) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .heightIn(min = 120.dp, max = 240.dp)
                .defaultMinSize(minHeight = 120.dp)
        ) {
            if (isEmpty) {
                Text(
                    "Waiting for activity...",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontStyle = FontStyle.Italic
                )
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        newestFirstLogs.forEach { log ->
                            Text(
                                text = systemLogLineText(log),
                                color = severityColorFor(MaterialTheme.colorScheme, log.severity),
                                fontSize = 13.sp,
                                fontFamily = systemLogFontFamily,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val systemLogFontFamily: FontFamily = FontFamily.Monospace

internal fun systemLogLineText(log: SystemLogEntry): String =
    "${formatLogTimestamp(log.timestampEpochMillis)} • ${log.severity.name} • ${log.message}"

internal fun systemLogClipboardText(logs: List<SystemLogEntry>): String =
    logs.asReversed().joinToString(separator = "\n", transform = ::systemLogLineText)

@Suppress("DEPRECATION")
@Composable
private fun rememberClipboardCopyHandler(): (String) -> Unit {
    val clipboardManager = LocalClipboardManager.current
    return remember(clipboardManager) {
        { copiedText: String -> clipboardManager.setText(AnnotatedString(copiedText)) }
    }
}

internal fun severityColorFor(colorScheme: ColorScheme, severity: SystemLogSeverity): Color = when (severity) {
    SystemLogSeverity.INFO -> colorScheme.primary
    SystemLogSeverity.WARN -> colorScheme.tertiary
    SystemLogSeverity.ERROR -> colorScheme.error
}

private val logTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatLogTimestamp(timestampEpochMillis: Long): String = logTimeFormatter.format(
    Instant.ofEpochMilli(timestampEpochMillis).atZone(ZoneId.systemDefault())
)

