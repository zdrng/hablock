package dev.hablock.app.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.system.HablockDeviceAdminReceiver

class DevicePolicyController(context: Context) : DeviceOwnerController {

    private val appContext = context.applicationContext
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, HablockDeviceAdminReceiver::class.java)

    override fun isDeviceOwner(): Boolean =
        devicePolicyManager?.isDeviceOwnerApp(appContext.packageName) == true

    override fun setPackagesSuspended(packages: Set<String>, suspended: Boolean): Set<String> {
        val dpm = devicePolicyManager ?: return packages
        if (packages.isEmpty()) return emptySet()
        if (!isDeviceOwner()) return packages
        return runCatching { dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspended).toSet() }
            .getOrDefault(packages)
    }

    override fun applyRestrictions() {
        val dpm = devicePolicyManager ?: return
        if (!isDeviceOwner()) return
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME) }
    }

    @Suppress("DEPRECATION")
    override fun relinquishOwnership(): Boolean {
        val dpm = devicePolicyManager ?: return false
        if (!isDeviceOwner()) return true
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME) }
        runCatching { dpm.clearDeviceOwnerApp(appContext.packageName) }
        return !isDeviceOwner()
    }
}
