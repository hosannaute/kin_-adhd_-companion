package app.kin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import app.kin.features.KinViewModel
import app.kin.domain.TaskEntry
import app.kin.domain.TaskEntryAction
import app.kin.features.PipFocusState
import app.kin.domain.BreakEntry
import kotlinx.coroutines.flow.first

private data class Destination(val route: String, val title: String, val icon: ImageVector)

@Composable
fun KinApp(vm: KinViewModel, requestedRoute: String?, requestId: Int = 0, taskEntry: TaskEntry? = null,
           compact: State<Boolean>, timer: State<PipFocusState>, enterPip: () -> Unit, pipSettings: () -> Unit,
           breakEntry: BreakEntry? = null) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val permissionsReviewed by vm.permissionSetupReviewed.collectAsStateWithLifecycle()
    val pref = preferences
    KinTheme(pref?.darkMode ?: false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (pref == null || permissionsReviewed == null) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            } else if (permissionsReviewed != true) {
                PermissionSetupScreen(vm) { vm.action { vm.app.preferences.markPermissionSetupReviewed() } }
            } else if (!pref.onboarded) {
                Onboarding(vm, pref)
            } else {
                val nav = rememberNavController()
                var handledRequest by rememberSaveable { mutableIntStateOf(-1) }
                val snackbar = remember { SnackbarHostState() }
                val error by vm.error.collectAsStateWithLifecycle()
                LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
                val destinations = listOf(Destination("home", "Home", Icons.Outlined.Home),
                    Destination("tasks", "Tasks", Icons.Outlined.CheckCircle),
                    Destination("focus", "Focus", Icons.Outlined.Timer),
                    Destination("world", "Companion", Icons.Outlined.AutoAwesome))
                val entry by nav.currentBackStackEntryAsState()
                fun go(route: String) { nav.navigate(route) { launchSingleTop = true; popUpTo("home") { saveState = true }; restoreState = true } }
                Box(Modifier.fillMaxSize()) {
                    // Keep the NavHost installed while pinned so expanding returns to the same route.
                    Scaffold(modifier = if (compact.value) Modifier.size(0.dp).clearAndSetSemantics { } else Modifier.fillMaxSize(),
                        snackbarHost = { if (!compact.value) SnackbarHost(snackbar) }, bottomBar = {
                        if (!compact.value) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            destinations.forEach { dest ->
                                NavigationBarItem(selected = entry?.destination?.route == dest.route,
                                    onClick = { go(dest.route) }, icon = { Icon(dest.icon, dest.title) },
                                    label = { Text(dest.title) })
                            }
                        }
                    }) { padding ->
                        NavHost(nav, startDestination = "home",
                            modifier = Modifier.padding(padding)) {
                            composable("home") { HomeScreen(vm, pref, { go("tasks") }, { go("focus") }, { go("more") }) }
                            composable("tasks") { TasksScreen(vm, { go("focus") },
                                taskEntry?.takeIf { it.action == TaskEntryAction.OPTIONS }?.taskId, requestId) }
                            composable("world") { Column { TextButton(onClick = { go("chat") }) { Text("Talk to your companion") }; Box(Modifier.weight(1f)) { WorldScreen(vm, pref) } } }
                            composable("chat") { ChatScreen(vm, pref) }
                            composable("more") { SettingsScreen(vm, pref, { go("focus") }) }
                            composable("focus") { if (!compact.value) FocusScreen(vm, pref, timer.value, enterPip, pipSettings) }
                        }
                        LaunchedEffect(requestedRoute, requestId) {
                            requestedRoute?.takeIf { it in listOf("tasks", "focus", "more") }?.let {
                                // Scaffold subcomposes its content during layout. Wait for NavHost
                                // to install its graph rather than racing the first layout pass.
                                nav.currentBackStackEntryFlow.first()
                                if (handledRequest != requestId) {
                                    handledRequest = requestId
                                    if (taskEntry?.action == TaskEntryAction.START) vm.reminderStart(taskEntry.taskId)
                                    go(it)
                                }
                            }
                        }
                    }
                    if (compact.value) FocusPipContent(vm, pref, timer)
                    else BreakCycleHost(vm, pref, breakEntry, requestId, { go("focus") })
                }
            }
        }
    }
}
