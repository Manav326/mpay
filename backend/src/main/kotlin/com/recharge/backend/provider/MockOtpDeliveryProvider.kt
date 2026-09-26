package com.recharge.backend.provider

import com.recharge.backend.service.OtpDeliveryProvider
import com.recharge.backend.service.OtpDeliveryResult
import com.recharge.backend.service.OtpPurpose
import org.springframework.stereotype.Component

@Component
class MockOtpDeliveryProvider : OtpDeliveryProvider {
    override val providerName: String = "mock"

    override fun send(mobile: String, otp: String, purpose: OtpPurpose): OtpDeliveryResult =
        OtpDeliveryResult(
            provider = "mock",
            demoOtp = otp
        )
}
