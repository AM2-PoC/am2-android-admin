package com.am2.admin.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.am2.admin.data.api.SessionExpiry
import android.graphics.Color
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.am2.admin.R
import com.am2.admin.data.api.RetrofitClient
import com.am2.admin.data.pref.SessionManager
import com.am2.admin.ui.access.UserAccessActivity
import com.am2.admin.ui.admin.AdminPanelActivity
import com.am2.admin.ui.channels.ChannelsActivity
import com.am2.admin.ui.login.LoginActivity
import com.am2.admin.ui.logs.LogsActivity
import com.am2.admin.ui.main.MainActivity
import com.am2.admin.ui.settings.SettingsActivity
import com.am2.admin.ui.track.LiveTrackActivity
import com.am2.admin.ui.users.UsersActivity

abstract class BaseActivity : AppCompatActivity() {

    /*
     * Every screen, without each one having to remember. A session the server
     * has forgotten reaches whichever screen the operator happens to be on.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeSessionExpiry()
    }


    protected lateinit var sessionManager: SessionManager
    private var drawerLayout: DrawerLayout? = null

    protected fun setupDrawer(drawer: DrawerLayout, navView: NavigationView, toolbar: androidx.appcompat.widget.Toolbar) {
        this.drawerLayout = drawer
        sessionManager = SessionManager(this)

        setSupportActionBar(toolbar)
        
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            drawer.openDrawer(GravityCompat.START)
        }

        val headerView = navView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.tvAdminName).text = sessionManager.getUsername()
        val tvRole = headerView.findViewById<TextView>(R.id.tvAdminRole)
        tvRole.text = sessionManager.getRole().uppercase()
        if (sessionManager.getRole() == "superadmin") {
            tvRole.setTextColor(Color.parseColor("#FFC300"))
        }

        val menu = navView.menu
        menu.findItem(R.id.nav_admin_panel).isVisible = (sessionManager.getRole() == "superadmin")

        navView.setNavigationItemSelectedListener { item ->
            handleNavigation(item)
            drawer.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun handleNavigation(item: MenuItem) {
        val nextActivity: Class<out AppCompatActivity>? = when (item.itemId) {
            R.id.nav_dashboard -> MainActivity::class.java
            R.id.nav_personnel -> UsersActivity::class.java
            R.id.nav_channels -> ChannelsActivity::class.java
            R.id.nav_access -> UserAccessActivity::class.java
            R.id.nav_livetrack -> LiveTrackActivity::class.java
            R.id.nav_logs -> LogsActivity::class.java
            R.id.nav_admin_panel -> AdminPanelActivity::class.java
            R.id.nav_settings -> SettingsActivity::class.java
            R.id.nav_logout -> {
                lifecycleScope.launch {
                    try {
                        RetrofitClient.instance.logout()
                    } finally {

                        if (!sessionManager.logout()) {
                            Toast.makeText(
                                this@BaseActivity,
                                "Logout belum tersimpan. Coba lagi.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            goToLogin()
                        }
                    }
                }
                null
            }
            else -> null
        }

        if (nextActivity != null && this::class.java != nextActivity) {
            startActivity(Intent(this, nextActivity))
            finish()
        }
    }

    /** Redirect expired sessions to a fresh login task. */
    private fun observeSessionExpiry() {
        SessionExpiry.expired.observe(this) { expired ->
            if (expired != true) return@observe
            SessionExpiry.acknowledge()
            Toast.makeText(this, "Sesi berakhir. Masuk lagi.", Toast.LENGTH_LONG).show()
            goToLogin()
        }
    }

    protected fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    override fun onBackPressed() {
        if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            drawerLayout?.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
