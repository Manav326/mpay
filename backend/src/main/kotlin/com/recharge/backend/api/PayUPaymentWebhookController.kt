package com.recharge.backend.api

import com.recharge.backend.provider.payu.PayUPaymentGatewayProvider
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/webhooks/payu")
class PayUPaymentWebhookController(
    private val payuPaymentGateway: PayUPaymentGatewayProvider
) {
    @PostMapping(
        value = ["/payment"],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]
    )
    fun paymentForm(@RequestParam parameters: MultiValueMap<String, String>): ResponseEntity<Map<String, String>> =
        handle(parameters.toSingleValueMap())

    @PostMapping(
        value = ["/payment"],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun paymentJson(@RequestBody parameters: Map<String, Any?>): ResponseEntity<Map<String, String>> =
        handle(parameters.mapValues { it.value?.toString().orEmpty() })

    private fun handle(parameters: Map<String, String>): ResponseEntity<Map<String, String>> {
        val result = payuPaymentGateway.handlePaymentCallback(parameters)
        return ResponseEntity.ok(
            mapOf(
                "status" to result.status,
                "message" to (result.message ?: "PayU payment callback processed")
            )
        )
    }
}
