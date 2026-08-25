#!/usr/bin/env bash
#
# Removes what the other scripts created.
#
#   ./scripts/uninstall.sh             # app from device + build outputs + generated files
#   ./scripts/uninstall.sh --app-only  # only remove the app from the connected device
#   ./scripts/uninstall.sh --sdk       # also delete the Android SDK and the AVD (~1GB+)
#   ./scripts/uninstall.sh --dry-run   # list what would be removed, delete nothing
#   ./scripts/uninstall.sh --yes       # skip the confirmation prompt
#
# Source files are never touched.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly APP_ID="dev.dublinbustracker.app"
readonly AVD_NAME="dublinbus-api36"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/sdk}}"
APP_ONLY=0
REMOVE_SDK=0
DRY_RUN=0
ASSUME_YES=0

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

usage() { sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --app-only) APP_ONLY=1 ;;
    --sdk)      REMOVE_SDK=1 ;;
    --dry-run)  DRY_RUN=1 ;;
    --yes|-y)   ASSUME_YES=1 ;;
    --sdk-dir)  SDK_ROOT="${2:?--sdk-dir needs a path}"; shift ;;
    -h|--help)  usage ;;
    *)          die "unknown option: $1 (try --help)" ;;
  esac
  shift
done

confirm() {
  (( ASSUME_YES )) && return 0
  (( DRY_RUN )) && return 0
  local reply
  printf '%sThis will delete the items listed above.%s Continue? [y/N] ' "$C_WARN" "$C_RESET"
  read -r reply || reply=""
  [[ "$reply" == [yY] ]]
}

remove_path() {
  local path="$1" label="$2"
  [[ -e "$path" ]] || return 0
  rm -rf "$path"
  ok "removed $label"
}

uninstall_app() {
  have adb || { warn "adb not on PATH - skipping device uninstall (source scripts/env.sh)"; return; }
  if [[ -z "$(adb devices | awk 'NR>1 && $2=="device"')" ]]; then
    warn "no device connected - skipping device uninstall"
    return
  fi
  if ! adb shell pm list packages | grep -q "^package:$APP_ID$"; then
    ok "$APP_ID is not installed"
    return
  fi
  if (( DRY_RUN )); then
    printf '  would uninstall  %s from the connected device\n' "$APP_ID"
    return
  fi
  adb uninstall "$APP_ID" >/dev/null && ok "uninstalled $APP_ID"
}

# Guard against a mistyped --sdk-dir turning this into a very bad afternoon.
looks_like_sdk() {
  local dir="$1"
  [[ -n "$dir" && "$dir" != "/" && "$dir" != "$HOME" && -d "$dir" ]] &&
    [[ -d "$dir/platforms" || -d "$dir/cmdline-tools" || -d "$dir/platform-tools" ]]
}

remove_sdk() {
  if ! looks_like_sdk "$SDK_ROOT"; then
    warn "$SDK_ROOT does not look like an Android SDK - not touching it"
    return
  fi
  if have avdmanager && avdmanager list avd 2>/dev/null | grep -q "$AVD_NAME"; then
    avdmanager delete avd -n "$AVD_NAME" >/dev/null 2>&1 && ok "deleted AVD $AVD_NAME"
  fi
  remove_path "$SDK_ROOT" "Android SDK ($SDK_ROOT)"
}

main() {
  info "Planning removal"

  uninstall_app
  (( APP_ONLY )) && { info "Done (--app-only)."; return; }

  printf '\nThe following will be removed:\n'
  local targets=(
    "$REPO_ROOT/build"
    "$REPO_ROOT/app/build"
    "$REPO_ROOT/core/build"
    "$REPO_ROOT/data/build"
    "$REPO_ROOT/server/build"
    "$REPO_ROOT/build-logic/convention/build"
    "$REPO_ROOT/.gradle"
    "$REPO_ROOT/local.properties"
    "$REPO_ROOT/scripts/env.sh"
    "$REPO_ROOT/data/src/main/assets/gtfs_static.json"
  )
  local found=0 t
  for t in "${targets[@]}"; do
    [[ -e "$t" ]] && { printf '  %s\n' "${t#"$REPO_ROOT"/}"; found=1; }
  done
  if (( REMOVE_SDK )) && looks_like_sdk "$SDK_ROOT"; then
    printf '  %s  %s(the whole Android SDK)%s\n' "$SDK_ROOT" "$C_WARN" "$C_RESET"
    found=1
  fi
  (( found )) || { ok "nothing to remove"; return; }
  printf '\n'

  (( DRY_RUN )) && { info "Dry run - nothing was removed."; return; }

  confirm || { info "Cancelled - nothing was removed."; return; }

  for t in "${targets[@]}"; do
    remove_path "$t" "${t#"$REPO_ROOT"/}"
  done
  (( REMOVE_SDK )) && remove_sdk

  ok "Done. Re-run ./scripts/setup-dev-env.sh to start again."
}

main "$@"
