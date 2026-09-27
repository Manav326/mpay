package com.recharge.backend.api

import com.recharge.backend.provider.payu.PayUPaymentGatewayProvider
import com.recharge.backend.repository.RechargeTransactionRepository
import com.recharge.backend.service.AuthService
import com.recharge.backend.service.PaymentGatewayService
import com.recharge.backend.service.RechargeHistoryService
import com.recharge.backend.service.RechargeService
import com.recharge.backend.service.WalletService
import com.recharge.backend.service.WithdrawalService
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ClientControllerWithdrawalHttpTest {

    private val wallet = Mockito.mock(WalletService::class.java)
    private val recharge = Mockito.mock(RechargeService::class.java)
    private val rechargeHistory = Mockito.mock(RechargeHistoryService::class.java)
    private val authService = Mockito.mock(AuthService::class.java)
    private val paymentGatewayService = Mockito.mock(PaymentGatewayService::class.java)
    private val payuPaymentGateway = Mockito.mock(PayUPaymentGatewayProvider::class.java)
    private val rechargeRepository = Mockito.mock(RechargeTransactionRepository::class.java)
    private val withdrawalService = Mockito.mock(WithdrawalService::class.java)

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
    fun completeWithdrawalJsonReachesServiceThroughMvc() {
        val expected = WithdrawMoneyResponse(
            withdrawalId = "WDR-TEST",
            status = "SUCCESS",
            provider = "mock",
            amount = BigDecimal("1.00"),
            upiId = "manav@ybl",
            balance = BigDecimal("99.00"),
            availableBalance = BigDecimal("99.00"),
            message = "ok"
        )

        Mockito.doReturn(expected).`when`(withdrawalService).withdraw(
            userId = 42L,
            amount = BigDecimal("1.00"),
            providerName = "mock",
            clientRequestId = "mvc-test-123",
            upiId = "manav@ybl"
        )

        mockMvc.perform(
            post("/api/v1/wallet/withdraw")
                .principal(UsernamePasswordAuthenticationToken("42", null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1.00,\"provider\":\"mock\",\"clientRequestId\":\"mvc-test-123\",\"upiId\":\"manav@ybl\"}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.withdrawalId").value("WDR-TEST"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.provider").value("mock"))

        Mockito.verify(withdrawalService).withdraw(
            42L,
            BigDecimal("1.00"),
            "mock",
            "mvc-test-123",
            "manav@ybl"
        )
    }
}
