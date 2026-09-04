package com.am2.admin.data.model

/**
 * What every guarded endpoint answers with.
 *
 * `message` is nullable because it genuinely is: the success paths reply
 * `{"success":true}` and nothing else, and Gson fills a missing field with null
 * whatever the declared type says. Calling it non-null did not make it present
 * -- it only meant nothing was obliged to check.
 */
data class GenericResponse(
    val success: Boolean,
    val message: String? = null,
)