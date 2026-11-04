package app.kin.services

import android.app.NotificationManager
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kin.KinApplication
import app.kin.data.*
import app.kin.domain.AlertMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalSoundsTest {
    private val app = ApplicationProvider.getApplicationContext<KinApplication>()
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private lateinit var previous: UserPreferences
    private fun uri(name: String) = Uri.parse("content://app.kin.companion.test.audio/$name")
    @Before fun before() = runBlocking<Unit> {
        previous = app.preferences.flow.first()
        app.preferences.save(previous.copy(alertMode = AlertMode.SOUND_VIBRATION,quietStart = 0,quietEnd = 0))
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        manager.cancelAll()
        app.sounds.builtIn(TimerAlertKind.BREAK);app.sounds.builtIn(TimerAlertKind.FOCUS)
    }
    @After fun after() = runBlocking<Unit> {
        manager.cancelAll();app.sounds.builtIn(TimerAlertKind.BREAK);app.sounds.builtIn(TimerAlertKind.FOCUS)
        app.preferences.save(previous)
    }
    @Test fun importedSoundSurvivesSourceDeletionAndIsUsedByTheRealNotification() = runBlocking<Unit> {
        val selected = app.sounds.import(TimerAlertKind.BREAK,uri("valid"))
        assertEquals("My phone tone.wav",selected.title)
        assertEquals("${app.packageName}.sounds",Uri.parse(selected.uri).authority)
        app.contentResolver.delete(uri("valid"),null,null)
        val player = MediaPlayer()
        try { player.setDataSource(app,Uri.parse(selected.uri));player.prepare();assertTrue(player.duration >= 1900) }
        finally { player.release() }
        TimerAlerts.preview(app,TimerAlertKind.BREAK)
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (manager.activeNotifications.none { it.tag == "kin.timer.preview" }) {
            check(SystemClock.elapsedRealtime() < deadline);SystemClock.sleep(50)
        }
        val notification = manager.activeNotifications.single { it.tag == "kin.timer.preview" }.notification
        assertEquals(TimerAlerts.channelId(TimerAlertKind.BREAK,AlertMode.SOUND_VIBRATION,selected),notification.channelId)
        assertEquals(selected.uri,manager.getNotificationChannel(notification.channelId).sound.toString())
        assertFalse(manager.getNotificationChannel(notification.channelId).canBypassDnd())
        // Observe the system's notification playback, not a test-only MediaPlayer preview.
        val audio = app.getSystemService(AudioManager::class.java)
        val audioDeadline = SystemClock.elapsedRealtime() + 5_000
        while (audio.activePlaybackConfigurations.none { it.isActive && it.audioAttributes.usage == AudioAttributes.USAGE_NOTIFICATION }) {
            check(SystemClock.elapsedRealtime() < audioDeadline) { "Android did not play the imported notification sound" }
            SystemClock.sleep(50)
        }
    }
    @Test fun invalidAndOversizedFilesKeepThePreviousWorkingSound() = runBlocking<Unit> {
        val selected = app.sounds.import(TimerAlertKind.FOCUS,uri("valid"))
        for (name in listOf("invalid","large")) {
            try { app.sounds.import(TimerAlertKind.FOCUS,uri(name));fail("Invalid import was accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(selected,app.sounds.flow.first().focusEnd)
        }
        app.contentResolver.openInputStream(Uri.parse(selected.uri))!!.use { assertTrue(it.read() >= 0) }
    }
    @Test fun newToneGetsANewChannelAndBreakAndFocusStayIndependent() = runBlocking<Unit> {
        val first = app.sounds.import(TimerAlertKind.BREAK,uri("valid"))
        val firstChannel = TimerAlerts.channel(app,TimerAlertKind.BREAK,AlertMode.SOUND_VIBRATION,first)
        val focus = app.sounds.flow.first().focusEnd
        val silent = app.sounds.silent(TimerAlertKind.BREAK)
        val secondChannel = TimerAlerts.channel(app,TimerAlertKind.BREAK,AlertMode.SOUND_VIBRATION,silent)
        assertNotEquals(firstChannel,secondChannel)
        assertNull(manager.getNotificationChannel(secondChannel).sound)
        assertTrue(manager.getNotificationChannel(secondChannel).shouldVibrate())
        assertEquals(focus,app.sounds.flow.first().focusEnd)
        val restored = app.sounds.builtIn(TimerAlertKind.BREAK)
        val channel = manager.getNotificationChannel(TimerAlerts.channel(app,TimerAlertKind.BREAK,AlertMode.SOUND_VIBRATION,restored))
        assertTrue(channel.sound.toString().contains("break_chime"))
        assertTrue(restored.revision > silent.revision)
    }
}
