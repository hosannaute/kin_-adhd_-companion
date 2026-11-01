#!/usr/bin/env bash
# A pinned, verified SDK installation shared by the APK and emulator CI jobs.
set -euo pipefail
kin_ci_cache="${KIN_CI_SDK_CACHE:-${RUNNER_TEMP:-/tmp}/kin-sdk-tools}"
kin_ci_sdk="${ANDROID_HOME:-${RUNNER_TEMP:-/tmp}/android-sdk}"
mkdir -p "$kin_ci_cache" "$kin_ci_sdk"
if [[ ! -x "$kin_ci_cache/cmdline-tools/bin/android" ]]; then
    curl --fail --location --retry 2 'https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip' -o "$kin_ci_cache/tools.zip"
    printf '%s  %s\n' '0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8' "$kin_ci_cache/tools.zip" | sha256sum --check --status
    unzip -q "$kin_ci_cache/tools.zip" -d "$kin_ci_cache"
fi
for kin_ci_package in 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'; do
    "$kin_ci_cache/cmdline-tools/bin/android" --no-metrics --sdk="$kin_ci_sdk" sdk install "$kin_ci_package"
done
if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$kin_ci_sdk" "$kin_ci_sdk" >> "$GITHUB_ENV"
    printf '%s\n%s\n' "$kin_ci_cache/cmdline-tools/bin" "$kin_ci_sdk/platform-tools" >> "$GITHUB_PATH"
fi
