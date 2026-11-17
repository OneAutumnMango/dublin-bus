> This is all vibe coded. Be warned. NEVER expose this server publicly.

# Dublin Bus Tracker

Search TFI/Dublin Bus stops by name or number, find one on a map, and see live arrivals.
Favourite stops for quick access, and add a home screen widget for one stop.

All transit data comes from the companion server in a separate, sibling repo:
[`OneAutumnMango/dublin-bus-server`](https://github.com/OneAutumnMango/dublin-bus-server). It polls the NTA feeds on its own rate-limited
schedule, so this app never touches NTA directly and is never rate limited itself.

## Modules
- `:core` — plain Kotlin models, no Android.
- `:data` — server settings and the `TransitApiClient` that talks to the server, plus favourites.
- `:app` — Jetpack Compose UI (Search/Map/Favourites/Settings tabs, stop detail) and a Glance
  home screen widget.

## Setup
```
./scripts/setup-dev-env.sh      # JDK/Gradle/Android SDK check, writes local.properties
./scripts/run-emulator.sh       # boot the dev emulator and wait until it's usable (--stop shuts it down)
./scripts/install-app.sh        # build the debug app, install it on the emulator and launch it
./scripts/build.sh --install    # build debug APK and install on a connected device/emulator
./scripts/test.sh               # run all unit tests
```

Running an emulator (rather than a physical device)? See
[`docs/emulator-setup.md`](docs/emulator-setup.md) - in particular its memory-constraints
section if you're on a machine with limited RAM (e.g. a modest WSL2 instance), since an
emulator plus a cold Gradle build can be enough to OOM-crash the whole VM.

Then point the app at a running server, either in the app or at build time.

### Configuring the server (in the app)
Open the **Settings** tab:

- **Server address** — e.g. `http://10.0.2.2:8080/` for a server on your dev machine reached
  from the emulator, or whatever public URL you've exposed it on.
- **Request headers** — any number of `Name` / `Value` rows, sent with every request. The
  server itself has no auth of its own (it's meant to sit behind something that already gates
  access, e.g. Cloudflare Access) - this is for headers something in front of it needs, such as
  a reverse proxy's auth header or a bearer token.
- **Save & test** — saves, then calls `/v1/status` and reports what the server came back with,
  so you know the address and headers are right before leaving the screen.

Settings are stored on the device and take effect immediately, so you can repoint the app at a
different server without rebuilding.

### Seeding defaults at build time
`local.properties` supplies the values the app starts with on a fresh install:

- `server.baseUrl` — e.g. `http://10.0.2.2:8080/`
- `server.headers` — comma-separated `Name=Value` pairs, only needed for whatever sits in front
  of the server (the server itself has no auth)

Anything saved in Settings overrides these.

### Widget
Add the "Dublin Bus Tracker" widget from the home screen widget picker; placing it opens a stop
picker, then it shows that stop's next arrivals. Tap Refresh on the widget for an immediate
update; Android otherwise only allows background refreshes every ~30 minutes.

### Map
A vector map (MapLibre) styled with OpenFreeMap's "positron" - free, no API key. Stops load for
whatever area you're looking at (clustered when zoomed out), and "Show buses" overlays live
vehicle positions for that viewport. Tapping an arrival on a stop's screen opens that trip's
route: the path, every stop along it, the stop you're viewing highlighted, and the buses running it.

The route icon at the top of the Map tab switches to following routes: type a route (S8, 4, 46A...)
to add it, and every bus on it is shown, with its path, coloured by route - full colour for one
direction and a lighter tint for the other, with a legend naming where each is heading. Tap a bus for
its details. The compass in the corner shows how the map is turned: tap it to face north, double-tap
to lock north (a padlock appears).

Dublin is downloaded for offline use on first launch (once on an unmetered connection - it waits for Wi-Fi rather than use mobile data). Settings > Offline maps lists what's stored
and lets you add more: pick Cork, Galway, Limerick or Waterford, or fit any area inside the frame
on the map, and it shows the size before you download. Delete areas there too.

## UI tests (Appium)
Smoke tests drive the real app on an emulator: it launches on the Search tab, a stop search opens
its arrivals, a favourite toggled on a stop shows on the Favourites tab, and Settings "Save & test"
reaches the server. They run against a stub `/v1` server (`appium/fake_server.py`, data in
`appium/fixtures/`), so no NTA keys or live data are needed. The map, widget and offline maps are
not covered.

```
./scripts/appium-test.sh [--headless] [--keep-emulator] [pytest args...]
```

It builds the debug APK, stops Gradle, boots the emulator (refusing below 3500 MB free), installs
the APK, starts a local Appium server, runs pytest and cleans up. It needs:
- ports 8080 and 4723 free - the stub binds 8080, so stop the real server first
  (`docker stop dublin-bus-server`)
- Node >= 22 (it switches to nvm's Node 22 if the default is older)
- `server.baseUrl=http://10.0.2.2:8080/` in `local.properties` (added if missing; it refuses to run
  if a different one is set, since the URL is baked into the APK)

Appium and its uiautomator2 driver install under `appium/` (`node_modules`, `.appium`) and the
Python deps in `appium/.venv`; nothing is installed globally.

To test a new screen: the app sets `testTagsAsResourceId` at the root, and
`app/src/main/kotlin/dev/dublinbustracker/app/ui/TestTags.kt` names the tagged elements. Tag the
element, then `by_tag(driver, "tag")` in the test (it matches with `UiSelector.resourceId`;
`AppiumBy.ID` prepends the package and misses these).

### In CI
`.github/workflows/appium.yml` is reusable (`workflow_call`) and also has `workflow_dispatch`.
`release.yml` runs it as job `appium` in parallel with `test`, and the `apk` job
`needs: [test, appium]`, so a failing smoke test publishes no release. It uses an API 34
`google_atd` x86_64 emulator with a cached AVD snapshot and `--reruns 1`, and uploads
`appium-artifacts` (screenshots on failure, Appium log) on every run. Expect about 5 minutes.

GitHub scopes caches made on a tag ref to that tag, so a tag-push run only gets the AVD cache
(key `avd-api34-google_atd-x86_64-v1`) if the default branch holds it. After merging to main, run
the Appium workflow once from the Actions tab on main to seed it; otherwise releases run cold
(about 5 minutes, still pass). Bump the `-v1` suffix in `appium.yml` when the emulator image changes.

- Flaky tag build: re-run the failed `appium` job from the Actions UI; no re-tag needed.
- If it proves flaky, `continue-on-error: true` on the `appium` job in `release.yml` makes it
  non-blocking.
- Manual runs (`gh workflow run appium.yml`, or the Actions tab button) only work for a workflow on
  the default branch. Until `appium.yml` is on main, run it on a branch by adding a temporary
  `push: branches: [<branch>]` trigger.

## Notes
- The app carries no timetable data of its own: stop search, nearby stops, arrivals and vehicle
  positions are all server queries. Without a reachable server the screens show an error
  pointing at Settings.
- Favourites are stored on the device with their stop name and number, so the list still renders
  when the server is unreachable.
- Stop detail refreshes every 30s. The server only refreshes from NTA every 30–60s, so that is
  the real limit on freshness; polling faster gains nothing but costs the server nothing either.
- Pushing a tag like `1.2.3` runs `.github/workflows/release.yml`: the unit tests and the Appium smoke tests, then one signed APK per CPU (the `apk` job needs both; signing needs repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`).
- `./scripts/release-apk.sh <feature-slug>` builds a debug APK into `apks/` (gitignored) for
  manual testing; each feature gets its own APK and commit.

## Credits

App icon: "Cute orange bus" by Fikri Azhari, from [Vecteezy.com](https://www.vecteezy.com) (free licence, attribution required).
