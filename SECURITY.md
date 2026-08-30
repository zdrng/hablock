# Security

## Threat model

Hablock is fully offline. The app does not hold the INTERNET permission, so the
Android OS blocks all network access — nothing the app reads (usage stats, Health
Connect data, app lists) can leave the device. There are no analytics, no accounts,
no telemetry, and no cloud sync. All data is stored in a local DataStore JSON file.

## Sensitive permissions

Hablock uses several sensitive permissions. Each is required for core functionality;
none are used for data collection or exfiltration (which is impossible without
INTERNET).

| Permission | Why it's needed | What it does NOT do |
|---|---|---|
| Accessibility service | Detect which app is in the foreground so blocked apps can be locked. | `canRetrieveWindowContent` is off — it never reads screen content or input. |
| Usage access (`PACKAGE_USAGE_STATS`) | Count foreground minutes in helper apps and the Screen Time view. | Does not log or transmit app usage. |
| Health Connect (read steps, exercise, mindfulness) | Evaluate step, workout, and meditation conditions for today's gate. | Read-only; no write access, no data sharing. |
| Exact alarms | End sessions and reset the day on time. | — |
| Notifications | Notify when a session ends or the relinquish timer completes. | — |
| Device admin (optional) | Device-owner mode suspends blocked apps at the OS level. | Can be revoked; 3-day cooldown prevents impulsive removal. |

## Verifying the APK

Release APKs are signed with a developer keystore. To verify the signing certificate
before installing:

```sh
keytool -printcert -jarfile app-release.apk
```

The certificate fingerprint is published in each GitHub release.

## Reporting a vulnerability

Please open a private security advisory on GitHub:
**[Report a vulnerability](https://github.com/zdrng/hablock/security/advisories/new)**

Or if you prefer, email the repository owner directly via GitHub. Do not open a
public issue for security-sensitive reports.
