#!/usr/bin/env bash
#
# Sets up everything needed to build and test Dublin Bus Tracker.
#
#   ./scripts/setup-dev-env.sh                 # JDK check + Gradle wrapper + Android SDK
#   ./scripts/setup-dev-env.sh --core-only     # skip the Android SDK (~1GB); :core tests only
#   ./scripts/setup-dev-env.sh --with-emulator # also install an AVD for on-device smoke tests
#   ./scripts/setup-dev-env.sh --check         # report what's installed, change nothing
#
# Safe to re-run: every step is a no-op if already satisfied.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Keep in sync with gradle/libs.versions.toml
readonly JAVA_MIN_VERSION=17
readonly GRADLE_VERSION="9.6.1"
readonly GRADLE_SHA256="9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14"
readonly SDK_PLATFORM=36
readonly SDK_BUILD_TOOLS="36.1.0"
readonly EMULATOR_IMAGE="system-images;android-${SDK_PLATFORM};google_apis;x86_64"
readonly AVD_NAME="dublinbus-api${SDK_PLATFORM}"

# Fallback only; the real URL is resolved from Google's manifest at runtime.
readonly CMDLINE_TOOLS_FALLBACK_URL="https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"
readonly CMDLINE_TOOLS_FALLBACK_SHA1="040d3996a65543d22ec4bf73e4c37aa37a8d4af4"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/sdk}}"
INSTALL_ANDROID=1
INSTALL_EMULATOR=0
CHECK_ONLY=0

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'
else
  C_RESET=''; C_INFO=''; C_OK=''; C_WARN=''; C_ERR=''
fi

info() { printf '%s==>%s %s\n' "$C_INFO" "$C_RESET" "$*"; }
ok()   { printf '%s  ok%s %s\n' "$C_OK" "$C_RESET" "$*"; }
warn() { printf '%swarn%s %s\n' "$C_WARN" "$C_RESET" "$*" >&2; }
die()  { printf '%s err%s %s\n' "$C_ERR" "$C_RESET" "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

# One scratch dir for the whole run; function-scoped RETURN traps leak into global scope.
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
scratch() { local d="$TMP_DIR/$1"; mkdir -p "$d"; printf '%s' "$d"; }

usage() { sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --core-only)     INSTALL_ANDROID=0 ;;
    --with-emulator) INSTALL_EMULATOR=1 ;;
    --check)         CHECK_ONLY=1 ;;
    --sdk-dir)       SDK_ROOT="${2:?--sdk-dir needs a path}"; shift ;;
    -h|--help)       usage ;;
    *)               die "unknown option: $1 (try --help)" ;;
  esac
  shift
done

detect_host() {
  case "$(uname -s)" in
    Linux)  HOST_OS=linux ;;
    Darwin) HOST_OS=macosx ;;
    *)      die "unsupported OS: $(uname -s). Use WSL2 on Windows." ;;
  esac
  HOST_ARCH="$(uname -m)"
  SHA1SUM=(sha1sum)
  have sha1sum || SHA1SUM=(shasum -a 1)
  SHA256SUM=(sha256sum)
  have sha256sum || SHA256SUM=(shasum -a 256)
}

verify_checksum() {
  local file="$1" expected="$2"; shift 2
  local actual
  actual="$("$@" "$file" | awk '{print $1}')"
  [[ "$actual" == "$expected" ]] || die "checksum mismatch for $file
  expected $expected
  actual   $actual"
}

# --- JDK -------------------------------------------------------------------

java_major() {
  have java || return 1
  java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

check_java() {
  info "Checking JDK (need $JAVA_MIN_VERSION+)"
  local major
  if ! major="$(java_major)" || [[ -z "$major" ]]; then
    die "no JDK found. Install one, then re-run:
  Ubuntu/Debian : sudo apt-get install -y openjdk-21-jdk
  macOS         : brew install --cask temurin@21
  Any platform  : https://adoptium.net/temurin/releases/?version=21"
  fi
  if (( major < JAVA_MIN_VERSION )); then
    die "JDK $major is too old; need $JAVA_MIN_VERSION+. Install openjdk-21-jdk and re-run."
  fi
  have javac || warn "javac not on PATH - you may have a JRE, not a JDK. Install the -jdk package."
  ok "JDK $major"
}

# --- Gradle wrapper --------------------------------------------------------

# The wrapper is committed, so this only fires on a brand-new tree.
ensure_gradle_wrapper() {
  info "Checking Gradle wrapper"
  if [[ -x "$REPO_ROOT/gradlew" && -f "$REPO_ROOT/gradle/wrapper/gradle-wrapper.jar" ]]; then
    ok "wrapper present"
    return
  fi
  (( CHECK_ONLY )) && { warn "wrapper missing"; return; }

  info "Bootstrapping Gradle $GRADLE_VERSION (one-off; the wrapper takes over afterwards)"
  local prefix="$HOME/.local/opt" dist="$HOME/.local/opt/gradle-${GRADLE_VERSION}"
  local tmp; tmp="$(scratch gradle)"
  if [[ ! -x "$dist/bin/gradle" ]]; then
    curl -fsSL -o "$tmp/gradle.zip" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    verify_checksum "$tmp/gradle.zip" "$GRADLE_SHA256" "${SHA256SUM[@]}"
    mkdir -p "$prefix"
    unzip -q "$tmp/gradle.zip" -d "$prefix"
  fi
  # Generated in a scratch dir because `gradle wrapper` refuses to run without a settings file.
  mkdir -p "$tmp/bootstrap" && touch "$tmp/bootstrap/settings.gradle.kts"
  (cd "$tmp/bootstrap" && "$dist/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" --quiet)
  mkdir -p "$REPO_ROOT/gradle/wrapper"
  cp "$tmp/bootstrap/gradlew" "$tmp/bootstrap/gradlew.bat" "$REPO_ROOT/"
  cp "$tmp/bootstrap/gradle/wrapper/gradle-wrapper.jar" \
     "$tmp/bootstrap/gradle/wrapper/gradle-wrapper.properties" "$REPO_ROOT/gradle/wrapper/"
  chmod +x "$REPO_ROOT/gradlew"
  # Defaults are 10s / no retries, which flakes on slow links.
  local props="$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties"
  sed -i.bak -e 's/^networkTimeout=.*/networkTimeout=120000/' -e 's/^retries=.*/retries=3/' "$props"
  rm -f "$props.bak"
  ok "wrapper generated"
}

# --- Android SDK -----------------------------------------------------------

resolve_cmdline_tools() {
  local manifest="$1"
  python3 - "$manifest" "$HOST_OS" <<'PY'
import sys, xml.etree.ElementTree as ET
manifest, host = sys.argv[1], sys.argv[2]
best = None
for pkg in ET.parse(manifest).getroot():
    if pkg.tag != 'remotePackage' or pkg.get('path') != 'cmdline-tools;latest':
        continue
    rev = pkg.find('revision')
    key = tuple(int(rev.findtext(p, '0')) for p in ('major', 'minor', 'micro'))
    for archive in pkg.iter('archive'):
        if archive.findtext('host-os') != host:
            continue
        c = archive.find('complete')
        cand = (key, c.findtext('url'), c.findtext('checksum'))
        if best is None or cand[0] > best[0]:
            best = cand
if best:
    print(best[1], best[2])
PY
}

install_android_sdk() {
  info "Checking Android SDK at $SDK_ROOT"
  local sdkmanager="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

  if [[ ! -x "$sdkmanager" ]]; then
    (( CHECK_ONLY )) && { warn "cmdline-tools not installed"; return; }
    info "Installing Android cmdline-tools (~180MB)"
    have unzip || die "unzip is required. Ubuntu: sudo apt-get install -y unzip"

    local url="" sha1="" tmp
    tmp="$(scratch sdk)"
    if have python3 && curl -fsSL -o "$tmp/repo.xml" https://dl.google.com/android/repository/repository2-3.xml; then
      read -r url sha1 < <(resolve_cmdline_tools "$tmp/repo.xml") || true
    fi
    if [[ -z "$url" ]]; then
      [[ "$HOST_OS" == linux ]] || die "could not resolve cmdline-tools for $HOST_OS (python3 required)"
      warn "falling back to pinned cmdline-tools build"
      url="$CMDLINE_TOOLS_FALLBACK_URL"; sha1="$CMDLINE_TOOLS_FALLBACK_SHA1"
    else
      [[ "$url" == http* ]] || url="https://dl.google.com/android/repository/$url"
    fi

    curl -fsSL --progress-bar -o "$tmp/tools.zip" "$url"
    verify_checksum "$tmp/tools.zip" "$sha1" "${SHA1SUM[@]}"
    # The zip contains a bare cmdline-tools/ dir; sdkmanager requires it at cmdline-tools/latest/.
    unzip -q "$tmp/tools.zip" -d "$tmp/x"
    mkdir -p "$SDK_ROOT/cmdline-tools"
    rm -rf "$SDK_ROOT/cmdline-tools/latest"
    mv "$tmp/x/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  fi
  ok "cmdline-tools"

  (( CHECK_ONLY )) && return

  local packages=("platform-tools" "platforms;android-${SDK_PLATFORM}" "build-tools;${SDK_BUILD_TOOLS}")
  if (( INSTALL_EMULATOR )); then
    packages+=("emulator" "$EMULATOR_IMAGE")
  fi

  info "Accepting SDK licenses"
  yes 2>/dev/null | "$sdkmanager" --sdk_root="$SDK_ROOT" --licenses >/dev/null || true

  info "Installing: ${packages[*]}"
  "$sdkmanager" --sdk_root="$SDK_ROOT" "${packages[@]}" >/dev/null
  ok "SDK packages installed"

  (( INSTALL_EMULATOR )) && create_avd
  return 0
}

create_avd() {
  info "Setting up emulator"
  if [[ ! -e /dev/kvm ]]; then
    warn "/dev/kvm missing - the emulator will be unusably slow.
     Most development needs no emulator: JVM tests and widget screenshots run without one."
  elif [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
    warn "/dev/kvm exists but is not accessible. Grant access with:
     sudo usermod -aG kvm \"\$USER\"   # then log out and back in"
  fi

  local avdmanager="$SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
  if "$avdmanager" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
    ok "AVD $AVD_NAME already exists"
    return
  fi
  echo no | "$avdmanager" create avd --name "$AVD_NAME" --package "$EMULATOR_IMAGE" --device pixel_6 >/dev/null
  ok "AVD $AVD_NAME created"
}

# --- Project wiring --------------------------------------------------------

write_local_properties() {
  local file="$REPO_ROOT/local.properties"
  # Machine-specific and gitignored; AGP reads sdk.dir from here.
  if [[ -f "$file" ]] && grep -q "^sdk.dir=$SDK_ROOT$" "$file"; then
    ok "local.properties"
  else
    printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$file"
    ok "wrote local.properties"
  fi

  if ! grep -q '^gtfsr.apiKey=' "$file" 2>/dev/null; then
    printf 'gtfsr.apiKey=\n' >> "$file"
    warn "gtfsr.apiKey is empty in local.properties - sign up and subscribe at
     https://developer.nationaltransport.ie/signup, then paste your key in
     (realtime arrivals will not work until you do; the key can take ~15 min to activate)"
  fi
  if ! grep -q '^gtfsr.apiKey2=' "$file" 2>/dev/null; then
    printf 'gtfsr.apiKey2=\n' >> "$file"
  fi
}

write_env_file() {
  local file="$REPO_ROOT/scripts/env.sh"
  cat > "$file" <<EOF
# Generated by setup-dev-env.sh - source this, or copy into your shell rc.
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="\$ANDROID_SDK_ROOT/platform-tools:\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/emulator:\$PATH"
EOF
  ok "wrote scripts/env.sh"
}

verify() {
  info "Verifying build"
  if [[ ! -x "$REPO_ROOT/gradlew" ]]; then
    warn "no gradlew yet - skipping"
    return
  fi
  if (cd "$REPO_ROOT" && ./gradlew --quiet projects >/dev/null 2>&1); then
    ok "Gradle build configures cleanly"
  else
    warn "Gradle could not configure the build yet (expected until the modules exist)"
  fi
}

main() {
  detect_host
  info "Host: $HOST_OS/$HOST_ARCH  repo: $REPO_ROOT"
  check_java
  ensure_gradle_wrapper
  if (( INSTALL_ANDROID )); then
    install_android_sdk
    (( CHECK_ONLY )) || { write_local_properties; write_env_file; }
  else
    info "Skipping Android SDK (--core-only)"
  fi
  (( CHECK_ONLY )) && { info "Check complete."; return; }
  verify

  cat <<EOF

${C_OK}Setup complete.${C_RESET}

  source scripts/env.sh          # adds adb/sdkmanager to PATH for this shell
  ./scripts/fetch-gtfs-data.sh    # download and filter the static GTFS stop data
  ./gradlew :core:test           # fast pure-JVM domain tests, no Android needed
  ./gradlew :app:testDebugUnitTest

EOF
}

main "$@"
