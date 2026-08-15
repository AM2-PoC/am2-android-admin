package com.am2.admin

import android.app.Application
import com.am2.admin.data.api.RetrofitClient

class AdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.initialize(this)
    }
}
