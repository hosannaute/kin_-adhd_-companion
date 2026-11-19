# Persistent timer and visual refresh (0.7)

## Public interfaces reviewed

These are public repository screenshots, not an exhaustive audit of each app's latest store build:

- [Loop Habit Tracker](https://github.com/iSoron/uhabits): [list screenshot](https://github.com/iSoron/uhabits/blob/dev/screenshots/1.png). Clear repeated rows, predictable completion controls and restrained visual hierarchy make scanning easy. KIN keeps its shorter three-task Home list and uses consistent circular completion targets.
- [Habitica Android](https://github.com/HabitRPG/habitica-android): [public store image](https://github.com/HabitRPG/habitica-android/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png). Companion identity, task grouping and visual progress can add warmth. KIN retains its original companion artwork and avoids copying Habitica's sprites, branding or reward mechanics.
- [Super Productivity](https://github.com/super-productivity/super-productivity): [light task-list screenshot](https://github.com/super-productivity/super-productivity/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2_task-list-light.png). Clear task/estimate hierarchy and a prominent timer action inform KIN's quieter task rows and active-timer summary.

No competitor artwork, fonts or source code are included in the APK. The implementation uses KIN's existing native artwork and Material components.

## Resulting design

A consistent lavender, white and sage palette, explicit typography and shared corner/spacing rules apply across Home, Tasks, Focus, Companion, chat, forms and Settings. Home has a compact companion banner, one action card, a live active-timer summary and a short task list. Focus uses a calm surface with a clear timer instead of text over a busy landscape. Task completion/edit/focus controls are distinct and large enough to tap. Search is available when the task list needs it. The world illustration uses softer curved mountains.

The scrolling-break control is now a small movable timer with its remaining time always visible. It stays on screen inside KIN as well as over ordinary apps while the break is running or paused. Expiry switches to one large reminder: KIN owns the dialog when foregrounded; the service owns the outside-app prompt. Starting a task collapses the floating controls before opening the focus picker. Preview testing is disabled during an active timer so a demo cannot replace it.

The compact control grows with the system font scale and retains its saved edge/relative height. Explicit Pause, End break, Start task and system permission choices remain in the user's control. Lock screens, sensitive apps that suppress overlays, force-stop and vendor battery restrictions can still hide or stop Android overlays; KIN does not bypass these restrictions.

## Acceptance

Device coverage includes the existing app-switching, docking, rotation, restoration and notification scenarios, plus persistent visibility across KIN's tabs and a 200% font-size interaction case. Local runtime screenshots and completed CI outcomes are recorded in [VALIDATION.md](VALIDATION.md). Broad physical-phone/OEM and accessibility acceptance remains separate from emulator evidence.
