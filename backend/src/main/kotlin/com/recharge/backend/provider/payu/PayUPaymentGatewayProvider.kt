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
    private val walletService: WalletService,
    private val paymentSettlementService: com.recharge.backend.service.PaymentSettlementService
) : PaymentGatewayProvider {

    override val providerName: String = "payu"

    override fun isConfigured(): Boolean =
        properties.effectivePgKey().isNotBlank() && properties.effectivePgSalt().isNotBlank()

    @Transactional
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

        val order = orders.save(
            PaymentOrderEntity(
                clientRequestId = request.clientRequestId.trim(),
                userId = userId,
                razorpayOrderId = txnId,
                providerName = providerName,
                amount = amount,
                currency = "INR",
                status = "CREATED",
                purpose = request.purpose,
                rechargeMobileNumber = request.rechargeMobileNumber,
                rechargeOperator = request.rechargeOperator,
                rechargeCircle = request.rechargeCircle,
                rechargePlanId = request.rechargePlanId,
                rechargeRecipientName = request.rechargeRecipientName
            )
        )

        return responseFor(userId, order)
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
            return paymentSettlementService.responseForCaptured(userId, order)
        }

        val statusResponse = verifyWithPayU(txnId)
        require(statusResponse.path("status").asInt() == 1) {
            statusResponse.path("msg").asText().ifBlank { "PayU verification request failed" }
        }
        val transaction = findTransaction(statusResponse, txnId)
        val status = transaction.path("status").asText().lowercase()
        val unmappedStatus = transaction.path("unmappedstatus").asText().lowercase()
        val paymentCaptured = status == "success" || unmappedStatus == "captured"

        if (!paymentCaptured) {
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

        val settled = paymentSettlementService.settleCaptured(
            userId = userId,
            order = order,
            externalPaymentReference = paymentReference
        )

        order.status = "CAPTURED"
        order.razorpayPaymentId = paymentReference
        order.razorpaySignature = request.signature
        order.verifiedAt = Instant.now()
        orders.save(order)

        return settled
    }

    @Transactional
    fun recordClientOutcome(userId: Long, request: com.recharge.backend.api.PayUPaymentStatusRequest): VerifyPaymentResponse {
        check(isConfigured()) { "PayU Payment Gateway test key/salt are not configured" }

        val txnId = request.orderId.trim()
        require(txnId.isNotBlank()) { "PayU transaction ID is required" }

        val order = orders.findByRazorpayOrderIdAndUserId(txnId, userId)
            .orElseThrow { IllegalArgumentException("PayU payment order not found") }
        require(order.providerName.equals(providerName, true)) { "Payment order belongs to another gateway" }

        if (order.status == "CAPTURED") {
            return paymentSettlementService.responseForCaptured(userId, order)
        }

        val normalized = request.status.trim().uppercase()
        require(normalized in setOf("FAILED", "FAILURE", "PENDING", "CANCEL", "CANCELLED")) {
            "PayU client outcome must be FAILED, PENDING or CANCELLED"
        }

        order.status = when (normalized) {
            "PENDING" -> "PENDING"
            "CANCEL", "CANCELLED" -> "CANCELLED"
            else -> "FAILED"
        }
        request.paymentId?.trim()?.takeIf { it.isNotBlank() }?.let { order.razorpayPaymentId = it }
        request.signature?.trim()?.takeIf { it.isNotBlank() }?.let { order.razorpaySignature = it }
        orders.save(order)

        return VerifyPaymentResponse(
            status = order.status,
            balance = walletService.getBalance(userId),
            availableBalance = walletService.getAvailableBalance(userId),
            message = when (order.status) {
                "PENDING" -> "PayU payment is pending final confirmation."
                "CANCELLED" -> "PayU payment was cancelled."
                else -> "PayU payment failed."
            }
        )
    }

    @Transactional
    fun handlePaymentCallback(parameters: Map<String, String>): VerifyPaymentResponse {
        check(isConfigured()) { "PayU Payment Gateway test key/salt are not configured" }

        val normalized = parameters.mapKeys { it.key.trim() }.mapValues { it.value }
        val key = normalized["key"]?.trim().orEmpty()
        val txnId = normalized["txnid"]?.trim().orEmpty()
        val amount = normalized["amount"]?.trim()?.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("PayU callback amount is missing")
        val status = normalized["status"]?.trim()?.lowercase().orEmpty()

        require(key == properties.effectivePgKey()) { "Invalid PayU callback merchant key" }
        require(txnId.isNotBlank()) { "PayU callback transaction id is missing" }

        val order = orders.findByRazorpayOrderId(txnId)
            .orElseThrow { IllegalArgumentException("PayU payment order not found") }
        require(order.providerName.equals(providerName, true)) { "Payment order belongs to another gateway" }
        require(amount.setScale(2) == order.amount.setScale(2)) { "PayU callback payment amount mismatch" }

        verifyReverseHash(normalized)

        if (order.status == "CAPTURED") {
            return paymentSettlementService.responseForCaptured(order.userId, order)
        }

        val paymentReference = normalized["mihpayid"]?.trim().takeIf { !it.isNullOrBlank() } ?: txnId
        order.razorpayPaymentId = paymentReference
        order.razorpaySignature = normalized["hash"]?.trim()?.takeIf { !it.isNullOrBlank() }

        return when (status) {
            "success" -> {
                val settled = paymentSettlementService.settleCaptured(
                    userId = order.userId,
                    order = order,
                    externalPaymentReference = paymentReference
                )
                order.status = "CAPTURED"
                order.verifiedAt = Instant.now()
                orders.save(order)
                settled
            }

            "pending", "in progress", "initiated" -> {
                order.status = "PENDING"
                orders.save(order)
                VerifyPaymentResponse(
                    status = "PENDING",
                    balance = walletService.getBalance(order.userId),
                    availableBalance = walletService.getAvailableBalance(order.userId),
                    message = "PayU payment is pending final confirmation."
                )
            }

            "cancel", "cancelled" -> {
                order.status = "CANCELLED"
                orders.save(order)
                VerifyPaymentResponse(
                    status = "CANCELLED",
                    balance = walletService.getBalance(order.userId),
                    availableBalance = walletService.getAvailableBalance(order.userId),
                    message = "PayU payment was cancelled."
                )
            }

            else -> {
                order.status = "FAILED"
                orders.save(order)
                VerifyPaymentResponse(
                    status = "FAILED",
                    balance = walletService.getBalance(order.userId),
                    availableBalance = walletService.getAvailableBalance(order.userId),
                    message = normalized["error_Message"]?.takeIf { it.isNotBlank() }
                        ?: normalized["error"]?.takeIf { it.isNotBlank() }
                        ?: "PayU payment failed."
                )
            }
        }
    }

    private fun verifyReverseHash(parameters: Map<String, String>) {
        val received = parameters["hash"]?.trim().orEmpty()
        require(received.isNotBlank()) { "PayU callback hash is missing" }

        val hashString = buildString {
            parameters["additional_charges"]?.trim()?.takeIf { it.isNotBlank() }?.let {
                append(it).append("|")
            }
            append(properties.effectivePgSalt())
                .append("|").append(parameters["status"].orEmpty())
                .append("||||||")
                .append(parameters["udf5"].orEmpty()).append("|")
                .append(parameters["udf4"].orEmpty()).append("|")
                .append(parameters["udf3"].orEmpty()).append("|")
                .append(parameters["udf2"].orEmpty()).append("|")
                .append(parameters["udf1"].orEmpty()).append("|")
                .append(parameters["email"].orEmpty()).append("|")
                .append(parameters["firstname"].orEmpty()).append("|")
                .append(parameters["productinfo"].orEmpty()).append("|")
                .append(parameters["amount"].orEmpty()).append("|")
                .append(parameters["txnid"].orEmpty()).append("|")
                .append(parameters["key"].orEmpty())
        }

        val calculated = sha512(hashString)
        require(MessageDigest.isEqual(
            calculated.lowercase().toByteArray(StandardCharsets.UTF_8),
            received.lowercase().toByteArray(StandardCharsets.UTF_8)
        )) { "Invalid PayU callback hash" }
    }

    fun generateHash(hashName: String, hashString: String, postSalt: String? = null, hashType: String? = null): String {
        check(isConfigured()) { "PayU Payment Gateway test key/salt are not configured" }
        require(hashName.isNotBlank() && hashString.isNotBlank()) { "PayU hash request is incomplete" }

        val normalizedHashType = hashType?.trim()?.uppercase()
        return when {
            normalizedHashType == "V2" -> hmacSha256(hashString, properties.effectivePgSalt())
            hashName.equals("mcpLookup", ignoreCase = true) -> hmacSha1(hashString, properties.effectivePgSalt())
            !postSalt.isNullOrBlank() -> sha512(hashString + properties.effectivePgSalt() + postSalt)
            else -> sha512(hashString + properties.effectivePgSalt())
        }
    }

    private fun responseFor(userId: Long, order: PaymentOrderEntity): CreatePaymentOrderResponse {
        val user = users.findById(userId).orElseThrow()
        val phone = user.mobile
        val firstName = user.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "mPay"
        val email = user.email?.trim()?.takeIf { it.isNotBlank() } ?: "${phone}@mpay.local"

        val productInfo = if (order.purpose.equals("RECHARGE", true)) "mPay Mobile Recharge" else "mPay Wallet Top-up"

        return CreatePaymentOrderResponse(
            provider = providerName,
            orderId = order.razorpayOrderId,
            amount = order.amount,
            currency = order.currency,
            keyId = properties.effectivePgKey(),
            checkoutParams = mapOf(
                "productInfo" to productInfo,
                "firstName" to firstName,
                "email" to email,
                "phone" to phone,
                "surl" to properties.pgSuccessUrl,
                "furl" to properties.pgFailureUrl,
                "userCredential" to "${properties.effectivePgKey()}:$phone",
                "vasForMobileSdkHash" to sha512("${properties.effectivePgKey()}|vas_for_mobile_sdk|${order.amount.toPlainString()}|${properties.effectivePgSalt()}"),
                "paymentRelatedDetailsHash" to sha512("${properties.effectivePgKey()}|payment_related_details_for_mobile_sdk|${properties.effectivePgKey()}:$phone|${properties.effectivePgSalt()}"),
                "paymentHash" to paymentHash(order, firstName, email),
                "isProduction" to properties.pgProduction.toString()
            )
        )
    }

    private fun paymentHash(
        order: PaymentOrderEntity,
        firstName: String,
        email: String
    ): String {
        val productInfo = if (order.purpose.equals("RECHARGE", true)) "mPay Mobile Recharge" else "mPay Wallet Top-up"
        val data = "${properties.effectivePgKey()}|${order.razorpayOrderId}|${order.amount.toPlainString()}|$productInfo|$firstName|$email|||||||||||${properties.effectivePgSalt()}"
        return sha512(data)
    }
    private fun findTransaction(statusResponse: JsonNode, txnId: String): JsonNode {
        val details = statusResponse.path("transaction_details")
        if (details.isObject) {
            val direct = details.path(txnId)
            if (!direct.isMissingNode && direct.isObject) return direct
            val byTxnId = details.fields().asSequence()
                .map { it.value }
                .firstOrNull { node -> node.path("txnid").asText().equals(txnId, true) }
            if (byTxnId != null) return byTxnId
        }
        throw IllegalArgumentException("PayU verification response did not contain transaction $txnId")
    }

    private fun verifyWithPayU(txnId: String): JsonNode {
        val hash = sha512("${properties.effectivePgKey()}|verify_payment|$txnId|${properties.effectivePgSalt()}")
        val encoded = "key=${enc(properties.effectivePgKey())}&command=verify_payment&var1=${enc(txnId)}&hash=${enc(hash)}"
        return RestClient.builder().build()
            .post()
            .uri(properties.pgVerifyUrl + if (properties.pgVerifyUrl.contains("?")) "" else "?form=2")
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

    private fun hmacSha256(message: String, secret: String): String =
        hmac("HmacSHA256", message, secret)

    private fun hmacSha1(message: String, secret: String): String =
        hmac("HmacSHA1", message, secret)

    private fun hmac(algorithm: String, message: String, secret: String): String {
        val mac = javax.crypto.Mac.getInstance(algorithm)
        mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), algorithm))
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}

