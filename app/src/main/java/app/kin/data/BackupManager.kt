package app.kin.data

import android.app.NotificationManager
import android.content.Intent
import androidx.room.withTransaction
import androidx.work.WorkManager
import app.kin.KinApplication
import app.kin.services.FloatingCompanionService
import app.kin.services.OverlayPreviewService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException

class BackupManager(private val app: KinApplication) {
    private val lock=Mutex()
    private val db get()=app.database
    private val dao get()=db.dao()
    suspend fun export(): ByteArray = withContext(Dispatchers.IO) { lock.withLock {
        val p=app.preferences.flow.first(); val t=app.timerPreferences.flow.first(); val c=app.companionMode.flow.first()
        val data=db.withTransaction { BackupSnapshot(System.currentTimeMillis(),p,t,c,dao.companionSnapshot() ?: CompanionState(),
            dao.allTasks(),dao.allGoals(),dao.allSteps(),dao.allSessions(),dao.allBreaks(),dao.allMessages(),dao.allAchievements()) }
        BackupCodec.encode(data)
    } }
    suspend fun read(input: InputStream): BackupSnapshot = withContext(Dispatchers.IO) {
        input.use {
            val out=ByteArrayOutputStream(); val chunk=ByteArray(8192)
            while (true) {
                val n=it.read(chunk); if (n<0) break
                require(out.size()+n<=BackupCodec.MAX_BYTES) { "Choose a KIN backup smaller than 8 MB." }
                out.write(chunk,0,n)
            }
            BackupCodec.decode(out.toByteArray())
        }
    }
    /** Database replacement is transactional. Fresh row IDs also invalidate old notification actions. */
    suspend fun restore(data: BackupSnapshot): String = withContext(Dispatchers.IO) { lock.withLock {
        BackupCodec.validate(data)
        var remindersEnabled=app.preferences.flow.first().reminders
        // Finish the commit and its cleanup even if the user navigates away during restoration.
        withContext(NonCancellable) {
            val remapped=db.withTransaction {
                require(dao.currentFocus()==null && dao.currentBreak()==null) { "End the current break or focus session before restoring a backup." }
                dao.clearBreaks();dao.clearSteps();dao.clearTasks();dao.clearGoals();dao.clearSessions();dao.clearMessages();dao.clearAchievements();dao.clearCompanion()
                val goals=data.goals.associate { it.id to dao.insertGoal(it.copy(id=0)) }
                val tasks=data.tasks.associate { it.id to dao.insertTask(it.copy(id=0,goalId=goals[it.goalId],nextOccurrenceId=null)) }
                data.tasks.forEach { dao.updateTask(it.copy(id=tasks.getValue(it.id),goalId=goals[it.goalId],nextOccurrenceId=tasks[it.nextOccurrenceId])) }
                data.steps.forEach { dao.insertStep(it.copy(id=0,taskId=tasks.getValue(it.taskId))) }
                val sessions=data.sessions.associate { old -> old.id to dao.insertSession(old.copy(id=0,taskId=tasks[old.taskId],
                    running=false,ended=true,startedElapsed=0,bootCount=-1)) }
                data.breaks.forEach { old -> dao.insertBreak(old.copy(id=0,taskId=tasks[old.taskId],focusSessionId=sessions[old.focusSessionId],
                    phase=if (old.phase in listOf("RUNNING","PAUSED","DUE","FOCUSING")) "STOPPED" else old.phase,
                    startedElapsed=0,bootCount=-1)) }
                data.messages.forEach { dao.insertMessage(it.copy(id=0)) }
                val originalKeys=data.achievements.map { it.key }.toSet(); val used=mutableSetOf<String>()
                data.achievements.forEach { old ->
                    val prefix=old.key.substringBefore(':'); val number=old.key.substringAfter(':',"").toLongOrNull()
                    val mapped=when(prefix) { "task" -> tasks[number]; "goal" -> goals[number]; "focus" -> sessions[number]; else -> null }
                    var key=if (mapped!=null) "$prefix:$mapped" else old.key
                    if (mapped==null && prefix in listOf("task","goal","focus")) {
                        var suffix=0
                        do { key="archived:${suffix++}:${old.key}" } while (key in originalKeys || key in used)
                    }
                    require(used.add(key)) { "Duplicate achievement in backup." }
                    dao.insertAchievement(old.copy(key=key))
                }
                dao.initCompanion(data.companion)
                data.timer.copy(taskId=tasks[data.timer.taskId] ?: 0,goalId=goals[data.timer.goalId] ?: 0)
            }
            // Old timer snapshots and their work must not run against restored content.
            val companionIntent=Intent(app,FloatingCompanionService::class.java)
            app.stopService(companionIntent)
            val previewIntent=Intent(app,OverlayPreviewService::class.java)
            app.stopService(previewIntent)
            WorkManager.getInstance(app).cancelAllWork().result.get()
            app.getSystemService(NotificationManager::class.java).cancelAll()
            var settingsSaved=true
            try {
                app.completions.clear()
                app.preferences.save(data.preferences.copy(onboarded=true))
                remindersEnabled=data.preferences.reminders
                app.timerPreferences.restore(remapped)
                app.companionMode.restore(data.companionMode)
            } catch (_: IOException) { settingsSaved=false }
            // Restore future reminders only. Old overdue tasks stay visible without an alert flood.
            if (remindersEnabled) dao.allTasks().filter { it.scheduledAt>System.currentTimeMillis() }.forEach(app.scheduler::task)
            if (settingsSaved) "Backup restored. Review permissions on this phone. App-time check-ins stay off until you enable them."
            else "Your data was restored. Some settings could not be saved; review them in Settings."
        }
    } }
}
