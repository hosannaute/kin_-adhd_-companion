package app.kin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.UserPreferences
import app.kin.domain.Evolution
import app.kin.features.KinViewModel
import app.kin.features.PipFocusState

@Composable
fun FocusPipContent(vm: KinViewModel, pref: UserPreferences, timerState: State<PipFocusState>) {
    val timer = timerState.value
    val companion by vm.companion.collectAsStateWithLifecycle()
    val session = timer.session
    val finished = session?.ended == true
    val seconds = (timer.remainingMillis.coerceAtLeast(0) + 999) / 1000
    Box(Modifier.fillMaxSize().background(Color(0xFF14243C)), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9).testTag("focus_pip")) {
            val timerSize = (maxHeight.value * .23f).coerceIn(20f, 44f).sp
            Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanionArt(Evolution.at(companion?.xp ?: 0), pref.reducedMotion,
                    Modifier.weight(.36f).fillMaxHeight(), if (session?.completed == true) CreatureMood.CELEBRATING else CreatureMood.SLEEPING)
                Column(Modifier.weight(.64f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(session?.taskTitle ?: "A little time for you", color = Color(0xFFE4EDF8), fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (finished) if (session?.completed == true) "Complete" else "Ended"
                        else "%02d:%02d".format(seconds / 60, seconds % 60),
                        color = Color.White, fontSize = timerSize, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(timer.message ?: when {
                        session?.completed == true -> "+15 XP · Well done"
                        finished -> "Saved · No XP"
                        session?.running == false -> "Paused"
                        else -> "Focus with ${pref.name}"
                    }, color = Color(0xFFCCC3F1), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
