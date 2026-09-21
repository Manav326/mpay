package com.recharge.backend.provider

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.security.MessageDigest

@Service
@ConditionalOnProperty(
    prefix = "app.recharge",
    name = ["plan-provider"],
    havingValue = "way2api",
    matchIfMissing = true
)
class Way2ApiROfferPlanProvider(
    @Value("\${app.way2api.base-url}") private val baseUrl: String,
    @Value("\${app.way2api.api-key}") private val apiKey: String,
    @Value("\${app.way2api.r-offer-path:/api/v1/mobile/r-offer}") private val rOfferPath: String
) : PlanCatalogProvider {

    private val http = RestClient.builder().baseUrl(baseUrl.trimEnd('/')).build()

    override fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan> {
        require(mobileNumber.matches(Regex("[6-9][0-9]{9}"))) {
            "Mobile number must be a valid 10 digit Indian mobile number"
        }

        val way2Operator = when (operator.uppercase()) {
            "AIRTEL" -> "airtel"
            "VI" -> "vi"
            else -> throw IllegalArgumentException(
                "Personalized R-Offers are currently available only for Airtel and VI prepaid numbers"
            )
        }

        val request = Way2ROfferRequest(mobile_number = mobileNumber, operator = way2Operator)
        val response = http.post()
            .uri(rOfferPath)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity(Way2ROfferResponse::class.java)

        val root = response.body ?: error("Way2API R-Offer returned an empty response")
        if (!root.success || !root.status.equals("SUCCESS", ignoreCase = true)) {
            val code = root.message_code?.takeIf { it.isNotBlank() }
            val message = root.message?.takeIf { it.isNotBlank() } ?: "No recharge offers are available"
            error(buildString {
                append("Recharge offers lookup failed")
                if (code != null) append(" [$code]")
                append(": ")
                append(message)
            })
        }

        val result = root.data?.result ?: error("Way2API R-Offer returned no offer data")
        if (!result.operator.equals(way2Operator, ignoreCase = true)) {
            error("Way2API returned a different operator than requested")
        }

        return result.offers.mapIndexed { index, offer ->
            val amount = offer.price.toBigDecimalOrNull()
                ?: throw IllegalStateException("Way2API returned an invalid offer price at index $index")

            val id = stableOfferId(
                mobileNumber = mobileNumber,
                operator = operator,
                amount = amount,
                description = offer.description,
                logDescription = offer.log_description
            )

            RechargePlan(
                id = id,
                amount = amount,
                validity = extractValidity(offer.description, offer.log_description),
                description = offer.description.ifBlank { offer.log_description },
                providerReference = id,
                providerOrderId = root.order_id ?: root.data.order_id,
                providerLogDescription = offer.log_description
            )
        }.filter { it.amount.signum() > 0 }
            .distinctBy { it.id }
    }

    private fun stableOfferId(
        mobileNumber: String,
        operator: String,
        amount: BigDecimal,
        description: String,
        logDescription: String
    ): String {
        val source = listOf(
            mobileNumber,
            operator.uppercase(),
            amount.setScale(2).toPlainString(),
            description.trim(),
            logDescription.trim()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return "WAY2-ROFFER-$digest"
    }

    private fun extractValidity(description: String, logDescription: String): String? {
        val text = "$description $logDescription"
        val days = Regex("""(?i)\b(\d+)\s*D(?:AYS?)?\b""").find(text)?.groupValues?.getOrNull(1)
        if (days != null) return "$days days"
        val months = Regex("""(?i)\b(\d+)\s*M(?:ONTHS?|)\b""").find(text)?.groupValues?.getOrNull(1)
        if (months != null) return if (months == "1") "1 month" else "$months months"
        return null
    }

    private data class Way2ROfferRequest(
        @JsonProperty("mobile_number") val mobile_number: String,
        val operator: String
    )

    private data class Way2ROfferResponse(
        val status: String,
        val status_code: Int,
        val charged: Boolean,
        val success: Boolean,
        val message: String?,
        val message_code: String?,
        val order_id: String?,
        val data: Way2ROfferData?
    )

    private data class Way2ROfferData(
        val order_id: String?,
        val result: Way2ROfferResult?
    )

    private data class Way2ROfferResult(
        val mobile_number: String,
        val operator: String,
        val offers: List<Way2ROffer> = emptyList()
    )

    private data class Way2ROffer(
        val price: String,
        val description: String = "",
        val log_description: String = ""
    )
}
