package app.kin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import app.kin.data.FocusSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun allPresetsAndCustomStayWithinANarrowLayoutAtDoubleFontSize() {
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                KinTheme(false) { Column(Modifier.width(280.dp).testTag("presets")) { MinutesPicker("25",{},listOf(5,10,15,25,45),"Focus minutes") } }
            }
        }
        val root=compose.onNodeWithTag("presets").fetchSemanticsNode().boundsInRoot
        for (text in listOf("5 m","10 m","15 m","25 m","45 m","Custom")) {
            val bounds=compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left>=root.left && bounds.right<=root.right)
        }
        assertTrue(compose.onNodeWithText("Custom").fetchSemanticsNode().boundsInRoot.top>compose.onNodeWithText("5 m").fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun completionAndChoiceControlsRemainReachableWithLargeFontsOnANarrowPhone() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                KinTheme(false) { Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                    ChoiceList("Focus on","A longer task title to finish today",listOf("1" to "A longer task title to finish today")) { }
                    FocusCompletionCard(FocusSession(taskTitle = "Read a chapter",durationMillis = 60_000,remainingMillis = 0,startedElapsed = 0,bootCount = 0),{}, {})
                } }
            }
        }
        compose.onNodeWithTag("choice:Focus on").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("option:Focus on:1").performClick()
        compose.onNodeWithText("Take a break").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stop").performScrollTo().assertIsDisplayed()
    }

}
