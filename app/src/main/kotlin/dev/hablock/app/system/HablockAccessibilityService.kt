package dev.hablock.app.system

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.hablock.app.HablockApplication
import dev.hablock.app.data.DiagnosticsRuntime
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 700L
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

class HablockAccessibilityService : AccessibilityService() {

    private var lastPackage: String? = null
    private var lastEventAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsRuntime.repository.recordAccessibilityServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        if (pkg == SYSTEM_UI_PACKAGE) return

        val now = SystemClock.elapsedRealtime()
        if (pkg == lastPackage && now - lastEventAt < DEBOUNCE_MS) return
        lastPackage = pkg
        lastEventAt = now
        DiagnosticsRuntime.repository.recordAccessibilityEvent()

        val container = (applicationContext as HablockApplication).container
        container.applicationScope.launch { container.gateEngine.onAppForegrounded(pkg) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        DiagnosticsRuntime.repository.recordAccessibilityServiceConnected(false)
        super.onDestroy()
    }
}
