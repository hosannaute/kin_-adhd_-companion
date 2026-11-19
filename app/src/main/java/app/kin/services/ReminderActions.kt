package app.kin.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import app.kin.KinApplication
import kotlinx.coroutines.CancellationException

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", 0)
        if (intent.action != SNOOZE || id <= 0) return
        // Persist the action rather than doing database I/O within a receiver's time limit.
        WorkManager.getInstance(context).enqueueUniqueWork("snooze-action:$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SnoozeActionWorker>().setInputData(workDataOf("taskId" to id)).build())
    }
    companion object { const val SNOOZE = "app.kin.SNOOZE_TASK" }
}

class SnoozeActionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("taskId", 0)
        if (id <= 0) return Result.success()
        return try {
            (applicationContext as KinApplication).repository.snooze(id, 10)
            Result.success()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
