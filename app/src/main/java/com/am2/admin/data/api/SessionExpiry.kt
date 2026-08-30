package com.am2.admin.data.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * The server has forgotten this session, said once, where a screen can hear it.
 *
 * Without this the only evidence was a 403 inside a call the screen made for
 * its own reasons, so every screen reported it as its own feature failing --
 * "GAGAL memperbarui fitur" on a switch that was never the problem.
 */
object SessionExpiry {
    private val _expired = MutableLiveData<Boolean>()
    val expired: LiveData<Boolean> = _expired

    fun announce() = _expired.postValue(true)

    /** Cleared once a screen has acted on it, so it is not answered twice. */
    fun acknowledge() = _expired.postValue(false)
}
