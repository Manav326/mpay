package com.recharge.backend.service

import com.recharge.backend.provider.PlanCatalogProvider
import com.recharge.backend.provider.RechargePlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class RechargeOfferServiceTest {

    @Test
    fun prefersConfiguredWay2ApiProviderForAirtel() {
        val way2 = FakeProvider("way2api", setOf("AIRTEL", "VI"), listOf(plan("WAY2-AIR")))
        val alternative = FakeProvider("alternative", setOf("AIRTEL"), listOf(plan("ALT-AIR")))
        val service = service(listOf(way2, alternative))

        val offers = service.getOffers("9876543210", "AIRTEL", "Bihar and Jharkhand")

        assertEquals(listOf("WAY2-AIR"), offers.map { it.id })
        assertEquals(1, way2.calls)
        assertEquals(0, alternative.calls)
    }

    @Test
    fun rejectsJioWhenNoConfiguredProviderSupportsIt() {
        val way2 = FakeProvider("way2api", setOf("AIRTEL", "VI"), listOf(plan("WAY2-AIR")))
        val service = service(listOf(way2))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.getOffers("8789559165", "JIO", "Bihar and Jharkhand")
        }

        assertEquals(
            "No configured recharge plan provider supports JIO prepaid numbers yet",
            ex.message
        )
        assertEquals(0, way2.calls)
    }

    @Test
    fun rejectsBsnlWhenNoConfiguredProviderSupportsIt() {
        val way2 = FakeProvider("way2api", setOf("AIRTEL", "VI"), listOf(plan("WAY2-AIR")))
        val service = service(listOf(way2))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.getOffers("9876543210", "BSNL", "Bihar and Jharkhand")
        }

        assertEquals(
            "No configured recharge plan provider supports BSNL prepaid numbers yet",
            ex.message
        )
        assertEquals(0, way2.calls)
    }

    @Test
    fun fallsBackToAnotherProviderForJio() {
        val way2 = FakeProvider("way2api", setOf("AIRTEL", "VI"), listOf(plan("WAY2-AIR")))
        val jioProvider = FakeProvider("jio-provider", setOf("JIO"), listOf(plan("JIO-199")))
        val service = service(listOf(way2, jioProvider))

        val offers = service.getOffers("8789559165", "JIO", "Bihar and Jharkhand")

        assertEquals(listOf("JIO-199"), offers.map { it.id })
        assertEquals(0, way2.calls)
        assertEquals(1, jioProvider.calls)
    }

    @Test
    fun returnsCachedOffersWithoutCallingProviderAgain() {
        val way2 = FakeProvider("way2api", setOf("AIRTEL", "VI"), listOf(plan("WAY2-AIR")))
        val cache = InMemoryCache()
        val service = service(listOf(way2), cache)

        val first = service.getOffers("9876543210", "AIRTEL", "Bihar and Jharkhand")
        val second = service.getOffers("9876543210", "AIRTEL", "Bihar and Jharkhand")

        assertEquals(first, second)
        assertEquals(1, way2.calls)
    }

    private fun service(
        providers: List<PlanCatalogProvider>,
        cache: RechargeOfferCacheStore = InMemoryCache()
    ) = RechargeOfferService(
        providers = providers,
        cache = cache,
        preferredProvider = "way2api",
        ttlSeconds = 600
    )

    private fun plan(id: String) = RechargePlan(
        id = id,
        amount = BigDecimal("199.00"),
        validity = "28 days",
        description = "Test offer $id"
    )

    private class FakeProvider(
        override val providerName: String,
        private val operators: Set<String>,
        private val offers: List<RechargePlan>
    ) : PlanCatalogProvider {
        var calls: Int = 0
            private set

        override fun supportsOperator(operator: String): Boolean =
            operators.contains(operator.trim().uppercase())

        override fun getPlans(
            mobileNumber: String,
            operator: String,
            circle: String
        ): List<RechargePlan> {
            calls++
            return offers
        }
    }

    private class InMemoryCache : RechargeOfferCacheStore {
        private data class Entry(
            val plans: List<RechargePlan>,
            val expiresAt: Instant
        )

        private val entries = linkedMapOf<String, Entry>()

        override fun getFreshPlans(cacheKey: String, now: Instant): List<RechargePlan> =
            entries[cacheKey]?.takeIf { it.expiresAt.isAfter(now) }?.plans.orEmpty()

        override fun getFreshPlan(
            cacheKey: String,
            offerId: String,
            now: Instant
        ): RechargePlan? =
            getFreshPlans(cacheKey, now).firstOrNull { it.id == offerId }

        override fun replace(
            cacheKey: String,
            offers: List<RechargePlan>,
            mobileNumber: String,
            operator: String,
            circle: String,
            fetchedAt: Instant,
            expiresAt: Instant
        ) {
            entries[cacheKey] = Entry(offers, expiresAt)
        }

        override fun invalidate(cacheKey: String) {
            entries.remove(cacheKey)
        }
    }
}
