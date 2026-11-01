package app.kin.ui

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
}
