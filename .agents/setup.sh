#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"

if [[ "$(uname -s)" == "Linux" ]]; then
  # Ensure non-interactive bash shells source the full ~/.bashrc
  sed -i '/case \$- in/,/esac/d' "$HOME/.bashrc" 2>/dev/null || true

  # Resolve JAVA_HOME (prefer JDK 17 if installed)
  if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]]; then
      JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    elif command -v javac >/dev/null 2>&1; then
      JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
    fi
  fi

  # Build a user-space PKCS12 Java truststore when system Java cacerts is missing or a proxy CA is present
  TRUSTSTORE="$HOME/.local/share/java/cacerts"
  if [[ -f "/etc/ssl/certs/ca-certificates.crt" && ( ! -e "/etc/ssl/certs/java/cacerts" || -n "${HTTPS_PROXY:-${HTTP_PROXY:-}}" ) ]]; then
    if [[ ! -f "$TRUSTSTORE" || "/etc/ssl/certs/ca-certificates.crt" -nt "$TRUSTSTORE" ]]; then
      mkdir -p "$(dirname "$TRUSTSTORE")"
      TMP_TRUSTSTORE="$(mktemp -u "$TRUSTSTORE.tmp.XXXXXX")"
      python3 - "$TRUSTSTORE" "$TMP_TRUSTSTORE" << 'EOF'
import os, re, subprocess, sys, tempfile

dest, tmp_store = sys.argv[1], sys.argv[2]
with open('/etc/ssl/certs/ca-certificates.crt', 'r', encoding='utf-8') as f:
    pem = f.read()

certs = re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', pem, re.S)
if not certs:
    sys.exit(0)

with tempfile.TemporaryDirectory() as tmpdir:
    for i, cert in enumerate(certs):
        cert_path = os.path.join(tmpdir, f'cert_{i}.pem')
        with open(cert_path, 'w', encoding='utf-8') as cf:
            cf.write(cert + '\n')
        subprocess.run([
            'keytool', '-importcert', '-noprompt',
            '-alias', f'ca_{i}',
            '-file', cert_path,
            '-keystore', tmp_store,
            '-storepass', 'changeit',
            '-storetype', 'PKCS12',
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

os.replace(tmp_store, dest)
EOF
    fi
  fi

  # Install Android command-line tools if not already present
  mkdir -p "$SDK_DIR/cmdline-tools"
  if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
    ZIP_NAME="$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml | grep -o 'commandlinetools-linux-[0-9]*_latest\.zip' | head -n 1)"
    TMP_ZIP="$(mktemp /tmp/cmdline-tools.XXXXXX.zip)"
    TMP_EXTRACT="$(mktemp -d /tmp/cmdline-tools-extract.XXXXXX)"
    trap 'rm -rf "$TMP_ZIP" "$TMP_EXTRACT"' EXIT
    curl -fsSL "https://dl.google.com/android/repository/${ZIP_NAME}" -o "$TMP_ZIP"
    unzip -q "$TMP_ZIP" -d "$TMP_EXTRACT"
    rm -rf "$SDK_DIR/cmdline-tools/latest"
    mv "$TMP_EXTRACT/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -rf "$TMP_ZIP" "$TMP_EXTRACT"
    trap - EXIT
  fi

  # Persist environment exports idempotently in ~/.bashrc
  python3 - "$HOME/.bashrc" "${JAVA_HOME:-}" "$SDK_DIR" << 'EOF'
import re, sys
from pathlib import Path

bashrc = Path(sys.argv[1])
java_home = sys.argv[2]
sdk_dir = sys.argv[3]

text = bashrc.read_text(encoding='utf-8') if bashrc.exists() else ''
text = re.sub(r'# >>> car-logging env-setup >>>.*?# <<< car-logging env-setup <<<\n?', '', text, flags=re.S)
if text and not text.endswith('\n'):
    text += '\n'

lines = ['# >>> car-logging env-setup >>>']
if java_home:
    lines.append(f'export JAVA_HOME="{java_home}"')
lines.extend([
    f'export ANDROID_HOME="{sdk_dir}"',
    f'export ANDROID_SDK_ROOT="{sdk_dir}"',
    'case ":$PATH:" in',
    '  *":$ANDROID_HOME/cmdline-tools/latest/bin:"*) ;;',
    '  *) export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH" ;;',
    'esac',
    'if [ -f "$HOME/.local/share/java/cacerts" ]; then',
    '  _java_opts="-Djavax.net.ssl.trustStore=$HOME/.local/share/java/cacerts -Djavax.net.ssl.trustStorePassword=changeit"',
    '  _proxy_url="${HTTPS_PROXY:-${HTTP_PROXY:-}}"',
    '  if [ -n "$_proxy_url" ]; then',
    '    _proxy_hostport="${_proxy_url#*://}"',
    '    _proxy_hostport="${_proxy_hostport%%/*}"',
    '    _proxy_host="${_proxy_hostport%%:*}"',
    '    _proxy_port="${_proxy_hostport##*:}"',
    '    if [ -n "$_proxy_host" ] && [ -n "$_proxy_port" ] && [ "$_proxy_host" != "$_proxy_port" ]; then',
    '      _java_opts="$_java_opts -Dhttp.proxyHost=$_proxy_host -Dhttp.proxyPort=$_proxy_port -Dhttps.proxyHost=$_proxy_host -Dhttps.proxyPort=$_proxy_port -Dhttp.nonProxyHosts=localhost|127.0.0.1"',
    '    fi',
    '  fi',
    '  export JAVA_TOOL_OPTIONS="$_java_opts"',
    '  unset _java_opts _proxy_url _proxy_hostport _proxy_host _proxy_port',
    'fi',
    '# <<< car-logging env-setup <<<',
    '',
])
bashrc.write_text(text + '\n'.join(lines), encoding='utf-8')
EOF

  export ANDROID_HOME="$SDK_DIR"
  export ANDROID_SDK_ROOT="$SDK_DIR"
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

  if [[ ! -d "$SDK_DIR/platform-tools" || ! -d "$SDK_DIR/platforms/android-35" || ! -d "$SDK_DIR/build-tools/35.0.0" ]]; then
    yes | sdkmanager --licenses >/dev/null || true
    sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
  fi
fi

# Generate gitignored local.properties if missing
if [[ ! -f "$REPO_ROOT/local.properties" ]]; then
  printf 'sdk.dir=%s\n' "$SDK_DIR" > "$REPO_ROOT/local.properties"
fi

# Generate a placeholder gitignored app/google-services.json if missing so Gradle builds/tests run in cloud VMs
if [[ ! -f "$REPO_ROOT/app/google-services.json" ]]; then
  mkdir -p "$REPO_ROOT/app"
  cat > "$REPO_ROOT/app/google-services.json" << 'EOF'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "car-logger-track",
    "storage_bucket": "car-logger-track.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": {
          "package_name": "com.example.car_logger"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "placeholder-api-key-for-ci-and-unit-tests"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000001",
        "android_client_info": {
          "package_name": "com.example.car_logger.debug"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "placeholder-api-key-for-ci-and-unit-tests"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
EOF
fi
