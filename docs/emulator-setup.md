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
  the emulator, not the build**: `pkill -f "qemu-system-x86_64.*dublinbus-api36"`. The build
  will finish compiling and only fail at the final install step (`No connected devices!`) -
  reboot the emulator and re-run `./gradlew :app:installDebug`, which reuses the now-cached
  compile output and takes seconds.
- **Stop the Gradle daemon after a build** rather than leaving it idle: `./gradlew --stop`. An
  idle daemon still holds its full heap.
- Check headroom with `free -h` (the `available` column, not `free`) between steps rather than
  assuming.

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

## Known-good state as of this setup

Verified working end to end: booted the emulator, built and installed the app, started
`dublin-bus-server` locally, launched the app, and searched for a real stop ("Trinity") -
got back real Dublin Bus stops with real route numbers from the live NTA feed. See the app
README's "Configuring the server" section for how the connection is wired.

One caveat: the `dublin-bus-server` binary used for that check was built before the
static-GTFS-auto-refresh commit, so it's missing that one feature (unrelated to anything the
app does - rebuild with `./gradlew installDist` in `dublin-bus-server/` to pick it up).
