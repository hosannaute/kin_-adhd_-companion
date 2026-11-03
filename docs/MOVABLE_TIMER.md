# Movable timer and quick choices (0.6)

## Timer icon

Start a break from Home or Focus, or start a focus session. With display-over-other-apps permission, one small companion icon appears outside KIN. Drag it and release: it snaps to the nearest physical screen edge. The chosen edge and relative vertical position are saved independently of the timer, and adapt to rotation/screen-size changes.

Tap the icon to see remaining time and Pause/Resume. Break controls also offer Start task and End break; focus controls offer Finish early. Collapse or tap outside the panel to return to the icon. An explicit break reaching zero opens the large companion reminder. Start task still asks for focus duration before starting; Snooze adds five minutes, briefly shows the goal message, then returns to the icon. Further expiry opens the reminder again. Collapsing a due reminder leaves an urgent icon and does not stop the saved break.

The same foreground service owns break and focus clocks. Its notification provides controls when allowed. The icon is static while collapsed, and only one timer window is attached. Ordinary floating-companion check-ins rest while either timer is active. Native focus PiP temporarily takes precedence; the timer icon returns when PiP is dismissed. KIN's full screen and the permission preview also take precedence.

Changing apps does not end or recreate a timer. Window attachment failures retry without resetting the saved clock. Locking hides the window; unlocking restores it. Activity re-entry recovers an active saved break/focus service, with bounded foreground retries. Android may restart a reclaimed sticky service. Persisted WorkManager reminders remain the fallback. Force-stop and vendor background/battery restrictions can suspend execution; no app can guarantee an unkillable overlay. Use permission setup's real sample window and battery guidance on your phone.

## Fewer forms

- Break presets: 5/10/15/20 minutes. Focus presets: 5/10/15/25/45 minutes. Custom reveals the numeric field only when selected; timers support 1–180 minutes.
- Last chosen break/focus durations and a valid pending task are restored. Tasks and goals use dropdown lists. Deleted/completed tasks are not preselected. Goal reminders remain optional.
- Task duration presets, common category choices and Today/Tomorrow/Next week buttons reduce entry. Native date/time pickers remain available; Custom category/duration and optional notes reveal fields when needed. Task estimates still support up to 1440 minutes.
- First permission setup leads directly to Home. People who left the older profile onboarding unfinished get a one-screen welcome; naming is optional. Tasks, goals and personal details can be added later.
- Home, Tasks, Focus and Companion remain the four main tabs. Settings opens from Home. Existing planner data, goals, rewards and history stay intact; Room schema remains 3. Timer choices/position use a separate DataStore.

## Validation

Six unit cases cover docking, bounds, normalization and rotation. New device scenarios exercise actual dragging, edge docking, outside-touch collapse, saved position/service recovery, rotation, repeated app switching, the same icon during focus, focus notification controls with overlay denied, preset remembering and explicit Custom input. Existing break, PiP, planner, permission, migration, reward and real app-usage regressions remain enabled. Completed outcomes are recorded in [VALIDATION.md](VALIDATION.md).

Physical/OEM devices, large-font/TalkBack acceptance and long-use battery behavior require manual checks. App permissions remain under user control; everything stays offline.
