package dev.hablock.app.ui.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

private fun Context.startSafely(intent: Intent, fallback: Intent? = null) {
    val flagged = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(flagged)
    } catch (_: ActivityNotFoundException) {
        fallback?.let { startSafely(it) }
    }
}

fun Context.openAccessibilitySettings() =
    startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

fun Context.openUsageAccessSettings() =
    startSafely(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

@RequiresApi(Build.VERSION_CODES.S)
fun Context.openExactAlarmSettings() =
    startSafely(
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", packageName, null),
        ),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )

fun Context.openNotificationSettings() =
    startSafely(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )

fun Context.openDeveloperSettings() =
    startSafely(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
