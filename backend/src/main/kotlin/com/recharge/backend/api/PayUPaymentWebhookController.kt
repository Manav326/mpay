package com.recharge.backend.api

import com.recharge.backend.config.PayUProperties
import com.recharge.backend.provider.payu.PayUPaymentGatewayProvider
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/webhooks/payu")
class PayUPaymentWebhookController(
    private val payuPaymentGateway: PayUPaymentGatewayProvider,
    private val properties: PayUProperties
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

    @PostMapping("/payment/return/success", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun paymentReturnSuccessPost(@RequestParam parameters: MultiValueMap<String, String>): ResponseEntity<String> =
        browserReturn(parameters.toSingleValueMap())

    @GetMapping("/payment/return/success")
    fun paymentReturnSuccessGet(@RequestParam parameters: MultiValueMap<String, String>): ResponseEntity<String> =
        browserReturn(parameters.toSingleValueMap())

    @PostMapping("/payment/return/failure", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun paymentReturnFailurePost(@RequestParam parameters: MultiValueMap<String, String>): ResponseEntity<String> =
        browserReturn(parameters.toSingleValueMap())

    @GetMapping("/payment/return/failure")
    fun paymentReturnFailureGet(@RequestParam parameters: MultiValueMap<String, String>): ResponseEntity<String> =
        browserReturn(parameters.toSingleValueMap())

    private fun handle(parameters: Map<String, String>): ResponseEntity<Map<String, String>> {
        val result = payuPaymentGateway.handlePaymentCallback(parameters)
        return ResponseEntity.ok(
            mapOf(
                "status" to result.status,
                "message" to (result.message ?: "PayU payment callback processed")
            )
        )
    }

    private fun browserReturn(parameters: Map<String, String>): ResponseEntity<String> {
        val safeTxnId = parameters["txnid"].orEmpty()
            .replace(Regex("[^A-Za-z0-9._-]"), "")
        val result = runCatching {
            payuPaymentGateway.handlePaymentCallback(parameters)
        }

        val state = when (result.getOrNull()?.status?.uppercase()) {
            "CAPTURED", "SUCCESS" -> "SUCCESS"
            "PENDING" -> "PENDING"
            "CANCELLED", "CANCEL" -> "CANCELLED"
            "FAILED" -> "FAILED"
            else -> "ERROR"
        }
        val title = when (state) {
            "SUCCESS" -> "Payment successful"
            "PENDING" -> "Payment pending"
            "CANCELLED" -> "Payment cancelled"
            "FAILED" -> "Payment failed"
            else -> "Payment confirmation"
        }
        val message = when (state) {
            "SUCCESS" -> "Your mPay payment was received and your wallet is being updated."
            "PENDING" -> "mPay is waiting for the final payment confirmation."
            "CANCELLED" -> "The PayU payment was cancelled."
            "FAILED" -> "The PayU payment was not successful."
            else -> "mPay could not validate this payment response yet."
        }
        val symbol = when (state) {
            "SUCCESS" -> "✓"
            "PENDING" -> "…"
            "CANCELLED" -> "×"
            "FAILED" -> "!"
            else -> "•"
        }
        val callbackPayload = """{"type":"mpay-payu-result","txnId":"$safeTxnId","status":"$state"}"""
        val targetOrigin = properties.pgWebOrigin.trim().removeSuffix("/")
        val logoUrl = "$targetOrigin/mpay-logo.png"

        val html = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>mPay — $title</title>
  <style>
    :root{color-scheme:light}
    body{margin:0;min-height:100vh;display:grid;place-items:center;background:#f7f4ef;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#1f1b17}
    .card{width:min(420px,calc(100vw - 40px));box-sizing:border-box;padding:30px;border-radius:24px;background:#fff;box-shadow:0 18px 55px rgba(49,38,24,.14);text-align:center}
    .brand{display:inline-flex;align-items:center;gap:9px;font-weight:800;font-size:19px;margin-bottom:20px}
    .brand img{width:34px;height:34px;border-radius:9px;object-fit:contain}
    .dot{width:52px;height:52px;border-radius:50%;margin:0 auto 16px;display:grid;place-items:center;background:#f4efe7;font-size:25px}
    h1{font-size:22px;margin:0 0 8px}
    p{margin:0;color:#71695f;line-height:1.55}
    a{display:inline-block;margin-top:22px;padding:11px 18px;border-radius:12px;background:#f3ad16;color:#1f1b17;text-decoration:none;font-weight:750}
  </style>
</head>
<body>
  <main class="card">
    <div class="brand"><img src="$logoUrl" alt="mPay"><span>mPay</span></div>
    <div class="dot">$symbol</div>
    <h1>$title</h1>
    <p>$message</p>
    <a href="$targetOrigin/">Return to mPay</a>
  </main>
  <script>
    (function(){
      var payload = $callbackPayload;
      var targetOrigin = ${jsString(targetOrigin)};
      try {
        if (window.opener && !window.opener.closed) {
          window.opener.postMessage(payload, targetOrigin);
        }
      } catch (e) {}
      setTimeout(function(){
        try { window.close(); } catch (e) {}
      }, 250);
    })();
  </script>
</body>
</html>""".trimIndent()

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html)
    }

    private fun jsString(value: String): String =
        """ + value.replace("\\", "\\\\").replace(""", "\\"").replace("\r", "").replace("\n", "") + """
}