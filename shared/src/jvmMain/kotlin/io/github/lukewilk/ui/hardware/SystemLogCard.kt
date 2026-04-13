package io.github.lukewilk.ui.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
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
fun SystemLogCard(logs: List<SystemLogEntry>, modifier: Modifier = Modifier) {
    val isEmpty = logs.isEmpty()
    val newestFirstLogs = remember(logs) { logs.asReversed() }
    val listState = rememberLazyListState()

    LaunchedEffect(newestFirstLogs.size) {
        if (newestFirstLogs.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    PanelCard(modifier = modifier) {
        CardHeader(
            icon = "\uD83D\uDCC4",
            iconColor = MaterialTheme.colorScheme.tertiary,
            title = "System Log"
        )
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = newestFirstLogs,
                        key = { "${it.timestampEpochMillis}-${it.message}" }
                    ) { log ->
                        Text(
                            text = "${formatLogTimestamp(log.timestampEpochMillis)} • ${log.severity.name} • ${log.message}",
                            color = severityColor(log.severity),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun severityColor(severity: SystemLogSeverity): Color = when (severity) {
    SystemLogSeverity.INFO -> MaterialTheme.colorScheme.primary
    SystemLogSeverity.WARN -> MaterialTheme.colorScheme.tertiary
    SystemLogSeverity.ERROR -> MaterialTheme.colorScheme.error
}

private val logTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatLogTimestamp(timestampEpochMillis: Long): String = logTimeFormatter.format(
    Instant.ofEpochMilli(timestampEpochMillis).atZone(ZoneId.systemDefault())
)

