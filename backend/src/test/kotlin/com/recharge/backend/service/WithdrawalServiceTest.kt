package com.recharge.backend.service

import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.WalletWithdrawalEntity
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletWithdrawalRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.util.Optional

class WithdrawalServiceTest {
    private val withdrawals = Mockito.mock(WalletWithdrawalRepository::class.java)
    private val users = Mockito.mock(UserRepository::class.java)
    private val wallet = Mockito.mock(WalletService::class.java)
    private val persistence = Mockito.mock(WithdrawalPersistenceService::class.java)
    private val razorpay = FakeProvider("razorpay", true)
    private val payu = FakeProvider("payu", false)
    private val mock = FakeProvider("mock", true)
    private val properties = com.recharge.backend.config.WithdrawalProperties("mock,razorpay,payu")

    private val service = WithdrawalService(
        withdrawals = withdrawals,
        users = users,
        wallet = wallet,
        providers = listOf(mock, razorpay, payu),
        properties = properties,
        persistence = persistence
    )


    @Test
    fun mockProviderCanCompleteWithdrawal() {
        val pending = withdrawal("WDR-MOCK", "REQ-MOCK", "PENDING")
        val success = withdrawal("WDR-MOCK", "REQ-MOCK", "SUCCESS", "mock")
        val user = user(42L)
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(42L)
        Mockito.doReturn(Optional.empty<WalletWithdrawalEntity>()).`when`(withdrawals).findByUserIdAndClientRequestId(42L, "REQ-MOCK")
        Mockito.doReturn(pending).`when`(persistence).createOrGetPending(
            42L, BigDecimal("10.00"), "mock@upi", "mock", "REQ-MOCK"
        )
        mock.result = WithdrawalProviderResult("SUCCESS", "mock_WDR-MOCK", "completed", "PROCESSED")
        Mockito.doReturn(success).`when`(persistence).markSucceeded(
            "WDR-MOCK", "mock", "mock_WDR-MOCK", "PROCESSED", "completed"
        )
        Mockito.doReturn(WalletSnapshot(BigDecimal("1000.00"), BigDecimal("0.00"), BigDecimal("1000.00")))
            .`when`(wallet).getWalletSnapshot(42L)

        val response = service.withdraw(42L, BigDecimal("10.00"), "mock", "REQ-MOCK", "test@mockupi")

        assertEquals("SUCCESS", response.status)
        assertEquals("mock", response.provider)
        assertEquals("mock_WDR-MOCK", mock.lastReference)
        assertEquals(1, mock.initiateCalls)
    }

    @Test
    fun blankUpiIsRejectedBeforeMockProviderCall() {
        val user = user(42L)
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(42L)
        Mockito.doReturn(Optional.empty<WalletWithdrawalEntity>()).`when`(withdrawals)
            .findByUserIdAndClientRequestId(42L, "REQ-BLANK-UPI")

        assertThrows(IllegalArgumentException::class.java) {
            service.withdraw(42L, BigDecimal("10.00"), "mock", "REQ-BLANK-UPI", "   ")
        }

        assertEquals(0, mock.initiateCalls)
    }

    @Test
    fun idempotentRequestReturnsExistingWithdrawalWithoutProviderCall() {
        val existing = withdrawal("WDR-1", "REQ-1", "PROCESSING")
        val user = user(42L)
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(42L)
        Mockito.doReturn(Optional.of(existing)).`when`(withdrawals).findByUserIdAndClientRequestId(42L, "REQ-1")
        Mockito.doReturn(WalletSnapshot(BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal("1000.00")))
            .`when`(wallet).getWalletSnapshot(42L)

        val response = service.withdraw(42L, BigDecimal("100.00"), "razorpay", "REQ-1", "user@upi")

        assertEquals("PROCESSING", response.status)
        assertEquals("WDR-1", response.withdrawalId)
        assertEquals(0, razorpay.initiateCalls)
    }

    @Test
    fun configuredProviderIsUsedAndProcessingResponseIsReturned() {
        val pending = withdrawal("WDR-2", "REQ-2", "PENDING")
        val processing = withdrawal("WDR-2", "REQ-2", "PROCESSING")
        val user = user(42L)
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(42L)
        Mockito.doReturn(Optional.empty<WalletWithdrawalEntity>()).`when`(withdrawals).findByUserIdAndClientRequestId(42L, "REQ-2")
        Mockito.doReturn(pending).`when`(persistence).createOrGetPending(
            42L, BigDecimal("100.00"), "user@upi", "razorpay", "REQ-2"
        )
        razorpay.result = WithdrawalProviderResult("PROCESSING", "pout_1", "queued", "QUEUED")
        Mockito.doReturn(processing).`when`(persistence).markProcessing(
            "WDR-2", "razorpay", "pout_1", "QUEUED", "queued"
        )
        Mockito.doReturn(WalletSnapshot(BigDecimal("1000.00"), BigDecimal("100.00"), BigDecimal("900.00")))
            .`when`(wallet).getWalletSnapshot(42L)

        val response = service.withdraw(42L, BigDecimal("100.00"), "razorpay", "REQ-2", "user@upi")

        assertEquals("PROCESSING", response.status)
        assertEquals("razorpay", response.provider)
        assertEquals("pout_1", razorpay.lastReference)
        assertEquals(1, razorpay.initiateCalls)
    }

    @Test
    fun providerFailureReleasesWithdrawalAndReturnsFailure() {
        val pending = withdrawal("WDR-3", "REQ-3", "PENDING")
        val failed = withdrawal("WDR-3", "REQ-3", "FAILED").apply {
            failureReason = "UPI rejected"
        }
        val user = user(42L)
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(42L)
        Mockito.doReturn(Optional.empty<WalletWithdrawalEntity>()).`when`(withdrawals).findByUserIdAndClientRequestId(42L, "REQ-3")
        Mockito.doReturn(pending).`when`(persistence).createOrGetPending(
            42L, BigDecimal("100.00"), "user@upi", "razorpay", "REQ-3"
        )
        razorpay.result = WithdrawalProviderResult("FAILED", message = "UPI rejected", providerStatus = "FAILED")
        Mockito.doReturn(failed).`when`(persistence).markFailed(
            "WDR-3", "razorpay", "UPI rejected"
        )
        Mockito.doReturn(WalletSnapshot(BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal("1000.00")))
            .`when`(wallet).getWalletSnapshot(42L)

        val response = service.withdraw(42L, BigDecimal("100.00"), "razorpay", "REQ-3", "user@upi")

        assertEquals("FAILED", response.status)
        assertEquals("UPI rejected", response.message)
        assertNotNull(razorpay.lastRequest)
    }

    private fun user(id: Long) = UserEntity(
        id = id,
        mobile = "9876543210",
        name = "Test User",
        email = "test@example.com"
    )

    private fun withdrawal(id: String, requestId: String, status: String, provider: String = "razorpay") = WalletWithdrawalEntity(
        withdrawalId = id,
        clientRequestId = requestId,
        userId = 42L,
        amount = BigDecimal("100.00"),
        upiId = "user@upi",
        providerName = provider,
        status = status
    )

    private class FakeProvider(
        override val providerName: String,
        private val configured: Boolean
    ) : WithdrawalProvider {
        var result = WithdrawalProviderResult("PROCESSING")
        var initiateCalls = 0
        var lastReference: String? = null
        var lastRequest: WithdrawalProviderRequest? = null

        override fun isConfigured(): Boolean = configured

        override fun initiate(request: WithdrawalProviderRequest): WithdrawalProviderResult {
            initiateCalls++
            lastRequest = request
            lastReference = result.providerReference
            return result
        }
    }
}
