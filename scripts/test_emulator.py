#!/usr/bin/env python3
"""Exercise emulator orchestration without an Android SDK or a running device."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().with_name("emulator.sh")
FAKE = r'''
import json, os, pathlib, sys, time
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ['CALLS'], 'a') as log:
    log.write(json.dumps([name] + args) + '\n')
if name == 'uname':
    print(os.environ.get('HOST', 'Darwin arm64'))
elif name == 'avdmanager':
    avd = args[args.index('-n') + 1]
    target = pathlib.Path(os.environ['ANDROID_AVD_HOME']) / (avd + '.avd')
    target.mkdir(parents=True)
    (target / 'config.ini').write_text('hw.ramSize=2048\nimage.sysdir.1=old-sdk/\n')
elif name == 'emulator':
    if args == ['-accel-check']:
        sys.exit(0)
    if os.environ.get('EMULATOR_FAIL'):
        print('simulated startup failure')
        sys.exit(1)
    time.sleep(30)
elif name == 'adb':
    connected = os.environ.get('CONNECTED') == '1'
    if args == ['devices']:
        print('List of devices attached')
        if connected:
            print('emulator-' + os.environ.get('HABLOCK_EMULATOR_PORT', '5554') + '\tdevice')
    elif args[-1:] == ['get-state']:
        print('device' if connected else 'offline')
    elif args[-3:] == ['emu', 'avd', 'name']:
        print(os.environ.get('ACTUAL_AVD', 'hablock-api36-arm64-v8a') + '\nOK')
    elif args[-3:] == ['shell', 'getprop', 'sys.boot_completed']:
        print(os.environ.get('BOOTED', '1'))
'''


class EmulatorScriptTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        binary = root / 'bin'
        binary.mkdir()
        for name in ('adb', 'avdmanager', 'emulator', 'uname'):
            path = binary / name
            path.write_text('#!' + shutil.which('python3') + '\n' + FAKE)
            path.chmod(0o755)
        sdk = root / 'sdk'
        (sdk / 'system-images/android-36/google_apis/arm64-v8a').mkdir(parents=True)
        self.avds = root / 'android/avd'
        self.calls = root / 'calls.jsonl'
        self.env = {key: value for key, value in os.environ.items()
                    if not key.startswith(('ANDROID_', 'HABLOCK_'))}
        self.env.update(PATH=str(binary) + os.pathsep + os.environ['PATH'],
                        ANDROID_HOME=str(sdk), ANDROID_USER_HOME=str(root / 'android'),
                        ANDROID_AVD_HOME=str(self.avds), CALLS=str(self.calls),
                        HABLOCK_BOOT_TIMEOUT='1')

    def run_script(self, *args, **env):
        return subprocess.run(['bash', str(SCRIPT), *args], env=self.env | env,
                              capture_output=True, text=True, timeout=10)

    def recorded(self):
        return [json.loads(line) for line in self.calls.read_text().splitlines()]

    def test_creates_native_image_and_refreshes_existing_image_path(self):
        for _ in range(2):
            result = self.run_script('create')
            self.assertEqual(result.returncode, 0, result.stderr)
        calls = [c for c in self.recorded() if c[0] == 'avdmanager']
        self.assertEqual(len(calls), 1)
        self.assertIn('system-images;android-36;google_apis;arm64-v8a', calls[0])
        config = (self.avds / 'hablock-api36-arm64-v8a.avd/config.ini').read_text()
        self.assertIn('hw.ramSize=2048', config)
        self.assertNotIn('old-sdk', config)
        self.assertEqual(config.count('image.sysdir.1='), 1)

    def test_rejects_foreign_device_before_stopping(self):
        result = self.run_script('stop', CONNECTED='1', ACTUAL_AVD='personal')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('belongs to AVD', result.stderr)
        self.assertFalse(any(c[-2:] == ['emu', 'kill'] for c in self.recorded()))

    def test_stop_targets_selected_serial(self):
        result = self.run_script('stop', CONNECTED='1', HABLOCK_EMULATOR_PORT='5556')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(['adb', '-s', 'emulator-5556', 'emu', 'kill'], self.recorded())

    def test_ready_device_is_reused_without_launching_another(self):
        result = self.run_script('start', CONNECTED='1')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any(c[0] == 'emulator' for c in self.recorded()))

    def test_startup_failure_returns_instead_of_waiting_for_adb(self):
        result = self.run_script('start', EMULATOR_FAIL='1', HABLOCK_BOOT_TIMEOUT='5')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('exited before boot', result.stderr)

    def test_timeout_covers_missing_adb_device(self):
        result = self.run_script('start', '--window', HABLOCK_EMULATOR_PORT='5556')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Boot timed out', result.stderr)
        start = next(c for c in self.recorded() if c[0] == 'emulator' and '-avd' in c)
        self.assertNotIn('-no-window', start)
        self.assertEqual(start[start.index('-port') + 1], '5556')

    def test_odd_port_is_rejected(self):
        result = self.run_script('start', HABLOCK_EMULATOR_PORT='5555')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('even emulator port', result.stderr)

    def test_wrong_host_abi_is_rejected(self):
        result = self.run_script('create', HOST='Linux x86_64', HABLOCK_EMULATOR_ABI='arm64-v8a')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Use x86_64 images', result.stderr)


if __name__ == '__main__':
    unittest.main()
