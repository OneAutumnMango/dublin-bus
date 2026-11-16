# Appium smoke tests - design

Approved in conversation 2026-11-16.

## Goal
Black-box smoke tests that drive the installed debug APK through Appium (UiAutomator2), run locally
and as a gate before a release build.

## Scope
Python 3.12 + pytest. Smoke flows only: launch, search a stop and open its arrivals, toggle a
favourite, settings "Save & test".

Non-goals: MapLibre map, widget, offline maps, iOS, running on every PR, a contract test against
the real server (the fake can drift from the real API; fixtures are typed against the
`TransitApiClient` DTOs to limit that).

## Layout (`appium/`)
- `fake_server.py` - stdlib `http.server`, canned JSON for `/v1/status`, `/v1/stops`,
  `/v1/stops/{id}`, `/v1/stops/{id}/arrivals`, `/v1/routes`. Binds 0.0.0.0:8080 (emulator sees it
  at 10.0.2.2:8080, already the `server.baseUrl` in `local.properties`). Fixtures checked in.
- `conftest.py` - starts the fake server, opens the Appium session on the debug APK, clears app
  data between tests, uses explicit waits only (no sleeps).
- `test_smoke.py` - 4-5 tests.
- `requirements.txt` - pinned Appium-Python-Client, pytest.
- Appium server + `uiautomator2` driver installed via npm into `appium/node_modules` (no globals).
- `scripts/appium-test.sh` - memory guard as in `run-emulator.sh`, boots the emulator if needed,
  builds/installs the debug APK, runs pytest, then `./gradlew --stop`. Locally it uses the existing
  `dublinbus-api36` AVD (no extra multi-GB image).

## App change (only production code)
`semantics { testTagsAsResourceId = true }` once at the root (`MainActivity`), plus `testTag`s on
the elements the tests drive: nav items, search field, arrival rows, favourite star, settings
address field and Save button. Fallback if tags do not surface: match `content-desc`/text.
Verify this on the emulator first (task 1).

## CI
- `.github/workflows/appium.yml`: `workflow_call` + `workflow_dispatch`.
- `release.yml`: new `appium` job in parallel with `test`; `apk` becomes `needs: [test, appium]`,
  so a failing smoke test publishes no release. Re-run the failed job from the Actions UI.
- Runner: `ubuntu-latest` with KVM, `reactivecircus/android-emulator-runner`, `google_atd`
  x86_64 API 34 image, `-no-window -no-audio -gpu swiftshader_indirect`, AVD snapshot cache,
  animations off, wait for `sys.boot_completed` and launcher before the session, one retry of the
  pytest step. It builds its own debug APK (release jobs build signed release APKs).
- Upload pytest logs and a failure screenshot as artifacts.
- Escape hatch if still flaky: `continue-on-error: true` on the job, or move to manual only.

## Failure modes
- Compose tags not visible to Appium: critical, handled by verifying first + fallback selectors.
- Fake server drift: accepted non-goal.
- CI emulator flake blocking a release: mitigated by ATD image, explicit waits, snapshot cache,
  one retry, and the escape hatch above.
- Local OOM (emulator ~4.3GB on 7.7GB): memory guard + Gradle stop in the script.

## Testing
The tests are the deliverable. The fake server gets a small pytest of its own (fixtures parse,
unknown path -> 404). Verified by a local run on the emulator and a manual `workflow_dispatch`.
