#!/usr/bin/env bash
#
# Runs the Dublin Bus Tracker test suites.
#
#   ./scripts/test.sh                 # every module
#   ./scripts/test.sh --core-only     # pure-JVM domain tests; no Android SDK needed
#   ./scripts/test.sh --coverage      # also print the Kover coverage summary
#   ./scripts/test.sh --previews      # force-regenerate the widget PNGs
#   ./scripts/test.sh --record-goldens # re-record the widget golden images
#   ./scripts/test.sh --filter Reset  # only tests whose name matches
#   ./scripts/test.sh --lint          # also run detekt (style + static analysis)
#
# Exits non-zero if any test fails.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PREVIEW_DIR="app/build/previews"

CORE_ONLY=0
COVERAGE=0
PREVIEWS=0
LINT=0
RECORD=0
FILTER=""

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'
else
  C_RESET=''; C_INFO=''; C_OK=''; C_WARN=''; C_ERR=''
fi

info() { printf '%s==>%s %s\n' "$C_INFO" "$C_RESET" "$*"; }
ok()   { printf '%s  ok%s %s\n' "$C_OK" "$C_RESET" "$*"; }
warn() { printf '%swarn%s %s\n' "$C_WARN" "$C_RESET" "$*" >&2; }
die()  { printf '%s err%s %s\n' "$C_ERR" "$C_RESET" "$*" >&2; exit 1; }

usage() { sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --core-only) CORE_ONLY=1 ;;
    --coverage)  COVERAGE=1 ;;
    --previews)  PREVIEWS=1 ;;
    --record-goldens) RECORD=1 ;;
    --lint)      LINT=1 ;;
    --filter)    FILTER="${2:?--filter needs a pattern}"; shift ;;
    -h|--help)   usage ;;
    *)           die "unknown option: $1 (try --help)" ;;
  esac
  shift
done

[[ -x "$REPO_ROOT/gradlew" ]] || die "no gradlew - run ./scripts/setup-dev-env.sh first"
cd "$REPO_ROOT"

gradle_args=(--console=plain)
result_roots=(core data server)
test_tasks=(:core:test :data:test :server:test)

if (( ! CORE_ONLY )); then
  [[ -f local.properties ]] ||
    die "no local.properties - the Android SDK is not configured.
  Run ./scripts/setup-dev-env.sh, or use --core-only to skip the Android tests."
  test_tasks+=(:app:testDebugUnitTest)
  result_roots+=(app)
fi

# --tests is an option of each Test task, not of the aggregate lifecycle task.
tasks=()
for t in "${test_tasks[@]}"; do
  tasks+=("$t")
  [[ -n "$FILTER" ]] && tasks+=(--tests "*${FILTER}*")
done

if [[ -n "$FILTER" ]]; then
  info "Filtering on *${FILTER}*"
fi

# Previews are written as a side effect of a cacheable task, so an up-to-date
# run would silently leave stale PNGs behind.
if (( PREVIEWS )); then
  (( CORE_ONLY )) && die "--previews needs the Android module; drop --core-only"
  gradle_args+=(--rerun-tasks)
fi

if (( RECORD )); then
  (( CORE_ONLY )) && die "--record-goldens needs the Android module; drop --core-only"
  gradle_args+=(-Dgoldens=record)
  warn "recording goldens - look at app/src/test/goldens before committing them"
fi

info "Running tests"
./gradlew "${gradle_args[@]}" "${tasks[@]}"

# Deliberately after the tests: a wrapped line is worth knowing about, but not
# at the cost of hiding a real failure behind it.
if (( LINT )); then
  info "Running detekt"
  ./gradlew --console=plain detekt
  ok "no style or static analysis findings"
fi

# Gradle reports "BUILD SUCCESSFUL" even when it skipped everything as up-to-date,
# so count what the XML reports actually contain. Only the selected modules are
# counted; results from other modules linger on disk from earlier runs.
summarise() {
  local total=0 failures=0 skipped=0 f root
  for root in "${result_roots[@]}"; do
    [[ -d "$root/build/test-results" ]] || continue
    while IFS= read -r f; do
      total=$(( total + $(sed -n 's/.*tests="\([0-9]*\)".*/\1/p' "$f" | head -1) ))
      failures=$(( failures + $(sed -n 's/.*failures="\([0-9]*\)".*/\1/p' "$f" | head -1) ))
      skipped=$(( skipped + $(sed -n 's/.*skipped="\([0-9]*\)".*/\1/p' "$f" | head -1) ))
    done < <(find "$root/build/test-results" -name 'TEST-*.xml' 2>/dev/null)
  done

  (( total > 0 )) || die "no test results found - did anything actually run?"
  if (( failures > 0 )); then
    die "$failures of $total tests failed"
  fi
  ok "$total tests passed$( (( skipped > 0 )) && printf ', %s skipped' "$skipped" )"
}
summarise

if (( PREVIEWS )) && [[ -d "$PREVIEW_DIR" ]]; then
  ok "previews in $PREVIEW_DIR: $(find "$PREVIEW_DIR" -name '*.png' | wc -l | tr -d ' ') images"
fi

if (( COVERAGE )); then
  info "Coverage"
  # koverLog prints a bare percentage with no module name, so ask each in turn.
  for root in "${result_roots[@]}"; do
    pct=$(./gradlew --console=plain --quiet ":$root:koverLog" 2>/dev/null |
      sed -n 's/.*line coverage: \([0-9.]*\)%.*/\1/p' | head -1)
    [[ -n "$pct" ]] && ok "$(printf '%-5s %5.1f%% lines' "$root" "$pct")"
  done
fi
