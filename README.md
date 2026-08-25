# Dublin Bus Tracker

Search TFI/Dublin Bus stops by name or number, find one on a map, and see live arrivals.
Favourite stops for quick access, and add a home screen widget for one stop.

## Modules
- `:core` — plain Kotlin models and search/distance logic, no Android.
- `:data` — stop lookup (from a bundled static-GTFS asset), favourites, an NTA GTFS-Realtime
  client, and a client for our own arrivals server.
- `:app` — Jetpack Compose UI (Search/Map/Favourites tabs, stop detail) plus a Glance home
  screen widget. Talks to `:server`, never to NTA directly.
- `:server` — a small Ktor service that polls the NTA feed on its own rate-limited schedule
  and serves cached arrivals to as many app installs as you like, so the app never has to
  think about NTA's 60s-per-key limit.

## Setup
```
./scripts/setup-dev-env.sh      # JDK/Gradle/Android SDK check, writes local.properties
./scripts/fetch-gtfs-data.sh    # downloads + filters static GTFS into data/src/main/assets/gtfs_static.json
./scripts/build.sh --install    # build debug APK and install on a connected device/emulator
./scripts/test.sh               # run all unit tests
```

### Arrivals server (required for live arrivals)
The app gets live arrivals from our own server, not from NTA directly. The server holds the
NTA key(s) and polls NTA on a loop; the app just asks the server and isn't rate-limited.

1. Sign up at https://developer.nationaltransport.ie/signup and subscribe to "GTFS-Realtime"
   (allow up to ~15 minutes to activate). A second, unlinked subscription is optional but lets
   the server alternate keys and poll NTA every 30s instead of 60s.
2. `cp server.env.example server.env` and fill in `SERVER_API_KEY` (any random string — this is
   what the app authenticates to *your* server with, separate from the NTA keys), `GTFSR_API_KEY`,
   and optionally `GTFSR_API_KEY_SECONDARY`.
3. `./scripts/run-server.sh` to run it (or `./scripts/run-server.sh --build` for a standalone
   `installDist` you can copy to a host and run without Gradle).
4. In `local.properties`, set `server.baseUrl` (e.g. `http://10.0.2.2:8080/` for a local server
   from the emulator, or your Cloudflare Tunnel https URL) and `server.apiKey` to match
   `SERVER_API_KEY`.

Without a reachable server, search and the map still work (static stop data needs no network);
the stop detail screen will show an error when it tries to fetch arrivals.

#### Exposing it via Cloudflare Tunnel
```
cloudflared tunnel --url http://localhost:8080
```
prints a `https://<random>.trycloudflare.com` URL for a quick, no-account tunnel (rotates on
restart). For a stable hostname, create a named tunnel (`cloudflared tunnel login`,
`cloudflared tunnel create dublin-bus`, add a DNS route, then `cloudflared tunnel run`) per
Cloudflare's docs. Either way, only requests with the correct `X-Api-Key` header reach real
data — the tunnel itself does not add authentication.

### Widget
Add the "Dublin Bus Tracker" widget from the home screen widget picker; placing it opens a
stop picker, then it shows that stop's next arrivals. Tap Refresh on the widget for an
immediate update from the server; Android otherwise only allows background refreshes every
~30 minutes.

### Map
Uses OpenStreetMap tiles via osmdroid — no API key needed.

## Notes
- Static GTFS is filtered to Dublin Bus, Bus Éireann and Go-Ahead Ireland (the operators covered
  by the GTFS-Realtime feed). Re-run `./scripts/fetch-gtfs-data.sh` to refresh it.
- The app polls the server every 15s; the server itself only refreshes from NTA every 30–60s,
  so that's the real data freshness — polling the server faster doesn't get newer data, it just
  costs nothing extra since the server isn't rate-limited per app instance.
- No background sync or notifications beyond the widget's periodic refresh — arrivals are
  fetched on demand and polled at the key-derived interval while a stop's detail screen is open.
- `./scripts/release-apk.sh <feature-slug>` builds a debug APK into `apks/` (gitignored) for
  manual testing; each feature gets its own APK and commit.
