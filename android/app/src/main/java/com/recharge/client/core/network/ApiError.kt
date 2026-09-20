package com.recharge.client.core.network

import com.google.gson.Gson
import com.recharge.client.core.model.ErrorResponse
import retrofit2.Response

object ApiError {
    private val gson = Gson()

    fun message(response: Response<*>): String {
        val body = response.errorBody()?.string().orEmpty()
        if (body.isBlank()) return "Request failed (${response.code()})"
        return runCatching { gson.fromJson(body, ErrorResponse::class.java).message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Request failed (${response.code()})"
    }

    fun throwableMessage(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: "Unable to connect to the server"
}
