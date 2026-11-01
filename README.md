# KIN

A native Android productivity companion for finding your way back to your goals.
Small adventures, a gentle fantasy creature, and focus without perfect streaks.

**Status: initial native Android implementation.** A debug APK builds, 17 unit tests and 7 Android 8 device tests pass, and lint completes. Newer Android and physical-device acceptance remain separate checks; this is not the complete master specification. See [validation evidence](docs/VALIDATION.md) and the [remaining milestones](docs/ROADMAP.md).

## Implemented in source

- Seven-step onboarding: meet and name a companion, optional goals and first task, reminder preferences, and an explanation of optional notification access.
- Home, Tasks, World, Chat, More, and a separate Focus screen with functioning navigation code. Interfaces are native components inspired by the supplied six-screen reference.
- Room-backed task creation, editing, deletion, completion, date/time rescheduling, ten-minute snoozing, category search, goal associations, priority, estimated duration, and per-task reminders. Today shows three pending priority tasks by default; additional tasks remain accessible. Overdue tasks stay in Today.
- Locally stored personal goals, motivations, achievements, and companion XP. Completing the same task, focus session, or goal cannot award XP twice. Deleting a goal unlinks its tasks without deleting them. Deleting a memory does not remove earned XP.
- Focus presets (5, 15, 25, 45 minutes), custom duration (1–180 minutes), pause/resume, task association, and saved completed/interrupted sessions. Monotonic time and a persisted boot count protect active countdown accuracy across process recreation and wall-clock changes. Active sessions are interrupted on reboot without awarding XP; paused sessions can be resumed. Finishing early awards no XP.
- An original Canvas-rendered creature with breathing, blinking, waving, sleeping, and celebration poses; six visible evolution stages. Reduced motion freezes animations. A lightweight painted-vector world gains a cottage and flowers with progression.
- Offline guided chat with contextual templates, emotional shortcuts, conversation history, and honest disclosure that it is not AI or mental healthcare. “I can't start” offers small steps that can be edited and saved to a task, rest, and short focus sessions.
- WorkManager task reminders and focus-completion work; optional notification permission; quiet hours; a global task-reminder pause; dark mode and companion naming in DataStore. Android may delay background notifications.
- No registration, ads, analytics, cloud provider, API keys, or network permission. Local Android backup is disabled. Uninstalling deletes data; there is no export yet.

Floating overlays, usage monitoring, recurring tasks, and several advanced requirements remain unimplemented. Compilation and unit checks do not establish all Android lifecycle and device behavior.

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
  services/   WorkManager scheduler and notifications
  ui/         Native screens, theme, original creature and landscape rendering
```

Repositories own data mutations; the ViewModel exposes observable state. Room transactions guard rewards and session completion. The initial database is version 1 with schema export configured. Keep the generated schema under `app/schemas` after the first successful build; introduce explicit migrations when changing it. Destructive migration fallback is intentionally absent.

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

Tests cover evolution boundaries, rewards, countdown math, snooze bounds, quiet hours, offline responses, actual Room persistence after reopening, concurrent exactly-once task completion, goal deletion, snoozing, and navigation across major screens. All 17 domain unit tests and all 7 API 26 instrumentation tests pass. See [validation evidence](docs/VALIDATION.md) for device-test status and the manual checks still needed for background timers, notification denial, reboot, battery restrictions, large fonts, and accessibility.

## GitHub Actions and APK download

[The Android workflow](.github/workflows/android.yml) configures Java and the Android SDK, runs unit tests, builds a debug APK, and uploads `KIN-debug-apk`. A second job runs database/navigation instrumentation tests on an API 35 emulator. Test reports are uploaded separately. Jobs use read-only repository permissions and no private signing keys.

After the branch is pushed and a workflow succeeds, open the run under **Actions → Android debug APK** and download the APK artifact. You can also trigger the workflow manually. This workflow has not run yet; no published artifact or download link exists.

## Permissions

- `POST_NOTIFICATIONS`: requested only in More when the user chooses to enable notifications. Denial leaves tasks, chat, progression, and the timer usable. System notification settings can be opened there too.
- `RECEIVE_BOOT_COMPLETED`: used by the WorkManager dependency to restore scheduled work. KIN does not auto-launch an activity after reboot.
- No overlay, usage access, accessibility service, exact alarm, microphone, Internet, storage, or location permission is requested by KIN.

WorkManager contributes normal `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, and `FOREGROUND_SERVICE` permissions plus its internal non-exported receiver permission in the merged manifest. KIN does not start a foreground service or acquire its own wake lock in this version.

Reminders use deferrable WorkManager jobs, not exact alarms. Quiet-hour task reminders are skipped rather than deferred. This build provides one scheduled notification per task, with snooze from the task editor; notification action buttons, firm mode, and reminder escalation are not implemented.

## Screenshots

No runtime screenshots are included because no app screenshots were captured during the automated device runs. After building, launch on a phone/emulator, finish onboarding, add your own sample tasks, and capture Home, Tasks, Chat, Focus, World, and More using Android Studio's device screenshot tool. Do not use the supplied reference as an app screenshot.

## Remaining work

See [ROADMAP.md](docs/ROADMAP.md) for honest acceptance status, including real overlays and usage monitoring, production artwork, advanced reminders, recurring tasks, more world unlocks, additional creature motions, accessibility/device validation, and release preparation. This early version is not a complete implementation of the master specification.
