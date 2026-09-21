package com.recharge.backend.provider

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.ResourceAccessException
import java.time.Duration

class Way2ApiException(
    val upstreamStatusCode: Int?,
    val providerMessageCode: String? = null,
    val charged: Boolean? = null,
    val providerOrderId: String? = null,
    override val message: String
) : RuntimeException(message)

@Component
class Way2ApiProvider(
    @Value("\${app.way2api.base-url}") private val baseUrl: String,
    @Value("\${app.way2api.api-key}") private val apiKey: String,
    @Value("\${app.way2api.operator-check-path}") private val operatorPath: String,
    @Value("\${app.way2api.connect-timeout-ms:5000}") private val connectTimeoutMs: Long,
    @Value("\${app.way2api.read-timeout-ms:45000}") private val readTimeoutMs: Long,
    private val objectMapper: ObjectMapper
) : RechargeProvider {

    override val providerName: String = "way2api"

    private val http = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/'))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(connectTimeoutMs.coerceAtLeast(100)))
                setReadTimeout(Duration.ofMillis(readTimeoutMs.coerceAtLeast(100)))
            }
        )
        .build()

    override fun detectOperator(mobileNumber: String): OperatorResult {
        val request = Way2OperatorCheckRequest(mobile_number = mobileNumber)

        val response = try {
            http.post()
                .uri(operatorPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus({ status -> status.isError }) { _, upstream ->
                    val body = runCatching { upstream.body.readBytes() }
                        .getOrDefault(ByteArray(0))

                    val root = runCatching {
                        objectMapper.readValue(body, Way2OperatorCheckResponse::class.java)
                    }.getOrNull()

                    throw Way2ApiException(
                        upstreamStatusCode = upstream.statusCode.value(),
                        providerMessageCode = root?.message_code,
                        charged = root?.charged,
                        providerOrderId = root?.order_id ?: root?.data?.order_id,
                        message = root?.message?.takeIf { it.isNotBlank() }
                            ?: "Way2API operator lookup failed (${upstream.statusCode.value()})"
                    )
                }
                .toEntity(Way2OperatorCheckResponse::class.java)
        } catch (ex: Way2ApiException) {
            throw ex
        } catch (ex: ResourceAccessException) {
            throw Way2ApiException(
                upstreamStatusCode = null,
                message = "Way2API operator lookup timed out or could not be reached. The request was not retried automatically because the provider billing state may be unknown."
            )
        } catch (ex: RestClientResponseException) {
            throw Way2ApiException(
                upstreamStatusCode = ex.statusCode.value(),
                message = "Way2API operator lookup failed (${ex.statusCode.value()})"
            )
        }

        val root = response.body
            ?: throw Way2ApiException(
                upstreamStatusCode = response.statusCode.value(),
                message = "Way2API returned an empty operator lookup response"
            )

        val providerOrderId = root.order_id ?: root.data?.order_id
        val status = root.status.trim().uppercase()
        val messageCode = root.message_code?.trim()?.uppercase()
        val message = root.message?.trim()?.takeIf { it.isNotBlank() }

        if (status == "PENDING" || messageCode == "ACCEPTED" || messageCode == "PROVIDER_NO_RESPONSE") {
            return OperatorResult(
                mobileNumber = root.data?.result?.mobile_number ?: mobileNumber,
                operator = "",
                providerOperator = "",
                circle = "",
                type = null,
                providerOrderId = providerOrderId,
                status = "PENDING",
                pending = true,
                message = message ?: "Operator detection is still being processed. Please try again later.",
                messageCode = messageCode
            )
        }

        if (!root.success || status != "SUCCESS") {
            val code = messageCode
            throw Way2ApiException(
                upstreamStatusCode = root.status_code,
                providerMessageCode = code,
                charged = root.charged,
                providerOrderId = providerOrderId,
                message = buildString {
                    append("Operator detection failed")
                    if (code != null) append(" [$code]")
                    if (message != null) append(": $message")
                }
            )
        }

        val result = root.data?.result
            ?: throw Way2ApiException(
                upstreamStatusCode = root.status_code,
                providerMessageCode = messageCode,
                charged = root.charged,
                providerOrderId = providerOrderId,
                message = "Way2API returned success without operator details"
            )

        val operator = OperatorCatalog.normalize(result.operator)

        return OperatorResult(
            mobileNumber = result.mobile_number,
            operator = operator,
            providerOperator = result.operator,
            circle = result.circle,
            type = result.type,
            providerOrderId = providerOrderId,
            status = "SUCCESS",
            pending = false,
            message = message,
            messageCode = messageCode
        )
    }

    override fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan> {
        // Plan retrieval is delegated to the PlanCatalogProvider implementation.
        return emptyList()
    }

    override fun recharge(userId: Long, mobileNumber: String, planId: String): ProviderRechargeResult {
        // Final real recharge execution remains behind RechargeExecutionProvider.
        return ProviderRechargeResult("PENDING", message = "Recharge execution provider is not configured yet")
    }

    private data class Way2OperatorCheckRequest(
        val mobile_number: String
    )

    private data class Way2OperatorCheckResponse(
        val status: String,
        val status_code: Int,
        val charged: Boolean,
        val success: Boolean,
        val message: String?,
        val message_code: String?,
        val order_id: String?,
        val data: Way2OperatorData?
    )

    private data class Way2OperatorData(
        val order_id: String?,
        val result: Way2OperatorResult?
    )

    private data class Way2OperatorResult(
        val mobile_number: String,
        val operator: String,
        val circle: String,
        val type: String?
    )
}

object OperatorCatalog {
    fun normalize(providerOperator: String): String = when {
        providerOperator.equals("AIRTEL", ignoreCase = true) -> "AIRTEL"
        providerOperator.equals("JIO", ignoreCase = true) -> "JIO"
        providerOperator.equals("VI", ignoreCase = true) -> "VI"
        providerOperator.equals("VODAFONE", ignoreCase = true) -> "VI"
        providerOperator.equals("VODAFONE IDEA", ignoreCase = true) -> "VI"
        providerOperator.equals("IDEA", ignoreCase = true) -> "VI"
        providerOperator.equals("BSNL", ignoreCase = true) -> "BSNL"
        else -> providerOperator.trim().uppercase()
    }
}
