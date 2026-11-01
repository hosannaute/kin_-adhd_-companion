package app.kin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val description: String = "", val category: String = "Personal",
    val scheduledAt: Long = System.currentTimeMillis(), val minutes: Int = 15,
    val priority: Int = 1, val reminder: Boolean = false, val goalId: Long? = null,
    val completed: Boolean = false, val rewarded: Boolean = false, val snoozeCount: Int = 0
)

@Entity(tableName = "goals")
data class PersonalGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val why: String, val achieved: Boolean = false, val rewarded: Boolean = false
)

@Entity(tableName = "companion")
data class CompanionState(@PrimaryKey val id: Int = 1, val xp: Int = 0)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null, val taskTitle: String = "A little time for you",
    val durationMillis: Long, val remainingMillis: Long, val startedElapsed: Long,
    val bootCount: Int, val running: Boolean = true,
    val ended: Boolean = false, val completed: Boolean = false
)

@Entity(tableName = "messages")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String, val fromUser: Boolean, val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val key: String, val title: String,
    val createdAt: Long = System.currentTimeMillis()
)
