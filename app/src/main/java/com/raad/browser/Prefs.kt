package com.raad.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * إعدادات التطبيق المخزنة عبر SharedPreferences:
 * - وضع المظهر (تلقائي/فاتح/داكن)
 * - مجلد التحميل الافتراضي (URI من SAF أو null لمجلد التطبيق)
 */
object Prefs {

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private lateinit var sp: SharedPreferences

    var themeMode by mutableStateOf(THEME_SYSTEM)
        private set

    /** URI لمجلد التحميل الافتراضي الذي يختاره المستخدم، أو null لاستخدام مجلد التطبيق */
    var defaultTreeUri by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("raad_prefs", Context.MODE_PRIVATE)
        themeMode = sp.getInt("theme_mode", THEME_SYSTEM)
        defaultTreeUri = sp.getString("default_tree_uri", null)
    }

    fun updateThemeMode(mode: Int) {
        themeMode = mode
        sp.edit().putInt("theme_mode", mode).apply()
    }

    fun updateDefaultTreeUri(uri: String?) {
        defaultTreeUri = uri
        sp.edit().putString("default_tree_uri", uri).apply()
    }
}
