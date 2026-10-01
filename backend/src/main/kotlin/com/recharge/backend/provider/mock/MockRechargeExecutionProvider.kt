package com.recharge.backend.provider.mock

import com.recharge.backend.provider.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MockRechargeExecutionProvider(
    @Value("\${app.recharge.mock-execution-status:SUCCESS}") private val configuredStatus: String
) : RechargeExecutionProvider {

    override val providerName: String = "MOCK"

    override fun prepareBeforeSubmission(request: ProviderRechargeRequest): ProviderRechargeRequest =
        request.copy(providerReference = request.providerReference ?: ("MOCK-" + request.transactionId + "-" + UUID.randomUUID().toString().take(8)))

    override fun recharge(request: ProviderRechargeRequest): ProviderRechargeResult {
        val status = configuredStatus.trim().uppercase()
        return when (status) {
            "SUCCESS" -> ProviderRechargeResult(
                status = "SUCCESS",
                providerReference = request.providerReference,
                message = "Mock recharge completed successfully"
            )
            "PENDING" -> ProviderRechargeResult(
                status = "PENDING",
                providerReference = request.providerReference,
                message = "Mock provider accepted recharge and marked it pending"
            )
            "FAILED" -> ProviderRechargeResult(
                status = "FAILED",
                providerReference = request.providerReference,
                message = "Mock provider rejected recharge"
            )
            else -> ProviderRechargeResult(
                status = "PENDING",
                providerReference = request.providerReference,
                message = "Mock provider status '$status' is not supported; keeping transaction pending"
            )
        }
    }
}
