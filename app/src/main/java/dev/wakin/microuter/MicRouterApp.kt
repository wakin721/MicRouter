package dev.wakin.microuter

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

class MicRouterApp : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        @Volatile
        var service: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<(XposedService?) -> Unit>()

        fun addServiceListener(listener: (XposedService?) -> Unit) {
            listeners += listener
            listener(service)
        }

        fun removeServiceListener(listener: (XposedService?) -> Unit) {
            listeners -= listener
        }

        private fun notifyServiceChanged(value: XposedService?) {
            listeners.forEach { it(value) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        Companion.service = service
        notifyServiceChanged(service)
    }

    override fun onServiceDied(service: XposedService) {
        Companion.service = null
        notifyServiceChanged(null)
    }
}
