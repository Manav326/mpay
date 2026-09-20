package com.recharge.backend.service

import com.recharge.backend.domain.RechargeOfferCacheEntity
import com.recharge.backend.provider.RechargePlan
import com.recharge.backend.repository.RechargeOfferCacheRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface RechargeOfferCacheStore {
    fun getFreshPlans(cacheKey: String, now: Instant): List<RechargePlan>
    fun getFreshPlan(cacheKey: String, offerId: String, now: Instant): RechargePlan?
    fun replace(cacheKey: String, offers: List<RechargePlan>, mobileNumber: String, operator: String, circle: String, fetchedAt: Instant, expiresAt: Instant)
    fun invalidate(cacheKey: String)
}

@Service
class JpaRechargeOfferCacheStore(
    private val repository: RechargeOfferCacheRepository
) : RechargeOfferCacheStore {

    override fun getFreshPlans(cacheKey: String, now: Instant): List<RechargePlan> =
        repository.findByCacheKeyAndExpiresAtAfterOrderByAmountAsc(cacheKey, now).map(::toPlan)

    override fun getFreshPlan(cacheKey: String, offerId: String, now: Instant): RechargePlan? =
        repository.findByCacheKeyAndOfferIdAndExpiresAtAfter(cacheKey, offerId, now).orElse(null)?.let(::toPlan)

    @Transactional
    override fun invalidate(cacheKey: String) {
        repository.deleteByCacheKey(cacheKey)
    }

    @Transactional
    override fun replace(
        cacheKey: String,
        offers: List<RechargePlan>,
        mobileNumber: String,
        operator: String,
        circle: String,
        fetchedAt: Instant,
        expiresAt: Instant
    ) {
        repository.deleteByCacheKey(cacheKey)
        repository.saveAll(
            offers.map { plan ->
                RechargeOfferCacheEntity(
                    cacheKey = cacheKey,
                    offerId = plan.id,
                    mobileNumber = mobileNumber,
                    operator = operator.uppercase(),
                    circle = circle,
                    amount = plan.amount.setScale(2),
                    validity = plan.validity,
                    description = plan.description,
                    providerReference = plan.providerReference,
                    providerOrderId = plan.providerOrderId,
                    providerLogDescription = plan.providerLogDescription,
                    fetchedAt = fetchedAt,
                    expiresAt = expiresAt
                )
            }
        )
    }

    private fun toPlan(row: RechargeOfferCacheEntity): RechargePlan = RechargePlan(
        id = row.offerId,
        amount = row.amount,
        validity = row.validity,
        description = row.description,
        providerReference = row.providerReference,
        providerOrderId = row.providerOrderId,
        providerLogDescription = row.providerLogDescription
    )
}
