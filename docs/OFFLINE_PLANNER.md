# Offline planner (0.3)

## Repeating adventures

Create or edit a task and choose Daily, Weekly or Monthly. Date and time buttons open Android's native pickers. Repeats keep the time zone shown in the editor, including when the device travels to a different zone. A daylight-saving gap moves that occurrence forward; later occurrences return to the chosen local time. An overlapping clock time uses the earlier offset once.

Completing or skipping a pending occurrence creates exactly one next future occurrence. Missed dates are skipped rather than added to a backlog. Each old occurrence stays in Completed, labelled skipped when appropriate. Skipping awards no XP. Checking a subtask awards no XP; the parent task must be completed separately.

Monthly repeats preserve the original day: January 31 → February 28/29 → March 31. Snoozing changes only the current due time and does not shift the recurring weekday or monthly day. Rescheduling or changing the repeat rule resets the cadence from the edited occurrence. Select Does not repeat on the pending occurrence to stop a series. Editing an already completed/skipped historical occurrence does not change a future task that was already created. Deleting a pending occurrence stops that branch of the series.

## Actionable small steps

Task cards support adding, checking, renaming and deleting steps. Three are shown initially; Show all exposes longer checklists. Steps persist locally and are copied unchecked to the next recurring occurrence. Deleting the parent removes its steps. Completed/skipped tasks keep read-only step history.

Options → Make it smaller opens the support flow for that exact task. Choose Too overwhelming or I don't know where to begin, edit the proposed lines and save them as real steps. Existing notes remain intact. Repeating the same suggestion does not add duplicate lines. Each task supports up to 100 steps, with 300 characters per step.

## Reminder actions

Task notifications have three primary buttons, fitting Android's standard notification layout:

- Start opens Focus for that task. An already running session is preserved; a paused session for the same task resumes.
- Snooze 10 min persists a new due time and cancels that task's visible notification. It retains the task's reminder setting.
- Options opens that task's Start focus, Snooze, Make it smaller, Reschedule or edit, and Skip this occurrence controls. Skip asks for confirmation.

Tapping the notification body also opens Options. Deleted or resolved tasks cannot be started, snoozed or rewarded by an old notification. Different tasks use separate notification/PendingIntent identities. Background snooze work is persisted through WorkManager. Task-start intents enter a non-exported activity; the public launcher can open options but cannot auto-start a timer from supplied extras.

Notifications still require system permission/channel access and the task/global reminder switches. WorkManager delivery is inexact, can be delayed by Android, and is suspended by force-stop until the app is launched again. Quiet-hour task reminders are skipped. This version does not implement repeated escalation or firm mode.

## Updating from 0.1/0.2

Room schema 1 migrates explicitly to schema 2, retaining tasks, notes, goals, XP, chat, achievements and focus history. Existing tasks default to Does not repeat and have no subtasks. There is no destructive-migration fallback. The debug APK uses the existing signing certificate so the update can be installed in place; uninstalling deletes local data.

Everything in this update runs offline. No account, server, AI provider or Internet permission is added.
