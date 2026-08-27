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

    /*
     * Persisted as a set, which is safe only because CookieStore guarantees one
     * entry per (name, domain, path). It did not before: two PHPSESSID cookies
     * from one login were both kept, and a set has no order to decide which
     * went out first.
     */
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            requireInitialized()
            val merged = CookieStore.merge(
                stored = read(url),
                received = cookies,
                nowMillis = System.currentTimeMillis(),
            )
            write(merged)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            requireInitialized()
            val now = System.currentTimeMillis()
            // Re-saving here is what prunes what has expired since the last
            // response; the store is only ever touched on a request or a reply.
            val stored = CookieStore.merge(read(url), emptyList(), now)
            write(stored)
            return CookieStore.select(url, stored, now)
        }

        private fun read(url: HttpUrl): List<Cookie> =
            sessionManager.cookieStore().mapNotNull { serialized -> Cookie.parse(url, serialized) }

        private fun write(cookies: List<Cookie>) =
            sessionManager.saveCookies(cookies.map { cookie -> cookie.toString() }.toSet())
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
