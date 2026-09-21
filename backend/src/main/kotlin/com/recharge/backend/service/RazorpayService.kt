package com.recharge.backend.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.api.CreatePaymentOrderResponse
import com.recharge.backend.api.VerifyPaymentRequest
import com.recharge.backend.api.VerifyPaymentResponse
import com.recharge.backend.domain.PaymentOrderEntity
import com.recharge.backend.repository.PaymentOrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class RazorpayService(
    private val paymentOrders: PaymentOrderRepository,
    private val walletService: WalletService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.razorpay.key-id:}") private val keyId: String,
    @Value("\${app.razorpay.key-secret:}") private val keySecret: String,
    @Value("\${app.razorpay.base-url:https://api.razorpay.com/v1}") private val baseUrl: String
) : PaymentGatewayProvider {
    override val providerName: String = "razorpay"
    override fun isConfigured(): Boolean = keyId.isNotBlank() && keySecret.isNotBlank()

    private val httpClient: HttpClient = HttpClient.newBuilder().build()

    override fun createWalletOrder(userId: Long, request: CreatePaymentOrderRequest): CreatePaymentOrderResponse {
        require(keyId.isNotBlank() && keySecret.isNotBlank()) {
            "Razorpay test keys are not configured on the backend"
        }

        val normalizedAmount = request.amount.setScale(2)
        require(normalizedAmount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        require(normalizedAmount <= BigDecimal("100000.00")) { "Amount cannot exceed ₹100,000 in a single wallet load" }
        require(request.clientRequestId.length in 8..80) { "Invalid client request id" }

        val existing = paymentOrders.findByClientRequestIdAndUserId(request.clientRequestId.trim(), userId)
        if (existing.isPresent) {
            val order = existing.get()
            return CreatePaymentOrderResponse(
                provider = providerName,
                orderId = order.razorpayOrderId,
                amount = order.amount,
                currency = order.currency,
                keyId = keyId
            )
        }

        val amountPaise = normalizedAmount.movePointRight(2).longValueExact()
        val receipt = "W${userId}${UUID.randomUUID().toString().replace("-", "").take(30)}"
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "amount" to amountPaise,
                "currency" to "INR",
                "receipt" to receipt,
                "notes" to mapOf("user_id" to userId.toString(), "purpose" to "WALLET_LOAD")
            )
        )

        val response = razorpayRequest("POST", "/orders", payload)
        val json = response.body
        val orderId = json["id"]?.asText()?.takeIf { it.isNotBlank() }
            ?: error("Razorpay did not return an order id")

        paymentOrders.save(
            PaymentOrderEntity(
                clientRequestId = request.clientRequestId.trim(),
                userId = userId,
                razorpayOrderId = orderId,
                amount = normalizedAmount,
                currency = "INR",
                status = "CREATED"
            )
        )

        return CreatePaymentOrderResponse(provider = providerName, orderId = orderId, amount = normalizedAmount, currency = "INR", keyId = keyId)
    }

    @Transactional
    override fun verifyWalletPayment(userId: Long, request: VerifyPaymentRequest): VerifyPaymentResponse {
        require(isConfigured()) {
            "Razorpay test keys are not configured on the backend"
        }
        val order = paymentOrders.findByRazorpayOrderIdAndUserId(request.orderId.orEmpty(), userId)
            .orElseThrow { IllegalArgumentException("Payment order not found") }
        require(order.providerName.equals(providerName, true)) { "Payment order belongs to another gateway" }
        
        if (order.status == "CAPTURED") {
            return VerifyPaymentResponse("CAPTURED", walletService.getBalance(userId))
        }

        val expectedSignature = hmacSha256(
            "${order.razorpayOrderId}|${request.paymentId.orEmpty()}",
            keySecret
        )
        require(MessageDigest.isEqual(expectedSignature.toByteArray(StandardCharsets.UTF_8), request.signature.orEmpty().toByteArray(StandardCharsets.UTF_8))) {
            order.status = "SIGNATURE_FAILED"
            paymentOrders.save(order)
            throw IllegalArgumentException("Invalid Razorpay payment signature")
        }

        val paymentJson = razorpayRequest("GET", "/payments/${request.razorpayPaymentId}", null).body
        val paymentOrderId = paymentJson["order_id"]?.asText()
        require(paymentOrderId == order.razorpayOrderId) { "Razorpay payment does not belong to this order" }

        val paymentAmount = paymentJson["amount"]?.asLong()
            ?: throw IllegalArgumentException("Razorpay payment amount missing")
        val expectedAmount = order.amount.movePointRight(2).longValueExact()
        require(paymentAmount == expectedAmount) { "Razorpay payment amount mismatch" }

        val status = paymentJson["status"]?.asText().orEmpty()
        require(status == "captured") { "Payment is not captured yet" }

        val balance = walletService.credit(
            userId = userId,
            amount = order.amount,
            externalRef = "RAZORPAY:${request.razorpayPaymentId}",
            referenceType = "ADD_MONEY",
            referenceId = request.razorpayPaymentId,
            description = "Wallet top-up via Razorpay"
        )

        order.status = "CAPTURED"
        order.razorpayPaymentId = request.razorpayPaymentId
        order.razorpaySignature = request.razorpaySignature
        order.verifiedAt = Instant.now()
        paymentOrders.save(order)

        return VerifyPaymentResponse("CAPTURED", balance)
    }

    private fun razorpayRequest(method: String, path: String, body: String?): RazorpayHttpResponse {
        require(keyId.isNotBlank() && keySecret.isNotBlank()) {
            "Razorpay test keys are not configured on the backend"
        }

        val credentials = Base64.getEncoder().encodeToString("$keyId:$keySecret".toByteArray(StandardCharsets.UTF_8))
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.trimEnd('/') + path))
            .header("Authorization", "Basic $credentials")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

        if (body != null) builder.method(method, HttpRequest.BodyPublishers.ofString(body))
        else builder.method(method, HttpRequest.BodyPublishers.noBody())

        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val parsed = runCatching { objectMapper.readTree(response.body()) }.getOrNull() ?: objectMapper.createObjectNode()
        if (response.statusCode() !in 200..299) {
            val message = parsed["error"]?.get("description")?.asText()
                ?: parsed["message"]?.asText()
                ?: "Razorpay request failed (${response.statusCode()})"
            throw IllegalArgumentException(message)
        }
        return RazorpayHttpResponse(response.statusCode(), parsed)
    }

    private fun hmacSha256(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class RazorpayHttpResponse(val statusCode: Int, val body: JsonNode)
}
