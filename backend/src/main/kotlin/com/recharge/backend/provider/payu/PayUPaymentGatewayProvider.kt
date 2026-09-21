package com.recharge.backend.provider.payu

import com.fasterxml.jackson.databind.JsonNode
import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.api.CreatePaymentOrderResponse
import com.recharge.backend.api.VerifyPaymentRequest
import com.recharge.backend.api.VerifyPaymentResponse
import com.recharge.backend.domain.PaymentOrderEntity
import com.recharge.backend.repository.PaymentOrderRepository
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.service.PaymentGatewayProvider
import com.recharge.backend.service.WalletService
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class PayUPaymentGatewayProvider(
    private val properties: com.recharge.backend.config.PayUProperties,
    private val orders: PaymentOrderRepository,
    private val users: UserRepository,
    private val walletService: WalletService
) : PaymentGatewayProvider {

    override val providerName: String = "payu"

    override fun isConfigured(): Boolean =
        properties.pgKey.isNotBlank() && properties.pgSalt.isNotBlank()

    override fun createWalletOrder(userId: Long, request: CreatePaymentOrderRequest): CreatePaymentOrderResponse {
        check(isConfigured()) { "PayU Payment Gateway test key/salt are not configured" }
        val amount = request.amount.setScale(2)
        require(amount in BigDecimal("1.00")..BigDecimal("100000.00")) { "Payment amount must be between ₹1 and ₹100,000" }

        val existing = orders.findByClientRequestIdAndUserId(request.clientRequestId.trim(), userId)
        if (existing.isPresent) {
            val order = existing.get()
            require(order.providerName.equals(providerName, true)) { "A different payment provider already owns this request id" }
            return responseFor(userId, order)
        }

        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        val phone = user.mobile
        val firstName = user.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "mPay"
        val email = user.email?.trim()?.takeIf { it.isNotBlank() } ?: "${phone}@mpay.local"
        val txnId = "MPAY" + UUID.randomUUID().toString().replace("-", "").take(20)

        orders.save(
            PaymentOrderEntity(
                clientRequestId = request.clientRequestId.trim(),
                userId = userId,
                razorpayOrderId = txnId,
                providerName = providerName,
                amount = amount,
                currency = "INR",
                status = "CREATED"
            )
        )

        return responseFor(userId, orders.findByRazorpayOrderIdAndUserId(txnId, userId).orElseThrow())
    }

    @Transactional
    override fun verifyWalletPayment(userId: Long, request: VerifyPaymentRequest): VerifyPaymentResponse {
        check(isConfigured()) { "PayU Payment Gateway test key/salt are not configured" }
        val txnId = request.orderId?.trim().orEmpty()
        require(txnId.isNotBlank()) { "PayU transaction ID is required" }

        val order = orders.findByRazorpayOrderIdAndUserId(txnId, userId)
            .orElseThrow { IllegalArgumentException("PayU payment order not found") }
        require(order.providerName.equals(providerName, true)) { "Payment order belongs to another gateway" }

        if (order.status == "CAPTURED") {
            return VerifyPaymentResponse("CAPTURED", walletService.getBalance(userId))
        }

        val statusResponse = verifyWithPayU(txnId)
        val transaction = statusResponse.path("transaction_details").path(txnId)
        val status = transaction.path("status").asText().lowercase()

        if (status != "success") {
            if (status in setOf("pending", "initiated", "in progress")) {
                return VerifyPaymentResponse("PENDING", walletService.getBalance(userId))
            }
            order.status = "FAILED"
            orders.save(order)
            throw IllegalArgumentException("PayU payment is not successful: " + status.ifBlank { "unknown status" })
        }

        val paidAmount = transaction.path("amt").asText().toBigDecimalOrNull()
            ?: transaction.path("amount").asText().toBigDecimalOrNull()
            ?: throw IllegalArgumentException("PayU payment amount is missing")
        require(paidAmount.setScale(2) == order.amount.setScale(2)) { "PayU payment amount mismatch" }

        val paymentReference = transaction.path("mihpayid").asText().takeIf { it.isNotBlank() } ?: txnId
        val balance = walletService.credit(
            userId = userId,
            amount = order.amount,
            externalRef = "PAYU:$txnId",
            referenceType = "ADD_MONEY",
            referenceId = txnId,
            description = "Wallet top-up via PayU"
        )

        order.status = "CAPTURED"
        order.razorpayPaymentId = paymentReference
        order.razorpaySignature = request.signature
        order.verifiedAt = Instant.now()
        orders.save(order)

        return VerifyPaymentResponse("CAPTURED", balance)
    }

    fun generateHash(hashName: String, hashString: String, postSalt: String? = null, hashType: String? = null): String {
        check(isConfigured()) { "PayU Payment Gateway test key/salt are not configured" }
        require(hashName.isNotBlank() && hashString.isNotBlank()) { "PayU hash request is incomplete" }
        val data = if (!postSalt.isNullOrBlank()) hashString + properties.pgSalt + postSalt else hashString + properties.pgSalt
        return if (hashType.equals("V2", true)) sha256WithKey(hashString, properties.pgSalt) else sha512(data)
    }

    private fun responseFor(userId: Long, order: PaymentOrderEntity): CreatePaymentOrderResponse {
        val user = users.findById(userId).orElseThrow()
        val phone = user.mobile
        val firstName = user.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "mPay"
        val email = user.email?.trim()?.takeIf { it.isNotBlank() } ?: "${phone}@mpay.local"

        return CreatePaymentOrderResponse(
            provider = providerName,
            orderId = order.razorpayOrderId,
            amount = order.amount,
            currency = order.currency,
            keyId = properties.pgKey,
            checkoutParams = mapOf(
                "productInfo" to "mPay wallet",
                "firstName" to firstName,
                "email" to email,
                "phone" to phone,
                "surl" to properties.pgSuccessUrl,
                "furl" to properties.pgFailureUrl,
                "userCredential" to "${properties.pgKey}:$phone",
                "vasForMobileSdkHash" to sha512("${properties.pgKey}|vas_for_mobile_sdk|${order.amount.toPlainString()}|${properties.pgSalt}"),
                "paymentRelatedDetailsHash" to sha512("${properties.pgKey}|payment_related_details_for_mobile_sdk|${properties.pgKey}:$phone|${properties.pgSalt}")
            )
        )
    }

    private fun verifyWithPayU(txnId: String): JsonNode {
        val hash = sha512("${properties.pgKey}|verify_payment|$txnId|${properties.pgSalt}")
        val encoded = "key=${enc(properties.pgKey)}&command=verify_payment&var1=${enc(txnId)}&hash=${enc(hash)}"
        return RestClient.builder().build()
            .post()
            .uri(properties.pgVerifyUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(encoded)
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw IllegalArgumentException("PayU verify payment returned an empty response")
    }

    private fun sha512(value: String): String =
        MessageDigest.getInstance("SHA-512")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun sha256WithKey(value: String, key: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
