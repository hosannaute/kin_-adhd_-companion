package app.kin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinutesPicker(value: String, onChange: (String) -> Unit, presets: List<Int>, label: String, maxMinutes: Int = 180) {
    var custom by rememberSaveable { mutableStateOf(value.toIntOrNull() !in presets) }
    Text(label, style = MaterialTheme.typography.titleSmall)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { amount -> FilterChip(!custom && value == amount.toString(), { custom = false; onChange(amount.toString()) }, label = { Text("$amount m") }) }
        FilterChip(custom, { custom = true }, label = { Text("Custom") })
    }
    if (custom) OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(maxMinutes.toString().length)) },
        label = { Text("$label (1–$maxMinutes)") }, singleLine = true,
        isError = value.toIntOrNull() !in 1..maxMinutes, supportingText = { Text("Choose 1–$maxMinutes minutes") })
}

@Composable
fun ChoiceList(label: String, selected: String, choices: List<Pair<String,String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(onClick = { open = true }, shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().testTag("choice:$label")) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected, style = MaterialTheme.typography.bodyLarge)
                }
                Icon(Icons.Outlined.ExpandMore,null,Modifier.padding(start = 8.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEach { (id,title) -> DropdownMenuItem(text = { Text(title) },
                modifier = Modifier.testTag("option:$label:$id"), onClick = { onSelect(id); open = false }) }
        }
    }
}
