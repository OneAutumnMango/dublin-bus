#!/usr/bin/env bash
#
# Builds the debug app, installs it on the running emulator and launches it.
#
#   ./scripts/install-app.sh                    # build, install, launch
#   ./scripts/install-app.sh --start-emulator   # boot the emulator first if one isn't running
#   ./scripts/install-app.sh --no-launch        # install only
#   ./scripts/install-app.sh --clean            # wipe build outputs first (a cold build - heavy on memory)
#   ./scripts/install-app.sh --keep-daemon      # leave the Gradle daemon running afterwards
#   ./scripts/install-app.sh --verbose          # show Gradle's full task output
#
# Afterwards the Gradle daemon is stopped, because an idle daemon still holds its whole heap and
# this machine has little RAM to spare alongside the emulator (docs/emulator-setup.md).

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly APP_ID="dev.dublinbustracker.app"
readonly SERVER_HEALTH_URL="http://localhost:8080/health"   # what the emulator's 10.0.2.2:8080 reaches

START_EMULATOR=0
LAUNCH=1
CLEAN=0
KEEP_DAEMON=0
VERBOSE=0

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'
else
  C_RESET=''; C_INFO=''; C_OK=''; C_WARN=''; C_ERR=''
fi

info() { printf '%s==>%s %s\n' "$C_INFO" "$C_RESET" "$*"; }
ok()   { printf '%s  ok%s %s\n' "$C_OK" "$C_RESET" "$*"; }
warn() { printf '%swarn%s %s\n' "$C_WARN" "$C_RESET" "$*" >&2; }
die()  { printf '%s err%s %s\n' "$C_ERR" "$C_RESET" "$*" >&2; exit 1; }

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --start-emulator) START_EMULATOR=1 ;;
    --no-launch)      LAUNCH=0 ;;
    --clean)          CLEAN=1 ;;
    --keep-daemon)    KEEP_DAEMON=1 ;;
    --verbose)        VERBOSE=1 ;;
    -h|--help)        usage ;;
    *)                die "unknown option: $1 (try --help)" ;;
  esac
  shift
done

[[ -x "$REPO_ROOT/gradlew" ]] || die "no gradlew - run ./scripts/setup-dev-env.sh first"
[[ -f "$REPO_ROOT/local.properties" ]] ||
  die "no local.properties - the Android SDK is not configured. Run ./scripts/setup-dev-env.sh"

SDK_ROOT=""
for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Android/sdk" "$HOME/Android/Sdk"; do
  if [[ -n "$candidate" && -x "$candidate/platform-tools/adb" ]]; then SDK_ROOT="$candidate"; break; fi
done
if [[ -n "$SDK_ROOT" ]]; then ADB="$SDK_ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"
else die "adb not found - run ./scripts/setup-dev-env.sh"; fi
readonly ADB

running_serial() { "$ADB" devices 2>/dev/null | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'; }

# --- an emulator to install on ------------------------------------------------------------

serial="$(running_serial)"
if [[ -z "$serial" ]]; then
  if (( START_EMULATOR )); then
    "$REPO_ROOT/scripts/run-emulator.sh"
    serial="$(running_serial)"
  fi
  [[ -n "$serial" ]] || die "no emulator is running - start one with ./scripts/run-emulator.sh (or pass --start-emulator)"
fi
ok "installing on $serial"

# --- build and install --------------------------------------------------------------------

cd "$REPO_ROOT"
gradle_args=(--console=plain)
(( VERBOSE )) || gradle_args+=(--quiet)   # still prints any error

stop_daemon() {
  (( KEEP_DAEMON )) && return 0
  ./gradlew --stop >/dev/null 2>&1 || true
}
trap stop_daemon EXIT

if (( CLEAN )); then
  info "Cleaning"
  ./gradlew "${gradle_args[@]}" clean
fi

info "Building and installing $APP_ID (the first build is slow; after that it is incremental)"
# ANDROID_SERIAL points Gradle's install step at the emulator even if a phone is plugged in too.
ANDROID_SERIAL="$serial" ./gradlew "${gradle_args[@]}" :app:installDebug
ok "installed"

# --- launch -------------------------------------------------------------------------------

if (( LAUNCH )); then
  info "Launching"
  "$ADB" -s "$serial" shell am force-stop "$APP_ID" >/dev/null
  "$ADB" -s "$serial" shell am start -n "$APP_ID/.MainActivity" >/dev/null
  ok "started $APP_ID"
fi

if ! curl -sf -o /dev/null --max-time 2 "$SERVER_HEALTH_URL" 2>/dev/null; then
  warn "no server answering on :8080 - the app will show \"Cannot reach 10.0.2.2\" until dublin-bus-server is running"
fi
