package com.recharge.backend.provider

interface PlanCatalogProvider {
    val providerName: String
        get() = this::class.simpleName.orEmpty().lowercase()

    fun isConfigured(): Boolean = true

    fun supportsOperator(operator: String): Boolean = false

    fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan>

    fun getPlans(
        mobileNumber: String,
        operator: String,
        circle: String,
        providerOperator: String?,
        providerCircle: String?
    ): List<RechargePlan> = getPlans(mobileNumber, operator, circle)
}
