# Dublin Bus Tracker

Search TFI/Dublin Bus stops by name or number, find one on a map, and see live arrivals.
Favourite stops for quick access, and add a home screen widget for one stop.

## Modules
- `:core` — plain Kotlin models and search/distance logic, no Android.
- `:data` — stop lookup (from a bundled static-GTFS asset), favourites, and realtime arrivals (NTA GTFS-Realtime feed).
- `:app` — Jetpack Compose UI (Search/Map/Favourites tabs, stop detail) plus a Glance home screen widget.

## Setup
```
./scripts/setup-dev-env.sh      # JDK/Gradle/Android SDK check, writes local.properties
./scripts/fetch-gtfs-data.sh    # downloads + filters static GTFS into data/src/main/assets/gtfs_static.json
./scripts/build.sh --install    # build debug APK and install on a connected device/emulator
./scripts/test.sh               # run all unit tests
```

### Realtime API key (required for live arrivals)
Live arrivals come from the NTA's GTFS-Realtime API. Each key has its own 60s rate limit, so
the app polls a single key every 60s, or every 30s if you add a second, unlinked key (the two
are alternated per request):
1. Sign up at https://developer.nationaltransport.ie/signup and subscribe to the "GTFS-Realtime" product.
2. Allow up to ~15 minutes for the key to activate.
3. Add it to `local.properties`: `gtfsr.apiKey=<your key>`.
4. Optionally repeat sign-up with a second account/subscription and add `gtfsr.apiKey2=<second key>`
   to halve the poll interval to 30s.

Without a key, search and the map still work (static stop data needs no key); the stop detail
screen will show an error when it tries to fetch arrivals.

### Widget
Add the "Dublin Bus Tracker" widget from the home screen widget picker; placing it opens a
stop picker, then it shows that stop's next arrivals. Tap Refresh on the widget for an
immediate update (same 60s/30s rate limit as the app); Android otherwise only allows
background refreshes every ~30 minutes.

### Map
Uses OpenStreetMap tiles via osmdroid — no API key needed.

## Notes
- Static GTFS is filtered to Dublin Bus, Bus Éireann and Go-Ahead Ireland (the operators covered
  by the GTFS-Realtime feed). Re-run `./scripts/fetch-gtfs-data.sh` to refresh it.
- No background sync or notifications beyond the widget's periodic refresh — arrivals are
  fetched on demand and polled at the key-derived interval while a stop's detail screen is open.
- `./scripts/release-apk.sh <feature-slug>` builds a debug APK into `apks/` (gitignored) for
  manual testing; each feature gets its own APK and commit.
