package com.recharge.backend.provider

/**
 * Provider abstraction for recharge plan discovery.
 * Android never talks to an upstream provider directly.
 *
 * A provider may support one or more operators. Additional provider
 * implementations can be added without changing the Android flow.
 */
interface PlanCatalogProvider {
    val providerName: String
        get() = this::class.simpleName.orEmpty().lowercase()

    fun supportsOperator(operator: String): Boolean = false

    fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan>
}
