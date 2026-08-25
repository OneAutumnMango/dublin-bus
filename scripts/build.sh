#!/usr/bin/env bash
#
# Builds the Dublin Bus Tracker APK.
#
#   ./scripts/build.sh                # debug APK
#   ./scripts/build.sh --release      # unsigned release APK
#   ./scripts/build.sh --clean        # wipe build outputs first
#   ./scripts/build.sh --install      # build debug, then install on the connected device
#   ./scripts/build.sh --quiet        # only print the resulting APK path
#
# The APK path is printed on the last line so it can be captured by other scripts.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly APP_ID="dev.dublinbustracker.app"

VARIANT=debug
CLEAN=0
INSTALL=0
QUIET=0

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'
else
  C_RESET=''; C_INFO=''; C_OK=''; C_WARN=''; C_ERR=''
fi

info() { (( QUIET )) || printf '%s==>%s %s\n' "$C_INFO" "$C_RESET" "$*"; }
ok()   { (( QUIET )) || printf '%s  ok%s %s\n' "$C_OK" "$C_RESET" "$*"; }
warn() { printf '%swarn%s %s\n' "$C_WARN" "$C_RESET" "$*" >&2; }
die()  { printf '%s err%s %s\n' "$C_ERR" "$C_RESET" "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

usage() { sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release) VARIANT=release ;;
    --debug)   VARIANT=debug ;;
    --clean)   CLEAN=1 ;;
    --install) INSTALL=1 ;;
    --quiet|-q) QUIET=1 ;;
    -h|--help) usage ;;
    *)         die "unknown option: $1 (try --help)" ;;
  esac
  shift
done

[[ -x "$REPO_ROOT/gradlew" ]] || die "no gradlew - run ./scripts/setup-dev-env.sh first"
[[ -f "$REPO_ROOT/local.properties" ]] ||
  die "no local.properties - the Android SDK is not configured. Run ./scripts/setup-dev-env.sh"

cd "$REPO_ROOT"

gradle_args=(--console=plain)
(( QUIET )) && gradle_args+=(--quiet)

if (( CLEAN )); then
  info "Cleaning"
  ./gradlew "${gradle_args[@]}" clean
fi

# assembleDebug / assembleRelease
task="assemble${VARIANT^}"
info "Building $VARIANT"
./gradlew "${gradle_args[@]}" ":app:$task"

apk="$(find "$REPO_ROOT/app/build/outputs/apk/$VARIANT" -name '*.apk' -print -quit 2>/dev/null || true)"
[[ -n "$apk" ]] || die "build reported success but no APK was found under app/build/outputs/apk/$VARIANT"

ok "$(du -h "$apk" | cut -f1) $(basename "$apk")"

if [[ "$VARIANT" == release ]]; then
  warn "this release APK is unsigned and will not install without a signing config"
fi

if (( INSTALL )); then
  have adb || die "adb not on PATH - run: source scripts/env.sh"
  [[ -n "$(adb devices | awk 'NR>1 && $2=="device"')" ]] || die "no device connected (adb devices)"
  info "Installing $APP_ID"
  adb install -r "$apk" >/dev/null
  ok "installed - add the widget from your launcher's widget picker"
fi

printf '%s\n' "$apk"
