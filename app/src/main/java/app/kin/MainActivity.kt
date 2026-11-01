package app.kin

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kin.features.KinViewModel
import app.kin.ui.KinApp

class MainActivity : ComponentActivity() {
    private var routeRequest by mutableStateOf<Pair<String, Int>?>(null)
    private fun acceptRoute(intent: Intent) {
        val route = intent.getStringExtra("route")?.takeIf { it in listOf("tasks", "focus", "more") } ?: return
        routeRequest = route to ((routeRequest?.second ?: 0) + 1)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptRoute(intent)
        setContent {
            val vm: KinViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = KinViewModel(application as KinApplication) as T
            })
            KinApp(vm, routeRequest?.first, routeRequest?.second ?: 0)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptRoute(intent)
    }
}
