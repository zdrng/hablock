# Hablock

Build better habits before opening distracting apps.

Hablock is a free, open-source Android app blocker. Set goals for steps, exercise,
meditation, or time in a chosen app, and earn time with the apps you want to use less.
Everything runs on your phone. No accounts, ads, tracking, or paid features.

[Download the latest release](https://github.com/zdrng/hablock/releases/latest) ·
[Report an issue](https://github.com/zdrng/hablock/issues)

<p>
  <img src="docs/screenshot-home.png" width="270" alt="Hablock home screen showing app blocks and habit goals" />
  <img src="docs/screenshot-blocked.png" width="270" alt="Hablock lock screen shown when a blocked app is opened" />
</p>

## How it works

1. Choose the apps you want to block.
2. Add habit goals and choose how many you need to complete.
3. Meet your goals to unlock a 30-minute session.

After each session, the goals increase a little before the next unlock. They reset
at midnight, so each day starts fresh. Steps, exercise, and meditation goals use
Health Connect where supported.

For stronger blocking, optional device-owner mode prevents uninstalling Hablock and
suspends blocked apps at the Android system level. Turning it off requires a
three-day cooldown. Read the [setup guide](docs/SETUP.md) before enabling it.

## Install

Requires **Android 8.0 or newer**.

Download the APK from [GitHub Releases](https://github.com/zdrng/hablock/releases/latest).
For updates through Obtainium, add `https://github.com/zdrng/hablock` as an app source.

## Privacy

Hablock has no internet permission and keeps its data on your device.
Accessibility access detects which app is open; it does not read screen content.
Usage access measures time in apps, and Health Connect supplies the habit data you
choose to share.

See [permissions and setup](docs/SETUP.md) for details.

## Support development

You can help by [reporting bugs or suggesting improvements](https://github.com/zdrng/hablock/issues),
contributing code, or sharing Hablock with someone who would find it useful.

## Contribute

See the [development guide](docs/DEVELOPMENT.md) for build instructions and a short
architecture overview. Please describe bugs with your Android version and steps
to reproduce them. For security issues, follow the [security policy](SECURITY.md).

With Nix on macOS (Apple Silicon or Intel) or x86_64 NixOS/Linux, launch a local
Android emulator and install the app:

```sh
make run
```

Run `make help` for other commands. If Make is not installed, use
`nix develop -c make run`. See the development guide for hardware acceleration
setup and headless testing.

## License

[GNU General Public License v3.0 only](LICENSE).
