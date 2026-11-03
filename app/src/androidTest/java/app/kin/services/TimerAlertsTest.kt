package app.kin.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.data.*
import app.kin.domain.AlertMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TimerAlertsTest {
    private val app=ApplicationProvider.getApplicationContext<KinApplication>()
    private val dao get()=app.database.dao()
    private val manager get()=app.getSystemService(NotificationManager::class.java)
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private var scenario: ActivityScenario<MainActivity>?=null
    private lateinit var previous: UserPreferences
    private var overlay=false
    private fun shell(value: String) = automation.executeShellCommand(value).use { ParcelFileDescriptor.AutoCloseInputStream(it).use { s -> s.readBytes() } }
    private fun await(predicate: ()->Boolean) {
        val end=SystemClock.elapsedRealtime()+20_000
        while (!predicate()) { if (SystemClock.elapsedRealtime()>end) fail("Timed out waiting for a timer alert");SystemClock.sleep(100) }
    }
    @Before fun before()=runBlocking<Unit> {
        previous=app.preferences.flow.first();overlay=Settings.canDrawOverlays(app)
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW deny")
        shell("appops set ${app.packageName} POST_NOTIFICATION allow")
        if (Build.VERSION.SDK_INT>=33) automation.grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        dao.currentFocus()?.let { app.repository.stopFocus(it.id) };dao.currentBreak()?.let { app.breaks.stop(it.id,it.revision) }
        app.stopService(Intent(app,BreakTimerService::class.java));FloatingCompanionService.stop(app)
        app.preferences.markPermissionSetupReviewed()
        app.preferences.save(UserPreferences(onboarded=true,reducedMotion=true,quietStart=0,quietEnd=0))
        manager.cancelAll()
        dao.allTasks().forEach { app.repository.deleteTask(it.id) }
        app.repository.saveTask(Task(title="Read a page"))
    }
    @After fun after()=runBlocking<Unit> {
        dao.currentFocus()?.let { app.repository.stopFocus(it.id) };dao.currentBreak()?.let { app.breaks.stop(it.id,it.revision) }
        scenario?.close();app.stopService(Intent(app,BreakTimerService::class.java))
        WorkManager.getInstance(app).cancelAllWork().result.get()
        manager.cancelAll();app.preferences.save(previous)
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW ${if (overlay) "allow" else "deny"}")
    }
    @Test fun channelsUseSeparateChimesAndRespectEachAlertMode() {
        for (kind in TimerAlertKind.entries) for (mode in AlertMode.entries) {
            val id=TimerAlerts.channel(app,kind,mode);val channel=manager.getNotificationChannel(id)
            assertEquals(mode!=AlertMode.SILENT,channel.shouldVibrate())
            assertFalse(channel.canBypassDnd())
            if (mode==AlertMode.SOUND_VIBRATION) {
                assertTrue(channel.sound.toString().contains(if (kind==TimerAlertKind.BREAK) "break_chime" else "focus_chime"))
                assertEquals(AudioAttributes.USAGE_NOTIFICATION,channel.audioAttributes.usage)
                app.contentResolver.openInputStream(channel.sound)!!.use { input ->
                    val header=ByteArray(12);assertEquals(12,input.read(header));assertEquals("RIFF",header.take(4).toByteArray().toString(Charsets.US_ASCII))
                }
            } else assertNull(channel.sound)
            val oldSound=channel.sound
            TimerAlerts.channel(app,kind,mode)
            assertEquals(oldSound,manager.getNotificationChannel(id).sound)
        }
    }
    @Test fun foregroundBreakExpiryPostsOneAlertAndRepeatedTicksDoNotPostAgain()=runBlocking<Unit> {
        scenario=ActivityScenario.launch(MainActivity::class.java);await { KinVisibility.resumed.value }
        val value=app.breaks.begin(dao.allTasks().single().id,null,1)
        val due=value.copy(phase="DUE",remainingMillis=0);dao.updateBreak(due)
        coroutineScope { List(8) { async { BreakNotifications.due(app,due) } }.awaitAll() }
        await { manager.activeNotifications.any { it.tag=="kin.break.${value.id}" && it.id==BreakNotifications.DUE_ID } }
        val posted=manager.activeNotifications.single { it.tag=="kin.break.${value.id}" && it.id==BreakNotifications.DUE_ID }
        val time=posted.postTime
        delay(150);repeat(5) { BreakNotifications.due(app,due) }
        assertEquals(time,manager.activeNotifications.single { it.tag==posted.tag && it.id==posted.id }.postTime)
        assertEquals(due.revision,dao.breakSession(due.id)!!.alertedRevision)
        assertEquals(0,posted.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals(TimerAlerts.channelId(TimerAlertKind.BREAK,AlertMode.SOUND_VIBRATION),posted.notification.channelId)
    }
    @Test fun quietHoursAndSilentPreferenceSuppressAlertingWithoutChangingTheChannel() {
        val hour=LocalTime.now().hour
        val quiet=UserPreferences(quietStart=(hour+23)%24,quietEnd=(hour+1)%24)
        for (pref in listOf(quiet,UserPreferences(alertMode=AlertMode.SILENT,quietStart=0,quietEnd=0))) {
            val notification=TimerAlerts.builder(app,TimerAlertKind.BREAK,pref).setContentTitle("Quiet check").build()
            assertEquals(Notification.GROUP_ALERT_SUMMARY,notification.groupAlertBehavior)
            assertNotNull(notification.group)
            assertNull(notification.sound)
        }
        assertNotNull(manager.getNotificationChannel(TimerAlerts.channelId(TimerAlertKind.BREAK,AlertMode.SOUND_VIBRATION)).sound)
    }
    @Test fun everyNaturalFocusCompletionPathAlertsOnceAndEarlyFinishStaysQuiet()=runBlocking<Unit> {
        val task=dao.allTasks().single();val id=app.repository.startFocus(1,task)
        val value=dao.session(id)!!
        dao.updateSession(value.copy(remainingMillis=1,startedElapsed=SystemClock.elapsedRealtime()-100))
        val results=coroutineScope { List(8) { async { app.repository.refreshFocus(id) } }.awaitAll() }
        assertEquals(1,results.count { it })
        await { manager.activeNotifications.any { it.tag=="kin.focus.$id" } }
        val posted=manager.activeNotifications.single { it.tag=="kin.focus.$id" }
        assertEquals(TimerAlerts.channelId(TimerAlertKind.FOCUS,AlertMode.SOUND_VIBRATION),posted.notification.channelId)
        delay(150);assertFalse(app.repository.refreshFocus(id))
        assertEquals(posted.postTime,manager.activeNotifications.single { it.tag==posted.tag }.postTime)
        val early=app.repository.startFocus(25,task);app.repository.stopFocus(early)
        assertTrue(manager.activeNotifications.none { it.tag=="kin.focus.$early" })
    }
    @Test fun customSnoozeLengthAndLimitApplyToActualNotificationActions()=runBlocking<Unit> {
        app.preferences.save(app.preferences.flow.first().copy(snoozeMinutes=10,snoozeLimit=1))
        val value=app.breaks.begin(dao.allTasks().single().id,null,1)
        val due=value.copy(phase="DUE",remainingMillis=0);dao.updateBreak(due);BreakNotifications.due(app,due)
        await { manager.activeNotifications.any { it.tag=="kin.break.${value.id}" } }
        val old=manager.activeNotifications.single { it.tag=="kin.break.${value.id}" }.notification.actions.single { it.title.toString()=="Snooze 10 min" }.actionIntent
        app.preferences.setSnoozeMinutes(15)
        old.send()
        await { runBlocking { dao.breakSession(value.id)!!.snoozeCount==1 } }
        await { WorkManager.getInstance(app).getWorkInfosForUniqueWork("break-action:${value.id}:${value.revision}").get().any { it.state.isFinished } }
        val snoozed=dao.breakSession(value.id)!!
        assertTrue(app.breaks.remaining(snoozed) in 570_000L..600_000L)
        await { manager.activeNotifications.any { it.tag=="kin.break.${value.id}" && it.notification.getText()=="10 more minutes" } }
        val next=snoozed.copy(phase="DUE",remainingMillis=0);dao.updateBreak(next);BreakNotifications.due(app,next)
        await { manager.activeNotifications.any { it.tag=="kin.break.${value.id}" && it.notification.getText()=="Your break is over" } }
        BreakNotifications.snoozed(app,next) // A delayed acknowledgement must not replace the new due reminder.
        delay(150)
        val notification=manager.activeNotifications.single { it.tag=="kin.break.${value.id}" }.notification
        assertEquals("Your break is over",notification.getText())
        assertEquals(0,notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) // An update to the silent acknowledgement must chime again.
        assertTrue(notification.actions.none { it.title.toString().startsWith("Snooze") })
        assertFalse(app.breaks.snooze(next.id,next.revision));old.send();delay(300)
        assertEquals(1,dao.breakSession(next.id)!!.snoozeCount)
    }
    private fun Notification.getText()=extras.getString(Notification.EXTRA_TITLE)
}
