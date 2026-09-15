# Development

[Back to Hablock](../README.md)

## Build and test

With [Nix](https://nixos.org) (pinned toolchain — JDK 17, Android SDK, Gradle 8):

```sh
nix develop -c gradle assembleDebug
nix develop -c gradle test
```

Without Nix you need JDK 17 and an Android SDK (platform 36, build-tools 36.0.0) with
`ANDROID_HOME` set:

```sh
./gradlew assembleDebug
./gradlew test
```

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Debug builds shorten
sessions to 1 minute for easier testing.

Release builds are signed via a local `keystore.properties` + keystore (not in the repo — see
`app/build.gradle.kts` for the expected properties; generate your own with `keytool`).


## Emergency reset for local testing

For local testing, explicitly include the emergency-use reset gesture:

```sh
nix develop -c gradle assembleDebug -PenableEmergencyReset=true
# Also supported with assembleRelease for a locally signed test build.
```

Tap the version row in Settings five times, with no more than one second between taps,
to restore both emergency unlocks. A toast confirms the reset. This restores the allowance;
it does not unlock any block. The default is disabled for **all** build types. The gesture
and reset implementation live in a separate source directory excluded from normal builds,
so exclusion does not depend on R8. Do not set this property in shared Gradle properties
or distribution CI. Rebuild without the flag to produce a normal APK.

## Architecture

Single Gradle module, manual DI (no Hilt), no Room/WorkManager/navigation-compose:

- `domain/` — pure Kotlin: models, gate math (`GateEvaluator`, `SessionMath`), the
  `DefaultGateEngine` orchestrator, repository interfaces. Zero Android imports, enforced by a
  unit test (`ArchitectureTest`).
- `data/` — DataStore + kotlinx.serialization, UsageStatsManager, Health Connect,
  DevicePolicyManager implementations.
- `enforcement/` — the two enforcement backends behind an `EnforcementCoordinator`.
- `system/` — accessibility service (a dumb foreground sensor with zero policy), broadcast
  receivers, alarms, notifications.
- `ui/` — Jetpack Compose + ViewModels, Material 3 Expressive.

JVM unit tests cover the domain layer (`nix develop -c gradle test`); hand-written fakes, no
mocking library.

Known i18n caveat: compact duration tokens ("2h 15m") and the day header are formatted in code
and not yet translatable; everything else lives in `res/values/strings*.xml`.

