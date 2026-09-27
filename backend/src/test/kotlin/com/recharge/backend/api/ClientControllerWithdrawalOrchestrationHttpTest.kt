package com.recharge.backend.api

import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.WalletWithdrawalEntity
import com.recharge.backend.provider.payu.PayUPaymentGatewayProvider
import com.recharge.backend.repository.RechargeTransactionRepository
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletWithdrawalRepository
import com.recharge.backend.service.AuthService
import com.recharge.backend.service.PaymentGatewayService
import com.recharge.backend.service.RechargeHistoryService
import com.recharge.backend.service.RechargeService
import com.recharge.backend.service.WalletService
import com.recharge.backend.service.WithdrawalPersistenceService
import com.recharge.backend.service.WithdrawalProvider
import com.recharge.backend.service.WithdrawalProviderRequest
import com.recharge.backend.service.WithdrawalProviderResult
import com.recharge.backend.service.WithdrawalService
import java.math.BigDecimal
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ClientControllerWithdrawalOrchestrationHttpTest {

    private val wallet = Mockito.mock(WalletService::class.java)
    private val recharge = Mockito.mock(RechargeService::class.java)
    private val rechargeHistory = Mockito.mock(RechargeHistoryService::class.java)
    private val authService = Mockito.mock(AuthService::class.java)
    private val paymentGatewayService = Mockito.mock(PaymentGatewayService::class.java)
    private val payuPaymentGateway = Mockito.mock(PayUPaymentGatewayProvider::class.java)
    private val rechargeRepository = Mockito.mock(RechargeTransactionRepository::class.java)

    private val withdrawals = Mockito.mock(WalletWithdrawalRepository::class.java)
    private val users = Mockito.mock(UserRepository::class.java)
    private val persistence = Mockito.mock(WithdrawalPersistenceService::class.java)
    private val mockProvider = FakeProvider("mock", configured = true)

    private val withdrawalService = WithdrawalService(
        withdrawals = withdrawals,
        users = users,
        wallet = wallet,
        providers = listOf(mockProvider),
        properties = com.recharge.backend.config.WithdrawalProperties("mock"),
        persistence = persistence
    )

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            ClientController(
                wallet = wallet,
                recharge = recharge,
                rechargeHistory = rechargeHistory,
                authService = authService,
                paymentGatewayService = paymentGatewayService,
                payuPaymentGateway = payuPaymentGateway,
                rechargeRepository = rechargeRepository,
                withdrawalService = withdrawalService
            )
        )
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun completeWithdrawalJsonTraversesMvcControllerAndWithdrawalService() {
        val user = UserEntity(
            id = 42L,
            mobile = "9876543210",
            name = "Test User",
            email = "test@example.com"
        )
        val pending = WalletWithdrawalEntity(
            withdrawalId = "WDR-HTTP",
            clientRequestId = "mvc-orchestration-123",
            userId = 42L,
            amount = BigDecimal("1.00"),
            upiId = "manav@ybl",
            providerName = "mock",
            status = "PENDING"
        )
        val success = WalletWithdrawalEntity(
            withdrawalId = "WDR-HTTP",
            clientRequestId = "mvc-orchestration-123",
            userId = 42L,
            amount = BigDecimal("1.00"),
            upiId = "manav@ybl",
            providerName = "mock",
            status = "SUCCESS"
        )

        Mockito.doReturn(Optional.of(user)).`when`(users).findById(42L)
        Mockito.doReturn(Optional.empty<WalletWithdrawalEntity>()).`when`(withdrawals)
            .findByUserIdAndClientRequestId(42L, "mvc-orchestration-123")
        Mockito.doReturn(pending).`when`(persistence).createOrGetPending(
            42L, BigDecimal("1.00"), "manav@ybl", "mock", "mvc-orchestration-123"
        )

        mockProvider.result = WithdrawalProviderResult(
            status = "SUCCESS",
            providerReference = "mock_WDR-HTTP",
            providerStatus = "PROCESSED",
            message = "Mock withdrawal completed successfully"
        )

        Mockito.doReturn(success).`when`(persistence).markSucceeded(
            "WDR-HTTP", "mock", "mock_WDR-HTTP", "PROCESSED", "Mock withdrawal completed successfully"
        )
        Mockito.doReturn(
            com.recharge.backend.service.WalletSnapshot(
                BigDecimal("99.00"), BigDecimal.ZERO, BigDecimal("99.00")
            )
        ).`when`(wallet).getWalletSnapshot(42L)

        mockMvc.perform(
            post("/api/v1/wallet/withdraw")
                .principal(UsernamePasswordAuthenticationToken("42", null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":1.00,"provider":"mock","clientRequestId":"mvc-orchestration-123","upiId":"manav@ybl"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.withdrawalId").value("WDR-HTTP"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.provider").value("mock"))

        assertEquals(1, mockProvider.initiateCalls)
        assertEquals("manav@ybl", mockProvider.lastRequest?.upiId)
    }

    private class FakeProvider(
        override val providerName: String,
        private val configured: Boolean
    ) : WithdrawalProvider {
        var result = WithdrawalProviderResult("PROCESSING")
        var initiateCalls = 0
        var lastRequest: WithdrawalProviderRequest? = null

        override fun isConfigured(): Boolean = configured

        override fun initiate(request: WithdrawalProviderRequest): WithdrawalProviderResult {
            initiateCalls++
            lastRequest = request
            return result
        }
    }
}