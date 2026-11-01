# KIN

A native Android productivity companion for finding your way back to your goals.
Small adventures, a gentle fantasy creature, and focus without perfect streaks.

**Status: native Android 0.3 development build with recurring tasks, actionable subtasks, reminder actions and an optional floating companion.** Build/test evidence is recorded in VALIDATION.md. Physical-device and OEM acceptance remain separate checks; this is not the complete master specification. See [validation evidence](docs/VALIDATION.md) and the [remaining milestones](docs/ROADMAP.md).

## Download

[Download KIN 0.3 debug APK](downloads/KIN-debug.apk?raw=true) · Android 8.0 or later.

This is an early debug build, not a production release. Android may ask you to allow installation from your browser or file manager. The matching SHA-256 is in [downloads/KIN-debug.apk.sha256](downloads/KIN-debug.apk.sha256). The checked-in APK is built from this branch's app source; subsequent documentation-only commits do not change its code. It uses the existing signing certificate and an explicit database migration from schema 1 to 2 for in-place updates. Known missing features are listed below and in the roadmap.

## Implemented in source

- Seven-step onboarding: meet and name a companion, optional goals and first task, reminder preferences, and an explanation of optional notification access.
- Home, Tasks, World, Chat, More, and a separate Focus screen with functioning navigation code. Interfaces are native components inspired by the supplied six-screen reference.
- Room-backed task creation, editing, deletion, completion, date/time rescheduling, ten-minute snoozing, category search, goal associations, priority, estimated duration, and per-task reminders. Today shows three pending priority tasks by default; additional tasks remain accessible. Overdue tasks stay in Today.
- Daily/weekly/monthly recurring tasks, persistent actionable subtasks, native date/time pickers, and task-specific Start/Snooze/Options notifications. Completing or skipping creates one next future occurrence; skipped tasks award no XP. See [offline planner behavior](docs/OFFLINE_PLANNER.md).
- Locally stored personal goals, motivations, achievements, and companion XP. Completing the same task, focus session, or goal cannot award XP twice. Deleting a goal unlinks its tasks without deleting them. Deleting a memory does not remove earned XP.
- Focus presets (5, 15, 25, 45 minutes), custom duration (1–180 minutes), pause/resume, task association, and saved completed/interrupted sessions. Monotonic time and a persisted boot count protect active countdown accuracy across process recreation and wall-clock changes. Active sessions are interrupted on reboot without awarding XP; paused sessions can be resumed. Finishing early awards no XP.
- An original Canvas-rendered creature with breathing, blinking, waving, sleeping, and celebration poses; six visible evolution stages. Reduced motion freezes animations. A lightweight painted-vector world gains a cottage and flowers with progression.
- Offline guided chat with contextual templates, emotional shortcuts, conversation history, and honest disclosure that it is not AI or mental healthcare. “I can't start” offers small steps that can be edited and saved to a task, rest, and short focus sessions.
- WorkManager task reminders and focus-completion work; optional notification permission; quiet hours; a global task-reminder pause; dark mode and companion naming in DataStore. Android may delay background notifications.
- Optional draggable WindowManager companion with the same original creature renderer, expansion/collapse, quiet-hour and lock-screen hiding, saved position, and persistent Pause/Stop controls. Optional selected-app foreground-time reminders with explicit Usage access, configurable thresholds and snoozing; all processing is local. See [how to enable it and what it measures](docs/FLOATING_COMPANION.md).
- No registration, ads, analytics, cloud provider, API keys, or network permission. Local Android backup is disabled. Uninstalling deletes data; there is no export yet.

Reminder escalation/firm mode, additional world progression, and several other requirements remain unimplemented. Overlay and usage-monitoring behavior on newer Android/OEM phones still requires acceptance testing. Compilation and unit checks do not establish all Android lifecycle and device behavior.

## Technology and architecture

- Kotlin 2.1.21, Jetpack Compose / Material 3, Navigation Compose.
- Gradle Kotlin DSL, Gradle 8.13, Android Gradle Plugin 8.10.1; Java 17 bytecode.
- Room 2.7.1, Preferences DataStore 1.1.7, WorkManager 2.10.1.
- Android 8.0+ (API 26), compile/target SDK 35.

```text
app/src/main/java/app/kin/
  data/       Room entities, DAO, database, repository, preferences
  domain/     XP, evolution, countdown math, quiet hours, offline responses
  features/   ViewModel; manual application-scoped dependency injection
  services/   WorkManager, notifications, floating service and usage polling
  ui/         Native screens, theme, original creature and landscape rendering
```

Repositories own data mutations; the ViewModel exposes observable state. Room transactions guard rewards and session completion. The database is version 2, with both exported schemas and an explicit tested migration from version 1 under `app/schemas`. Add explicit migrations for future schema changes. Destructive migration fallback is intentionally absent.

## Build

Use Android Studio with JDK 17 or 21, Android SDK command-line tools, platform 35 and build-tools 35.0.0. Ensure `ANDROID_HOME` points to your SDK, or create an untracked `local.properties` with `sdk.dir=/absolute/path/to/sdk`.

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

APK, **after a successful build**: `app/build/outputs/apk/debug/app-debug.apk`.

In this cloud environment, the home directory is read-only. Set a writable Gradle cache first:

```sh
export GRADLE_USER_HOME=/workspace/.cache/kin-gradle
cd /workspace/kin_-adhd_-companion
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

### Wrapper and cloud setup

The official Gradle 8.13 wrapper is included for Unix and Windows. The wrapper JAR was verified against Gradle's published SHA-256; the distribution checksum is pinned in wrapper properties.

For the managed Linux cloud environment, `scripts/install-cloud.sh` installs a checksum-verified JDK 17 and Android command-line tools into `/workspace/.cache`, then installs the required SDK packages and runs the build/tests/lint. Source `scripts/cloud-env.sh` in each new shell to activate the retained toolchain, proxy and system CA trust. Network commands and Gradle's local daemon require the supported sandbox escalation in this environment; the default sandbox cannot reach the proxy. Use the existing checkout; each cloud task is already isolated and needs no worktree.

## Tests

```sh
./gradlew testDebugUnitTest          # domain unit tests
./gradlew connectedDebugAndroidTest # needs an Android emulator/device
./gradlew lintDebug
```

Tests cover evolution boundaries, rewards, countdown math, snooze bounds, quiet hours, offline responses, recurrence boundaries, actual Room persistence after reopening, concurrent exactly-once task completion, goal deletion, snoozing, schema migration, actionable steps, real notification actions, and navigation across major screens. The suite now also tests app-time accounting, cooldowns, actual overlay attachment, quiet-hour hiding/resuming, notification stopping, permission revocation and permission-settings availability. Current counts and platform limits are recorded in VALIDATION.md. See [validation evidence](docs/VALIDATION.md) for device-test status and the manual checks still needed for background timers, notification denial, reboot, battery restrictions, large fonts, and accessibility.

## GitHub Actions and APK download

[The Android workflow](.github/workflows/android.yml) configures Java and the Android SDK, runs unit tests, builds a debug APK, and uploads `KIN-debug-apk`. A hardware-accelerated matrix runs persistence, migration, navigation, notification and overlay instrumentation tests on API 26 and 35 emulators. Test reports are uploaded separately. Jobs use read-only repository permissions and no private signing keys.

After a workflow succeeds, open the run under **Actions → Android debug APK** and download its APK artifact. You can also trigger the workflow manually. The tested debug APK linked above is published directly in `downloads/`; Actions artifacts are separate.

## Permissions

- `POST_NOTIFICATIONS`: requested only in More when the user chooses to enable notifications. Denial leaves tasks, chat, progression, and the timer usable. System notification settings can be opened there too.
- `RECEIVE_BOOT_COMPLETED`: used by the WorkManager dependency to restore scheduled work. KIN does not auto-launch an activity after reboot.
- `SYSTEM_ALERT_WINDOW`: optional Settings permission for the user-started floating companion. Denial leaves core features usable.
- `PACKAGE_USAGE_STATS`: optional Settings/AppOps access for app-time reminders, separately enabled after selecting apps. It does not identify scrolling or content types.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`: the explicitly started companion service and its ongoing control notification. It does not restart automatically after reboot/process death.
- No accessibility service, exact alarm, microphone, Internet, storage, or location permission is requested by KIN.

WorkManager contributes normal `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, and `FOREGROUND_SERVICE` permissions plus its internal non-exported receiver permission in the merged manifest. The optional floating mode starts its declared foreground service. KIN does not acquire its own wake lock.

Reminders use deferrable WorkManager jobs, not exact alarms. Quiet-hour task reminders are skipped rather than deferred. This build provides one scheduled notification per occurrence with Start, Snooze 10 min and Options actions. Options includes breakdown, rescheduling and skip. Firm mode and reminder escalation are not implemented.

## Screenshots

No runtime screenshots are included because no app screenshots were captured during the automated device runs. After building, launch on a phone/emulator, finish onboarding, add your own sample tasks, and capture Home, Tasks, Chat, Focus, World, and More using Android Studio's device screenshot tool. Do not use the supplied reference as an app screenshot.

## Remaining work

See [ROADMAP.md](docs/ROADMAP.md) for honest acceptance status, including newer-device overlay/usage acceptance, production artwork, reminder escalation, more world unlocks, additional creature motions, accessibility/device validation, and release preparation. This early version is not a complete implementation of the master specification.
