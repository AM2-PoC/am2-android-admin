package com.am2.admin.data.pref

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "admin_session",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveSession(adminId: Int, username: String, role: String, csrfToken: String) {
        prefs.edit()
            .putInt("admin_id", adminId)
            .putString("username", username)
            .putString("role", role)
            .putString("csrf_token", csrfToken)
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun getAdminId(): Int = prefs.getInt("admin_id", -1)
    fun getRole(): String = prefs.getString("role", "admin") ?: "admin"
    fun getUsername(): String = prefs.getString("username", "Administrator") ?: "Administrator"
    fun csrfToken(): String = prefs.getString("csrf_token", "") ?: ""
    fun cookieStore(): Set<String> = prefs.getStringSet("session_cookies", emptySet())?.toSet() ?: emptySet()
    fun saveCookies(cookies: Set<String>) {
        prefs.edit().putStringSet("session_cookies", cookies).apply()
    }
    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false) && csrfToken().isNotEmpty()

    fun logout() {
        prefs.edit().clear().apply()
    }
}
