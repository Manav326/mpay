package com.recharge.backend.provider

interface PlanCatalogProvider {
    val providerName: String
        get() = this::class.simpleName.orEmpty().lowercase()

    fun supportsOperator(operator: String): Boolean = false

    fun getPlans(
        mobileNumber: String,
        operator: String,
        circle: String,
        providerOperator: String? = null,
        providerCircle: String? = null
    ): List<RechargePlan>
}
