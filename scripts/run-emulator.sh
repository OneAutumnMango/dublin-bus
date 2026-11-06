#!/usr/bin/env bash
#
# Boots the dev emulator and waits until Android has finished starting.
#
#   ./scripts/run-emulator.sh              # boot (fresh, no snapshot) and wait until it's usable
#   ./scripts/run-emulator.sh --headless   # no window (adb screencap still works)
#   ./scripts/run-emulator.sh --no-wait    # start it and return straight away
#   ./scripts/run-emulator.sh --gps        # once it's up, put its GPS in Dublin city centre
#   ./scripts/run-emulator.sh --stop       # shut a running emulator down
#   ./scripts/run-emulator.sh --force      # boot even if free memory looks too low to be safe
#
#   AVD_NAME=other-avd ./scripts/run-emulator.sh   # use a different virtual device
#
# The emulator uses about 4.3GB of RAM in practice (see docs/emulator-setup.md), which on this
# 7.7GB machine leaves little for a Gradle build: it refuses to start when free memory is very
# low, and tells you to stop the Gradle daemons when it is merely tight.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly AVD_NAME="${AVD_NAME:-dublinbus-api36}"   # keep in sync with setup-dev-env.sh
readonly BOOT_TIMEOUT_S=300
readonly MIN_FREE_MB=3500    # below this a boot risks taking the whole WSL VM down
readonly WARN_FREE_MB=5000
readonly LOG_FILE="$REPO_ROOT/build/emulator.log"
# Dublin city centre (longitude first - that is the order `emu geo fix` wants).
readonly DUBLIN_LON=-6.2603
readonly DUBLIN_LAT=53.3498

HEADLESS=0
WAIT=1
GPS=0
STOP=0
FORCE=0

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
    --headless) HEADLESS=1 ;;
    --no-wait)  WAIT=0 ;;
    --gps)      GPS=1 ;;
    --stop)     STOP=1 ;;
    --force)    FORCE=1 ;;
    -h|--help)  usage ;;
    *)          die "unknown option: $1 (try --help)" ;;
  esac
  shift
done

# --- find the SDK tools -------------------------------------------------------------------

SDK_ROOT=""
for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Android/sdk" "$HOME/Android/Sdk"; do
  if [[ -n "$candidate" && -x "$candidate/emulator/emulator" ]]; then SDK_ROOT="$candidate"; break; fi
done
[[ -n "$SDK_ROOT" ]] || die "no Android emulator found - run ./scripts/setup-dev-env.sh --with-emulator"

readonly EMULATOR="$SDK_ROOT/emulator/emulator"
if [[ -x "$SDK_ROOT/platform-tools/adb" ]]; then ADB="$SDK_ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"
else die "adb not found - run ./scripts/setup-dev-env.sh"; fi
readonly ADB

# The serial of a running emulator, or nothing.
running_serial() { "$ADB" devices 2>/dev/null | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'; }

# PIDs of this AVD's emulator: the `emulator` launcher, and the qemu process it starts a moment later.
# It can be up but still booting, so adb may not list it yet.
emulator_pids() { pgrep -f "(emulator|qemu-system).* -avd $AVD_NAME( |$)" || true; }

boot_done() { [[ "$("$ADB" -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; }

free_mb() { awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo; }

# --- stop ---------------------------------------------------------------------------------

if (( STOP )); then
  serial="$(running_serial)"
  if [[ -z "$serial" && -z "$(emulator_pids)" ]]; then ok "no emulator is running"; exit 0; fi
  info "Stopping the emulator"
  # Ask it nicely first; the emulator console is the clean way.
  [[ -n "$serial" ]] && "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
  for _ in $(seq 1 20); do [[ -z "$(emulator_pids)" ]] && break; sleep 1; done
  # A name-based kill can silently match nothing (docs/emulator-setup.md), so look at what's left.
  leftover="$(emulator_pids)"
  if [[ -n "$leftover" ]]; then
    warn "still running - killing PID(s): $(echo "$leftover" | tr '\n' ' ')"
    # shellcheck disable=SC2086
    kill $leftover 2>/dev/null || true
    sleep 2
    [[ -z "$(emulator_pids)" ]] || die "could not stop it - check: pgrep -af qemu-system"
  fi
  ok "stopped"
  exit 0
fi

# --- already up? --------------------------------------------------------------------------

serial="$(running_serial)"
if [[ -n "$serial" ]] && boot_done "$serial"; then
  ok "the emulator is already running ($serial)"
  (( GPS )) && "$ADB" -s "$serial" emu geo fix "$DUBLIN_LON" "$DUBLIN_LAT" >/dev/null && ok "GPS set to Dublin city centre"
  exit 0
fi
if [[ -n "$(emulator_pids)" ]]; then
  die "an emulator for $AVD_NAME is already starting - wait for it, or stop it with: $0 --stop"
fi

# --- checks before booting ----------------------------------------------------------------

"$EMULATOR" -list-avds | grep -qx "$AVD_NAME" ||
  die "no virtual device called $AVD_NAME - run ./scripts/setup-dev-env.sh --with-emulator"
[[ -e /dev/kvm ]] || die "/dev/kvm is missing - hardware acceleration is not available here"

free="$(free_mb)"
if (( free < MIN_FREE_MB && ! FORCE )); then
  die "only ${free}MB of memory is free and the emulator needs ~4300MB; booting now risks crashing WSL. Stop the Gradle daemons (./gradlew --stop) and the server, or use --force"
elif (( free < WARN_FREE_MB )); then
  warn "only ${free}MB free - tight. Don't start a cold Gradle build while the emulator is up (./gradlew --stop frees the daemon)"
fi

# --- boot ---------------------------------------------------------------------------------

cmd=("$EMULATOR" -avd "$AVD_NAME" -netdelay none -netspeed full -no-snapshot -no-audio)
(( HEADLESS )) && cmd+=(-no-window)

mkdir -p "$(dirname "$LOG_FILE")"
info "Starting $AVD_NAME$( ((HEADLESS)) && echo ' (headless)' )  - log: ${LOG_FILE#"$REPO_ROOT"/}"

if id -nG | tr ' ' '\n' | grep -qx kvm; then
  nohup "${cmd[@]}" >"$LOG_FILE" 2>&1 &
  launcher=$!
elif getent group kvm | grep -qw "$USER"; then
  # In the kvm group, but this shell predates it - `sg` runs the one command with the group.
  nohup sg kvm -c "$(printf '%q ' "${cmd[@]}")" >"$LOG_FILE" 2>&1 &
  launcher=$!
else
  die "you are not in the kvm group: sudo usermod -aG kvm $USER, then log in again"
fi
disown

(( WAIT )) || { ok "started - it will take a minute or two to boot"; exit 0; }

info "Waiting for Android to finish booting (up to ${BOOT_TIMEOUT_S}s)"
start=$SECONDS
serial=""
while (( SECONDS - start < BOOT_TIMEOUT_S )); do
  # Gone when the launcher we started has ended and nothing for this AVD is left running.
  if ! kill -0 "$launcher" 2>/dev/null && [[ -z "$(emulator_pids)" ]]; then
    tail -n 15 "$LOG_FILE" >&2 || true
    die "the emulator exited while starting - see $LOG_FILE"
  fi
  serial="$(running_serial)"
  if [[ -n "$serial" ]] && boot_done "$serial"; then
    ok "booted after $((SECONDS - start))s ($serial)"
    if (( GPS )); then
      "$ADB" -s "$serial" emu geo fix "$DUBLIN_LON" "$DUBLIN_LAT" >/dev/null
      ok "GPS set to Dublin city centre"
      info "an app only receives a fake fix while it is asking for location - see docs/emulator-setup.md"
    fi
    exit 0
  fi
  sleep 3
done
die "it did not finish booting within ${BOOT_TIMEOUT_S}s - see $LOG_FILE"
