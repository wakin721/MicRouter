package dev.wakin.microuter

import android.content.SharedPreferences

object RouteStore {
    const val PREFS = "routes"
    const val GLOBAL_KEY = "global_rule"

    fun readSystemRoute(prefs: SharedPreferences): SystemRoute =
        SystemRoute.fromJson(prefs.getString(GLOBAL_KEY, null))

    fun writeSystemRoute(prefs: SharedPreferences, route: SystemRoute) {
        prefs.edit().putString(GLOBAL_KEY, route.toJson()).apply()
    }
}
