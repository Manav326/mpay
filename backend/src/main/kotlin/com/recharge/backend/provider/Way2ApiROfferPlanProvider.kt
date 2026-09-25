package com.recharge.backend.provider

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal
import java.time.Duration
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
    @Value("\${app.way2api.r-offer-path:/api/v1/mobile/r-offer}") private val rOfferPath: String,
    @Value("\${app.way2api.recharge-plans-path:/api/v1/mobile/recharge-plans}") private val rechargePlansPath: String,
    @Value("\${app.way2api.connect-timeout-ms:5000}") private val connectTimeoutMs: Long,
    @Value("\${app.way2api.r-offer-read-timeout-ms:50000}") private val rOfferReadTimeoutMs: Long,
    @Value("\${app.way2api.read-timeout-ms:45000}") private val readTimeoutMs: Long,
    private val objectMapper: ObjectMapper
) : PlanCatalogProvider {

    override val providerName: String = "way2api"

    override fun supportsOperator(operator: String): Boolean =
        when (operator.trim().uppercase()) {
            "AIRTEL", "VI", "JIO", "BSNL" -> true
            else -> false
        }

    private val http = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/'))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(connectTimeoutMs.coerceAtLeast(100)))
                setReadTimeout(Duration.ofMillis(readTimeoutMs.coerceAtLeast(100)))
            }
        )
        .build()

    override fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan> =
        getPlans(mobileNumber, operator, circle, null, null)

    override fun getPlans(
        mobileNumber: String,
        operator: String,
        circle: String,
        providerOperator: String?,
        providerCircle: String?
    ): List<RechargePlan> {
        require(mobileNumber.matches(Regex("[6-9][0-9]{9}"))) {
            "Mobile number must be a valid 10 digit Indian mobile number"
        }

        val normalizedOperator = operator.trim().uppercase()
        return when (normalizedOperator) {
            "AIRTEL", "VI" -> getROffers(mobileNumber, normalizedOperator)
            "JIO", "BSNL" -> getGenericPlans(
                mobileNumber = mobileNumber,
                operator = normalizedOperator.lowercase(),
                circle = normalizeCircle(providerCircle ?: circle)
            )
            else -> throw IllegalArgumentException(
                "Way2API prepaid recharge plans currently support Airtel, Jio, VI and BSNL"
            )
        }
    }

    private fun getGenericPlans(
        mobileNumber: String,
        operator: String,
        circle: String
    ): List<RechargePlan> {
        require(circle.isNotBlank()) { "Recharge circle is required to fetch prepaid plans" }

        val request = Way2RechargePlansRequest(operator = operator, circle = circle)
        val response = try {
            http.post()
                .uri(rechargePlansPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus({ status -> status.isError }) { _, upstream ->
                    val body = runCatching { upstream.body.readBytes() }.getOrDefault(ByteArray(0))
                    val root = runCatching {
                        objectMapper.readValue(body, Way2RechargePlansResponse::class.java)
                    }.getOrNull()
                    throw Way2ApiException(
                        upstreamStatusCode = upstream.statusCode.value(),
                        providerMessageCode = root?.message_code,
                        charged = root?.charged,
                        providerOrderId = root?.order_id ?: root?.data?.order_id,
                        message = root?.message?.takeIf { it.isNotBlank() }
                            ?: "Way2API recharge plan lookup failed (" + upstream.statusCode.value() + ")"
                    )
                }
                .toEntity(Way2RechargePlansResponse::class.java)
        } catch (ex: Way2ApiException) {
            throw ex
        } catch (ex: ResourceAccessException) {
            throw Way2ApiException(
                upstreamStatusCode = null,
                message = "Way2API recharge plan lookup timed out or could not be reached. The request was not retried automatically."
            )
        } catch (ex: RestClientResponseException) {
            throw Way2ApiException(
                upstreamStatusCode = ex.statusCode.value(),
                message = "Way2API recharge plan lookup failed (" + ex.statusCode.value() + ")"
            )
        } catch (ex: Exception) {
            throw Way2ApiException(
                upstreamStatusCode = 502,
                message = "Way2API recharge plan response could not be processed: " +
                    (ex.message ?: "unexpected provider error")
            )
        }

        val root = response.body ?: throw Way2ApiException(
            upstreamStatusCode = response.statusCode.value(),
            message = "Way2API recharge plan API returned an empty response"
        )
        val rootStatus = root.status.trim().uppercase()
        val rootMessageCode = root.message_code?.trim()?.uppercase()
        val rootMessage = root.message?.trim()?.takeIf { it.isNotBlank() }

        if (response.statusCode.value() == 202 ||
            rootStatus == "PENDING" ||
            rootMessageCode == "ACCEPTED" ||
            rootMessageCode == "PROVIDER_NO_RESPONSE"
        ) {
            throw Way2ApiException(
                upstreamStatusCode = 202,
                providerMessageCode = rootMessageCode,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = rootMessage
                    ?: "Way2API accepted the recharge plan lookup but it is still processing. Please try again later; no automatic retry was performed."
            )
        }

        if (!root.success || rootStatus != "SUCCESS") {
            throw Way2ApiException(
                upstreamStatusCode = root.status_code,
                providerMessageCode = rootMessageCode,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = buildString {
                    append("Recharge plans lookup failed")
                    if (rootMessageCode != null) append(" [").append(rootMessageCode).append("]")
                    if (rootMessage != null) append(": ").append(rootMessage)
                }
            )
        }

        val result = root.data?.result ?: throw Way2ApiException(
            upstreamStatusCode = root.status_code,
            providerMessageCode = rootMessageCode,
            charged = root.charged,
            providerOrderId = root.order_id ?: root.data?.order_id,
            message = "Way2API recharge plan API returned success without plan data"
        )

        if (!result.operator.equals(operator, ignoreCase = true)) {
            throw Way2ApiException(
                upstreamStatusCode = 502,
                providerMessageCode = rootMessageCode,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = "Way2API returned a different operator than requested"
            )
        }

        return result.plans.mapIndexedNotNull { index, plan ->
            if (plan.status.isNotBlank() && !plan.status.equals("active", ignoreCase = true)) {
                return@mapIndexedNotNull null
            }
            val amount = plan.amount
                ?: throw IllegalStateException("Way2API returned an invalid plan amount at index " + index)
            if (amount.signum() <= 0) return@mapIndexedNotNull null

            val description = plan.description.trim().takeIf { it.isNotBlank() }
                ?: plan.categoryLabel.trim().takeIf { it.isNotBlank() }
                ?: plan.category.trim().takeIf { it.isNotBlank() }
                ?: "Recharge plan"
            val validity = plan.validity.trim().takeIf { it.isNotBlank() }
                ?: plan.validityDays?.let { it.toString() + " days" }

            val idSource = listOf(
                mobileNumber,
                operator.uppercase(),
                result.circle.trim().uppercase(),
                amount.setScale(2).toPlainString(),
                validity.orEmpty(),
                description.trim(),
                plan.category.trim().lowercase()
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(idSource.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(24)

            RechargePlan(
                id = "WAY2-PLAN-" + digest,
                amount = amount,
                validity = validity,
                description = description,
                providerReference = "WAY2-PLAN-" + digest,
                providerOrderId = root.order_id ?: root.data?.order_id,
                providerMetadata = mapOf(
                    "category" to plan.category,
                    "categoryLabel" to plan.categoryLabel,
                    "status" to plan.status,
                    "validityDays" to (plan.validityDays?.toString() ?: "")
                )
            )
        }.distinctBy { it.id }
    }

    private fun getROffers(mobileNumber: String, operator: String): List<RechargePlan> {
        require(mobileNumber.matches(Regex("[6-9][0-9]{9}"))) {
            "Mobile number must be a valid 10 digit Indian mobile number"
        }

        val way2Operator = operator.trim().lowercase()
        val request = Way2ROfferRequest(mobile_number = mobileNumber, operator = way2Operator)
        val response = try {
            http.post()
                .uri(rOfferPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus({ status -> status.isError }) { _, upstream ->
                    val body = runCatching { upstream.body.readBytes() }
                        .getOrDefault(ByteArray(0))
                    val root = runCatching {
                        objectMapper.readValue(body, Way2ROfferResponse::class.java)
                    }.getOrNull()
                    throw Way2ApiException(
                        upstreamStatusCode = upstream.statusCode.value(),
                        providerMessageCode = root?.message_code,
                        charged = root?.charged,
                        providerOrderId = root?.order_id ?: root?.data?.order_id,
                        message = root?.message?.takeIf { it.isNotBlank() }
                            ?: "Way2API R-Offer lookup failed (${upstream.statusCode.value()})"
                    )
                }
                .toEntity(Way2ROfferResponse::class.java)
        } catch (ex: Way2ApiException) {
            throw ex
        } catch (ex: ResourceAccessException) {
            throw Way2ApiException(
                upstreamStatusCode = null,
                message = "Way2API R-Offer lookup timed out or could not be reached. The request was not retried automatically because the provider billing state may be unknown."
            )
        } catch (ex: RestClientResponseException) {
            throw Way2ApiException(
                upstreamStatusCode = ex.statusCode.value(),
                message = "Way2API R-Offer lookup failed (" + ex.statusCode.value() + ")"
            )
        } catch (ex: Exception) {
            throw Way2ApiException(
                upstreamStatusCode = 502,
                message = "Way2API R-Offer response could not be processed: " +
                    (ex.message ?: "unexpected provider error")
            )
        }

        val root = response.body ?: throw Way2ApiException(
            upstreamStatusCode = response.statusCode.value(),
            message = "Way2API R-Offer returned an empty response"
        )
        val rootStatus = root.status.trim().uppercase()
        val rootMessageCode = root.message_code?.trim()?.uppercase()
        val rootMessage = root.message?.trim()?.takeIf { it.isNotBlank() }

        if (response.statusCode.value() == 202 ||
            rootStatus == "PENDING" ||
            rootMessageCode == "ACCEPTED" ||
            rootMessageCode == "PROVIDER_NO_RESPONSE"
        ) {
            throw Way2ApiException(
                upstreamStatusCode = 202,
                providerMessageCode = rootMessageCode,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = rootMessage
                    ?: "Way2API accepted the R-Offer lookup but it is still processing. Please try again later; no automatic retry was performed."
            )
        }
        if (!root.success || rootStatus != "SUCCESS") {
            val code = rootMessageCode
            val message = rootMessage ?: "No recharge offers are available"
            throw Way2ApiException(
                upstreamStatusCode = root.status_code,
                providerMessageCode = code,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = buildString {
                    append("Recharge offers lookup failed")
                    if (code != null) append(" [$code]")
                    append(": ")
                    append(message)
                }
            )
        }

        val result = root.data?.result
            ?: throw Way2ApiException(
                upstreamStatusCode = root.status_code,
                providerMessageCode = rootMessageCode,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = "Way2API R-Offer returned success without offer data"
            )

        if (!result.operator.equals(way2Operator, ignoreCase = true)) {
            throw Way2ApiException(
                upstreamStatusCode = 502,
                providerMessageCode = rootMessageCode,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data.order_id,
                message = "Way2API returned a different operator than requested"
            )
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

    private fun normalizeCircle(value: String): String {
        val normalized = value.trim()
            .lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        return when (normalized) {
            "bihar and jharkhand", "bihar jharkhand", "bihar" -> "bihar"
            else -> normalized.replace(' ', '-')
        }
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

    private data class Way2RechargePlansRequest(
        val operator: String,
        val circle: String
    )

    private data class Way2RechargePlansResponse(
        val status: String,
        val status_code: Int,
        val charged: Boolean,
        val success: Boolean,
        val message: String?,
        val message_code: String?,
        val order_id: String?,
        val data: Way2RechargePlansData?
    )

    private data class Way2RechargePlansData(
        val order_id: String?,
        val result: Way2RechargePlansResult?
    )

    private data class Way2RechargePlansResult(
        val operator: String,
        val circle: String,
        val plan_count: Int = 0,
        val plans: List<Way2RechargePlan> = emptyList()
    )

    private data class Way2RechargePlan(
        val category: String = "",
        val categoryLabel: String = "",
        val amount: BigDecimal?,
        val validity: String = "",
        val validityDays: Int?,
        val description: String = "",
        val status: String = ""
    )
}
