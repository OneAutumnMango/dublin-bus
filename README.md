# Dublin Bus Tracker

Search TFI/Dublin Bus stops by name or number, find one on a map, and see live arrivals.

## Modules
- `:core` — plain Kotlin models and search/distance logic, no Android.
- `:data` — stop lookup (from a bundled static-GTFS asset) and realtime arrivals (NTA GTFS-Realtime feed).
- `:app` — Jetpack Compose UI: Search tab, Map tab (OpenStreetMap via osmdroid), stop detail with live arrivals.

## Setup
```
./scripts/setup-dev-env.sh      # JDK/Gradle/Android SDK check, writes local.properties
./scripts/fetch-gtfs-data.sh    # downloads + filters static GTFS into data/src/main/assets/gtfs_static.json
./scripts/build.sh --install    # build debug APK and install on a connected device/emulator
./scripts/test.sh               # run all unit tests
```

### Realtime API key (required for live arrivals)
Live arrivals come from the NTA's GTFS-Realtime API. It needs a free key:
1. Sign up at https://developer.nationaltransport.ie/signup and subscribe to the "GTFS-Realtime" product.
2. Allow up to ~15 minutes for the key to activate.
3. Add it to `local.properties`: `gtfsr.apiKey=<your key>`.

Without a key, search and the map still work (static stop data needs no key); the stop detail
screen will show an error when it tries to fetch arrivals.

### Map
Uses OpenStreetMap tiles via osmdroid — no API key needed.

## Notes
- Static GTFS is filtered to Dublin Bus, Bus Éireann and Go-Ahead Ireland (the operators covered
  by the GTFS-Realtime feed). Re-run `./scripts/fetch-gtfs-data.sh` to refresh it.
- No persistence, background work, or notifications in this version — arrivals are fetched
  on demand and polled every 30s while a stop's detail screen is open.
