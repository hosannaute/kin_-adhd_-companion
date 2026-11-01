#!/usr/bin/env bash
# Source in each cloud shell. These paths deliberately stay outside the checkout.
export ANDROID_HOME="${ANDROID_HOME:-/workspace/.cache/android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.cache/kin-gradle}"
export JAVA_HOME="${JAVA_HOME:-/workspace/.cache/jdk-17/jdk-17.0.21+9}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Java does not automatically honor HTTPS_PROXY. Preserve the platform proxy and CA trust.
if [[ -n "${HTTPS_PROXY:-}" && "${JAVA_TOOL_OPTIONS:-}" != *-Dhttps.proxyHost=* ]]; then
    read -r kin_proxy_host kin_proxy_port < <(python3 - <<'PY'
import os, urllib.parse
p = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
print(p.hostname, p.port or 80)
PY
)
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=$kin_proxy_host -Dhttps.proxyPort=$kin_proxy_port -Dhttp.proxyHost=$kin_proxy_host -Dhttp.proxyPort=$kin_proxy_port -Dhttp.nonProxyHosts=localhost|127.*"
    unset kin_proxy_host kin_proxy_port
fi
if [[ -f /etc/ssl/certs/java/cacerts && "${JAVA_TOOL_OPTIONS:-}" != *-Djavax.net.ssl.trustStore=* ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
