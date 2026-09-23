package com.recharge.backend.repository

import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.junit.jupiter.api.Assertions.assertTrue

class RentalPayoutRepositoryContractTest {

    @Test
    fun `paged payout repository methods use collection return types`() {
        RentalPayoutRepository::class.java.declaredMethods
            .filter { method ->
                method.parameterTypes.any { parameterType ->
                    Pageable::class.java.isAssignableFrom(parameterType)
                }
            }
            .forEach { method ->
                val returnType = method.returnType
                assertTrue(
                    Page::class.java.isAssignableFrom(returnType) ||
                        Slice::class.java.isAssignableFrom(returnType) ||
                        List::class.java.isAssignableFrom(returnType),
                    "Paged payout repository method ${method.name} must return Page, Slice, or List but returns ${returnType.name}"
                )
            }
    }
}
