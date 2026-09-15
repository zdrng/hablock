#!/usr/bin/env bash
# Use inside nix develop. Run with --help for commands and overrides.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

case "$(uname -sm)" in
  'Darwin arm64') DEFAULT_ABI="arm64-v8a" ;;
  'Darwin x86_64'|'Linux x86_64') DEFAULT_ABI="x86_64" ;;
  *) DEFAULT_ABI="unsupported" ;;
esac
ABI="${HABLOCK_EMULATOR_ABI:-$DEFAULT_ABI}"
API="${HABLOCK_EMULATOR_API:-36}"
AVD="${HABLOCK_AVD:-hablock-api${API}-${ABI}}"
PORT="${HABLOCK_EMULATOR_PORT:-5554}"
SERIAL="emulator-${PORT}"
BOOT_TIMEOUT="${HABLOCK_BOOT_TIMEOUT:-360}"
# All mutable emulator state lives outside the immutable Nix SDK.
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/hablock/android}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$ANDROID_USER_HOME/avd}"
LOG="$ANDROID_USER_HOME/${AVD}-${PORT}.log"
IMG="system-images;android-${API};google_apis;${ABI}"
PACKAGE="dev.hablock.app"
COMPONENT="${PACKAGE}/.ui.MainActivity"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

die() { echo "Error: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null || die "$1 not on PATH. Run inside 'nix develop'."; }

need_env() {
  [ "$DEFAULT_ABI" != unsupported ] || die "Supported emulator hosts: Intel Linux, Intel macOS, Apple Silicon macOS."
  [ "$ABI" = "$DEFAULT_ABI" ] || die "Use $DEFAULT_ABI images for acceleration on this host (configured: $ABI)."
  [ -n "${ANDROID_HOME:-}" ] || die "ANDROID_HOME not set. Run inside 'nix develop'."
  need avdmanager
  need emulator
  need adb
  case "$PORT" in ''|*[!0-9]*) die "HABLOCK_EMULATOR_PORT must be an even port from 5554 to 5682." ;; esac
  (( PORT >= 5554 && PORT <= 5682 && PORT % 2 == 0 )) || die "Use an even emulator port from 5554 to 5682."
  case "$BOOT_TIMEOUT" in ''|*[!0-9]*) die "HABLOCK_BOOT_TIMEOUT must be a positive number of seconds." ;; esac
  (( BOOT_TIMEOUT > 0 )) || die "HABLOCK_BOOT_TIMEOUT must be positive."
  case "$AVD" in ''|*[!a-zA-Z0-9_.-]*) die "HABLOCK_AVD may only contain letters, digits, dots, underscores and hyphens." ;; esac
  mkdir -p "$ANDROID_AVD_HOME"
}

is_connected() { [ "$(adb -s "$SERIAL" get-state 2>/dev/null)" = device ]; }

check_identity() {
  local actual
  actual="$(adb -s "$SERIAL" emu avd name 2>/dev/null | tr -d '\r' | head -n 1)"
  [ "$actual" = "$AVD" ] || die "$SERIAL belongs to AVD '$actual', not '$AVD'. Choose another HABLOCK_EMULATOR_PORT."
}

need_device() {
  need adb
  is_connected || die "$SERIAL not ready. Run '$0 start' first."
  check_identity
  [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] ||
    die "$SERIAL is still booting. Run '$0 start' to wait for it."
}

cmd_create() {
  need_env
  [ -d "$ANDROID_HOME/system-images/android-$API/google_apis/$ABI" ] ||
    die "Image $IMG is missing. Add it to flake.nix and re-enter nix develop; do not modify the Nix SDK with sdkmanager."
  if [ -f "$ANDROID_AVD_HOME/$AVD.avd/config.ini" ]; then
    echo "AVD '$AVD' already exists."
  else
    avdmanager create avd -n "$AVD" -k "$IMG" -d pixel_6 <<< "no"
  fi
  # Resolve image paths against the current pinned SDK after a flake update, too.
  # Python avoids GNU/BSD sed -i differences and preserves all other AVD settings.
  need python3
  python3 - "$ANDROID_AVD_HOME/$AVD.avd/config.ini" "$ANDROID_HOME/system-images/android-$API/google_apis/$ABI/" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
lines = [line for line in path.read_text().splitlines() if not line.startswith("image.sysdir.1=")]
path.write_text("\n".join(lines + ["image.sysdir.1=" + sys.argv[2]]) + "\n")
PY
}

cmd_start() {
  need_env
  local window="${1:-}" pid="" deadline
  [ -z "$window" ] || [ "$window" = --window ] || die "Usage: $0 start [--window]"
  if is_connected; then
    check_identity
  else
    # Never attach to or replace another emulator using the requested port.
    if adb devices | awk '{print $1}' | grep -Fxq "$SERIAL"; then
      die "$SERIAL is present but offline. Wait for it or select another port."
    fi
    cmd_create
    emulator -accel-check || die "Acceleration unavailable. On NixOS check /dev/kvm access; on macOS check Hypervisor.Framework."
    local args=(-avd "$AVD" -port "$PORT" -no-audio -no-boot-anim -gpu software -accel on)
    [ "$window" = --window ] || args+=(-no-window)
    echo "Starting $AVD ($SERIAL). Log: $LOG"
    nohup emulator "${args[@]}" >"$LOG" 2>&1 < /dev/null &
    pid=$!
  fi

  # Bound the whole wait, including the period before adb discovers the emulator.
  deadline=$((SECONDS + BOOT_TIMEOUT))
  while (( SECONDS < deadline )); do
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      tail -n 40 "$LOG" >&2
      die "Emulator exited before boot. Log: $LOG"
    fi
    if is_connected; then
      check_identity
      if [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
        adb -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true
        echo "Ready: $SERIAL"
        return
      fi
    fi
    sleep 2
  done
  # Leave an already-running emulator alone; clean up only the process we started.
  if [ -n "$pid" ]; then kill "$pid" 2>/dev/null || true; fi
  die "Boot timed out after ${BOOT_TIMEOUT}s. Log: $LOG"
}

cmd_stop() {
  need adb
  is_connected || die "$SERIAL is not connected."
  check_identity
  adb -s "$SERIAL" emu kill
}

cmd_status() { need adb; adb devices -l; }
cmd_shot() {
  need_device
  local out="${1:-scratchpad/emu.png}"
  mkdir -p "$(dirname "$out")"
  adb -s "$SERIAL" exec-out screencap -p > "$out"
  echo "Screenshot: $out"
}
cmd_tap() { need_device; adb -s "$SERIAL" shell input tap "${1:?X required}" "${2:?Y required}"; }
cmd_key() { need_device; adb -s "$SERIAL" shell input keyevent "${1:?key code required}"; }
cmd_text() { need_device; adb -s "$SERIAL" shell input text "$(printf '%s' "${1:?text required}" | sed 's/ /%s/g')"; }
cmd_logcat() {
  need_device
  if [ -n "${1:-}" ]; then
    adb -s "$SERIAL" logcat -d | grep -E "$1" | tail -100
  else
    adb -s "$SERIAL" logcat -d | tail -100
  fi
}
cmd_install() {
  need_device
  "$ROOT/gradlew" :app:assembleDebug
  [ -f "$APK" ] || die "Debug APK not found at $APK."
  adb -s "$SERIAL" install -r "$APK"
}
cmd_test() {
  need_device
  ANDROID_SERIAL="$SERIAL" "$ROOT/gradlew" :app:connectedDebugAndroidTest
}
cmd_smoke() {
  cmd_install
  adb -s "$SERIAL" shell am force-stop "$PACKAGE"
  adb -s "$SERIAL" shell am start -W -n "$COMPONENT"
  sleep 2
  adb -s "$SERIAL" shell pidof "$PACKAGE" >/dev/null || die "$PACKAGE did not remain running."
  echo "Launcher smoke check passed on $SERIAL."
}
cmd_mirror() { need_device; need scrcpy; exec scrcpy -s "$SERIAL"; }

command="${1:---help}"
[ "$#" -eq 0 ] || shift
case "$command" in
  create) cmd_create ;;
  start) cmd_start "$@" ;;
  up) cmd_start "$@"; cmd_smoke ;;
  stop) cmd_stop ;;
  status) cmd_status ;;
  shot) cmd_shot "$@" ;;
  tap) cmd_tap "$@" ;;
  key) cmd_key "$@" ;;
  text) cmd_text "$@" ;;
  logcat) cmd_logcat "$@" ;;
  install) cmd_install ;;
  test) cmd_test ;;
  smoke) cmd_smoke ;;
  mirror) cmd_mirror ;;
  --help|-h) cat <<'HELP'
Usage: scripts/emulator.sh COMMAND
  up [--window]       Create, boot, build, install and launch Hablock
  start [--window]    Create and boot (headless by default)
  create             Create the AVD without booting
  install / smoke    Build and install / also verify the app launches
  test               Run connected Android instrumentation tests
  status / stop      List devices / stop only the selected Hablock AVD
  mirror             Show the running emulator with scrcpy
  shot [file]        Save a PNG (default scratchpad/emu.png)
  tap X Y / key CODE / text STR / logcat [pattern]

Run inside nix develop. Overrides:
  HABLOCK_AVD, HABLOCK_EMULATOR_PORT (5554), HABLOCK_BOOT_TIMEOUT (360 seconds)
  ANDROID_USER_HOME, ANDROID_AVD_HOME (writable state; never inside the SDK)
  HABLOCK_EMULATOR_API / HABLOCK_EMULATOR_ABI (must match flake.nix's image)
HELP
    ;;
  *) die "Unknown command '$command'. Run '$0 --help'." ;;
esac
