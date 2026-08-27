package com.am2.admin.data.api

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cookie store is a map, not a bag.
 *
 * RFC 6265 section 5.3 has a receiving store replace any cookie with the same
 * name, domain and path as the one being processed, and process a response's
 * cookies in the order they arrived -- so when a server sends two cookies of
 * one name, only the last survives.
 *
 * The panel's login does exactly that. session_start() queues a Set-Cookie for
 * the id the request arrived with, session_regenerate_id() appends another for
 * the id that actually holds the login, and both leave the server. A browser
 * discards the first. This jar kept both, replayed both, and PHP honours the
 * first PHPSESSID in a Cookie header -- so the app spent that whole login
 * anonymous, and every feature switch needing an admin right was refused.
 */
class CookieStoreTest {

    private val url = "https://webadmin.am2-poc.com/".toHttpUrl()
    private val now = 1_700_000_000_000L

    private fun cookie(raw: String) = Cookie.parse(url, raw)!!

    @Test
    fun keepsOnlyTheLastOfTwoCookiesOfOneName() {
        // The login response, verbatim: the retired id, then the live one.
        val merged = CookieStore.merge(
            stored = emptyList(),
            received = listOf(
                cookie("PHPSESSID=retired; path=/; secure; HttpOnly; SameSite=Lax"),
                cookie("PHPSESSID=live; path=/; secure; HttpOnly; SameSite=Lax"),
            ),
            nowMillis = now,
        )

        assertEquals(
            "a second cookie of the same name must replace the first, not join it",
            1, merged.size,
        )
        assertEquals("live", merged.single().value)
    }

    @Test
    fun aReceivedCookieReplacesTheStoredOneOfTheSameName() {
        val merged = CookieStore.merge(
            stored = listOf(cookie("PHPSESSID=old; path=/")),
            received = listOf(cookie("PHPSESSID=new; path=/")),
            nowMillis = now,
        )

        assertEquals(1, merged.size)
        assertEquals("new", merged.single().value)
    }

    @Test
    fun cookiesOfOneNameOnDifferentPathsBothSurvive() {
        // Same name, different path: two distinct cookies, and the store may
        // not collapse them just because the names match.
        val merged = CookieStore.merge(
            stored = listOf(cookie("scope=panel; path=/panel")),
            received = listOf(cookie("scope=api; path=/api")),
            nowMillis = now,
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.path == "/panel" && it.value == "panel" })
        assertTrue(merged.any { it.path == "/api" && it.value == "api" })
    }

    @Test
    fun anExpiredCookieRemovesWhatWasStored() {
        val merged = CookieStore.merge(
            stored = listOf(cookie("PHPSESSID=live; path=/")),
            received = listOf(cookie("PHPSESSID=; path=/; max-age=0")),
            nowMillis = now,
        )

        assertTrue("a deleted cookie must not survive the merge", merged.isEmpty())
    }

    @Test
    fun anUnexpiredStoredCookieIsKeptWhenTheResponseCarriesNothing() {
        val merged = CookieStore.merge(
            stored = listOf(cookie("PHPSESSID=live; path=/")),
            received = emptyList(),
            nowMillis = now,
        )

        assertEquals(1, merged.size)
        assertEquals("live", merged.single().value)
    }
}
