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
- Milestone 7: offline template conversations and editable small-step suggestions saved into task notes.
- Milestone 8: a clearing, cottage and flower unlocks, preferences, reduced motion and dark mode.
- Milestone 9: baseline verification is extended for 0.2 with usage-policy and real-overlay tests, plus an API 26/35 CI matrix. See VALIDATION.md for the exact current outcomes and remaining device limits.

## Still unimplemented

- Newer Android/OEM overlay and foreground-service acceptance, real selected-app threshold observations, and physical-device battery testing. Local overlay and UsageStats implementations are present; see FLOATING_COMPANION.md. No scrolling gesture/content classification is claimed.
- Recurring/repeating tasks; richer per-task reminder policies; notification actions (start/snooze/breakdown/reschedule/skip); repeated and firm-mode reminders with explicit limits and emergency stop. Precise alarms, if justified, need proper permission and denial flows.
- Separate actionable subtasks with their own completion; current suggested steps are editable notes on the original task.
- AI provider integration and explicit consent flow. Offline conversation works without one and does not claim AI capability.
- Walking, waking transitions, richer thinking/encouragement animations, production-quality illustrated environments, interactive pathways/pond/overlook/village unlocks, parallax, configurable sound and intensity beyond reduced motion.
- Data export/import, complete privacy/data-reset flow, localization, TalkBack and large-font audits, lifecycle/notification UI tests, migration tests and real-device coverage.
- Full device acceptance, a successful remote Actions run, and release preparation.

## Acceptance checklist

| Requirement | Current evidence |
| --- | --- |
| Android project builds | Debug APK and test APK assemble successfully |
| Main screens navigate | Navigation instrumentation test passes on API 26 |
| Create/complete/persist tasks | Room persistence and completion tests pass on API 26 |
| Focus timer and persistence | Clock unit tests and recovery/reward device tests pass; background/real-device acceptance pending |
| Scheduled reminders | WorkManager implementation; untested and inexact |
| Animated companion | Native Canvas implementation; main-screen navigation exercised on API 26; visual/motion audit pending |
| Persistent XP/evolution | Transactional Room implementation; exactly-once reward tests pass on API 26 |
| Floating companion | Real overlay and controls; API 26/35 CI covers windows, permissions, quiet hours and stop controls; physical-device acceptance remains |
| Usage monitoring | Local UsageStats app-time check-ins; policy tests and a real one-minute cross-app CI scenario; physical-phone/battery checks remain |
| Disable overlays/reminders | Task reminders have a global switch; overlay Stop controls and notification Pause/Stop implemented |
| “I can't start” actionable support | Implemented; template tests pass; full UI acceptance pending |
| Offline core | No runtime Internet permission; local database/navigation tests pass on API 26 |
| Actions produces debug APK | Build job succeeded and uploads APK artifacts; current full check status is linked in VALIDATION.md |

There is no release-completion claim. Complete modern-device and battery acceptance before treating background services as production-ready.
