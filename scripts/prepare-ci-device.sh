#!/usr/bin/env bash
set -euo pipefail
kin_ci_api="${1:?Android API level required}"
kin_ci_provisioned="$(adb shell settings get global device_provisioned | tr -d '\r')"
printf '::notice title=Emulator setup::device_provisioned before tests=%s\n' "$kin_ci_provisioned"
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1
case "$kin_ci_api" in
  26|27)
    # Android 8 initializes a sticky notification-effects disable flag on first
    # boot before provisioning. A later boot reads the completed setup state.
    # See NotificationManagerService.onStart in android-8.0.0_r1, lines 1175–1180.
    kin_ci_effects_before="$(adb shell dumpsys notification | python3 -c 'import sys; print(next((s.strip() for s in sys.stdin if "mDisableNotificationEffects=" in s), "not reported"))')"
    printf '::notice title=Legacy notification effects::before reboot: %s\n' "$kin_ci_effects_before"
    adb reboot
    timeout 60s adb wait-for-device
    kin_ci_booted=0
    for kin_ci_attempt in $(seq 1 240); do
      kin_ci_booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      [[ "$kin_ci_booted" == 1 ]] && break
      sleep 1
    done
    [[ "$kin_ci_booted" == 1 ]] || { printf 'Emulator did not finish rebooting\n' >&2; exit 1; }
    kin_ci_effects_after="$(adb shell dumpsys notification | python3 -c 'import sys; print(next((s.strip() for s in sys.stdin if "mDisableNotificationEffects=" in s), "not reported"))')"
    printf '::notice title=Legacy notification effects::after reboot: %s\n' "$kin_ci_effects_after"
    ;;
esac
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_MENU
