package app.kin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kin.data.UserPreferences
import app.kin.domain.Evolution
import app.kin.features.KinViewModel

/** Short recovery path for people who left an older version's seven-step profile unfinished. */
@Composable
fun Onboarding(vm: KinViewModel, pref: UserPreferences) {
    var personalize by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(pref.name) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Welcome to your little clearing", style = MaterialTheme.typography.headlineMedium)
        CompanionArt(Evolution.EGG, pref.reducedMotion, Modifier.size(180.dp), CreatureMood.WAVING)
        Text("Take a scrolling break or focus on one small task. Your companion keeps time with you. Everything works offline.")
        TextButton(onClick = { personalize = !personalize }) { Text("Name your companion (optional)") }
        if (personalize) OutlinedTextField(name, { name = it.take(32) }, label = { Text("Companion name") }, singleLine = true)
        Button(onClick = { vm.savePreferences(pref.copy(onboarded = true, name = name)) }, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
        Text("You can add tasks, goals and personal details later in the app.", style = MaterialTheme.typography.bodySmall)
    }
}
