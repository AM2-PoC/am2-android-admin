package com.am2.admin.data.api

import android.content.Context
import com.am2.admin.data.pref.SessionManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private lateinit var appContext: Context
    private lateinit var sessionManager: SessionManager
    private var retrofit: Retrofit? = null

    fun initialize(context: Context) {
        if (retrofit != null) return
        appContext = context.applicationContext
        sessionManager = SessionManager(appContext)
    }

    private fun requireInitialized() {
        check(::appContext.isInitialized) { "RetrofitClient must be initialized from AdminApplication" }
    }

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            requireInitialized()
            val existing = sessionManager.cookieStore().mapNotNull { serialized -> Cookie.parse(url, serialized) }
            val stored = existing
                .filterNot { prior -> cookies.any { incoming ->
                    incoming.name == prior.name && incoming.domain == prior.domain && incoming.path == prior.path
                } }
                .plus(cookies)
                .filterNot { cookie -> cookie.expiresAt < System.currentTimeMillis() }
                .map { cookie -> cookie.toString() }
                .toSet()
            sessionManager.saveCookies(stored)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            requireInitialized()
            val parsed = sessionManager.cookieStore().mapNotNull { Cookie.parse(url, it) }
            sessionManager.saveCookies(parsed.filterNot { it.expiresAt < System.currentTimeMillis() }.map { it.toString() }.toSet())
            return parsed.filter { it.matches(url) && it.expiresAt >= System.currentTimeMillis() }
        }
    }

    private val csrfInterceptor = Interceptor { chain ->
        val request = chain.request()
        val unsafe = request.method in setOf("POST", "PUT", "PATCH", "DELETE")
        val token = if (::sessionManager.isInitialized) sessionManager.csrfToken() else ""
        val guarded = if (unsafe && token.isNotEmpty() && !request.url.encodedPath.endsWith("api_login.php")) {
            request.newBuilder().header("X-CSRF-Token", token).build()
        } else request
        chain.proceed(guarded)
    }

    // Never log HTTP headers or bodies: sessions use credential-bearing cookies.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private fun api(): ApiService {
        requireInitialized()
        val existing = retrofit
        if (existing != null) return existing.create(ApiService::class.java)
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(csrfInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val created = Retrofit.Builder()
            .baseUrl(com.am2.admin.BuildConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        retrofit = created
        return created.create(ApiService::class.java)
    }

    val instance: ApiService
        get() = api()
}
