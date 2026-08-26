package dev.hablock.app.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.hablock.app.HablockApplication
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 700L
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

class HablockAccessibilityService : AccessibilityService() {

    private var launcherPackage: String? = null
    private var lastPackage: String? = null
    private var lastEventAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launcherPackage = packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        if (pkg == packageName) {
            // Our own screens end the debounce window, so backing out re-triggers the block.
            lastPackage = null
            return
        }
        if (pkg == SYSTEM_UI_PACKAGE || pkg == launcherPackage) return

        val now = SystemClock.elapsedRealtime()
        if (pkg == lastPackage && now - lastEventAt < DEBOUNCE_MS) return
        lastPackage = pkg
        lastEventAt = now

        val container = (applicationContext as HablockApplication).container
        container.applicationScope.launch { container.gateEngine.onAppForegrounded(pkg) }
    }

    override fun onInterrupt() = Unit
}
