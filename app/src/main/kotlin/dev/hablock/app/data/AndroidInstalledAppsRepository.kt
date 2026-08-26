package dev.hablock.app.data

import android.content.Context
import android.content.Intent
import dev.hablock.app.domain.model.InstalledApp
import dev.hablock.app.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidInstalledAppsRepository(context: Context) : InstalledAppsRepository {

    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    override suspend fun launcherApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homes = queryPackages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        runCatching { packageManager.queryIntentActivities(launcherIntent, 0) }
            .getOrDefault(emptyList())
            .asSequence()
            .map { resolveInfo ->
                InstalledApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                )
            }
            .filter { it.packageName != appContext.packageName && it.packageName !in homes }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override suspend fun homePackages(): Set<String> = withContext(Dispatchers.IO) {
        queryPackages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    @Suppress("DEPRECATION")
    private fun queryPackages(intent: Intent): Set<String> =
        runCatching { appContext.packageManager.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
}
