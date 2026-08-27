package com.am2.admin.data.api

import okhttp3.Cookie

/**
 * What the cookie jar keeps, decided in one place that can be tested.
 *
 * This logic used to live inline in RetrofitClient's anonymous CookieJar, where
 * nothing could reach it without an Android runtime and a SharedPreferences.
 * It is lifted out unchanged so it can be asserted on directly; the behaviour
 * is corrected in the commit that follows this one.
 */
object CookieStore {

    /**
     * Fold a response's cookies into what is already stored.
     *
     * `nowMillis` is a parameter rather than a call to the clock so a test can
     * state when "now" is.
     */
    fun merge(stored: List<Cookie>, received: List<Cookie>, nowMillis: Long): List<Cookie> =
        stored
            .filterNot { prior ->
                received.any { incoming ->
                    incoming.name == prior.name &&
                        incoming.domain == prior.domain &&
                        incoming.path == prior.path
                }
            }
            .plus(received)
            .filterNot { cookie -> cookie.expiresAt < nowMillis }
}
