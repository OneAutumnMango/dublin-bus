# Appium Smoke Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-optimized:subagent-driven-development (recommended) or superpowers-optimized:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Appium (Python/pytest) smoke tests that drive the debug APK against a fake `/v1` server, runnable locally and as a release gate in CI.
**Architecture:** `appium/` holds a stdlib fake server, a pytest harness (session-scoped Appium driver, explicit waits) and smoke tests. The app gets one root `testTagsAsResourceId` plus a `TestTags` constants object and tags on driven elements. `scripts/appium-test.sh` runs it locally; `appium.yml` (reusable) gates `release.yml`'s `apk` job.
**Tech Stack:** Python 3.12, pytest, Appium-Python-Client, Appium server + uiautomator2 driver via npm, Kotlin/Compose (tags), GitHub Actions, `google_atd` API 34 emulator in CI.
**Spec:** `docs/specs/2026-11-16-appium-smoke-tests-design.md`
**Assumptions:**
- Assumes the app reads its server URL from `server.baseUrl` in `local.properties` at build time — will NOT work if that file is absent at build time; CI and the script must write `server.baseUrl=http://10.0.2.2:8080/` first.
- Assumes port 8080 on the host is free — will NOT work if a real `dublin-bus-server` is running; the script and conftest fail fast if it is busy.
- Assumes `testTagsAsResourceId` surfaces tags as `resource-id` (verified in Task 1; fallback is content-desc/text selectors).

## Global Constraints

- Tests use explicit waits (`WebDriverWait`), never `time.sleep`.
- Appium server and driver install via npm into `appium/node_modules`; no global installs.
- Local runs reuse the existing AVD `dublinbus-api36`; CI uses `google_atd` x86_64 API 34 with `-no-window -no-audio -gpu swiftshader_indirect`.
- Local script must honour the memory guard in `scripts/run-emulator.sh` (min free 3500 MB) and run `./gradlew --stop` before the tests.
- Smoke scope only: launch, search stop -> arrivals, favourite toggle, settings "Save & test". No map, widget, offline maps.
- The `appium` CI job gates `apk` (`needs: [test, appium]`); it builds its own debug APK.
- Commit after every task, on branch `feat/appium-tests`. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Fake server hit with an unknown path or a query matching nothing: expect 404 / empty list, not a crash (tested in Task 2).
- Port 8080 already in use: expect a clear failure message, not a hung run (Task 5).
- Appium session left running when pytest fails: expect the driver quit and the fake server stopped (fixture teardown, Task 3).
- App data from a previous test (favourites, recents) leaking into the next: expect a cleared app per test (Task 3).

## File Structure

- `app/src/main/kotlin/dev/dublinbustracker/app/ui/TestTags.kt` - tag constants (one responsibility: names shared by UI code).
- Modified for tags: `MainActivity.kt`, `search/SearchScreen.kt`, `detail/StopDetailScreen.kt`, `favourites/FavouriteStarButton.kt`, `settings/SettingsScreen.kt`.
- `appium/fake_server.py`, `appium/fixtures/*.json`, `appium/test_fake_server.py`.
- `appium/conftest.py`, `appium/test_smoke.py`, `appium/requirements.txt`, `appium/package.json`.
- `scripts/appium-test.sh`.
- `.github/workflows/appium.yml`, modified `.github/workflows/release.yml`.
- `README.md` section.

---

### Task 1: Test tags in the app (and verify Appium can see them)

**Files:**
- Create: `app/src/main/kotlin/dev/dublinbustracker/app/ui/TestTags.kt`
- Modify: `MainActivity.kt`, `search/SearchScreen.kt`, `detail/StopDetailScreen.kt`, `favourites/FavouriteStarButton.kt`, `settings/SettingsScreen.kt` (all under `app/src/main/kotlin/dev/dublinbustracker/app/`)

**Interfaces:**
- Consumes: nothing
- Produces: `object TestTags` with `const val`s: `NAV_SEARCH="nav_search"`, `NAV_MAP="nav_map"`, `NAV_FAVOURITES="nav_favourites"`, `NAV_SETTINGS="nav_settings"`, `SEARCH_FIELD="search_field"`, `FAVOURITE_STAR="favourite_star"`, `SETTINGS_SERVER_ADDRESS="settings_server_address"`, `SETTINGS_SAVE_TEST="settings_save_test"`, and `fun stopRow(id: String) = "stop_row_$id"`, `fun arrivalRow(tripId: String) = "arrival_row_$tripId"`.

**Security flag:** `none`

**Does NOT cover:** the map screen, widget, or any element not driven by the smoke tests. Tags do not change visible behavior.

- [x] **Step 1: Create `TestTags.kt`** with the object above (package `dev.dublinbustracker.app.ui`).
- [x] **Step 2: Root semantics.** In `AppRoot`'s outermost container in `MainActivity.kt`, apply `Modifier.semantics { testTagsAsResourceId = true }` (needs `@OptIn(ExperimentalComposeUiApi::class)`). It must wrap everything, including the `NavigationBar`.
- [x] **Step 3: Apply tags** with `Modifier.testTag(TestTags.X)`: each `NavigationBarItem` (by `AppTab`), the search `OutlinedTextField`, each stop `ListItem` row in search (`stopRow(stop.id)`), each arrival row in `StopDetailScreen` (`arrivalRow(arrival.tripId)`), the `IconButton` inside `FavouriteStarButton`, the settings server-address field and the "Save & test" button.
- [x] **Step 4: Build and unit tests still pass.**
  Run: `./gradlew --console=plain :app:assembleDebug test && ./gradlew --stop`
  Expected: BUILD SUCCESSFUL.
- [x] **Step 5: Verify tags reach UiAutomator** (the spec's critical risk). With the emulator up (`./scripts/run-emulator.sh`) and the debug APK installed (`./scripts/install-app.sh`):
  Run: `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml && grep -o 'resource-id="[^"]*nav_[a-z]*"' /tmp/ui.xml`
  Expected: four `nav_*` ids. If none appear, STOP and report: the plan switches to content-desc/text selectors.
- [x] **Step 6: Commit** `git add app && git commit -m "Tag driven UI elements for Appium and expose tags as resource ids"`.

---

### Task 2: Fake server

**Files:**
- Create: `appium/fake_server.py`, `appium/fixtures/status.json`, `appium/fixtures/stops.json`, `appium/fixtures/arrivals_stop-1.json`, `appium/test_fake_server.py`

**Interfaces:**
- Consumes: nothing
- Produces: `fake_server.start(port: int = 8080, host: str = "0.0.0.0") -> http.server.ThreadingHTTPServer` (already serving on a daemon thread; caller calls `.shutdown()`), and the fixtures below. CLI: `python fake_server.py` serves until killed.

**Security flag:** `none` (test-only, never deployed)

**Does NOT cover:** endpoints other than the five below (they return 404); the vehicles, shapes and trips endpoints.

Fixture contract (field names from the `*Dto`s in `data/.../TransitApiClient.kt`):
- `stops.json`: list with two stops: `{"id":"stop-1","code":"1234","name":"O'Connell Street","lat":53.3498,"lon":-6.2603,"routes":["16","46A"]}` and `{"id":"stop-2","code":"5678","name":"Parnell Square","lat":53.3522,"lon":-6.2639,"routes":["16"]}`.
- `arrivals_stop-1.json`: two arrivals: `{"routeShortName":"16","destination":"Ballinteer","dueMinutes":3,"isRealtime":true,"tripId":"trip-a","routeId":"r16","expectedEpochSeconds":1800000000}` and `{"routeShortName":"46A","destination":"Dun Laoghaire","dueMinutes":9,"isRealtime":false,"tripId":"trip-b","routeId":"r46a","expectedEpochSeconds":1800000360}`.
- `status.json`: `{"pollIntervalMs":30000,"historyDepth":10,"configuredFeeds":["fake"],"staticData":{"stops":2}}`.
- Check the real response *shapes* (list vs wrapped object) in `TransitApiClient.kt` lines ~163-215 and match them exactly.

- [ ] **Step 1: Failing tests** in `test_fake_server.py` (start on port 0 via a fixture, use `urllib`):
  - `test_status_ok`: `GET /v1/status` -> 200 and `configuredFeeds == ["fake"]`.
  - `test_stop_search_filters_by_name`: `GET /v1/stops?query=connell&limit=20` -> the O'Connell stop only; `query=zzz` -> empty result, 200.
  - `test_arrivals_for_stop`: `GET /v1/stops/stop-1/arrivals?limit=20` -> two arrivals, first `tripId == "trip-a"`.
  - `test_unknown_path_404`: `GET /v1/nope` -> 404; `GET /v1/stops/missing/arrivals` -> 404.
- [ ] **Step 2: Run** `cd appium && python -m pytest test_fake_server.py -q` -> Expected FAIL (module missing).
- [ ] **Step 3: Implement** `fake_server.py` with `BaseHTTPRequestHandler`, fixtures loaded from `fixtures/` next to the file, `start()` as above. Port 0 must work (tests read `server.server_address[1]`).
- [ ] **Step 4: Run again** -> Expected: 4 passed.
- [ ] **Step 5: Commit** `git add appium && git commit -m "Add fake /v1 server for Appium smoke tests"`.

---

### Task 3: Appium harness

**Files:**
- Create: `appium/requirements.txt`, `appium/package.json`, `appium/conftest.py`, `appium/test_launch.py`, `appium/.gitignore` (`node_modules/`, `.venv/`)

**Interfaces:**
- Consumes: `fake_server.start(port, host)`; `TestTags` values (as literal resource-id suffixes, app package `dev.dublinbustracker.app`).
- Produces pytest fixtures: `fake_server` (session; starts on 8080, shuts down at teardown, fails with `pytest.exit("port 8080 busy")` if bind fails), `driver` (session; `webdriver.Remote` to `http://127.0.0.1:4723`, UiAutomator2, `appium:app` from env `APK_PATH` default `../app/build/outputs/apk/debug/app-debug.apk`, `appium:noReset=True`), `app` (function; runs `mobile: clearApp` then `activate_app`, yields the driver), and helper `by_tag(driver, tag, timeout=15) -> WebElement` that waits for `id=dev.dublinbustracker.app:id/<tag>`... use the resource-id form Task 1 verified.

**Security flag:** `none`

**Does NOT cover:** starting the Appium server (Task 5's script and the CI job do that); parallel sessions.

- [ ] **Step 1: Pin deps.** `requirements.txt`: `Appium-Python-Client==<latest 4.x at time of work>`, `pytest==<latest 8.x>`. `package.json`: dev deps `appium` and (via script `"drivers": "appium driver install uiautomator2"`) pinned exact versions; set `APPIUM_HOME=appium/.appium` in the script so the driver also stays local.
- [ ] **Step 2: Failing test** `test_launch.py::test_app_launches_on_search_tab`: `by_tag(app, "nav_search")` is displayed and `by_tag(app, "search_field")` is displayed.
- [ ] **Step 3: Implement fixtures** in `conftest.py` per the Interfaces. `driver` teardown must `quit()` even on failure; on test failure save a screenshot to `appium/artifacts/<test>.png` (pytest hookwrapper `pytest_runtest_makereport`).
- [ ] **Step 4: Run manually** (emulator up, APK installed, Appium server started by hand: `cd appium && npm ci && APPIUM_HOME=$PWD/.appium npx appium driver install uiautomator2 && APPIUM_HOME=$PWD/.appium npx appium &`): `python -m pytest test_launch.py -q` -> Expected: 1 passed.
- [ ] **Step 5: Commit** `git add appium && git commit -m "Add Appium pytest harness and launch test"`.

---

### Task 4: Smoke tests

**Files:**
- Create: `appium/test_smoke.py`

**Interfaces:**
- Consumes: fixtures `fake_server`, `app`; helper `by_tag`; tags `search_field`, `stop_row_stop-1`, `arrival_row_trip-a`, `arrival_row_trip-b`, `favourite_star`, `nav_favourites`, `nav_settings`, `settings_server_address`, `settings_save_test`; fixture data from Task 2.
- Produces: nothing

**Security flag:** `none`

**Does NOT cover:** error states (server down), recents, the "View route on map" action.

- [ ] **Step 1: Write tests** (they fail until the app and fake server cooperate, so run each as written):
  - `test_search_stop_shows_arrivals`: type `connell` into `search_field`; tap `stop_row_stop-1`; assert `arrival_row_trip-a` text contains `Ballinteer` and `arrival_row_trip-b` text contains `Dun Laoghaire`.
  - `test_toggle_favourite_appears_in_favourites`: search `connell`, open the stop, tap `favourite_star` (assert its content-desc changes from "Add to favourites" to "Remove from favourites"); press back; tap `nav_favourites`; assert a row with text `O'Connell Street` exists.
  - `test_settings_save_and_test_reports_success`: tap `nav_settings`; set `settings_server_address` to `http://10.0.2.2:8080/`; tap `settings_save_test`; wait for text matching what `SettingsViewModel.testConnection` reports on success (read it from `settings/SettingsViewModel.kt`; assert on that exact string).
- [ ] **Step 2: Run** `python -m pytest -q` (from `appium/`, setup as Task 3 step 4) -> Expected: all pass (launch + 3 smoke + fake-server tests).
- [ ] **Step 3: Flake check:** run the smoke file 3 times in a row; all must pass. If a test needs a wait, add an explicit one — never a sleep.
- [ ] **Step 4: Commit** `git add appium && git commit -m "Add Appium smoke tests"`.

---

### Task 5: Local runner script

**Files:**
- Create: `scripts/appium-test.sh`

**Interfaces:**
- Consumes: `scripts/run-emulator.sh` (flags `--headless`, `--stop`), `scripts/install-app.sh`, `scripts/env.sh`, the Task 3 harness.
- Produces: `./scripts/appium-test.sh [--headless] [pytest args...]`; exit code is pytest's.

**Security flag:** `none`

**Does NOT cover:** CI (it has its own workflow); starting more than one emulator.

- [ ] **Step 1: Write the script** (`set -euo pipefail`, same style/header comment as `run-emulator.sh`): fail fast with a clear message if `127.0.0.1:8080` is already listening; ensure `local.properties` has `server.baseUrl=http://10.0.2.2:8080/` (add if missing, never overwrite other lines); `run-emulator.sh` (passes through `--headless`); `install-app.sh`; `./gradlew --stop`; `npm ci` + install the uiautomator2 driver with `APPIUM_HOME=appium/.appium`; start Appium in the background with a trap that kills it on exit; wait for `http://127.0.0.1:4723/status`; create `appium/.venv` if missing and `pip install -r requirements.txt`; run `python -m pytest "$@"` from `appium/`.
- [ ] **Step 2: Verify** `chmod +x scripts/appium-test.sh && ./scripts/appium-test.sh --headless` -> Expected: all tests pass; no `appium`/`node` process left (`pgrep -af appium` empty); `free -h` available memory recovered after `./scripts/run-emulator.sh --stop`.
- [ ] **Step 3: Verify the busy-port failure:** `python3 -m http.server 8080 & ./scripts/appium-test.sh; kill %1` -> Expected: exits non-zero with the "port 8080 busy" message before booting anything.
- [ ] **Step 4: Commit** `git add scripts && git commit -m "Add local Appium test runner"`.

---

### Task 6: CI workflow and release gate

**Files:**
- Create: `.github/workflows/appium.yml`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: the Task 3-4 harness.
- Produces: reusable workflow `appium.yml` (`workflow_call` + `workflow_dispatch`); job `appium` in `release.yml`; `apk` job `needs: [test, appium]`.

**Security flag:** `none` (no secrets used by the new workflow)

**Does NOT cover:** PR-triggered runs (non-goal); signing (unchanged).

- [ ] **Step 1: Write `appium.yml`.** `permissions: contents: read`. One job on `ubuntu-latest`, `timeout-minutes: 30`: checkout, `setup-java` 21 temurin, `gradle/actions/setup-gradle`, `actions/setup-node` (LTS) and `actions/setup-python` 3.12; enable KVM (udev rule step from the `android-emulator-runner` README); write `local.properties` with `server.baseUrl=http://10.0.2.2:8080/` and `sdk.dir=$ANDROID_HOME`; `./gradlew --console=plain :app:assembleDebug`; AVD snapshot cache keyed on the API level (`actions/cache`, then the runner's `force-avd-creation: false`); `reactivecircus/android-emulator-runner` with `api-level: 34`, `target: google_atd`, `arch: x86_64`, `disable-animations: true`, `emulator-options: -no-window -no-audio -gpu swiftshader_indirect -no-snapshot-save`, and a `script:` that installs Appium + driver, starts Appium in the background, waits for `/status`, installs the APK (`adb install -r`), and runs pytest. Pin action versions by the major tag the existing workflows use (check `test.yml`; for actions not used there, the latest major at time of work). Retry: run pytest with `--reruns 1` (add `pytest-rerunfailures` pinned to `requirements.txt`) rather than a custom loop. `if: always()` upload `appium/artifacts` via `actions/upload-artifact`.
- [ ] **Step 2: Gate the release.** In `release.yml` add job `appium: uses: ./.github/workflows/appium.yml` beside `test`, and change `apk` to `needs: [test, appium]`.
- [ ] **Step 3: Validate syntax:** `python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/appium.yml .github/workflows/release.yml` -> Expected: no error. If `actionlint` is installed, run it too.
- [ ] **Step 4: Commit** `git add .github appium/requirements.txt && git commit -m "Run Appium smoke tests in CI and gate releases on them"`.
- [ ] **Step 5 (needs the user, after pushing the branch):** run the workflow via Actions -> Appium -> Run workflow on `feat/appium-tests`. Expected: green. Report the run time and any flake; the escape hatch is `continue-on-error: true` on the `appium` job in `release.yml`.

---

### Task 7: Docs

**Files:**
- Modify: `README.md`, `docs/emulator-setup.md`

**Interfaces:** Consumes: Tasks 1-6. Produces: nothing.

**Security flag:** `none`

- [ ] **Step 1: README section "UI tests (Appium)":** what they cover, `./scripts/appium-test.sh [--headless]`, that it needs port 8080 free and the memory rules, how the CI gate works (runs before `apk` on tag pushes; re-run the failed job from the Actions UI; `workflow_dispatch` for branches), and the `continue-on-error` escape hatch.
- [ ] **Step 2: `docs/emulator-setup.md`:** a short note that Appium runs add Node + the Appium server on top of the emulator's ~4.3 GB, so run it after `./gradlew --stop`.
- [ ] **Step 3: Verify every command and flag named in the docs exists** (`grep` the script for each flag).
- [ ] **Step 4: Commit** `git add README.md docs && git commit -m "Document Appium smoke tests"`.

---

## Self-review

- **Spec coverage:** layout (Tasks 2-3), tags + verification (1), smoke flows (4), local script + memory guard (5), CI reusable workflow + release gate + ATD + retry + artifacts (6), escape hatch (6, 7), fake-server pytest (2), docs (7). No gaps.
- **Type consistency:** every tag in Task 3/4's Consumes appears in Task 1's Produces; `fake_server.start` signature matches in Tasks 2 and 3.
- **Scope-reduction scan:** no unsanctioned downgrades; "simple"/"basic" not used.
- **Proportion:** plan is ~2x the spec, mostly interfaces and test names; no function bodies.
