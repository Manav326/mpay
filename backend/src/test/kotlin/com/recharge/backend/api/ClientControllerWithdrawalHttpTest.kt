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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [ClientController::class])
@AutoConfigureMockMvc(addFilters = false)
class ClientControllerWithdrawalHttpTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean private lateinit var wallet: WalletService
    @MockBean private lateinit var recharge: RechargeService
    @MockBean private lateinit var rechargeHistory: RechargeHistoryService
    @MockBean private lateinit var authService: AuthService
    @MockBean private lateinit var paymentGatewayService: PaymentGatewayService
    @MockBean private lateinit var payuPaymentGateway: PayUPaymentGatewayProvider
    @MockBean private lateinit var rechargeRepository: RechargeTransactionRepository
    @MockBean private lateinit var withdrawalService: WithdrawalService

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

    @Test
    fun withdrawalValidationStillRejectsSubminimumAmount() {
        mockMvc.perform(
            post("/api/v1/wallet/withdraw")
                .principal(UsernamePasswordAuthenticationToken("42", null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0.50,\"provider\":\"mock\",\"clientRequestId\":\"mvc-test-invalid\",\"upiId\":\"manav@ybl\"}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("amount: must be greater than or equal to 1.00"))

        Mockito.verifyNoInteractions(withdrawalService)
    }
}
