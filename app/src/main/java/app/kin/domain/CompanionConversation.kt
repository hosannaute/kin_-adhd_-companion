package app.kin.domain

enum class Feeling(val label: String) {
    OVERWHELMED("Too overwhelming"), TIRED("I'm exhausted"), DISTRACTED("I can't stop scrolling"),
    UNSURE("I don't know where to begin"), UPSET("Something is bothering me")
}

data class ConversationContext(val task: String?, val goal: String?, val completed: Int)

/** Offline templates, not an AI or a mental healthcare service. */
class CompanionConversation {
    fun reply(message: String, context: ConversationContext, previous: String? = null): String {
        val words = message.lowercase()
        if (listOf("progress", "achievement", "what have i done").any(words::contains)) {
            return "You've completed ${context.completed} adventures. Small steps count, and returning counts too. What would feel manageable next?"
        }
        val feeling = when {
            listOf("overwhelm", "too much", "anxious").any(words::contains) -> Feeling.OVERWHELMED
            listOf("tired", "exhaust", "sleep").any(words::contains) -> Feeling.TIRED
            listOf("scroll", "distract", "phone").any(words::contains) -> Feeling.DISTRACTED
            listOf("sad", "upset", "bother", "lonely").any(words::contains) -> Feeling.UPSET
            else -> Feeling.UNSURE
        }
        if (previous != null && listOf("yes", "ready", "okay", "ok").contains(words.trim())) {
            return "Let's try one tiny step: ${steps(context.task ?: "your task").first()} You can stop after two minutes."
        }
        return support(feeling, context)
    }

    fun support(feeling: Feeling, context: ConversationContext): String = when (feeling) {
        Feeling.OVERWHELMED -> "It's a lot to hold at once. We can make ${context.task ?: "one task"} smaller. What's one thing you could open or put in front of you?"
        Feeling.TIRED -> "Rest counts too. You can reschedule, take a break, or try just five gentle minutes. What feels manageable?"
        Feeling.DISTRACTED -> "It's easy to get pulled in. Put your phone down for a moment, take a breath, and try two minutes${context.goal?.let { " toward $it" } ?: " on one small step"}."
        Feeling.UNSURE -> "We don't need the whole plan. ${steps(context.task ?: "your task").first()} Would you like to start there?"
        Feeling.UPSET -> "I'm here with you. Take a breath and give yourself some room. You can rest or talk to someone you trust. I'm a supportive companion, not a mental health professional."
    }

    fun steps(task: String): List<String> = listOf(
        "Gather or open what you need for $task.",
        "Choose one tiny action you can do in two minutes.",
        "Try that action, then pause and choose whether to continue."
    )
}
