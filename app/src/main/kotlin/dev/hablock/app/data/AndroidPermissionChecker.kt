package dev.hablock.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import dev.hablock.app.domain.repository.PermissionChecker

private const val ACCESSIBILITY_SERVICE_CLASS = "dev.hablock.app.system.HablockAccessibilityService"
private const val ACCESSIBILITY_SERVICE_SHORT_CLASS = ".system.HablockAccessibilityService"

class AndroidPermissionChecker(context: Context) : PermissionChecker {

    private val appContext = context.applicationContext

    override fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()
        val packageName = appContext.packageName
        return enabled.contains("$packageName/$ACCESSIBILITY_SERVICE_CLASS") ||
            enabled.contains("$packageName/$ACCESSIBILITY_SERVICE_SHORT_CLASS")
    }

    override fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching { usageStatsOpMode(appOps) }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> isGranted(Manifest.permission.PACKAGE_USAGE_STATS)
            else -> false
        }
    }

    override fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            appContext.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: true
        }

    override fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

    @Suppress("DEPRECATION")
    private fun usageStatsOpMode(appOps: AppOpsManager): Int {
        val uid = Process.myUid()
        val packageName = appContext.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        }
    }

    private fun isGranted(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
