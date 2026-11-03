# Validation record

## 0.6 movable timer and quick choices

- Tested app/test revision: [`5db27e2`](https://github.com/hosannaute/kin_-adhd_-companion/commit/5db27e2448b9144fd3009148a4de91f589c38b46). [CI run 41673995806](https://github.com/hosannaute/kin_-adhd_-companion/actions/runs/41673995806) completed successfully: build, API 26 and API 35 jobs all passed. **48 unit tests and 55 device scenarios per API passed, with 0 failures and 0 skips**, as recorded in check annotations. The publication follow-up changes only documentation and APK/checksum files; tested app/test/build source is unchanged.
- Six unit cases cover physical docking, nearest-edge selection, clamping, normalized position after rotation and expanded-window bounds. Four new device cases exercise real input dragging, saved edge/height and service recovery, actual rotation, outside-touch collapse, repeated app switches with one icon, reuse for focus/pause/resume/finish, actual focus notification PendingIntents with overlay denied, remembered presets/task and Custom-only input. Existing onboarding coverage now verifies direct entry to Home without typing. Native PiP, large reminder/snooze, quiet-hour, permission, migration, concurrency, XP and real one-minute usage regressions remain enabled.
- App and instrumentation APKs assemble locally. Final `lintDebug`: **0 findings**. Android 15 outside taps are handled at root dispatch before ViewGroup can discard them; the test touches the underlying app's content rather than protected system navigation. Actual Compose click semantics remain accessible. The collapsed companion has no continuous animation.
- The APK is **0.6.0/code 7**, min API 26/target 35, signed with the existing published certificate. Signature and SHA-256 verify against the local build and the checked-in checksum. Room stays at schema 3; timer choices/position use a separate DataStore. The merged manifest has no Internet permission; production DEX excludes instrumentation fixtures and test permission helpers.
- See [MOVABLE_TIMER.md](MOVABLE_TIMER.md) for behavior and limits. Hardware-accelerated emulator results do not establish behavior on every physical phone. OEM battery/background policy, long use and broad TalkBack/large-font acceptance still need manual device checks. Force-stop suspends Android background execution; the app does not bypass that restriction.

## 0.5.1 permission and overlay repair

- Tested app/test revision: [`01351d1`](https://github.com/hosannaute/kin_-adhd_-companion/commit/01351d15f5170bfe59cb3eaa90c7d25aadc85aa2). [CI run 41415482372](https://github.com/hosannaute/kin_-adhd_-companion/actions/runs/41415482372) completed successfully: build, API 26 and API 35 jobs all passed. **42 unit tests and 51 device scenarios per API passed, with 0 failures and 0 skips.** Completed JUnit counts appear in check annotations. The publication follow-up changes only this record and APK/checksum files; tested app/test/build source is unchanged.
- Four additional scenarios verify permission setup before fresh onboarding and for existing users, skipping/recreating/reopening setup, a real sample window over the home screen without creating a timer, and the direct Home break/Focus-tab flow. The existing cross-app scenario now verifies the compact countdown → large prompt → snooze acknowledgment → compact countdown → repeat prompt transitions. The paused/recovered-service scenario verifies that an explicit break still reminds during quiet hours. Notification denial, persistence, rewards, native PiP, migration and real one-minute usage regressions remain enabled.
- Local app/test APK assembly and final `lintDebug` pass, with **0 lint findings**. A narrow-emulator screen inspection informed the clearer Home entry points and the shorter/narrower speech bubble. Physical-phone visual/accessibility and OEM background/popup behavior still require acceptance; emulator results do not establish behavior on every phone.
- The published APK is **0.5.1/code 6**, **19,587,808 bytes**, min API 26/target 35. Its checksum matches the local build and its signature verifies against the existing published certificate. Room remains at schema 3; permission-review flags are separate DataStore keys, preserving profile/preferences and existing data. The manifest has no Internet permission and production DEX excludes instrumentation fixtures and permission-revocation test helpers.
- See [BREAK_FOCUS_CYCLE.md](BREAK_FOCUS_CYCLE.md) for the updated first-open setup, real window test, quiet-hour behavior, battery guidance and platform limits. Android permission approval remains the user's choice.

## 0.5 break/focus cycle validation

- Tested app/test revision: [`e1f92d1`](https://github.com/hosannaute/kin_-adhd_-companion/commit/e1f92d1eb9eaa56b81d620f825394b04409c52e8). [CI run 41357705819](https://github.com/hosannaute/kin_-adhd_-companion/actions/runs/41357705819) completed successfully: build, API 26 and API 35 jobs all passed. The publication follow-up changes only documentation and the APK; tested app/test/build source is unchanged.
- **42 unit tests pass**, and **47 Android scenarios pass on each of API 26 and API 35**, with **0 failed and 0 skipped**. Completed JUnit totals are exposed directly in each CI check's annotations. The six added end-to-end scenarios exercise the actual large window over a separate app, a goal-linked snooze and repeat reminder, task-specific focus-duration confirmation, pause/recreation/service recovery, quiet hours, real notification PendingIntents and replay guards, denial of both permissions, natural completion XP and the next-break offer. Eight repository/concurrency scenarios and an explicit schema 2 → 3 migration also pass. Existing native PiP, planner, navigation and real one-minute app-usage regressions remain enabled.
- App and instrumentation APKs assemble. `lintDebug`: **0 errors and 0 warnings**. The UI driver uses root-independent, bounded cross-app polling. Android 13+ notification denial uses the platform's scoped test-only no-kill revocation API with shell test permissions; ordinary permission revocation would terminate the instrumentation target. These helpers are excluded from the production APK.
- The published debug APK is **0.5.0/code 5**, min API 26/target 35, **19,505,177 bytes**. Its signature verifies against the existing published certificate, and its SHA-256 matches `downloads/KIN-debug.apk.sha256` and the local build. The merged manifest has no Internet permission; DEX contains no instrumentation fixture or permission-test helper. Explicit Room migrations retain existing schema-1/2 data through schema 3.
- See [BREAK_FOCUS_CYCLE.md](BREAK_FOCUS_CYCLE.md) for behavior and Android limits. Physical-device/OEM battery handling, lock-screen variations, long use, TalkBack and large fonts still need manual acceptance; these hardware-accelerated emulator results are not a production-release claim.

## 0.4 Picture-in-Picture validation

- Tested app/test revision: [`848e102`](https://github.com/hosannaute/kin_-adhd_-companion/commit/848e102d6698da50d4f3562c9f9801891e12b783). [CI run 41345733936](https://github.com/hosannaute/kin_-adhd_-companion/actions/runs/41345733936) completed successfully: build, API 26 and API 35 jobs all passed. The publication follow-up only updates documentation and the APK; tested app/test source is unchanged.
- **36 unit tests pass**, and **32 Android scenarios pass on each of API 26 and API 35**. The six added scenarios open a real native PiP window, verify a live countdown above a separate app, invoke actual System UI pause/resume/finish buttons, expand back to the same Focus route, rotate/recreate the activity, dismiss through Android's native accessibility action, preserve a running timer, recover from denied PiP access through settings, restore/suspend an existing overlay correctly, and award natural completion XP exactly once. The existing planner, migration, reminder, navigation, floating-window and real one-minute usage regressions remain enabled.
- Debug and instrumentation APKs assemble. `lintDebug`: **0 errors and 0 warnings**. Tests refresh KIN's unfocused accessibility content and use native accessibility actions for controls to avoid racing Android's menu animation/auto-hide timeout; timer/state/reward assertions remain in place.
- The published candidate is version **0.4.0/code 4**, min API 26/target 35, with Room schema 2 unchanged. The APK signature verifies against the existing published certificate, and its checksum matches the local build. Both Focus-hosting activities declare PiP support; the merged manifest has no Internet permission. Production DEX has no test fixtures or temporary components.
- See [PICTURE_IN_PICTURE.md](PICTURE_IN_PICTURE.md) for user flows. Physical-device/OEM menus, long-use battery behavior, lock screens, TalkBack and large fonts still need manual acceptance; these emulator results are not a production-release claim.

## 0.3 offline planner validation

- Tested app/test revision: [`262c41d`](https://github.com/hosannaute/kin_-adhd_-companion/commit/262c41d7f268416d1263900269296a11ca2e689d). [CI run 41336235123](https://github.com/hosannaute/kin_-adhd_-companion/actions/runs/41336235123) completed successfully: build, API 26 and API 35 jobs all passed. Subsequent documentation-only commits do not change the tested app or published APK.
- `testDebugUnitTest`: **36 passed, 0 failed, 0 skipped**, including nine recurrence cases for missed dates, weekly anchors, month ends, leap years and daylight-saving changes.
- **26 Android scenarios passed on each of API 26 and API 35**. New coverage includes schema 1 → 2 migration with existing task/goal/XP/chat/focus records, concurrent recurring completion/skip, reset steps on the next occurrence, subtask persistence/deletion, native schedule pickers, task-specific breakdown, and actual notification Start/Snooze/Options PendingIntents. Snooze is checked for a persisted due time and replacement WorkManager work. Stale/closed-task actions and exported-launcher auto-start rejection are also covered. The existing overlay, real one-minute app-usage, persistence and navigation regressions remain enabled.
- Debug APK and instrumentation APK assemble successfully. `lintDebug`: **0 errors and 0 warnings**.
- The checked-in 0.3 APK signature verifies with the existing published certificate; package version is 0.3.0/code 3, min API 26/target 35. Its merged manifest has no Internet permission and its DEX contains no instrumentation fixture. A fresh public GitHub download matched `downloads/KIN-debug.apk.sha256` and the locally built APK.
- Behavior, upgrade semantics and limits are described in [OFFLINE_PLANNER.md](OFFLINE_PLANNER.md). Physical-phone/OEM battery behavior, broad TalkBack/large-font acceptance and real background delivery under Android restrictions still need manual validation; these emulator results are not a production-release claim.

## 0.2 floating companion validation

- `testDebugUnitTest`: **27 passed, 0 failed, 0 skipped**, including ten usage-session/cooldown cases.
- Debug APK and instrumentation APK assemble successfully. The checked-in 0.2 APK includes the activity-intent routing and navigation-readiness fixes.
- `lintDebug`: **0 errors and 0 warnings**. Explicit legacy backup rules accompany Android 12+ extraction rules. The one scoped protected-permission exception documents the supported user-controlled Usage access AppOp.
- APK signature verifies; its certificate matches published 0.1, and the Room database version remains 1. An uninstall is not needed for this update.
- The hardware CI matrix runs **13 Android scenarios on each of API 26 and API 35**. The authoritative outcome for the current revision is in [PR #2 checks](https://github.com/hosannaute/kin_-adhd_-companion/pull/2/checks) and the [Android workflow](https://github.com/hosannaute/kin_-adhd_-companion/actions/workflows/android.yml). A suite definition or started job is not a pass; assess the completed results for the exact head revision.
- Those scenarios cover persisted tasks/goals/focus, navigation, repeated notification intents, overlay permission denial/revocation, a real WindowManager attachment, quiet-hour hiding/reattachment, notification stopping, and a real one-minute selected-app intervention returning to Tasks.
- The external scrollable test activity lives only in the separate instrumentation APK. It uses actual Android UsageStats foreground events; production code has no fixture or fabricated usage data. Compose UI frames are advanced for rendering while SystemClock still measures a real minute.
- The SDK installer used by CI is pinned and checksum-verified, and was exercised locally. JUnit failure annotations expose failing case details on PR checks.

Local software-emulator attempts encountered System UI ANRs, and overlapping local commands were cancelled and excluded from readiness evidence. The isolated hardware CI jobs provide the device acceptance record. Physical-device/OEM behavior, long-use battery observations, notification denial on Android 13+, and restricted screens still need the manual checks in FLOATING_COMPANION.md. Android can intentionally hide overlays on sensitive screens; KIN does not bypass that policy.

## 0.1 baseline: verified toolchain and build

- Installed a full Temurin JDK 17, Gradle 8.13, Android platform 35, build-tools 35.0.0 and platform-tools.
- The official wrapper JAR was checked against Gradle's published SHA-256; the distribution checksum is pinned. JDK and SDK downloads were checked against official release/repository metadata, with the verified SHA-256 values pinned in the reusable setup script. TLS verification remained enabled.
- `testDebugUnitTest`: **17 passed, 0 failed, 0 skipped** (9 progression/timing/reminder-policy tests and 8 conversation tests).
- `assembleDebug`: successful, producing `app/build/outputs/apk/debug/app-debug.apk` (approximately 19 MB).
- `assembleDebugAndroidTest`: successful, including Room and navigation tests.
- `lintDebug`: completes successfully. Explicit backup/transfer exclusions and the modifier-order fix are included. One generic DataExtractionRules warning remains despite the manifest referencing the rules; there are no lint errors.
- `apksigner verify --verbose`: APK verifies with the v2 signature scheme. Package metadata confirms `app.kin.companion`, minimum API 26 and target API 35.
- `scripts/install-cloud.sh` completed successfully on replay, checking SDK requirements and repeating the unit-test/build/lint workflow using retained dependencies.

## Environment diagnosis

The default command sandbox cannot reach the configured proxy. Supported sandbox escalation made native Git and HTTPS operations work without changing the allowlist or requesting another credential. A successful `git ls-remote` confirmed that the remote repository has no refs. Java needed explicit proxy properties and the existing system Java CA trust; the preinstalled Java 21 was only a runtime, so a full JDK was necessary. These settings are captured in `scripts/cloud-env.sh`.

Use the existing checkout. Cloud tasks are isolated and do not require another worktree.

## Additional static checks

Android XML and GitHub Actions YAML parse successfully. Shell syntax checks pass for the wrapper and cloud scripts. Git whitespace checks are performed before committing. These checks supplement rather than replace Android compilation and tests.

## Device and remote checks

The API 35 Google APIs x86_64 emulator could not respond reliably to property/shell requests under software acceleration, so its instrumentation setup failed before app tests executed. This is not a passing API 35 run.

A lighter API 26 emulator completed boot. The final `connectedDebugAndroidTest` run passed **7 tests, 0 failures, 0 errors, 0 skipped**: six Room/repository tests plus one Compose navigation test. The tests exercise real database reopening, concurrent completion without duplicate XP, snoozing, goal deletion, expired focus recovery with exactly-once XP, reboot interruption without XP, and Home → Tasks → World → Chat → More → Home → Focus navigation.

This host has no `/dev/kvm`. Installation and UI tests were slow, and the emulator's Android System UI displayed an ANR dialog after the first run. This was not a KIN crash; both API 26 instrumentation runs completed successfully. These results do not replace hardware-accelerated API 35 or physical-device acceptance, notification permission checks, or long-running timer/battery testing.

The GitHub Actions workflow builds/uploads an APK and runs emulator tests. Workflow configuration alone is not a successful remote run or a published APK artifact.

## Manual device checklist after a successful build

1. Finish onboarding without permissions or optional fields. Open all five navigation destinations and the Focus screen. Kill/relaunch and verify onboarding stays complete.
2. Create a task with every field, associate a goal, edit/reschedule it, filter it, snooze it, complete it twice rapidly, and reopen the app. Verify persisted values and a single XP award. Delete a task and verify its reminder is cancelled.
3. Start a short timer, background the app, rotate, force process recreation without force-stop, change wall time, pause/resume, and finish early. Verify real elapsed countdown, accurate pause, no early-completion XP, and exactly one reward for a naturally completed session. Force-stop explicitly suspends Android jobs until the app is launched again.
4. Reboot during a running timer: it should be recorded as interrupted without XP. Reboot while paused: its saved remaining time should be resumable. Confirm behavior on multiple vendors.
5. Deny notification permission. Repeat task, timer and chat flows; no crash should occur. Enable notifications later in More. Schedule a reminder and allow Android's inexact delivery window; inspect notification tap navigation and quiet hours. Repeat with battery saver and notification channel disabled.
6. Complete tasks/goals across evolution thresholds; reopen and verify stage/XP. Repeated completion must not farm XP. Delete memories and goals and verify XP stays earned.
7. Use “I can't start,” edit the proposed steps, save them, and verify the saved checklist and unchanged task notes after restart. Test chat with empty task/goal lists and delete the conversation.
8. Check dark mode, reduced motion, 200% font size, landscape, compact screens, TalkBack labels and targets. Capture real runtime screenshots only after these checks.

For 0.2 overlay permissions, timing, privacy, controls and the physical-device checklist, see FLOATING_COMPANION.md.
