package app.kin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun MinutesPicker(value: String, onChange: (String) -> Unit, presets: List<Int>, label: String, maxMinutes: Int = 180) {
    var custom by rememberSaveable { mutableStateOf(value.toIntOrNull() !in presets) }
    Text(label, style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    Text(label, style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("choice:$label")) { Text(selected) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEach { (id,title) -> DropdownMenuItem(text = { Text(title) },
                modifier = Modifier.testTag("option:$label:$id"), onClick = { onSelect(id); open = false }) }
        }
    }
}
