package app.kin.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface KinDao {
    @Query("SELECT * FROM tasks ORDER BY (completed OR skipped), priority DESC, scheduledAt")
    fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: Long): Task?
    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<Task>
    @Insert suspend fun insertTask(task: Task): Long
    @Update suspend fun updateTask(task: Task)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)
    @Query("SELECT * FROM subtasks ORDER BY taskId, position, id") fun subtasks(): Flow<List<Subtask>>
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id") suspend fun steps(taskId: Long): List<Subtask>
    @Query("SELECT * FROM subtasks WHERE id = :id") suspend fun step(id: Long): Subtask?
    @Insert suspend fun insertStep(step: Subtask): Long
    @Update suspend fun updateStep(step: Subtask)
    @Query("DELETE FROM subtasks WHERE id = :id") suspend fun deleteStep(id: Long)
    @Query("SELECT * FROM goals ORDER BY id DESC") fun goals(): Flow<List<PersonalGoal>>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: Long): PersonalGoal?
    @Insert suspend fun insertGoal(goal: PersonalGoal): Long
    @Update suspend fun updateGoal(goal: PersonalGoal)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: Long)
    @Query("UPDATE tasks SET goalId = NULL WHERE goalId = :id") suspend fun unlinkGoal(id: Long)
    @Query("SELECT * FROM companion WHERE id = 1") fun companion(): Flow<CompanionState?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun initCompanion(companion: CompanionState)
    @Query("UPDATE companion SET xp = xp + :amount WHERE id = 1") suspend fun award(amount: Int)
    @Query("SELECT * FROM focus_sessions WHERE ended = 0 ORDER BY id DESC LIMIT 1")
    fun activeFocus(): Flow<FocusSession?>
    @Query("SELECT * FROM focus_sessions WHERE ended = 0 ORDER BY id DESC LIMIT 1")
    suspend fun currentFocus(): FocusSession?
    @Query("SELECT * FROM focus_sessions WHERE id = :id") suspend fun session(id: Long): FocusSession?
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completed = 1") fun sessionCount(): Flow<Int>
    @Insert suspend fun insertSession(session: FocusSession): Long
    @Update suspend fun updateSession(session: FocusSession)
    @Query("SELECT * FROM focus_sessions ORDER BY id DESC LIMIT 1") fun latestFocus(): Flow<FocusSession?>
    @Query("SELECT * FROM break_sessions WHERE phase IN ('RUNNING','PAUSED','DUE','FOCUSING') ORDER BY id DESC LIMIT 1")
    fun activeBreak(): Flow<BreakSession?>
    @Query("SELECT * FROM break_sessions WHERE phase IN ('RUNNING','PAUSED','DUE','FOCUSING') ORDER BY id DESC LIMIT 1")
    suspend fun currentBreak(): BreakSession?
    @Query("SELECT * FROM break_sessions WHERE id = :id") suspend fun breakSession(id: Long): BreakSession?
    @Query("SELECT * FROM break_sessions WHERE focusSessionId = :id AND phase = 'FOCUSING'") suspend fun breakForFocus(id: Long): BreakSession?
    @Insert suspend fun insertBreak(value: BreakSession): Long
    @Update suspend fun updateBreak(value: BreakSession)
    @Query("SELECT * FROM messages ORDER BY id") fun messages(): Flow<List<ConversationMessage>>
    @Query("SELECT * FROM messages ORDER BY id DESC LIMIT 1") suspend fun lastMessage(): ConversationMessage?
    @Insert suspend fun insertMessage(message: ConversationMessage)
    @Query("DELETE FROM messages") suspend fun clearMessages()
    @Query("SELECT * FROM achievements ORDER BY createdAt DESC") fun achievements(): Flow<List<Achievement>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAchievement(achievement: Achievement)
    @Query("DELETE FROM achievements WHERE `key` = :key") suspend fun deleteAchievement(key: String)
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<PersonalGoal>
    @Query("SELECT * FROM subtasks") suspend fun allSteps(): List<Subtask>
    @Query("SELECT * FROM companion WHERE id = 1") suspend fun companionSnapshot(): CompanionState?
    @Query("SELECT * FROM focus_sessions") suspend fun allSessions(): List<FocusSession>
    @Query("SELECT * FROM break_sessions") suspend fun allBreaks(): List<BreakSession>
    @Query("SELECT * FROM messages") suspend fun allMessages(): List<ConversationMessage>
    @Query("SELECT * FROM achievements") suspend fun allAchievements(): List<Achievement>
    @Query("DELETE FROM break_sessions") suspend fun clearBreaks()
    @Query("DELETE FROM subtasks") suspend fun clearSteps()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM focus_sessions") suspend fun clearSessions()
    @Query("DELETE FROM achievements") suspend fun clearAchievements()
    @Query("DELETE FROM companion") suspend fun clearCompanion()
}

@Database(entities = [Task::class, PersonalGoal::class, CompanionState::class,
    FocusSession::class, ConversationMessage::class, Achievement::class, Subtask::class, BreakSession::class], version = 3, exportSchema = true)
abstract class KinDatabase : RoomDatabase() {
    abstract fun dao(): KinDao
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS break_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER, taskTitle TEXT NOT NULL, goalTitle TEXT NOT NULL, durationMillis INTEGER NOT NULL, remainingMillis INTEGER NOT NULL, startedElapsed INTEGER NOT NULL, bootCount INTEGER NOT NULL, dueAtWall INTEGER NOT NULL, phase TEXT NOT NULL, snoozeCount INTEGER NOT NULL, revision INTEGER NOT NULL, focusSessionId INTEGER, alertedRevision INTEGER NOT NULL, FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_break_sessions_taskId ON break_sessions(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_break_sessions_focusSessionId ON break_sessions(focusSessionId)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatRule TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatAnchorDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatAnchorAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatTime TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatZone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN nextOccurrenceId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS subtasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_subtasks_taskId ON subtasks(taskId)")
            }
        }
    }
}
