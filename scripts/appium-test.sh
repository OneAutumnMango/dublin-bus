#!/usr/bin/env bash
#
# Runs the Appium smoke tests (appium/) against the dev emulator, with a fake server on :8080.
#
#   ./scripts/appium-test.sh                       # build, boot the emulator, run every test
#   ./scripts/appium-test.sh --headless            # emulator without a window
#   ./scripts/appium-test.sh --keep-emulator       # leave the emulator running afterwards
#   ./scripts/appium-test.sh -k favourite -x       # anything else goes to pytest
#
# Needs :8080 and :4723 free (stop the real server first: docker stop dublin-bus-server).
# The exit code is pytest's. On exit it stops Appium, and the emulator if this script booted it
# (an emulator that was already running is left alone).
#
# Order matters on this 7.7GB machine (docs/emulator-setup.md): the APK is built first, the Gradle
# daemon is stopped, and only then does the emulator boot. Gradle never runs beside the emulator.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly APPIUM_DIR="$REPO_ROOT/appium"
readonly APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
readonly BASE_URL_LINE="server.baseUrl=http://10.0.2.2:8080/"
readonly APPIUM_LOG="$REPO_ROOT/build/appium.log"
readonly APPIUM_TIMEOUT_S=60

HEADLESS=()
KEEP_EMULATOR=0
PYTEST_ARGS=()

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

for arg in "$@"; do
  case "$arg" in
    --headless)       HEADLESS=(--headless) ;;
    --keep-emulator)  KEEP_EMULATOR=1 ;;
    -h|--help)        usage ;;
    *)                PYTEST_ARGS+=("$arg") ;;
  esac
done

cd "$REPO_ROOT"
# shellcheck source=/dev/null
[[ -f scripts/env.sh ]] && source scripts/env.sh

listening() { ss -ltn "sport = :$1" 2>/dev/null | tail -n +2 | grep -q .; }

# --- checks before touching anything ------------------------------------------------------

listening 8080 && die "port 8080 busy - the tests serve a fake server there. Stop the real one: docker stop dublin-bus-server"
listening 4723 && die "port 4723 busy - a stale Appium? Check: pgrep -af appium"

node_major() { node -v 2>/dev/null | sed 's/^v//; s/\..*//'; }
if (( $(node_major || echo 0) < 22 )); then
  # nvm's scripts are not safe under `set -u`.
  if [[ -s "$HOME/.nvm/nvm.sh" ]]; then
    set +u; source "$HOME/.nvm/nvm.sh"; nvm use 22 >/dev/null || { set -u; die "nvm use 22 failed - nvm install 22"; }; set -u
  fi
  (( $(node_major || echo 0) >= 22 )) || die "Appium 4 needs Node >= 22 (found $(node -v 2>/dev/null || echo none)). Install it, e.g. nvm install 22"
fi
ok "node $(node -v)"

# The build bakes server.baseUrl into the APK, so never edit a different one silently.
if grep -q '^server\.baseUrl=' local.properties 2>/dev/null; then
  grep -qxF "$BASE_URL_LINE" local.properties ||
    die "local.properties sets a different server.baseUrl; the tests need: $BASE_URL_LINE"
else
  info "Adding $BASE_URL_LINE to local.properties"
  [[ ! -s local.properties || -z "$(tail -c1 local.properties)" ]] || echo >> local.properties
  echo "$BASE_URL_LINE" >> local.properties
fi

# --- build before the emulator exists -----------------------------------------------------

info "Building the debug APK"
./gradlew --console=plain :app:assembleDebug
./gradlew --stop >/dev/null 2>&1 || true
[[ -f "$APK" ]] || die "build produced no APK at $APK"

# --- python and node tooling --------------------------------------------------------------

if [[ ! -x "$APPIUM_DIR/.venv/bin/python" ]]; then
  info "Creating appium/.venv"
  python3 -m venv "$APPIUM_DIR/.venv"
fi
"$APPIUM_DIR/.venv/bin/pip" install -q -r "$APPIUM_DIR/requirements.txt"

cd "$APPIUM_DIR"
export APPIUM_HOME="$APPIUM_DIR/.appium"
[[ -d node_modules ]] || { info "npm ci"; npm ci; }
[[ -d "$APPIUM_HOME/node_modules/appium-uiautomator2-driver" ]] || { info "Installing the uiautomator2 driver"; npm run drivers; }

# --- cleanup ------------------------------------------------------------------------------

APPIUM_PID=""
BOOTED=0
cleanup() {
  [[ -z "$APPIUM_PID" ]] || { kill "$APPIUM_PID" 2>/dev/null || true; }
  pkill -f "$APPIUM_DIR/node_modules/.*appium" 2>/dev/null || true
  if (( BOOTED && ! KEEP_EMULATOR )); then "$REPO_ROOT/scripts/run-emulator.sh" --stop || true; fi
}
trap cleanup EXIT

# --- emulator -----------------------------------------------------------------------------

if adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { f=1 } END { exit !f }'; then
  ok "emulator already running - leaving it alone afterwards"
else
  BOOTED=1
  "$REPO_ROOT/scripts/run-emulator.sh" ${HEADLESS[@]+"${HEADLESS[@]}"}
fi
info "Installing the APK"
adb install -r "$APK" >/dev/null
ok "installed"

# --- appium -------------------------------------------------------------------------------

mkdir -p "$(dirname "$APPIUM_LOG")"
info "Starting Appium - log: ${APPIUM_LOG#"$REPO_ROOT"/}"
npx appium >"$APPIUM_LOG" 2>&1 &
APPIUM_PID=$!
for _ in $(seq 1 "$APPIUM_TIMEOUT_S"); do
  curl -sf -o /dev/null --max-time 2 http://127.0.0.1:4723/status && break
  kill -0 "$APPIUM_PID" 2>/dev/null || { tail -n 15 "$APPIUM_LOG" >&2; die "Appium exited while starting"; }
  sleep 1
done
curl -sf -o /dev/null --max-time 2 http://127.0.0.1:4723/status || die "Appium not answering after ${APPIUM_TIMEOUT_S}s - see $APPIUM_LOG"
ok "Appium is up"

# --- tests --------------------------------------------------------------------------------

info "Running pytest"
rc=0
.venv/bin/python -m pytest ${PYTEST_ARGS[@]+"${PYTEST_ARGS[@]}"} || rc=$?
exit "$rc"
