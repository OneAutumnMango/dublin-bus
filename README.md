# Dublin Bus Tracker

Search TFI/Dublin Bus stops by name or number, find one on a map, and see live arrivals.
Favourite stops for quick access, and add a home screen widget for one stop.

All transit data comes from the companion server in a separate, sibling repo:
[`../dublin-bus-server`](../dublin-bus-server). It polls the NTA feeds on its own rate-limited
schedule, so this app never touches NTA directly and is never rate limited itself.

## Modules
- `:core` — plain Kotlin models, no Android.
- `:data` — server settings and the `TransitApiClient` that talks to the server, plus favourites.
- `:app` — Jetpack Compose UI (Search/Map/Favourites/Settings tabs, stop detail) and a Glance
  home screen widget.

## Setup
```
./scripts/setup-dev-env.sh      # JDK/Gradle/Android SDK check, writes local.properties
./scripts/build.sh --install    # build debug APK and install on a connected device/emulator
./scripts/test.sh               # run all unit tests
```

Then point the app at a running server, either in the app or at build time.

### Configuring the server (in the app)
Open the **Settings** tab:

- **Server address** — e.g. `http://10.0.2.2:8080/` for a server on your dev machine reached
  from the emulator, or whatever public URL you've exposed it on.
- **Request headers** — any number of `Name` / `Value` rows, sent with every request. Use
  `X-Api-Key` with the value of the server's `SERVER_API_KEY`. Add more rows for anything
  sitting in front of the server (a reverse proxy's auth headers, a bearer token, and so on).
- **Save & test** — saves, then calls `/v1/status` and reports what the server came back with,
  so you know the address and headers are right before leaving the screen.

Settings are stored on the device and take effect immediately, so you can repoint the app at a
different server without rebuilding.

### Seeding defaults at build time
`local.properties` supplies the values the app starts with on a fresh install:

- `server.baseUrl` — e.g. `http://10.0.2.2:8080/`
- `server.headers` — comma-separated `Name=Value` pairs, e.g. `X-Api-Key=<your key>`

Anything saved in Settings overrides these.

### Widget
Add the "Dublin Bus Tracker" widget from the home screen widget picker; placing it opens a stop
picker, then it shows that stop's next arrivals. Tap Refresh on the widget for an immediate
update; Android otherwise only allows background refreshes every ~30 minutes.

### Map
OpenStreetMap tiles via osmdroid — no API key needed. Stops load for whatever area you're
looking at, and "Show buses" overlays live vehicle positions for that viewport.

## Notes
- The app carries no timetable data of its own: stop search, nearby stops, arrivals and vehicle
  positions are all server queries. Without a reachable server the screens show an error
  pointing at Settings.
- Favourites are stored on the device with their stop name and number, so the list still renders
  when the server is unreachable.
- Stop detail refreshes every 30s. The server only refreshes from NTA every 30–60s, so that is
  the real limit on freshness; polling faster gains nothing but costs the server nothing either.
- `./scripts/release-apk.sh <feature-slug>` builds a debug APK into `apks/` (gitignored) for
  manual testing; each feature gets its own APK and commit.
