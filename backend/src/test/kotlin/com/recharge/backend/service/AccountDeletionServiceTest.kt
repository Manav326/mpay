package com.recharge.backend.service

import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.UserRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.crypto.password.PasswordEncoder
import java.math.BigDecimal
import java.util.Optional

class AccountDeletionServiceTest {

    private val users = Mockito.mock(UserRepository::class.java)
    private val passwordEncoder = Mockito.mock(PasswordEncoder::class.java)
    private val entityManager = Mockito.mock(EntityManager::class.java)
    private val query = Mockito.mock(Query::class.java)
    private val profileImageStorage = Mockito.mock(ProfileImageStorage::class.java)
    private val rentalImageStorage = Mockito.mock(RentalImageStorage::class.java)

    private val service = AccountDeletionService(
        users = users,
        passwordEncoder = passwordEncoder,
        entityManager = entityManager,
        profileImageStorage = profileImageStorage,
        rentalImageStorage = rentalImageStorage
    )

    @Test
    fun successfulDeletionRedactsIdentityAndDisablesAccount() {
        val user = user()
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(18L)
        Mockito.doReturn(Optional.empty<UserEntity>()).`when`(users).findByMobile(Mockito.anyString())
        Mockito.doReturn(true).`when`(passwordEncoder).matches("correct-password", "stored-hash")
        Mockito.doReturn("redacted-password-hash").`when`(passwordEncoder).encode(Mockito.anyString())

        stubNativeQueries(
            walletRows = listOf(arrayOf(BigDecimal.ZERO, BigDecimal.ZERO)),
            scalarResults = listOf(0L, 0L, 0L, 0L),
            vendorRows = emptyList()
        )

        Mockito.doReturn(user).`when`(users).save(Mockito.any(UserEntity::class.java))

        service.deleteAccount(18L, "correct-password", "DELETE")

        assertEquals("DELETED", user.role)
        assertEquals(false, user.active)
        assertTrue(user.deletedAt != null)
        assertEquals("Deleted Account", user.name)
        assertEquals(null, user.email)
        assertEquals(null, user.profileImageKey)
        assertEquals(null, user.profileImageContentType)
        assertEquals(null, user.mobileVerifiedAt)
        assertEquals("redacted-password-hash", user.passwordHash)
        assertNotEquals("8527419630", user.mobile)
        assertEquals("DELETED", user.role)

        Mockito.verify(profileImageStorage).delete("profile-18.jpg")
        Mockito.verify(entityManager).createNativeQuery(
            "delete from recharge_offer_cache where mobile_number = :mobile"
        )
        Mockito.verify(query, Mockito.atLeastOnce()).setParameter("mobile", "8527419630")
        Mockito.verify(users).save(user)
    }

    @Test
    fun nonZeroWalletBlocksDeletionWithoutMutatingUser() {
        val user = user()
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(18L)
        Mockito.doReturn(true).`when`(passwordEncoder).matches("correct-password", "stored-hash")

        stubNativeQueries(
            walletRows = listOf(arrayOf(BigDecimal("10.00"), BigDecimal.ZERO)),
            scalarResults = emptyList(),
            vendorRows = emptyList()
        )

        val ex = assertThrows(AccountDeletionBlockedException::class.java) {
            service.deleteAccount(18L, "correct-password", "DELETE")
        }

        assertEquals(
            "Please withdraw or settle your remaining wallet balance before deleting your account",
            ex.message
        )
        assertEquals("CLIENT", user.role)
        assertEquals(true, user.active)
        assertEquals("8527419630", user.mobile)
        Mockito.verify(users, Mockito.never()).save(Mockito.any(UserEntity::class.java))
        Mockito.verifyNoInteractions(profileImageStorage, rentalImageStorage)
    }

    @Test
    fun pendingRechargeBlocksDeletionWithoutMutatingUser() {
        val user = user().apply {
            profileImageKey = null
        }
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(18L)
        Mockito.doReturn(Optional.empty<UserEntity>()).`when`(users).findByMobile(Mockito.anyString())
        Mockito.doReturn(true).`when`(passwordEncoder).matches("correct-password", "stored-hash")

        stubNativeQueries(
            walletRows = listOf(arrayOf(BigDecimal.ZERO, BigDecimal.ZERO)),
            scalarResults = listOf(0L, 1L),
            vendorRows = emptyList()
        )

        val ex = assertThrows(AccountDeletionBlockedException::class.java) {
            service.deleteAccount(18L, "correct-password", "DELETE")
        }

        assertEquals(
            "Please wait until pending recharge transactions are completed before deleting your account",
            ex.message
        )
        assertEquals("CLIENT", user.role)
        assertEquals(true, user.active)
        assertEquals("8527419630", user.mobile)
        Mockito.verify(users, Mockito.never()).save(Mockito.any(UserEntity::class.java))
        Mockito.verifyNoInteractions(profileImageStorage, rentalImageStorage)
    }

    @Test
    fun deletionRequiresExplicitConfirmation() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.deleteAccount(18L, "correct-password", "DELETE-ME")
        }

        assertEquals("Type DELETE to confirm account deletion", ex.message)
        Mockito.verifyNoInteractions(users, passwordEncoder, entityManager, profileImageStorage, rentalImageStorage)
    }

    private fun user() = UserEntity(
        id = 18L,
        mobile = "8527419630",
        name = "Test-2",
        email = "abcd2@gmail.com",
        passwordHash = "stored-hash",
        role = "CLIENT",
        active = true,
        profileImageKey = "profile-18.jpg",
        profileImageContentType = "image/jpeg"
    )

    private fun stubNativeQueries(
        walletRows: List<Any>,
        scalarResults: List<Long>,
        vendorRows: List<Any>
    ) {
        Mockito.doReturn(query).`when`(entityManager).createNativeQuery(Mockito.anyString())
        Mockito.doReturn(query).`when`(query).setParameter(Mockito.anyString(), Mockito.any())

        Mockito.doReturn(walletRows, vendorRows).`when`(query).resultList
        if (scalarResults.isNotEmpty()) {
            Mockito.doReturn(
                scalarResults.first(),
                *scalarResults.drop(1).toTypedArray()
            ).`when`(query).singleResult
        }
    }
}
