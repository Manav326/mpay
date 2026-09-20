package com.recharge.client.core.network

import com.recharge.client.BuildConfig

object ApiConfig {
    // Configured at build time. Local development defaults to the Windows PC LAN API.
    // Production builds can use: -PmpayApiBaseUrl=https://api.example.com/
    const val BASE_URL = BuildConfig.MPAY_API_BASE_URL
}
