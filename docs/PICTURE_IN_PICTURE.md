# Focus Picture-in-Picture (0.4)

## Using the window

1. Open Focus, select a task or a personal session, and start the timer.
2. Choose **Open Picture-in-Picture**. KIN enters Android's native 16:9 pinned window, showing the companion, task name and remaining time.
3. Use other apps while the countdown continues. Tap the small window to reveal Android's controls: **Pause focus** / **Resume focus**, **Finish focus early**, expand/full screen, and dismiss. Placement, resizing and the precise menu appearance depend on Android and the device.
4. Expand/full screen to return to the same Focus screen and session. The normal navigation bar is hidden in the pinned window and restored on expansion.

Entry is explicit; pressing Home elsewhere in KIN does not automatically pin a timer. A paused session can enter PiP too. Starting a session is required; an empty timer cannot open a window.

**Dismissing the window keeps a running session running and a paused session paused.** Reopen KIN → Focus to continue controlling it. **Finish focus early** records an interruption without awarding XP. Natural completion awards 15 XP once and shows a completion state in the window. PiP controls affect only the session attached to that window; expired controls cannot start a new session.

## Android support and settings

The app still supports Android 8.0/API 26 and above. PiP additionally requires the device's `FEATURE_PICTURE_IN_PICTURE` capability and KIN's Android PiP setting. Unsupported devices retain the full-screen timer. When PiP is disabled, Focus offers **Allow Picture-in-Picture**, opening Android's PiP settings with an app-settings fallback. Returning to KIN refreshes availability without resetting the session.

PiP is separate from **Display over other apps** permission. It requires no overlay, notification, accessibility or Internet permission. No account, server, media playback or additional foreground service is introduced. An explicit PiP request that Android rejects leaves the session available in Focus and shows an error.

## Floating companion coordination

An already-running floating companion hides while the PiP window is entering or visible. Usage polling resets during that suspension. On expansion/dismissal it can reappear, subject to its existing pause, quiet-hour, screen-lock and permission rules. A companion paused for 15 minutes stays paused. PiP never starts a floating service that was off, and does not change its settings or saved position.

## Timing and lifecycle

The window uses the same persisted Room session and monotonic elapsed time as full-screen Focus. Its activity continues observing while STARTED: Android normally pauses a PiP activity, so collecting only while RESUMED would freeze the countdown. Pause/resume and early finish use the existing repository transactions. The background WorkManager job remains the fallback when no Focus activity is visible; Android may delay its completion notification.

The navigation graph is retained while the compact content is displayed. Screen-size/orientation configuration changes are handled by the activity; saved session identity also survives activity recreation. Closing/recreating a window does not grant XP. A running session interrupted by a reboot retains the existing interruption behavior without a completion reward.

## Validation

Six added Android scenarios exercise a real native PiP window, actual System UI action buttons, countdown updates with a different app foregrounded, pause/resume, expansion, rotation/recreation, early finish, dismissal with a running timer, overlay restoration and pause preservation, denied access/settings recovery, and exactly-once natural completion. All 32 scenarios, including the 26 existing regressions, passed on both API 26 and API 35. See [VALIDATION.md](VALIDATION.md) for the exact tested revision and completed CI run. Native accessibility actions invoke the real controls and dismiss the real window without relying on menu animation timing.

Physical-device/OEM controls, battery restrictions, lock-screen behavior, large fonts and TalkBack still need manual acceptance. Check that Android's menu remains usable, long task names truncate legibly, PiP closes cleanly, the existing overlay respects pause/quiet hours after closing, and reopening KIN preserves the timer. PiP is designed for a short focus companion view; task editing stays in the full app.
