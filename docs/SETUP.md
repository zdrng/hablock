# Setup and permissions

[Back to Hablock](../README.md)

## Permissions and privacy

Hablock asks for a lot of sensitive access, so here is exactly what each permission does:

| Permission | Used for |
|---|---|
| Accessibility service | Only to notice which app comes to the foreground so blocked apps can be locked. `canRetrieveWindowContent` is off — it never reads screen content. |
| Usage access | Counting foreground minutes in helper apps and the Screen Time view. |
| Health Connect (read steps / exercise / mindfulness) | Evaluating step, workout and meditation conditions for today's window. |
| Exact alarms | Ending sessions and resetting the day on time. Without it, re-locks fire up to 2 minutes late. |
| Notifications | A heads-up when a session ends or the relinquish timer completes. |

There is **no INTERNET permission**, no analytics, no accounts. All data lives in a local
DataStore file.


## Optional uninstall protection

Device-owner mode suspends blocked apps at the OS level and prevents uninstalling Hablock.
It can only be granted on a device without other accounts/work profiles, via adb:

```sh
adb shell dpm set-device-owner dev.hablock.app/.system.HablockDeviceAdminReceiver
```

To hand back control, start the relinquish timer in Settings; after the 3-day cooldown you can
confirm, which unsuspends everything, drops ownership, and makes the app uninstallable again.
(A factory reset also always works.)

