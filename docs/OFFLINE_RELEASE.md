# KIN 0.8 offline release and handover

KIN runs on the phone without ChatGPT, a Plus subscription, an account or an API key. Keep the GitHub repository and APK. Your installed app will continue working when a subscription ends.

## Timer alerts

Settings → Timer alerts offers Sound + vibration, Vibration only and Silent. Break expiry uses a short original two-note chime; natural focus completion uses a softer, lower chime. The ongoing countdown, pause/resume, early finish and snooze acknowledgment stay silent. A new alert is eligible when a snoozed break expires. A persisted break revision claim and the transactional focus completion guard prevent repeated ticks or competing completion paths from posting the same alert again.

Both the in-app and outside-app expiry paths use notifications for sound. Allow Android notifications to hear an alert. Quiet hours silence timer sound and vibration while keeping the reminder visible; task reminders during quiet hours remain skipped. Android notification volume, channel choices, app notification permission and Do Not Disturb still apply. KIN does not bypass them or change the phone's volume. Test buttons post real, short-lived notifications using these same settings; they also respect quiet hours.

Android keeps each channel's user settings. Settings links directly to the break and focus channels for the selected mode. Re-selecting a mode does not reset a channel you muted in Android. If a test is silent, check the selected mode, quiet hours, phone notification volume, Do Not Disturb and the channel's Android settings. Channel names distinguish break/focus and mode. Sound files are original PCM WAV resources; no external assets or online audio are used. Regenerate them with `python3 scripts/generate-timer-chimes.py`. VIBRATE is a normal Android permission and has no separate approval dialog.

## Reminder choices and forms

Gentle wording is the default. Scheduled task notifications and break reminders use the chosen style. Firm break wording directly names the user's chosen task and goal. Break snooze length is selectable as 5, 10 or 15 minutes. The optional snooze limit is Unlimited, 1 or 3 per break. The repository enforces the limit, including notification actions; stale actions cannot extend a later timer revision. Reaching a limit removes Snooze and leaves Start task and Stop break available. No focus session starts automatically.

Duration, scheduling and priority chips wrap within the screen, including at larger font sizes. Quiet-hour hours use lists rather than numeric typing. Home has a smaller companion banner and moves permission review into Settings once the window is allowed.

## Backup and restore

Settings → Backup and restore uses Android's document picker. Save backup writes a version-1 KIN JSON file wherever the user chooses. It includes tasks, recurring metadata, subtasks, goals, companion XP, messages, achievements, focus/break history, preferences, remembered timer choices and companion check-in choices. The file is private personal data in plain text; KIN does not encrypt it or upload it itself. Android automatic backup remains disabled. No storage or Internet permission is added.

Restore reads and validates the entire file before showing its task/goal/XP/message summary and an explicit Replace data and restore confirmation. Cancel preserves current data. The reader rejects files over 8 MB, unsupported versions, incomplete records, duplicate IDs, invalid values and broken required relationships. It also limits record counts. Current timers must be ended before restoration. A database transaction replaces the data; imported records receive fresh IDs and references are remapped so old task/timer notification actions cannot target restored rows. XP and completed/rewarded flags are preserved, without awarding XP for import.

An active timer stored in a backup becomes stopped/interrupted history, preserving completed history and never resuming an old device clock. Existing scheduled work and notifications are cleared; future task reminders are scheduled again. Overdue tasks stay in the planner without a flood of old notifications. Android permissions and notification-channel settings remain specific to the phone. Automatic app-time check-ins remain off until explicitly enabled. Review permissions and selected apps on a new phone. Database replacement is transactional; preferences live in separate DataStores. If preference writing fails after replacement, the app explicitly reports that the data was restored and settings need review.

To update KIN, install the new APK over the existing one. To change phones or reinstall, save a backup first, install KIN, then restore and review phone permissions. Uninstalling without a saved backup deletes local data.

## Check the timer on your actual phone

Settings → Check timer on this phone guides a real one-minute break using a pending task. It does not complete a task or award XP. The observation checkboxes are user-entered; KIN does not claim to have independently verified the phone.

1. Review display-over-other-apps and notification permissions. In the phone's KIN battery settings, allow background activity or Unrestricted if available.
2. Start the one-minute check with a pending task. Open your usual browser/social app. Confirm the countdown stays visible, drag it to either edge and tap to expand/collapse controls.
3. Lock and unlock. Confirm the timer returns while unlocked and continues from its saved clock.
4. Let the break end. Confirm one large prompt and the selected alert. Snooze and confirm the next expiry; choose Start task and confirm that focus asks for its own duration.
5. Repeat with a longer break and your normal battery-saver setting. Test silent/vibration modes, quiet hours, Do Not Disturb and notification denial. Check a dark screen and large fonts for readable controls.

A lock screen hides the overlay; unlocking can restore it. Secure apps can suppress overlays. Force-stop suspends background execution. OEM battery policy can reclaim services. WorkManager fallback is inexact. Hardware-accelerated emulator tests are recorded in VALIDATION.md; they do not certify a particular physical phone or long-use battery behavior.

## Continue development without this cloud session

The latest complete source is on `feature/offline-release-and-alerts`, including the earlier release branches. The changes are published as stacked pull requests for review; merge them from oldest to newest when ready. No Plus subscription is needed to review or merge them on GitHub.

Open the repository in Android Studio, select JDK 17, install Android SDK 35/build-tools 35.0.0, and use the checked-in Gradle wrapper. README.md describes local and managed-cloud setup. A normal local setup needs no cloud credentials. Run:

```sh
./gradlew --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
# With an Android emulator or USB-debugging test device:
./gradlew --no-daemon connectedDebugAndroidTest
```

The debug APK is app/build/outputs/apk/debug/app-debug.apk. The public download and checksum are in downloads/. GitHub Actions runs the unit/build job plus device suites on API 26 and 35; its annotations include completed test counts. VALIDATION.md identifies the exact tested revision. Documentation/APK publication commits leave that app/test/build source unchanged.

The current APK uses the existing debug certificate for in-place updates. Future APK updates must keep the same signing certificate. Keep private signing material outside Git; a new production signing key requires a migration plan and a backup before replacing a differently signed install. A Play/store release still needs private production signing, store policy review, physical-device acceptance and broader accessibility checks. Do not publish signing keys in this public repository.

Room stays at schema 3 with explicit schema-1/2 migrations. New preferences use existing DataStores. Future database changes require explicit migrations and exported schemas. Backup format changes need a versioned reader/migration. Online accounts, cloud sync and AI are optional future work; none are required to keep this release functioning.
