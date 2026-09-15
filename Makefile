.DEFAULT_GOAL := help

# Every command enters the project's pinned environment, even outside nix develop.
NIX := nix develop -c
EMU := scripts/emulator.sh

.PHONY: help run run-headless start start-headless install build test test-device stop status mirror screenshot logs

help:
	@printf '%s\n' \
	  'make run            Boot with a window, build, install and launch Hablock' \
	  'make run-headless   Boot headless, build, install and launch Hablock' \
	  'make start          Boot the emulator with a window' \
	  'make start-headless Boot the emulator without a window' \
	  'make install        Rebuild and install on the running emulator' \
	  'make build          Build the debug APK' \
	  'make test           Run JVM unit tests' \
	  'make test-device    Boot headless if needed and run Android tests' \
	  'make stop           Stop the selected Hablock emulator' \
	  'make status         List connected devices' \
	  'make mirror         Show a headless emulator using scrcpy' \
	  'make screenshot     Save scratchpad/emu.png' \
	  'make logs           Show recent emulator logcat output'

run:
	$(NIX) $(EMU) up --window

run-headless:
	$(NIX) $(EMU) up

start:
	$(NIX) $(EMU) start --window

start-headless:
	$(NIX) $(EMU) start

install:
	$(NIX) $(EMU) install

build:
	$(NIX) ./gradlew :app:assembleDebug

test:
	$(NIX) ./gradlew :app:testDebugUnitTest

test-device:
	$(NIX) bash -c '$(EMU) start && $(EMU) test'

stop:
	$(NIX) $(EMU) stop

status:
	$(NIX) $(EMU) status

mirror:
	$(NIX) $(EMU) mirror

screenshot:
	$(NIX) $(EMU) shot

logs:
	$(NIX) $(EMU) logcat
