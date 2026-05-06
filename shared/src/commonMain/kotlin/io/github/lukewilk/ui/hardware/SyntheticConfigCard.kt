package io.github.lukewilk.ui.hardware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text as M3Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.ui.elements.buttons.SecondaryButton
import io.github.lukewilk.ui.elements.cards.PanelCard
import io.github.lukewilk.ui.elements.text.SectionTitle
import io.github.lukewilk.ui.elements.layout.VerticalSpacer

/**
 * Small configuration card for synthetic waveform generator.
 * Shows existing waves and allows adding/removing and toggling enabled state.
 */
@Composable
fun SyntheticConfigCard(
    waveSpecs: List<WaveSpec>,
    onAddWave: (WaveSpec) -> Unit,
    onRemoveWave: (Int) -> Unit,
    onToggleEnabled: (Int, Boolean) -> Unit,
    onEditWave: (Int, WaveSpec) -> Unit
) {
    PanelCard {
        SectionTitle("Synthetic Signal Configuration")
        VerticalSpacer(12.dp)

        var editingIndex by remember { mutableStateOf<Int?>(null) }
        var editingWave by remember { mutableStateOf<WaveSpec?>(null) }

        if (waveSpecs.isEmpty()) {
            Text(
                text = "No synthetic waves configured. Add a wave to start generating a signal.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpacer(12.dp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                waveSpecs.forEachIndexed { idx, wave ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (wave.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (wave.enabled) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // Left: descriptive column
                            Column(modifier = Modifier.fillMaxWidth(0.72f)) {
                                val labelPart = if (wave.label.isNotBlank()) wave.label else "Wave ${idx + 1}"
                                M3Text(text = labelPart, color = if (wave.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.size(4.dp))
                                val desc = "[${wave.type}] — ${wave.amplitude} A, ${wave.frequencyHz} Hz"
                                Text(desc, color = if (wave.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.size(6.dp))
                                // Small status marker keeps enabled state visible at a glance.
                                Box(modifier = Modifier.size(10.dp).background(color = if (wave.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(6.dp)))
                            }
                            // Right: actions
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                // enabled toggle: show Play or Stop icon
                                IconButton(onClick = { onToggleEnabled(idx, !wave.enabled) }) {
                                    Icon(imageVector = if (wave.enabled) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = if (wave.enabled) "Disable" else "Enable")
                                }
                                IconButton(onClick = { editingIndex = idx; editingWave = wave }) {
                                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { onRemoveWave(idx) }) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
                VerticalSpacer(8.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // New waves default to disabled
            SecondaryButton(onClick = { onAddWave(WaveSpec()) }, text = "Add Wave")
        }

        // Edit dialog
        if (editingIndex != null && editingWave != null) {
            EditWaveDialog(
                initial = editingWave!!,
                onConfirm = { updated ->
                    onEditWave(editingIndex!!, updated)
                    editingIndex = null
                    editingWave = null
                },
                onDismiss = {
                    editingIndex = null
                    editingWave = null
                }
            )
        }
    }
}

@Composable
private fun EditWaveDialog(
    initial: WaveSpec,
    onConfirm: (WaveSpec) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember { mutableStateOf(initial.enabled) }
    var label by remember { mutableStateOf(initial.label) }
    var amplitudeText by remember { mutableStateOf(initial.amplitude.toString()) }
    var frequencyText by remember { mutableStateOf(initial.frequencyHz.toString()) }
    var phaseText by remember { mutableStateOf(initial.phaseShiftRad.toString()) }
    var type by remember { mutableStateOf(initial.type) }

    // validation state
    var amplitudeError by remember { mutableStateOf<String?>(null) }
    var frequencyError by remember { mutableStateOf<String?>(null) }
    var phaseError by remember { mutableStateOf<String?>(null) }

    fun validateAll(): Boolean {
        amplitudeError = if (amplitudeText.toDoubleOrNull() == null || amplitudeText.toDouble() < 0.0) "Invalid amplitude" else null
        frequencyError = if (frequencyText.toDoubleOrNull() == null || frequencyText.toDouble() <= 0.0) "Invalid frequency" else null
        phaseError = if (phaseText.toDoubleOrNull() == null) "Invalid phase" else null
        return amplitudeError == null && frequencyError == null && phaseError == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // explicitly set dialog container color to the theme surface to avoid picking up
        // unexpected system-derived window background colors
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit Wave") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Name") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    androidx.compose.material3.Text("Enabled:")
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                }
                VerticalSpacer(8.dp)
                TextField(
                    value = amplitudeText,
                    onValueChange = { amplitudeText = it },
                    label = { Text("Amplitude") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                amplitudeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextField(
                    value = frequencyText,
                    onValueChange = { frequencyText = it },
                    label = { Text("Frequency (Hz)") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                frequencyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextField(
                    value = phaseText,
                    onValueChange = { phaseText = it },
                    label = { Text("Phase (rad)") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                phaseError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Column {
                    Text("Type:")
                    // nicer exposed dropdown (material3) wrapped in our shared DropdownMenu helper
                    val options = WaveType.values().map { it.name }
                    io.github.lukewilk.ui.elements.forms.DropdownMenu(
                        items = options,
                        selected = type.name,
                        onSelected = { sel -> type = WaveType.valueOf(sel) },
                        label = "Type",
                        modifier = Modifier.widthIn(min = 200.dp, max = 360.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (validateAll()) {
                    val amp = amplitudeText.toDouble()
                    val freq = frequencyText.toDouble()
                    val phase = phaseText.toDouble()
                    onConfirm(initial.copy(enabled = enabled, amplitude = amp, frequencyHz = freq, phaseShiftRad = phase, type = type, label = label))
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

