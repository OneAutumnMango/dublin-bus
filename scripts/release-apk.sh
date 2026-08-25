#!/usr/bin/env bash
#
# Builds a debug APK and copies it into apks/ (gitignored) under a name tagging
# which feature it represents, so a batch of them can be tested later.
#
#   ./scripts/release-apk.sh <feature-slug>
#
# Example: ./scripts/release-apk.sh favourites -> apks/dublin-bus-tracker-favourites-<date>.apk

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SLUG="${1:?usage: release-apk.sh <feature-slug>}"

mkdir -p "$REPO_ROOT/apks"
apk="$("$REPO_ROOT/scripts/build.sh" --quiet)"

dest="$REPO_ROOT/apks/dublin-bus-tracker-${SLUG}-$(date +%Y%m%d-%H%M%S).apk"
cp "$apk" "$dest"
printf '%s\n' "$dest"
