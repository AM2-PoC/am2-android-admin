package com.am2.admin.data.api

import android.content.Context
import com.am2.admin.data.pref.SessionManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

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
        val builder = request.newBuilder()
            /*
             * Say that a JSON refusal can be read.
             *
             * am2_csrf_require() writes JSON only when the request says it
             * accepts JSON, and plain text otherwise. This client never said
             * so, so a rejected call came back as a body the converter could
             * not parse -- and the operator was told the feature had failed
             * rather than that the session had ended.
             */
            .header("Accept", "application/json")
        if (unsafe && token.isNotEmpty() && !request.url.encodedPath.endsWith("api_login.php")) {
            builder.header("X-CSRF-Token", token)
        }
        chain.proceed(builder.build())
    }

    private val sessionExpiryInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 401 && ::sessionManager.isInitialized
            && !chain.request().url.encodedPath.endsWith("api_login.php")
        ) {
            sessionManager.logout()
            SessionExpiry.announce()
        }
        response
    }


    private fun api(): ApiService {
        requireInitialized()
        val existing = retrofit
        if (existing != null) return existing.create(ApiService::class.java)
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(csrfInterceptor)
            .addInterceptor(sessionExpiryInterceptor)
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
