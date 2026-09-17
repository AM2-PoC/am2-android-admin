package com.am2.admin.data.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object SessionExpiry {
    private val _expired = MutableLiveData<Boolean>()
    val expired: LiveData<Boolean> = _expired

    fun announce() = _expired.postValue(true)

    fun acknowledge() = _expired.postValue(false)
}
