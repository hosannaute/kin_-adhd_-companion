package app.kin.data

import app.kin.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId

data class BackupSnapshot(
    val createdAt: Long, val preferences: UserPreferences, val timer: TimerOptions,
    val companionMode: CompanionModeOptions, val companion: CompanionState,
    val tasks: List<Task>, val goals: List<PersonalGoal>, val steps: List<Subtask>,
    val sessions: List<FocusSession>, val breaks: List<BreakSession>,
    val messages: List<ConversationMessage>, val achievements: List<Achievement>
) {
    val summary get() = "${tasks.size} tasks · ${goals.size} goals · ${companion.xp} XP · ${messages.size} messages"
}

/** Explicit, versioned data format. No database files, executable content or device permissions. */
object BackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    private fun obj(vararg pairs: Pair<String,Any?>) = JSONObject().apply { pairs.forEach { (k,v) -> put(k,v ?: JSONObject.NULL) } }
    private class Budget(var bytes: Int = 0)
    private fun <T> array(values: List<T>, budget: Budget, convert: (T) -> JSONObject) = JSONArray().apply {
        values.forEach {
            val row=convert(it)
            budget.bytes += row.toString().toByteArray(Charsets.UTF_8).size+1
            require(budget.bytes<=MAX_BYTES) { "This backup exceeds the 8 MB limit." }
            put(row)
        }
    }
    fun encode(v: BackupSnapshot): ByteArray {
        validate(v)
        val budget=Budget()
        val p=v.preferences; val t=v.timer; val c=v.companionMode
        val root=obj("format" to "KIN", "version" to 1, "createdAt" to v.createdAt,
            "preferences" to obj("onboarded" to p.onboarded,"name" to p.name,"reducedMotion" to p.reducedMotion,"darkMode" to p.darkMode,
                "reminders" to p.reminders,"quietStart" to p.quietStart,"quietEnd" to p.quietEnd,"alertMode" to p.alertMode.name,
                "reminderStyle" to p.reminderStyle.name,"snoozeMinutes" to p.snoozeMinutes,"snoozeLimit" to p.snoozeLimit),
            "timer" to obj("dockRight" to t.dockRight,"verticalFraction" to t.verticalFraction.toDouble(),"breakMinutes" to t.breakMinutes,
                "focusMinutes" to t.focusMinutes,"taskId" to t.taskId,"goalId" to t.goalId),
            "companionMode" to obj("selectedApps" to JSONArray(c.selectedApps.sorted()),"thresholdMinutes" to c.thresholdMinutes,"x" to c.x,"y" to c.y),
            "xp" to v.companion.xp,
            "tasks" to array(v.tasks,budget) { x -> obj("id" to x.id,"title" to x.title,"description" to x.description,"category" to x.category,
                "scheduledAt" to x.scheduledAt,"minutes" to x.minutes,"priority" to x.priority,"reminder" to x.reminder,"goalId" to x.goalId,
                "completed" to x.completed,"rewarded" to x.rewarded,"snoozeCount" to x.snoozeCount,"repeatRule" to x.repeatRule,
                "repeatAnchorDay" to x.repeatAnchorDay,"repeatAnchorAt" to x.repeatAnchorAt,"repeatTime" to x.repeatTime,
                "repeatZone" to x.repeatZone,"skipped" to x.skipped,"nextOccurrenceId" to x.nextOccurrenceId) },
            "goals" to array(v.goals,budget) { x -> obj("id" to x.id,"title" to x.title,"why" to x.why,"achieved" to x.achieved,"rewarded" to x.rewarded) },
            "steps" to array(v.steps,budget) { x -> obj("id" to x.id,"taskId" to x.taskId,"title" to x.title,"completed" to x.completed,"position" to x.position) },
            "sessions" to array(v.sessions,budget) { x -> obj("id" to x.id,"taskId" to x.taskId,"taskTitle" to x.taskTitle,"durationMillis" to x.durationMillis,
                "remainingMillis" to x.remainingMillis,"startedElapsed" to x.startedElapsed,"bootCount" to x.bootCount,"running" to x.running,"ended" to x.ended,"completed" to x.completed) },
            "breaks" to array(v.breaks,budget) { x -> obj("id" to x.id,"taskId" to x.taskId,"taskTitle" to x.taskTitle,"goalTitle" to x.goalTitle,
                "durationMillis" to x.durationMillis,"remainingMillis" to x.remainingMillis,"startedElapsed" to x.startedElapsed,
                "bootCount" to x.bootCount,"dueAtWall" to x.dueAtWall,"phase" to x.phase,"snoozeCount" to x.snoozeCount,
                "revision" to x.revision,"focusSessionId" to x.focusSessionId,"alertedRevision" to x.alertedRevision) },
            "messages" to array(v.messages,budget) { x -> obj("id" to x.id,"text" to x.text,"fromUser" to x.fromUser,"createdAt" to x.createdAt) },
            "achievements" to array(v.achievements,budget) { x -> obj("key" to x.key,"title" to x.title,"createdAt" to x.createdAt) })
        return root.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "This backup exceeds the 8 MB limit." } }
    }
    private fun JSONObject.n(k: String): Long { val v=get(k); require(v is Int || v is Long) { "Invalid number in backup." }; return (v as Number).toLong() }
    private fun JSONObject.i(k: String): Int = n(k).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JSONObject.b(k: String): Boolean { val v=get(k); require(v is Boolean); return v }
    private fun JSONObject.s(k: String): String { val v=get(k); require(v is String && v.length<=10_000); return v }
    private fun JSONObject.nullId(k: String): Long? = if (get(k)==JSONObject.NULL) null else n(k).also { require(it>0) }
    private fun <T> JSONObject.rows(k: String, convert: (JSONObject)->T): List<T> {
        val a=getJSONArray(k); require(a.length()<=10_000) { "Too many records in backup." }
        return (0 until a.length()).map { convert(a.getJSONObject(it)) }
    }
    fun decode(bytes: ByteArray): BackupSnapshot {
        require(bytes.size<=MAX_BYTES) { "Choose a KIN backup smaller than 8 MB." }
        try {
            val root=JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.s("format")=="KIN") { "Choose a KIN backup file." }
            require(root.i("version")==1) { "This backup version is not supported by this KIN build." }
            val p=root.getJSONObject("preferences"); val t=root.getJSONObject("timer"); val c=root.getJSONObject("companionMode")
            val apps=c.getJSONArray("selectedApps"); require(apps.length()<=1_000)
            val mode=CompanionModeOptions(false,(0 until apps.length()).map { val item=apps.get(it); require(item is String);item }.toSet(),c.i("thresholdMinutes"),c.i("x"),c.i("y"))
            val pref=UserPreferences(p.b("onboarded"),p.s("name"),p.b("reducedMotion"),p.b("darkMode"),p.b("reminders"),p.i("quietStart"),p.i("quietEnd"),
                AlertMode.valueOf(p.s("alertMode")),ReminderStyle.valueOf(p.s("reminderStyle")),p.i("snoozeMinutes"),p.i("snoozeLimit"))
            val timer=TimerOptions(t.b("dockRight"),(t.get("verticalFraction") as Number).toFloat(),t.i("breakMinutes"),t.i("focusMinutes"),t.n("taskId"),t.n("goalId"))
            return BackupSnapshot(root.n("createdAt"),pref,timer,mode,CompanionState(xp=root.i("xp")),
                root.rows("tasks") { x -> Task(x.n("id"),x.s("title"),x.s("description"),x.s("category"),x.n("scheduledAt"),x.i("minutes"),x.i("priority"),x.b("reminder"),x.nullId("goalId"),x.b("completed"),x.b("rewarded"),x.i("snoozeCount"),x.s("repeatRule"),x.i("repeatAnchorDay"),x.n("repeatAnchorAt"),x.s("repeatTime"),x.s("repeatZone"),x.b("skipped"),x.nullId("nextOccurrenceId")) },
                root.rows("goals") { x -> PersonalGoal(x.n("id"),x.s("title"),x.s("why"),x.b("achieved"),x.b("rewarded")) },
                root.rows("steps") { x -> Subtask(x.n("id"),x.n("taskId"),x.s("title"),x.b("completed"),x.i("position")) },
                root.rows("sessions") { x -> FocusSession(x.n("id"),x.nullId("taskId"),x.s("taskTitle"),x.n("durationMillis"),x.n("remainingMillis"),x.n("startedElapsed"),x.i("bootCount"),x.b("running"),x.b("ended"),x.b("completed")) },
                root.rows("breaks") { x -> BreakSession(x.n("id"),x.nullId("taskId"),x.s("taskTitle"),x.s("goalTitle"),x.n("durationMillis"),x.n("remainingMillis"),x.n("startedElapsed"),x.i("bootCount"),x.n("dueAtWall"),x.s("phase"),x.i("snoozeCount"),x.i("revision"),x.nullId("focusSessionId"),x.i("alertedRevision")) },
                root.rows("messages") { x -> ConversationMessage(x.n("id"),x.s("text"),x.b("fromUser"),x.n("createdAt")) },
                root.rows("achievements") { x -> Achievement(x.s("key"),x.s("title"),x.n("createdAt")) }).also(::validate)
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("backup version")==true || e.message?.startsWith("Choose a KIN")==true) throw e
            throw IllegalArgumentException("This backup is invalid or incomplete. Your current data is unchanged.",e)
        } catch (e: Exception) { throw IllegalArgumentException("This backup is invalid or incomplete. No data has been changed.",e) }
    }
    fun validate(v: BackupSnapshot) {
        fun <T> ids(rows: List<T>, id: (T)->Long): Set<Long> = rows.map(id).also { require(it.size<=10_000 && it.all { n -> n>0 } && it.distinct().size==it.size) { "Invalid or duplicate IDs in backup." } }.toSet()
        fun text(s: String, required: Boolean=false) { require(s.length<=10_000 && (!required || s.isNotBlank())) { "Invalid text in backup." } }
        fun time(n: Long) { require(n in -2_208_988_800_000L..32_503_680_000_000L) { "Invalid date in backup." } }
        val tasks=ids(v.tasks) { it.id }; val goals=ids(v.goals) { it.id }; val sessions=ids(v.sessions) { it.id }
        ids(v.steps) { it.id }; ids(v.breaks) { it.id }; ids(v.messages) { it.id }
        require(v.tasks.size+v.goals.size+v.steps.size+v.sessions.size+v.breaks.size+v.messages.size+v.achievements.size<=50_000)
        time(v.createdAt); require(v.companion.id==1 && v.companion.xp>=0)
        val budget=Budget()
        val p=v.preferences; require(p.name.isNotBlank() && p.name.length<=32 && p.quietStart in 0..23 && p.quietEnd in 0..23 && p.snoozeMinutes in listOf(5,10,15) && p.snoozeLimit in listOf(0,1,3))
        val t=v.timer; require(t.verticalFraction.isFinite() && t.verticalFraction in 0f..1f && t.breakMinutes in 1..180 && t.focusMinutes in 1..180 && t.taskId>=0 && t.goalId>=0)
        val c=v.companionMode; require(c.thresholdMinutes in 1..180 && c.selectedApps.size<=1_000 && c.selectedApps.all { it.length in 1..255 && it.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*")) })
        v.tasks.forEach { x ->
            text(x.title,true);text(x.description);text(x.category);time(x.scheduledAt);time(x.repeatAnchorAt)
            require(x.minutes in 1..1440 && x.priority in 0..2 && x.snoozeCount>=0 && x.repeatAnchorDay in 0..31 && !(x.completed && x.skipped))
            require(x.goalId==null || x.goalId in goals) { "A task refers to a missing goal." }
            require(x.nextOccurrenceId==null || x.nextOccurrenceId>0)
            val rule=RepeatRule.valueOf(x.repeatRule)
            if (rule!=RepeatRule.NONE) { ZoneId.of(x.repeatZone); LocalTime.parse(x.repeatTime) }
        }
        v.goals.forEach { text(it.title,true);text(it.why) }
        v.steps.forEach { text(it.title,true);require(it.taskId in tasks && it.position>=0) { "A step refers to a missing task." } }
        v.sessions.forEach { x -> text(x.taskTitle,true);require(x.durationMillis in 1L..10_800_000L && x.remainingMillis in 0..x.durationMillis && x.startedElapsed>=0 && (!x.completed || (x.ended && !x.running && x.remainingMillis==0L))) }
        v.breaks.forEach { x ->
            text(x.taskTitle,true);text(x.goalTitle);time(x.dueAtWall)
            require(x.durationMillis in 1L..10_800_000L && x.remainingMillis in 0L..10_800_000L && x.startedElapsed>=0 && x.snoozeCount>=0 && x.revision>=0 && x.alertedRevision in -1..x.revision)
            BreakPhase.valueOf(x.phase)
            require(x.taskId==null || x.taskId in tasks); require(x.focusSessionId==null || x.focusSessionId in sessions)
        }
        v.messages.forEach { text(it.text);time(it.createdAt) }
        require(v.achievements.size<=10_000 && v.achievements.map { it.key }.distinct().size==v.achievements.size)
        v.achievements.forEach { text(it.key,true);text(it.title,true);time(it.createdAt) }
    }
}
