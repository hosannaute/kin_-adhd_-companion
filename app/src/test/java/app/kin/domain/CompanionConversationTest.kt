package app.kin.domain

import org.junit.Assert.*
import org.junit.Test

class CompanionConversationTest {
    private val engine = CompanionConversation()
    private val context = ConversationContext("Read lecture notes", "becoming an engineer", 3)
    @Test fun overwhelmUsesCurrentTask() { assertTrue(engine.reply("I'm overwhelmed", context).contains("Read lecture notes")) }
    @Test fun scrollingUsesPersonalGoal() { assertTrue(engine.reply("I can't stop scrolling", context).contains("becoming an engineer")) }
    @Test fun fatigueOffersRest() { assertTrue(engine.reply("I'm exhausted", context).contains("Rest counts")) }
    @Test fun progressResponseUsesActualCompletedCount() { assertTrue(engine.reply("Have I made progress?", context).contains("3 adventures")) }
    @Test fun emotionalResponseDisclosesItsLimits() { assertTrue(engine.reply("I'm sad", context).contains("not a mental health professional")) }
    @Test fun agreementContinuesTheConversation() { assertTrue(engine.reply("yes", context, "Would you like to start?").contains("two minutes")) }
    @Test fun emptyContextStillOffersAnAction() {
        val response = engine.reply("hello", ConversationContext(null, null, 0))
        assertTrue(response.contains("your task")); assertFalse(response.contains("null"))
    }
    @Test fun breakdownReferencesTheActualTask() {
        val steps = engine.steps("wash clothes")
        assertEquals(3, steps.size); assertTrue(steps.first().contains("wash clothes"))
    }
}
