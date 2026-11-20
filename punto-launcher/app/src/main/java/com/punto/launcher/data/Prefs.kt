package com.punto.launcher.data

import android.content.Context

/** Preferências simples do usuário (endereços e apps padrão). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("punto_os", Context.MODE_PRIVATE)

    var homeAddress: String
        get() = sp.getString("home_address", "") ?: ""
        set(value) = sp.edit().putString("home_address", value.trim()).apply()

    var workAddress: String
        get() = sp.getString("work_address", "") ?: ""
        set(value) = sp.edit().putString("work_address", value.trim()).apply()

    /** "spotify" ou "ytmusic" */
    var musicApp: String
        get() = sp.getString("music_app", MUSIC_SPOTIFY) ?: MUSIC_SPOTIFY
        set(value) = sp.edit().putString("music_app", value).apply()

    /** "waze" ou "maps" */
    var navApp: String
        get() = sp.getString("nav_app", NAV_WAZE) ?: NAV_WAZE
        set(value) = sp.edit().putString("nav_app", value).apply()

    companion object {
        const val MUSIC_SPOTIFY = "spotify"
        const val MUSIC_YTMUSIC = "ytmusic"
        const val NAV_WAZE = "waze"
        const val NAV_MAPS = "maps"
    }
}
