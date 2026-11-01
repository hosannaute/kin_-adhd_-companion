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
    @Query("SELECT * FROM messages ORDER BY id") fun messages(): Flow<List<ConversationMessage>>
    @Query("SELECT * FROM messages ORDER BY id DESC LIMIT 1") suspend fun lastMessage(): ConversationMessage?
    @Insert suspend fun insertMessage(message: ConversationMessage)
    @Query("DELETE FROM messages") suspend fun clearMessages()
    @Query("SELECT * FROM achievements ORDER BY createdAt DESC") fun achievements(): Flow<List<Achievement>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAchievement(achievement: Achievement)
    @Query("DELETE FROM achievements WHERE `key` = :key") suspend fun deleteAchievement(key: String)
}

@Database(entities = [Task::class, PersonalGoal::class, CompanionState::class,
    FocusSession::class, ConversationMessage::class, Achievement::class, Subtask::class], version = 2, exportSchema = true)
abstract class KinDatabase : RoomDatabase() {
    abstract fun dao(): KinDao
    companion object {
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
