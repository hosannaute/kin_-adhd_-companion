package app.kin

import android.app.Application
import androidx.room.Room
import app.kin.data.*
import app.kin.services.Notifications
import app.kin.services.Scheduler

class KinApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, KinDatabase::class.java, "kin.db").build() }
    val preferences by lazy { Preferences(this) }
    val scheduler by lazy { Scheduler(this) }
    val repository by lazy { KinRepository(this, database, scheduler) }
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
