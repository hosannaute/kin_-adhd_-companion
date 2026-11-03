# Implementation and acceptance status

The supplied KIN master specification and six-screen image guide the project. Initial local source was created on `development/kin-android`. A successful `git ls-remote` outside the command sandbox confirmed that the remote has no refs. This is an initial project, not a replacement of existing remote code.

## Reproduce the build and finish device acceptance

1. Use the supported sandbox escalation for proxy and local Gradle daemon access.
2. Activate the installed JDK/Android SDK using `source scripts/cloud-env.sh`.
3. Run the build and tests with a writable `GRADLE_USER_HOME`.
4. Keep the official Gradle wrapper and exported Room schema checked in; verify the built APK on Android.

## Implementation

- Milestone 1: project, theme, onboarding, main navigation and native interfaces. The floating screen is not represented by a fake overlay.
- Milestone 2: persistent tasks/goals, focus sessions with monotonic clock, pause/resume, process recovery code.
- Milestone 3: original vector companion, six evolution stages, XP with transactional reward guards.
- Milestone 4: basic WorkManager notifications, task-editor snooze and rescheduling; optional notification permission and quiet hours.
- Milestones 5–6 (0.2): optional real WindowManager overlay, foreground controls, app selection and UsageStats check-ins, permission handling, quiet hours and session counters. Newer Android/physical-device acceptance remains open.
- Milestone 7: offline template conversations and editable small-step suggestions saved as actionable subtasks (0.3); existing task notes are preserved.
- Milestone 8: a clearing, cottage and flower unlocks, preferences, reduced motion and dark mode.
- Milestone 9: baseline verification is extended for 0.2 with usage-policy and real-overlay tests, plus an API 26/35 CI matrix. See VALIDATION.md for the exact current outcomes and remaining device limits.

- Offline planner (0.3): daily/weekly/monthly repeats, persistent subtasks, native schedule pickers, Start/Snooze/Options notifications, task-specific breakdown/reschedule/skip and a schema 1 → 2 migration. See [behavior and limits](OFFLINE_PLANNER.md).
- Focus Picture-in-Picture (0.4): native pinned companion/timer, pause/resume/finish actions, expand/dismiss behavior, settings recovery and overlay coordination. See [behavior and device-validation scope](PICTURE_IN_PICTURE.md).

- Persistent break/design refresh (0.7): always-visible active break countdown in KIN and other apps, larger-font layout, unified typography/colour/cards and a calmer Home/Focus layout. See [design review](DESIGN_REVIEW.md).
- Movable timer and comfort (0.6): one docked icon for break/focus, expandable controls, position restoration, preset/list choices and short onboarding. See [behavior](MOVABLE_TIMER.md).
- Permission/overlay repair (0.5.1): first-open/upgrade permission setup, notification prompt, real overlay test, visible break countdown, explicit timers during quiet hours and direct Home/Focus entry points.
- Break/focus cycle (0.5): persistent planned breaks, large return-to-task window, goal-linked snooze, explicit focus-duration choice, notification fallback and cycle completion. See [behavior and acceptance scope](BREAK_FOCUS_CYCLE.md).

## Still unimplemented

- Newer Android/OEM overlay and foreground-service acceptance, real selected-app threshold observations, and physical-device battery testing. Local overlay and UsageStats implementations are present; see FLOATING_COMPANION.md. No scrolling gesture/content classification is claimed.
- Richer per-task reminder policies; repeated and firm-mode reminders with explicit limits and emergency stop. Precise alarms, if justified, need proper permission and denial flows.
- AI provider integration and explicit consent flow. Offline conversation works without one and does not claim AI capability.
- Walking, waking transitions, richer thinking/encouragement animations, production-quality illustrated environments, interactive pathways/pond/overlook/village unlocks, parallax, configurable sound and intensity beyond reduced motion.
- Data export/import, complete privacy/data-reset flow, localization, TalkBack and large-font audits, broader lifecycle coverage and real-device acceptance.
- Full physical-device acceptance and production release preparation. Remote Actions evidence is linked in VALIDATION.md.

## Acceptance checklist

| Requirement | Current evidence |
| --- | --- |
| Android project builds | Debug APK and test APK assemble successfully |
| Main screens navigate | Navigation instrumentation tests pass on API 26 and 35 |
| Create/complete/persist tasks | Persistence, recurring completion/skip, migration and subtask tests pass on API 26 and 35 |
| Focus timer and persistence | Clock, recovery/reward and native PiP lifecycle/control scenarios pass on API 26/35; physical-device acceptance remains |
| Scheduled reminders | WorkManager implementation; notification actions and snooze scheduling pass API 26/35; background delivery remains inexact |
| Animated companion | Native Canvas implementation; main-screen navigation exercised on API 26; visual/motion audit pending |
| Persistent XP/evolution | Transactional Room implementation; exactly-once reward tests pass on API 26 and 35 |
| Floating companion | Real overlay and controls; API 26/35 CI covers windows, permissions, quiet hours and stop controls; physical-device acceptance remains |
| Usage monitoring | Local UsageStats app-time check-ins; policy tests and a real one-minute cross-app CI scenario; physical-phone/battery checks remain |
| Disable overlays/reminders | Task reminders have a global switch; overlay Stop controls and notification Pause/Stop implemented |
| “I can't start” actionable support | Template tests and task-specific checklist UI scenarios pass on API 26 and 35 |
| Offline core | No runtime Internet permission; database/navigation tests pass on API 26 and 35 |
| Actions produces debug APK | Build job succeeded and uploads APK artifacts; current full check status is linked in VALIDATION.md |

There is no release-completion claim. Complete modern-device and battery acceptance before treating background services as production-ready.
