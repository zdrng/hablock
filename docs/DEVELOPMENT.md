# Development

[Back to Hablock](../README.md)

## Build and test

### Nix: macOS and NixOS

Install Nix with flakes enabled, then enter the pinned development environment:

```sh
nix develop
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The flake supplies JDK 17, Android SDK 36, build-tools 36.0.0, the emulator,
a Google APIs system image, Gradle, scrcpy, Python and ShellCheck. Use `./gradlew`
for builds so the Gradle version matches the repository wrapper. The first run
needs internet access and several GB of free space for the SDK, image and Maven
and Gradle downloads. The flake accepts the Android SDK license.

| Host | Nix system | Emulator image | Acceleration |
| --- | --- | --- | --- |
| Apple Silicon macOS | `aarch64-darwin` | `arm64-v8a` | Hypervisor.Framework |
| Intel macOS | `x86_64-darwin` | `x86_64` | Hypervisor.Framework |
| Intel/AMD NixOS or Linux | `x86_64-linux` | `x86_64` | KVM |

Intel macOS uses a separately locked `nixpkgs-26.05-darwin` input because the
current unstable branch dropped that platform. ARM Linux is not exposed: Google's
Linux emulator distribution does not provide the native host binary needed here.
Run native ARM Nix on Apple Silicon, rather than an Intel shell under Rosetta.

On NixOS, enable CPU virtualization in firmware and grant your user access to KVM:

```nix
users.users.YOUR_USERNAME.extraGroups = [ "kvm" ];
```

Apply the configuration and log out and back in. Check `ls -l /dev/kvm` and
`emulator -accel-check` inside `nix develop`. A Linux VM also needs nested
virtualization from its host. macOS uses its built-in Hypervisor.Framework;
no KVM configuration is needed. See [Android's acceleration documentation](https://developer.android.com/studio/run/emulator-acceleration).

The flake points Linux Gradle builds at Nix's patched `aapt2`; this avoids trying
to execute Maven's unpatched Linux binary on NixOS. macOS uses AGP's normal binary.

### Run the app in the emulator

From the repository root, the Makefile enters the pinned Nix environment for you:

```sh
make run             # boot with a window, build, install and launch Hablock
make run             # after code changes: rebuild, reinstall and relaunch
make test            # JVM unit tests
make test-device     # boot if needed and run Android instrumentation tests
make screenshot      # save scratchpad/emu.png
make logs            # recent logcat output
make stop            # stop the emulator
make help            # all available commands
```

For headless use, run `make run-headless`, then `make mirror` to interact through
scrcpy. Click/tap and type in the emulator window as on an Android phone. Complete
Hablock's onboarding and requested permissions to try blocking.

These commands require Nix and `make` on your PATH. If `make` is not installed,
use `nix develop -c make run`; the flake supplies GNU Make. Environment overrides
also work with Make, for example `HABLOCK_EMULATOR_PORT=5556 make run`.
If an emulator is already running headless, use `make mirror`, or `make stop`
followed by `make run` to start it with a native window.

Equivalent direct commands:

```sh
# Create the device, boot with a window, build, install and launch Hablock:
nix develop -c scripts/emulator.sh up --window

# Or boot headless, then install and launch:
nix develop -c scripts/emulator.sh start
nix develop -c scripts/emulator.sh smoke

nix develop -c scripts/emulator.sh test       # connected instrumentation tests
nix develop -c scripts/emulator.sh mirror     # scrcpy window for a headless device
nix develop -c scripts/emulator.sh shot       # scratchpad/emu.png
nix develop -c scripts/emulator.sh stop
```

`start` defaults to headless software graphics with CPU acceleration enabled.
`--window` takes effect when starting a stopped emulator. Boot waits are bounded
(default 360 seconds), and failures report a log path. Scripts check the AVD name
before operating on a serial, so a different emulator on port 5554 is not reused
or stopped. To run alongside it:

```sh
HABLOCK_EMULATOR_PORT=5556 nix develop -c scripts/emulator.sh up --window
```

AVDs are named by API and ABI (for example `hablock-api36-arm64-v8a`), leaving the
old Intel-only `hablock` AVD untouched. Mutable state and logs default to
`${XDG_DATA_HOME:-$HOME/.local/share}/hablock/android`; `ANDROID_USER_HOME` and
`ANDROID_AVD_HOME` can override those locations. The SDK stays immutable in the
Nix store. Add SDK packages in `flake.nix`, rather than using `sdkmanager` to
install into it. Existing AVD image paths are refreshed to the current SDK on start.

Use `HABLOCK_AVD` for a separate device name and `HABLOCK_BOOT_TIMEOUT` for slower
machines. API/ABI overrides must correspond to an image included in the flake.
Run `scripts/emulator.sh --help` for input, screenshot and logcat commands.

The launch smoke check confirms only that the app process starts. Complete the
onboarding and grant usage/accessibility/Health Connect permissions in the emulator
to exercise blocking. Device-owner provisioning is optional; see [setup](SETUP.md).

### Without Nix

Install JDK 17 and Android SDK platform 36 with build-tools 36.0.0, and set
`ANDROID_HOME`:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release builds are signed via a local `keystore.properties` and keystore
(not in the repo; see `app/build.gradle.kts` for the expected properties).

### Tooling checks

```sh
nix flake show --all-systems
nix develop -c shellcheck scripts/emulator.sh
python3 scripts/test_emulator.py
```

The script tests use fake Android commands, so they cover startup failure,
serial selection and image setup without hardware acceleration or SDK downloads.

## Emergency reset for local testing

For local testing, explicitly include the emergency-use reset gesture:

```sh
nix develop -c ./gradlew assembleDebug -PenableEmergencyReset=true
# Also supported with assembleRelease for a locally signed test build.
```

Tap the version row in Settings five times, with no more than one second between taps,
to restore both emergency unlocks. A toast confirms the reset. This restores the allowance;
it does not unlock any block. The default is disabled for **all** build types. The gesture
and reset implementation live in a separate source directory excluded from normal builds,
so exclusion does not depend on R8. Do not set this property in shared Gradle properties
or distribution CI. Rebuild without the flag to produce a normal APK.

## Architecture

Single Gradle module, manual DI (no Hilt), Room persistence, no WorkManager/navigation-compose:

- `domain/` — pure Kotlin: models, gate math (`GateEvaluator`, `SessionMath`), the
  `DefaultGateEngine` orchestrator, repository interfaces. Zero Android imports, enforced by a
  unit test (`ArchitectureTest`).
- `data/` — Room + DataStore + kotlinx.serialization, UsageStatsManager, Health Connect,
  DevicePolicyManager implementations.
- `enforcement/` — the two enforcement backends behind an `EnforcementCoordinator`.
- `system/` — accessibility service (a dumb foreground sensor with zero policy), broadcast
  receivers, alarms, notifications.
- `ui/` — Jetpack Compose + ViewModels, Material 3 Expressive.

JVM unit tests cover the domain layer (`nix develop -c ./gradlew testDebugUnitTest`); hand-written fakes, no
mocking library.

Known i18n caveat: compact duration tokens ("2h 15m") and the day header are formatted in code
and not yet translatable; everything else lives in `res/values/strings*.xml`.

