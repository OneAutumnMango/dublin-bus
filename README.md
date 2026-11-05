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

Dublin is downloaded for offline use on first launch. Settings > Offline maps lists what's stored
and lets you add more: pick Cork, Galway, Limerick or Waterford, or fit any area inside the frame
on the map, and it shows the size before you download. Delete areas there too.

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
