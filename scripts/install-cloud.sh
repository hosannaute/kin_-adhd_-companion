#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
source scripts/cloud-env.sh
mkdir -p /workspace/.cache "$GRADLE_USER_HOME" "$ANDROID_HOME/cmdline-tools"
kin_download=$(mktemp -d /workspace/.cache/kin-install.XXXXXX)
trap 'rm -rf "$kin_download"' EXIT

if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    curl --fail --location --retry 2 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz' -o "$kin_download/jdk.tar.gz"
    printf '%s  %s\n' '8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711' "$kin_download/jdk.tar.gz" | sha256sum --check --status
    mkdir -p /workspace/.cache/jdk-17
    tar -xzf "$kin_download/jdk.tar.gz" -C /workspace/.cache/jdk-17
fi
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/android" ]]; then
    curl --fail --location --retry 2 'https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip' -o "$kin_download/sdk.zip"
    # This archive was first validated against Google's repository metadata checksum.
    printf '%s  %s\n' '0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8' "$kin_download/sdk.zip" | sha256sum --check --status
    unzip -q "$kin_download/sdk.zip" -d "$kin_download/sdk"
    if [[ -e "$ANDROID_HOME/cmdline-tools/latest" ]]; then
        echo 'Existing command-line tools are incomplete; inspect them before replacing.' >&2
        exit 1
    fi
    mv "$kin_download/sdk/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
for kin_package in 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'; do
    "$ANDROID_HOME/cmdline-tools/latest/bin/android" --no-metrics --sdk="$ANDROID_HOME" sdk install "$kin_package"
done
./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug
