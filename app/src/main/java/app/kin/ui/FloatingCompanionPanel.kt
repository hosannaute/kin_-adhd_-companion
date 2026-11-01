package app.kin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kin.domain.Evolution
import kotlin.math.roundToInt

@Composable
// The overlay window wraps its content; containerSize is not the available screen size here.
@android.annotation.SuppressLint("ConfigurationScreenWidthHeight")
fun FloatingCompanionPanel(name: String, stage: Evolution, reduced: Boolean, expanded: Boolean,
    message: String, onToggle: () -> Unit, onDrag: (Int, Int) -> Unit, onDragEnd: () -> Unit,
    onBreak: () -> Unit, onScrolling: () -> Unit, onGoals: () -> Unit, onStop: () -> Unit) {
    val configuration = LocalConfiguration.current
    Column(Modifier.width(if (expanded) minOf(300, configuration.screenWidthDp - 16).coerceAtLeast(160).dp else 100.dp)
        .heightIn(max = (configuration.screenHeightDp - 100).coerceAtLeast(240).dp)
        .padding(4.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        CompanionArt(stage, reduced, Modifier.size(88.dp)
            .semantics { contentDescription = "$name, floating companion. Drag to move; tap to expand or collapse." }
            .pointerInput(Unit) { detectDragGestures(onDragEnd = onDragEnd) { change, amount ->
                change.consume(); onDrag(amount.x.roundToInt(), amount.y.roundToInt())
            } }.clickable(onClick = onToggle), CreatureMood.WAVING)
        if (expanded) Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 6.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(message)
                FilledTonalButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) { Text("Taking a break · 10 min") }
                OutlinedButton(onClick = onScrolling, modifier = Modifier.fillMaxWidth()) { Text("Just scrolling · remind later") }
                Button(onClick = onGoals, modifier = Modifier.fillMaxWidth()) { Text("Return to my goals") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onToggle) { Text("Collapse") }
                    TextButton(onClick = onStop) { Text("Stop") }
                }
            }
        } else FilledTonalButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Stop") }
    }
}
