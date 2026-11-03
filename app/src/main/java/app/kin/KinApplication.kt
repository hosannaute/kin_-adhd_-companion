package app.kin

import android.app.Application
import androidx.room.Room
import app.kin.data.*
import app.kin.services.Notifications
import app.kin.services.Scheduler

class KinApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, KinDatabase::class.java, "kin.db").addMigrations(KinDatabase.MIGRATION_1_2, KinDatabase.MIGRATION_2_3).build() }
    val preferences by lazy { Preferences(this) }
    val companionMode by lazy { CompanionModePreferences(this) }
    val timerPreferences by lazy { TimerPreferences(this) }
    val scheduler by lazy { Scheduler(this) }
    val repository by lazy { KinRepository(this, database, scheduler) }
    val backups by lazy { BackupManager(this) }
    val breaks by lazy { BreakRepository(this, database, scheduler, repository) }
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
