#!/usr/bin/env bash
# Headless Android emulator for local UI verification.
#
# Usage (always inside the Nix devshell so $ANDROID_HOME/emulator/avdmanager are on PATH):
#   nix develop -c scripts/emulator.sh start         # create (if needed) + boot headless
#   nix develop -c scripts/emulator.sh status
#   nix develop -c scripts/emulator.sh shot [file]   # PNG screenshot (default: scratchpad/emu.png)
#   nix develop -c scripts/emulator.sh tap X Y       # tap screen coordinate
#   nix develop -c scripts/emulator.sh key CODE      # send keyevent (e.g. 4 = back)
#   nix develop -c scripts/emulator.sh text "..."    # type text into focused field
#   nix develop -c scripts/emulator.sh logcat [pat]  # dump recent logcat, optionally grep pattern
#   nix develop -c scripts/emulator.sh mirror        # mirror screen as a window (scrcpy)
#   nix develop -c scripts/emulator.sh stop          # clean stop (saves quickboot snapshot)
#
# HW acceleration via /dev/kvm (user must be in group 'kvm'). Headless, no display
# needed — everything driven through adb.
set -euo pipefail

AVD="hablock"
IMG="system-images;android-35;google_apis;x86_64"
DEVICE="pixel_6"
SERIAL="emulator-5554"
LOG="/tmp/hablock-emulator.log"

die() { echo "✗ $*" >&2; exit 1; }

need_env() {
  [ -n "${ANDROID_HOME:-}" ] || die "ANDROID_HOME not set — run inside 'nix develop'."
  command -v avdmanager >/dev/null || die "avdmanager not on PATH — re-enter 'nix develop'."
  command -v emulator   >/dev/null || die "emulator not on PATH — re-enter 'nix develop'."
}

cmd_create() {
  need_env
  if avdmanager list avd 2>/dev/null | grep -q "Name: ${AVD}\b"; then
    echo "✓ AVD '${AVD}' already exists."
    return
  fi
  echo "→ Creating AVD '${AVD}' ($IMG, $DEVICE) …"
  echo "no" | avdmanager create avd -n "$AVD" -k "$IMG" -d "$DEVICE" --force
  echo "✓ AVD '${AVD}' created."
}

cmd_start() {
  need_env
  if adb devices | grep -q "^${SERIAL}[[:space:]]*device$"; then
    echo "✓ Emulator already running ($SERIAL)."
    return
  fi
  cmd_create
  echo "→ Starting emulator headless (KVM) …"
  # No -no-snapshot: the quickboot snapshot gives warm restarts.
  nohup emulator -avd "$AVD" \
    -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -accel on \
    >"$LOG" 2>&1 &

  echo "→ Waiting for adb device …"
  adb wait-for-device
  echo "→ Waiting for boot (sys.boot_completed) …"
  for _ in $(seq 1 180); do
    if [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      adb -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true # dismiss lockscreen
      echo "✓ ready: $SERIAL"
      return
    fi
    sleep 2
  done
  die "Boot timeout. Log: $LOG (if adb wait hangs, check opensnitch loopback rules for adb/emulator/qemu)."
}

cmd_stop() {
  adb -s "$SERIAL" emu kill 2>/dev/null || adb emu kill 2>/dev/null || true
  echo "✓ Emulator stopped."
}

cmd_status() {
  adb devices | grep "emulator-" || echo "No emulator running."
}

cmd_shot() {
  local out="${1:-scratchpad/emu.png}"
  mkdir -p "$(dirname "$out")"
  adb -s "$SERIAL" exec-out screencap -p > "$out"
  echo "✓ Screenshot: $out"
}

cmd_tap()  { adb -s "$SERIAL" shell input tap "$1" "$2"; }
cmd_key()  { adb -s "$SERIAL" shell input keyevent "$1"; }
cmd_text() { adb -s "$SERIAL" shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')"; }

cmd_logcat() {
  if [ -n "${1:-}" ]; then
    adb -s "$SERIAL" logcat -d | grep -E "$1" | tail -100
  else
    adb -s "$SERIAL" logcat -d | tail -100
  fi
}

cmd_mirror() {
  command -v scrcpy >/dev/null || die "scrcpy not on PATH — re-enter 'nix develop'."
  adb devices | grep -q "^${SERIAL}[[:space:]]*device$" || die "Emulator not running — 'start' first."
  exec scrcpy -s "$SERIAL"
}

case "${1:-}" in
  create) cmd_create ;;
  start)  cmd_start ;;
  stop)   cmd_stop ;;
  status) cmd_status ;;
  shot)   shift; cmd_shot "${1:-}" ;;
  tap)    shift; cmd_tap "$1" "$2" ;;
  key)    shift; cmd_key "$1" ;;
  text)   shift; cmd_text "$1" ;;
  logcat) shift; cmd_logcat "${1:-}" ;;
  mirror) cmd_mirror ;;
  *) echo "Usage: $0 {create|start|stop|status|shot [file]|tap X Y|key CODE|text STR|logcat [pat]|mirror}" >&2; exit 1 ;;
esac
