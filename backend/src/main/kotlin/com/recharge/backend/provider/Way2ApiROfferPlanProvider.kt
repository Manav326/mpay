package com.recharge.backend.provider

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
    @Value("\${app.way2api.recharge-plans-path:/api/v1/mobile/recharge-plans}") private val rechargePlansPath: String,
    @Value("\${app.way2api.connect-timeout-ms:5000}") private val connectTimeoutMs: Long,
    @Value("\${app.way2api.read-timeout-ms:45000}") private val readTimeoutMs: Long,
    private val objectMapper: ObjectMapper
) : PlanCatalogProvider {

    override val providerName: String = "way2api"

    override fun supportsOperator(operator: String): Boolean =
        when (operator.trim().uppercase()) {
            "AIRTEL", "JIO", "VI", "BSNL" -> true
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

    override fun getPlans(
        mobileNumber: String,
        operator: String,
        circle: String
    ): List<RechargePlan> = getPlans(
        mobileNumber = mobileNumber,
        operator = operator,
        circle = circle,
        providerOperator = null,
        providerCircle = null
    )

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

        val way2Operator = when (operator.trim().uppercase()) {
            "AIRTEL" -> "airtel"
            "JIO" -> "jio"
            "VI" -> "vi"
            "BSNL" -> "bsnl"
            else -> throw IllegalArgumentException(
                "Way2API prepaid recharge plans currently support Airtel, Jio, VI and BSNL"
            )
        }

        val way2Circle = (providerCircle ?: circle).trim()
        require(way2Circle.isNotBlank()) { "Recharge circle is required to fetch prepaid plans" }

        val request = Way2RechargePlansRequest(
            operator = way2Operator,
            circle = way2Circle
        )
        val response = try {
            http.post()
                .uri(rechargePlansPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus({ status -> status.isError }) { _, upstream ->
                    val body = runCatching { upstream.body.readBytes() }
                        .getOrDefault(ByteArray(0))
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
                message = "Way2API recharge plan lookup timed out or could not be reached. The request was not retried automatically because the provider billing state may be unknown."
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
            val code = rootMessageCode
            val message = rootMessage ?: "No recharge plans are available"
            throw Way2ApiException(
                upstreamStatusCode = root.status_code,
                providerMessageCode = code,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = buildString {
                    append("Recharge plans lookup failed")
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
                message = "Way2API recharge plan API returned success without plan data"
            )

        if (!result.operator.equals(way2Operator, ignoreCase = true)) {
            throw Way2ApiException(
                upstreamStatusCode = 502,
                providerMessageCode = rootMessageCode,
                charged = root.charged,
                providerOrderId = root.order_id ?: root.data?.order_id,
                message = "Way2API returned a different operator than requested"
            )
        }

        return result.plans.mapIndexedNotNull { index, plan ->
            if (!plan.status.equals("active", ignoreCase = true)) return@mapIndexedNotNull null
            val amount = plan.amount
                ?: throw IllegalStateException("Way2API returned an invalid plan amount at index $index")
            if (amount.signum() <= 0) return@mapIndexedNotNull null

            val description = plan.description.trim().takeIf { it.isNotBlank() }
                ?: plan.categoryLabel.trim().takeIf { it.isNotBlank() }
                ?: plan.category

            val stableId = stablePlanId(
                mobileNumber = mobileNumber,
                operator = way2Operator,
                circle = result.circle,
                amount = amount,
                validity = plan.validity,
                description = description,
                category = plan.category
            )

            RechargePlan(
                id = stableId,
                amount = amount,
                validity = plan.validity.trim().takeIf { it.isNotBlank() },
                description = description,
                providerReference = stableId,
                providerOrderId = root.order_id ?: root.data.order_id,
                providerMetadata = mapOf(
                    "category" to plan.category,
                    "categoryLabel" to plan.categoryLabel,
                    "status" to plan.status,
                    "validityDays" to (plan.validityDays?.toString() ?: "")
                )
            )
        }.distinctBy { it.id }
    }
    private fun stablePlanId(
        mobileNumber: String,
        operator: String,
        circle: String,
        amount: BigDecimal,
        validity: String,
        description: String,
        category: String
    ): String {
        val source = listOf(
            mobileNumber,
            operator.uppercase(),
            circle.trim().uppercase(),
            amount.setScale(2).toPlainString(),
            validity.trim(),
            description.trim(),
            category.trim().lowercase()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return "WAY2-PLAN-$digest"
    }

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