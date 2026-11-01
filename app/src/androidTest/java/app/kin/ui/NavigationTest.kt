package app.kin.ui

import android.content.Intent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.data.UserPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun allMajorScreensAreReachable() {
        val app = ApplicationProvider.getApplicationContext<KinApplication>()
        runBlocking { app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true)) }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Hello, dreamer").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Tasks", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Your adventures").assertIsDisplayed()
            compose.onNodeWithText("World", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Your little world").assertIsDisplayed()
            compose.onNodeWithText("Chat", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Offline companion · guided responses").assertIsDisplayed()
            compose.onNodeWithText("More", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Make room for you").assertIsDisplayed()
            compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
            compose.onNodeWithText("A little focus time", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("A little focus").assertIsDisplayed()
        }
    }
    @Test fun repeatedIntentsNavigateAnAlreadyRunningActivity() {
        val app = ApplicationProvider.getApplicationContext<KinApplication>()
        runBlocking { app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Hello, dreamer").fetchSemanticsNodes().isNotEmpty() }
            fun request(route: String) { scenario.onActivity { activity ->
                // Retain the launch intent's filter identity so ActivityScenario can
                // continue observing this same activity after setIntent/onNewIntent.
                activity.startActivity(Intent(activity.intent).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("route", route)
                })
            } }
            fun expectScreen(title: String) {
                compose.waitUntil(45_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(title).assertIsDisplayed()
            }
            request("tasks")
            expectScreen("Your adventures")
            request("more")
            expectScreen("Make room for you")
            compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
            request("more")
            expectScreen("Make room for you")
        }
    }
}
