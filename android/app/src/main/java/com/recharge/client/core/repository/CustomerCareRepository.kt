package com.recharge.client.core.repository

import android.content.Context
import com.recharge.client.core.model.CreateSupportTicketRequest
import com.recharge.client.core.model.SupportMessageRequest
import com.recharge.client.core.model.SupportTicketPageResponse
import com.recharge.client.core.model.SupportTicketResponse
import com.recharge.client.core.network.ApiError
import com.recharge.client.core.network.ClientApi
import com.recharge.client.core.network.NetworkModule
import kotlinx.coroutines.CancellationException

class CustomerCareRepository private constructor(context: Context) {
    private val api: ClientApi = NetworkModule.clientApi(context.applicationContext)

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun tickets(page: Int = 0, size: Int = 20, status: String? = null): Result<SupportTicketPageResponse> = apiCall {
        val response = api.supportTickets(page, size, status)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun ticket(ticketId: String): Result<SupportTicketResponse> = apiCall {
        val response = api.supportTicket(ticketId)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun create(category: String, subject: String, message: String): Result<SupportTicketResponse> = apiCall {
        val response = api.createSupportTicket(
            CreateSupportTicketRequest(category = category, subject = subject, message = message)
        )
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun reply(ticketId: String, message: String): Result<SupportTicketResponse> = apiCall {
        val response = api.addSupportMessage(ticketId, SupportMessageRequest(message))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun close(ticketId: String): Result<SupportTicketResponse> = apiCall {
        val response = api.closeSupportTicket(ticketId)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    companion object {
        @Volatile
        private var instance: CustomerCareRepository? = null

        fun getInstance(context: Context): CustomerCareRepository =
            instance ?: synchronized(this) {
                instance ?: CustomerCareRepository(context).also { instance = it }
            }
    }
}
