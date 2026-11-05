# Emulator dev setup (this machine)

What's installed on this WSL2 box for running the app on an emulator, how it's wired to a local
`dublin-bus-server`, and - importantly - how to not crash WSL doing it. Written after setting
this up hit exactly that crash once, so the memory section isn't theoretical.

## What's installed

| What | Where | Notes |
| --- | --- | --- |
| Android SDK | `~/Android/Sdk` | cmdline-tools, platform-tools, build-tools, platforms 27/33/34/36 |
| SDK symlink | `~/Android/sdk` → `~/Android/Sdk` | `scripts/setup-dev-env.sh` defaults to the lowercase path; the symlink lets that script (and `scripts/env.sh`) recognize this install instead of fetching a second copy |
| Emulator + system image | `~/Android/Sdk/emulator`, `~/Android/Sdk/system-images/android-36/google_apis/x86_64` | matches the app's `compileSdk`/`targetSdk` (36) and the host's x86_64 CPU, so no ARM translation |
| AVD | `dublinbus-api36` (pixel_5 profile, 1536M RAM, 2 cores) | named to match what `setup-dev-env.sh --with-emulator` would create; profile is pixel_5 not pixel_6 (pixel_6 wasn't in this cmdline-tools version's device list) - cosmetic only |
| `kvm` group membership | `/etc/group` | `david` was added via `sudo usermod -aG kvm david`; needed for hardware-accelerated virtualization. New shells pick this up automatically; a shell that existed before the usermod needs `sg kvm -c '<command>'` to actually get the group (see below) |

`local.properties` (gitignored, machine-specific) points `sdk.dir` at `/home/david/Android/Sdk`
and sets `server.baseUrl=http://10.0.2.2:8080/` - `10.0.2.2` is the standard Android-emulator
alias for the host machine's `localhost`, so the app finds a server running locally with zero
`adb reverse` setup. See `app/build.gradle.kts` for how that's wired into `BuildConfig`.

## The memory constraint (read this first)

**This WSL2 instance has 7.7GB total RAM, no more.** That's shared between the emulator, the
Gradle daemon, the Kotlin compile daemon, and everything else running. The first attempt at
`:app:installDebug` with an emulator already running OOM-crashed the whole WSL instance - not
just a process, the *VM* - because `dublin-bus/gradle.properties` allowed a 3GB Gradle daemon
plus parallel module workers, stacked on top of the emulator's own ~2GB.

Fixed in both `dublin-bus/gradle.properties` and `dublin-bus-server/gradle.properties`:
`-Xmx1536m`, `org.gradle.parallel=false`. The AVD is also set to 1536M RAM / 2 cores rather than
the default 2048M+/4 cores. These bound the worst case, but there still isn't much headroom -
**treat 7.7GB as tight even with the fix**, not as solved.

Working rules that kept it stable after the fix:
- **Don't run a cold/clean Gradle build while the emulator is up**, if you can avoid it. A
  from-scratch Kotlin compile of `:app` is the single heaviest thing in this repo. An
  *incremental* build (most of the task graph `UP-TO-DATE`) is fine - that's most of what you'll
  do day to day.
- If a build does start climbing (`free -h` available memory dropping, swap climbing), **kill
  the emulator, not the build**: find its real PID with `pgrep -af qemu-system` and `kill` it (see
  the warning below about why a name-based `pkill` can silently miss it). The build will finish
  compiling and only fail at the final install step (`No connected devices!`) - reboot the
  emulator and re-run `./gradlew :app:installDebug`, which reuses the now-cached compile output
  and takes seconds.
- **Stop the Gradle daemon after a build** rather than leaving it idle: `./gradlew --stop`. An
  idle daemon still holds its full heap.
- Check headroom with `free -h` (the `available` column, not `free`) between steps rather than
  assuming.
- **The running emulator costs more than its configured RAM.** The 1536M in `config.ini` is only
  the guest's RAM; QEMU's own overhead (graphics, translation, disk cache) pushed the real RSS to
  about **4.3GB** in practice - over half this machine's total RAM by itself. Budget for that, not
  for 1536M.

### A `pkill` pattern can silently fail to match - verify, don't trust

After renaming the AVD mid-session (`avdmanager move avd --rename`), every later boot command in
this doc already used the new name `dublinbus-api36` - but a *process already running* keeps
whatever `-avd <name>` it was launched with in its command line. A `pkill -f
"qemu-system.*dublinbus-api36"` run against an older process still carrying the previous `-avd
dbus-dev` just matches nothing and exits quietly - it does not error, so nothing in the output
tells you it failed. That let one emulator instance keep running, un-killed, for the rest of a
session despite several "kill the emulator" attempts. If you kill-by-pattern and memory doesn't
recover, don't assume the kill worked: confirm with `pgrep -af qemu-system` and check the actual
command line, or just kill by PID once you've looked.

## Day-to-day commands

```bash
# one-time per shell, or add to your rc:
source scripts/env.sh                           # puts adb/sdkmanager/emulator on PATH

# boot the emulator (needs the kvm group; see note below)
sg kvm -c "emulator -avd dublinbus-api36 -netdelay none -netspeed full" &
disown
adb wait-for-device
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do sleep 3; done

# build + install (first one is slow, after that it's incremental)
./scripts/build.sh --install
# or directly:
./gradlew :app:installDebug
./gradlew --stop                                  # free the daemon when you're done building

# run the server the app will talk to (separate repo)
cd ../dublin-bus-server
set -a; source server.env; set +a
GTFS_STATIC_PATH= PORT=8080 build/install/dublin-bus-server/bin/dublin-bus-server &
disown

# launch the app, look at it
adb shell am start -n dev.dublinbustracker.app/.MainActivity
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png
```

### Why `sg kvm -c`, not just running `emulator` directly

Adding a user to a group (`usermod -aG`) only takes effect for *new* login sessions - a shell
that was already open keeps its old group list. `sg kvm -c '<command>'` runs one command with
`kvm` added to its group list without needing a fresh login. A brand new shell started after the
`usermod` doesn't need this.

## Faking the GPS (so "nearest first" and "my location" make sense)

The emulator thinks it is at Google's HQ in California, about 8,200 km from every stop. Point it at
Dublin city centre (note the order: **longitude, then latitude**):

```bash
adb emu geo fix -6.2603 53.3498
```

It prints `OK`, but a new fix only reaches an app **while that app is actively asking for
location** - sent beforehand it is dropped, and the emulator keeps serving its old California
fix. So send it repeatedly while the screen is opening, e.g.
`for i in $(seq 12); do adb emu geo fix -6.2603 53.3498; sleep 1; done &` and then open the tab.
The app ignores a remembered fix older than two minutes (and asks for a fresh one), which is
what makes this work; if no fresh fix arrives within ten seconds it falls back to the old one.

## Known-good state as of this setup

Verified working end to end: booted the emulator, built and installed the app, started
`dublin-bus-server` locally, and clicked through all four tabs (Search, Map, Favourites,
Settings) against the live NTA feed - stop search, arrivals, favouriting, and the map all
return and display real Dublin Bus data. `./gradlew :core:test :data:test :app:testDebugUnitTest`
is green (`:app` has no unit tests of its own; its logic lives in `:core`/`:data`).

Three real bugs were found this way (not reachable by unit tests, since they're about how
Compose actually renders/handles touch on a device) and fixed:
- Top-row controls (stop detail's Back and favourite-star buttons, and on first boot the tab
  row) sat under the status bar on `targetSdk` 36 (edge-to-edge is mandatory from Android 15,
  with no opt-out) and were untappable. Fixed with `safeDrawingPadding()` on the root `Surface`
  in both `MainActivity` and `WidgetConfigActivity`.
- The map rendered every nearby stop (up to 200) as a full-size pin with no decluttering -
  at the default city-wide zoom this produced ~80 fully overlapping, untappable markers over
  central Dublin. Fixed with simple screen-space clustering in `MapScreen.kt`.
- The Settings screen's header text still told users to set `X-Api-Key` for "the server's own
  key," left over from before the server repo dropped its own auth.

One caveat: the `dublin-bus-server` binary used for that check was built before the
static-GTFS-auto-refresh commit, so it's missing that one feature (unrelated to anything the
app does - rebuild with `./gradlew installDist` in `dublin-bus-server/` to pick it up).

## Current state (end of this session)

The emulator and Gradle daemon were both stopped to free RAM; `dublin-bus-server` is still
running locally on port 8080. To pick back up: boot the emulator (command above), it'll find
the running server automatically via `10.0.2.2:8080`, no server restart needed.
