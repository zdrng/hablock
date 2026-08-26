package dev.hablock.app

import android.app.Application
import dev.hablock.app.di.AppContainer
import kotlinx.coroutines.launch

class HablockApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.alarmScheduler.scheduleDayReset(container.dayClock.nextReset())
        if (container.deviceOwnerController.isDeviceOwner()) container.deviceOwnerController.applyRestrictions()
        container.applicationScope.launch { container.gateEngine.refreshAll() }
    }
}
