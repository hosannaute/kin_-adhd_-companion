# Personal sounds and a calmer KIN

KIN 0.9.0 keeps the existing offline planner, saved timers, movable countdown, large break reminder and native focus PiP. The update gives the four tabs a consistent ink, off-white and teal palette, simpler selection rows, compact Home tasks and short navigation transitions. Reduced motion removes the transitions and creature motion. Android font scaling and dark mode remain supported.

## Find the floating timer

Home now has a **Floating timer** row. With no timer, Start opens break setup. Set up opens permission guidance when display-over-other-apps is off. During a break, Show reattaches and collapses the existing countdown without restarting it or moving its saved dock. At expiry, Open leads to the return-to-task reminder. During focus, Open leads to the timer; the focus icon appears outside KIN, with native PiP taking precedence.

The running or paused break remains at the screen edge across ordinary apps. Secure screens and the lock screen can hide overlays. Android force-stop and manufacturer background restrictions can suspend the service; saved clocks recover when KIN opens again.

## Choose sounds

In Settings → Timer alerts, tap **Break sound** or **Focus sound**. Choose:

- **Phone sounds** to use Android's ringtone/notification/alarm tone picker. Available tones depend on the phone.
- **Audio file from storage** for a supported MP3, WAV, OGG or M4A clip up to 10 MB. Android's document picker grants access to that selected file only.
- **KIN chime** to restore the built-in tone, or **No sound** to silence that timer's tone. Vibration still follows Alert mode.
- **Android sound settings** to review the channel's phone-level sound and permissions. If an OEM has no sound picker, the file and Android-settings options remain available.

KIN validates and copies a selected clip into its private storage. Moving or deleting the original file does not break the alert. A failed, unsupported or oversized import keeps the last working sound. Selecting a new sound creates a new Android notification channel because Android cannot edit a channel's original sound programmatically; a blocked channel stays blocked. Break and focus choices remain independent. Old unused custom channels/files are cleaned up, preserving current choices and active notifications.

**Test break alert** and **Test focus alert** post real notifications through the same channels as timer expiry. Android notification permission, notification volume, quiet hours, channel edits and Do Not Disturb apply. KIN does not force the volume, bypass silence or repeatedly play the countdown. Allow notifications, select Sound + vibration and turn off quiet hours temporarily when testing an audible alert.

Audio files and sound selections are phone-local and excluded from portable JSON backups. Choose them again after moving to a different phone or reinstalling. Restoring a task backup on the same installation keeps that phone's sound choices. No Internet or broad storage/media permission is added. Only the private `timer_sounds` subdirectory is exposed through a non-exported, URI-granting FileProvider for Android notification playback.

## Finish focus

Natural completion saves the session and awards its existing 15 XP exactly once. Home and Focus show **Congratulations!** with **Stop** and **Take a break**. The completion notification offers the same actions.

Stop acknowledges that completed session and dismisses its notification; it does not stop a future timer or change XP. Take a break opens the task/duration picker. A new break begins only after **Begin break**. Old notification actions cannot acknowledge a newer completion or start a timer automatically. Finishing early still awards no XP. Acknowledgement survives reopening KIN, and restoring history does not replay congratulations for imported sessions.

## Tecno KM5 / Android 15

KIN targets Android 15 and supports Android 8.0 and later. Automated API 26/35 checks cover the supported endpoints, not a physical KM5 certification. HiOS firmware labels vary. Permission setup and the phone-check guide show Tecno-specific guidance when the device identifies as Tecno:

1. Allow display over other apps and notifications for KIN.
2. Review KIN's battery/background settings. If present, check Phone Master / Battery Lab for background activity and auto-start restrictions.
3. Use Settings → **Check timer on this phone** for a real one-minute break. Switch apps, move/tap the icon, lock/unlock, hear the chosen tone and wait for the large reminder.
4. Complete a short focus session and check Congratulations, Stop and Take a break. Repeat with your normal power-saving setting.

Use the same signing certificate for updates; install over the current app to retain local data. Save a JSON backup before uninstalling or changing phones. The private signing-key handover stays outside the public repository.
