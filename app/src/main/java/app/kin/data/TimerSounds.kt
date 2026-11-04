package app.kin.data

import android.app.NotificationManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.kin.services.TimerAlertKind
import app.kin.services.TimerAlerts
import app.kin.domain.AlertMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private val Context.soundStore by preferencesDataStore("timer_sounds")
data class TimerSound(val uri: String = "", val title: String = "KIN chime", val revision: Long = 0,
                      val silent: Boolean = false, val importance: Int = NotificationManager.IMPORTANCE_DEFAULT)
data class TimerSoundChoices(val breakEnd: TimerSound = TimerSound(), val focusEnd: TimerSound = TimerSound()) {
    fun forKind(kind: TimerAlertKind) = if (kind == TimerAlertKind.BREAK) breakEnd else focusEnd
}

/** Phone-local audio is deliberately separate from portable task/progress backups. */
class TimerSounds(private val context: Context) {
    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
    private val store = context.soundStore
    private val lock = Mutex()
    private val directory get() = File(context.filesDir,"timer_sounds").apply { mkdirs() }
    private fun prefix(kind: TimerAlertKind) = kind.name.lowercase()
    private fun read(p: androidx.datastore.preferences.core.Preferences, kind: TimerAlertKind): TimerSound {
        val k = prefix(kind)
        return TimerSound(p[stringPreferencesKey("${k}_uri")] ?: "", p[stringPreferencesKey("${k}_title")] ?: "KIN chime",
            p[longPreferencesKey("${k}_revision")] ?: 0,p[booleanPreferencesKey("${k}_silent")] ?: false,
            p[intPreferencesKey("${k}_importance")] ?: NotificationManager.IMPORTANCE_DEFAULT)
    }
    val flow = store.data.map { TimerSoundChoices(read(it,TimerAlertKind.BREAK),read(it,TimerAlertKind.FOCUS)) }
    private suspend fun save(kind: TimerAlertKind, uri: String, title: String, silent: Boolean): TimerSound {
        val old = flow.first().forKind(kind)
        val manager = context.getSystemService(NotificationManager::class.java)
        val oldChannel = TimerAlerts.channel(context,kind,AlertMode.SOUND_VIBRATION,old)
        // A tone change must not bypass a channel that was blocked in Android settings.
        val importance = manager.getNotificationChannel(oldChannel)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
        lateinit var selected: TimerSound
        store.edit { p ->
            val k = prefix(kind);val revision = read(p,kind).revision + 1
            p[stringPreferencesKey("${k}_uri")] = uri;p[stringPreferencesKey("${k}_title")] = title.take(100)
            p[longPreferencesKey("${k}_revision")] = revision;p[booleanPreferencesKey("${k}_silent")] = silent
            p[intPreferencesKey("${k}_importance")] = importance
            selected = read(p,kind)
        }
        TimerAlerts.channel(context,kind,AlertMode.SOUND_VIBRATION,selected)
        cleanup()
        return selected
    }
    suspend fun builtIn(kind: TimerAlertKind) = withContext(Dispatchers.IO) { lock.withLock { save(kind,"","KIN chime",false) } }
    suspend fun silent(kind: TimerAlertKind) = withContext(Dispatchers.IO) { lock.withLock { save(kind,"","No sound",true) } }
    suspend fun import(kind: TimerAlertKind, uri: Uri, titleHint: String? = null): TimerSound = withContext(Dispatchers.IO) { lock.withLock {
        require(uri.scheme in listOf("content","android.resource")) { "Choose a phone sound or an audio file." }
        val temp = File.createTempFile("import-",".audio",directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            (context.contentResolver.openInputStream(uri) ?: throw IOException("Could not read this sound.")).use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192);var total = 0
                    while (true) {
                        val count = input.read(buffer);if (count < 0) break
                        total += count;require(total <= MAX_BYTES) { "Choose an audio file smaller than 10 MB." }
                        output.write(buffer,0,count);digest.update(buffer,0,count)
                    }
                }
            }
            val reader = MediaMetadataRetriever()
            val mime: String
            try {
                reader.setDataSource(temp.absolutePath)
                require(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" &&
                    reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes" &&
                    (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0) { "This file is not a supported audio clip." }
                mime = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/wav"
            } catch (e: RuntimeException) { throw IllegalArgumentException("This audio file could not be played. Choose an MP3, WAV, OGG or M4A clip.",e)
            } finally { reader.release() }
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.matches(Regex("[a-zA-Z0-9]+")) } ?: "audio"
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val file = File(directory,"$hash.$extension")
            if (!file.exists() && !temp.renameTo(file)) throw IOException("Could not save this sound. Try again.")
            val name = titleHint?.takeIf { it.isNotBlank() } ?: runCatching {
                context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "My sound"
            save(kind,FileProvider.getUriForFile(context,"${context.packageName}.sounds",file).toString(),name,false)
        } finally { temp.delete() }
    } }
    private suspend fun cleanup() {
        val selections = flow.first()
        val manager = context.getSystemService(NotificationManager::class.java)
        val active = manager.activeNotifications.map { it.notification.channelId }.toSet()
        val current = TimerAlertKind.entries.map { TimerAlerts.channelId(it,AlertMode.SOUND_VIBRATION,selections.forKind(it)) }.toSet()
        manager.notificationChannels.filter { it.id.startsWith("kin_sound_v2_") && it.id !in active && it.id !in current }
            .forEach { manager.deleteNotificationChannel(it.id) }
        val keep = selections.let { listOf(it.breakEnd.uri,it.focusEnd.uri) }.toMutableSet()
        manager.activeNotifications.forEach { manager.getNotificationChannel(it.notification.channelId)?.sound?.toString()?.let(keep::add) }
        directory.listFiles()?.filter { !it.name.startsWith("import-") }?.forEach { file ->
            val uri = FileProvider.getUriForFile(context,"${context.packageName}.sounds",file).toString()
            if (uri !in keep) {
                context.revokeUriPermission(uri.toUri(),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                file.delete()
            }
        }
    }
}
