package com.recharge.client.core.network

import com.google.gson.Gson
import com.recharge.client.core.model.WithdrawMoneyRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.util.UUID

class ApiServicesTest {

    @Test
    fun withdrawalRequestWritesJsonBodyToHttpWire() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"withdrawalId":"WDR-MOCK","status":"SUCCESS","provider":"mock","amount":10.00,"upiId":"test@mockupi","balance":990.00,"availableBalance":990.00,"message":"Mock withdrawal completed successfully"}"""
                )
        )
        server.start()

        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create(Gson()))
                .build()
                .create(ClientApi::class.java)

            val request = WithdrawMoneyRequest(
                amount = BigDecimal("10.00"),
                provider = "mock",
                clientRequestId = UUID.randomUUID().toString(),
                upiId = "test@mockupi"
            )
            val body = Gson().toJson(request).toRequestBody("application/json".toMediaType())

            val response = api.withdraw(body)
            assertTrue(response.isSuccessful)
            assertNotNull(response.body())

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/wallet/withdraw", recorded.path)
            assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))

            val payload = recorded.body.readUtf8()
            assertTrue(payload.contains(""amount":10.00") || payload.contains(""amount":10"))
            assertTrue(payload.contains(""provider":"mock""))
            assertTrue(payload.contains(""upiId":"test@mockupi""))
            assertTrue(payload.contains(""clientRequestId":"" + request.clientRequestId + """))
        } finally {
            server.shutdown()
        }
    }
}
