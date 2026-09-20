package com.recharge.backend.provider

/**
 * Provider abstraction for recharge plan discovery.
 * Android never talks to an upstream provider directly.
 */
interface PlanCatalogProvider {
    fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan>
}
