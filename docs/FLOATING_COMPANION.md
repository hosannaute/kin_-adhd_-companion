# Floating companion and app-time check-ins (0.2)

This mode is optional and processes usage events on the device. Tasks, focus, chat, XP and goals remain usable when either special permission is denied.

## Use it

1. Open **More → Floating companion**. Read the permission explanation and choose **Allow display over other apps**. Return to KIN after making your choice in Android Settings.
2. Choose **Start floating companion** while KIN is open. This starts a foreground service with a quiet ongoing notification. On Android 13+, enable notifications separately to see its Pause/Stop controls in the notification drawer. The creature's Stop button and the More screen remain available if notifications are denied.
3. Drag the creature to reposition it. Tap it to expand or collapse the interaction panel. The position is saved locally and constrained to the screen after resizing/rotation.
4. For optional app-time check-ins, open **Choose usage access for KIN**, choose KIN in Android's list and grant access. Back in KIN, select apps, set a threshold from 1 to 180 minutes, and switch on **App-time reminders**. Granting access alone does not enable monitoring.
5. Leave KIN and use a selected app. After approximately the threshold of uninterrupted foreground time, the creature offers **Taking a break**, **Just scrolling**, and **Return to my goals**. These are your descriptions of your intent, not content classifications detected by KIN.

**Taking a break** snoozes check-ins for ten minutes; **Just scrolling** acknowledges the prompt and reminds later without shortening the ten-minute minimum between prompts. **Return to my goals** opens Tasks through a direct user interaction. The panel can always be collapsed or stopped.

## Quiet hours and stopping

- Quiet hours share the More screen's existing hours. Equal start/end hours disable the quiet interval. During quiet hours the overlay is detached and app-time counters are reset; the service notification stays available so you can stop it.
- Locking or switching off the screen hides the overlay and resets counting. Detached overlay compositions are disposed, so creature animations do not continue in the hidden view.
- The ongoing notification has **Pause 15 min** and **Stop** actions. Pause hides the overlay and stops counting for fifteen minutes. Open More and use Resume to end a pause early.
- Stop from the collapsed creature, expanded panel, More, or the notification. Revoking overlay permission stops the service on its next check. Revoking usage access disables counting while leaving the optional creature usable.
- The service is not sticky and does not auto-start after reboot or process death. Open KIN and start it again. This respects modern Android's foreground-service start restrictions.

## What is measured

Android UsageStats events describe app/activity foreground transitions, not touch gestures or video categories. KIN cannot identify Shorts, Reels, educational content, attention, or doom-scrolling. It offers gentle app-time check-ins instead.

The poller reads at most once every fifteen seconds while the screen is interactive, quiet hours are inactive, and monitoring is explicitly enabled with selected apps. A first query of the preceding day infers the current foreground app, but counting begins at service activation/resumption rather than retroactively counting the day. Later queries cover only the new interval. App/activity events are not stored in Room or uploaded. Only selection, threshold and window position persist in DataStore.

Counts use elapsed realtime, with event timestamps mapped into the current polling interval. Wall-clock jumps reset inference instead of manufacturing usage time. Leaving an app resets its session; a brief pause/resume within the same app (an activity transition) retains its progress without counting the pause. Missing/denied usage data clears inference rather than keeping a stale foreground app. OEM delays, multi-window and picture-in-picture can make usage events approximate.

There is no Internet permission, account or online provider in this implementation.

## Android implementation and validation limits

`FloatingCompanionService` uses `TYPE_APPLICATION_OVERLAY`, a non-focusable window that wraps its content, the same Compose creature renderer as the app, and a lifecycle/saved-state owner for its composition. It requests no accessibility service and never adds a full-screen invisible touch layer. A foreground `specialUse` service declares its user-visible purpose and normal foreground-service permissions for Android 14+; store release still requires platform/store acceptance of that use case.

`PACKAGE_USAGE_STATS` is declared for Android's user-controlled Usage access AppOp. The manifest's one scoped `ProtectedPermissions` lint exception documents this special-access path; the app never tries to obtain a system-only runtime grant. Denied permissions are normal optional states.

The unit suite covers selection, foreground/background transitions, internal activity changes, cooldowns and snoozing. Device tests use actual Android WindowManager and notification actions; test-only shell AppOps grants simulate the user's permission decision on an emulator. Production code only uses Android Settings. CI is configured to exercise these tests on both API 26 and API 35. The test harness grants notification permission on API 33+ so it can verify notification controls; production permission requests remain optional and user-controlled. Physical-device/OEM behavior still needs separate checks.

Before calling this production-ready, check on Android 13–15+ phones: permission denial/revocation, initial start while the activity is visible, notification denial, dragging at every screen edge, rotation/font scaling, bubble buttons over another app, lock/quiet-hour hiding, a one-minute selected-app threshold, repeated check-in suppression, process death, and a thirty-minute battery observation. This implementation does not establish that all OEMs will allow a continuously running overlay.
