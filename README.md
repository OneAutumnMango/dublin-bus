# Dublin Bus Tracker

Search TFI/Dublin Bus stops by name or number, find one on a map, and see live arrivals.
Favourite stops for quick access, and add a home screen widget for one stop.

## Modules
- `:core` — plain Kotlin models and search/distance logic, no Android.
- `:data` — stop lookup (from a bundled static-GTFS asset), favourites, and a client for our
  arrivals server.
- `:app` — Jetpack Compose UI (Search/Map/Favourites tabs, stop detail) plus a Glance home
  screen widget. Talks to the arrivals server, never to NTA directly.

The server itself lives in a separate, sibling repo: [`../dublin-bus-server`](../dublin-bus-server).
It polls the NTA feed on its own rate-limited schedule and serves cached arrivals to as many
app installs as you like, so this app never has to think about NTA's 60s-per-key limit.

## Setup
```
./scripts/setup-dev-env.sh      # JDK/Gradle/Android SDK check, writes local.properties
./scripts/fetch-gtfs-data.sh    # downloads + filters static GTFS into data/src/main/assets/gtfs_static.json
./scripts/build.sh --install    # build debug APK and install on a connected device/emulator
./scripts/test.sh               # run all unit tests
```

### Arrivals server (required for live arrivals)
The app gets live arrivals from our own server ([`../dublin-bus-server`](../dublin-bus-server)),
not from NTA directly. See that repo's README for running it and getting an NTA key.

Once it's running and reachable, set in `local.properties`:
- `server.baseUrl` — e.g. `http://10.0.2.2:8080/` for a local server from the emulator, or
  wherever you've exposed it (this app doesn't care how — tunnel, reverse proxy, VPN, etc.).
- `server.headers` — comma-separated `Name=Value` pairs sent as request headers on every call,
  e.g. `X-Api-Key=<value matching the server's SERVER_API_KEY>`. Add more pairs here for
  whatever else sits in front of the server needs (e.g. a proxy's auth headers).

Without a reachable server, search and the map still work (static stop data needs no network);
the stop detail screen will show an error when it tries to fetch arrivals.

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
