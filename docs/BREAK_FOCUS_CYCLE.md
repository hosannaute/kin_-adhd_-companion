# Break → reminder → focus (0.6)

## Using the cycle

1. On first launch/update, follow **Set up your companion**. Allow the companion window and notifications; use **Test window over other apps** to verify a real overlay before relying on it. Then choose **Take a scrolling break** on Home, or **Plan a scrolling break** in the Focus tab. Select a pending task, an optional personal goal, and 1–180 break minutes. After a completed focus session the button is **Plan next break**.
2. Choose **Begin break**, then use other apps. A movable companion icon docks at the screen edge over other apps. Tap it for the countdown, pause/resume and task controls. The foreground notification also provides timer controls. The optional floating companion's older app-time check-ins pause during this planned break.
3. At expiry, a large companion window shows the task and **Start task**, **Snooze 5 min**, and **Stop break**. The creature fills its upper image area. In KIN the reminder is a dialog; outside KIN it needs display-over-other-apps permission. A notification provides the same actions when allowed.
4. **Start task** opens **How long do you want to focus?** Choose a preset or 1–180 focus minutes. **Begin focus** commits the transition. Opening/cancelling the picker does not start focus or discard the break. You can also choose Start task during a running/paused break.
5. **Snooze 5 min** extends the break and strengthens the goal-linked message. The window briefly acknowledges the snooze; the notification/in-app countdown retains the message. After the acknowledgment, the icon returns. The large prompt returns at the new deadline. Further snoozes ask whether you still want to achieve the selected goal.
6. Natural focus completion saves progress and awards 15 XP once, then offers another break. Early finish, stopping a break and snoozing award no XP. Finishing a focus session does not automatically mark the whole task completed.

## Permissions and background behavior

Permission setup appears before profile onboarding on a fresh install and once for existing users upgrading to 0.5.1. Android 13+ gets a first-use notification request; the overlay permission opens Android settings for user approval. No permissions can be auto-granted. The setup explains overlay/notification availability and provides explicit permission/settings buttons. No Usage access, Accessibility service, Internet permission, account or server is needed for this cycle.

Android restricts opening native PiP from the background, so the automatic prompt uses a permission-granted WindowManager overlay. Native PiP remains available for focus as in 0.4. A visible native PiP or sample window takes precedence over the break window. The ordinary floating bubble hides while the external timer icon/prompt or in-app due dialog is shown and resumes under its existing rules.

The foreground service starts when you begin a break and recovers an active break when KIN returns to the foreground. Its ongoing notification provides timer controls when notifications are allowed. Sticky service restart may recover after process reclamation where Android permits. Persisted WorkManager work provides a notification fallback; background workers do not launch activities or foreground services.

Explicitly started break timers show their timer icon and large reminder during quiet hours; their notification is silent. Quiet hours still apply to passive task reminders and optional app-usage check-ins. Locking/switching off the screen hides the window until unlock. The due state remains available in KIN. Android can hide overlays on sensitive screens; the notification remains a fallback. If both permissions are denied, return to KIN to see the saved due reminder.

WorkManager is inexact, and OEM battery controls/process killing can delay background recovery. Force-stop suspends work until the app is launched again. No exact alarms or restrictions bypass are used.

## Persistence and action guards

Room schema 2 migrates explicitly to 3 by adding break records; schema 1 → 2 remains supported. Tasks, checklists, goals, chat, focus history and XP are retained. Breaks store the chosen task/goal text, phase, remaining time, monotonic start, boot count, wall deadline and snooze revision. Running timers use elapsed time; after reboot a stored deadline restores a bounded remainder. Paused timers retain their saved remainder.

One break/focus cycle is active at a time. Revision-bound pause/resume/snooze/stop actions are transactional. Replayed notifications cannot extend a break twice or affect a later phase. Concurrent Start task confirmations create one focus session. Deleted/completed tasks stop pending break reminders. Focus/cycle completion and the existing exactly-once XP guard share a transaction. Each due revision claims one notification alert.

## Validation

Ten end-to-end scenarios exercise first-open/upgrade permission setup, skipping and reopening setup, a real sample overlay, the direct Home/Focus flow, the cross-app window, goal snooze/repeat, chosen focus duration, paused activity/service recovery, quiet hours, notification fallback/stale actions, denied permissions, focus rewards and the next-break offer. Eight repository scenarios cover persistence, reboot, concurrent starts/snoozes, deletion and rewards. A migration scenario validates existing schema-2 planner data. Six unit cases cover break-clock and message rules. See [VALIDATION.md](VALIDATION.md) for completed outcomes; compiling is not a device pass.

Physical-device/OEM battery behavior, lock-screen variations, permission/channel revocation, long use, TalkBack and large fonts still need manual acceptance.

For movable controls, saved choices, shorter onboarding and reliability limits, see [MOVABLE_TIMER.md](MOVABLE_TIMER.md).
