package com.am2.admin.data.pref

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("admin_session", Context.MODE_PRIVATE)

    fun saveSession(adminId: Int, username: String, role: String) {
        prefs.edit().apply {
            putInt("admin_id", adminId)
            putString("username", username)
            putString("role", role)
            putBoolean("is_logged_in", true)
            apply()
        }
    }

    fun getAdminId(): Int = prefs.getInt("admin_id", -1)
    fun getRole(): String = prefs.getString("role", "admin") ?: "admin"
    fun getUsername(): String = prefs.getString("username", "Administrator") ?: "Administrator"
    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)

    fun logout() {
        prefs.edit().clear().apply()
    }
}
