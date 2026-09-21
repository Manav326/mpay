package com.recharge.backend.service

import com.recharge.backend.provider.PlanCatalogProvider
import com.recharge.backend.provider.RechargePlan
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RechargeOfferService(
    private val providers: List<PlanCatalogProvider>,
    private val cache: RechargeOfferCacheStore,
    @Value("\${app.recharge.plan-providers:way2api}") private val configuredProviders: String,
    @Value("\${app.recharge.offer-cache-ttl-seconds:600}") private val ttlSeconds: Long
) {
    fun getOffers(mobileNumber: String, operator: String, circle: String, providerOperator: String? = null, providerCircle: String? = null): List<RechargePlan> {
        val cacheKey = cacheKey(mobileNumber, operator, circle)
        val now = Instant.now()
        val cached = cache.getFreshPlans(cacheKey, now)
        if (cached.isNotEmpty()) return cached

        val provider = resolveProvider(operator)
        val fetched = provider.getPlans(mobileNumber, operator, circle, providerOperator, providerCircle)
        val filtered = fetched.filter { it.amount.signum() > 0 }
        if (filtered.isEmpty()) return emptyList()

        val expiresAt = now.plusSeconds(ttlSeconds.coerceAtLeast(30))
        cache.replace(
            cacheKey = cacheKey,
            offers = filtered,
            mobileNumber = mobileNumber,
            operator = operator,
            circle = circle,
            fetchedAt = now,
            expiresAt = expiresAt
        )
        return filtered
    }

    fun resolveCachedOffer(mobileNumber: String, operator: String, circle: String, offerId: String): RechargePlan? =
        cache.getFreshPlan(
            cacheKey(mobileNumber, operator, circle),
            offerId,
            Instant.now()
        )

    fun invalidate(mobileNumber: String, operator: String, circle: String) {
        cache.invalidate(cacheKey(mobileNumber, operator, circle))
    }

    private fun resolveProvider(operator: String): PlanCatalogProvider {
        val configured = configuredProviders.split(",").map { it.trim() }.filter { it.isNotBlank() }
        configured.forEach { name ->
            providers.firstOrNull {
                it.providerName.equals(name, ignoreCase = true) && it.supportsOperator(operator)
            }?.let { return it }
        }
        providers.firstOrNull { it.isConfigured() && it.supportsOperator(operator) }?.let { return it }

        throw IllegalArgumentException(
            "No configured recharge plan provider supports ${operator.trim().uppercase()} prepaid numbers yet"
        )
    }
    private fun cacheKey(mobileNumber: String, operator: String, circle: String): String =
        listOf(mobileNumber.trim(), operator.trim().uppercase(), circle.trim().uppercase()).joinToString("|")
}
