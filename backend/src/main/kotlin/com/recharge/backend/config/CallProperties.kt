package com.recharge.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.calls")
data class CallProperties(
    var ringingTimeoutSeconds: Long = 30,
    var signalingTokenTtlSeconds: Long = 300,
    var websocketPath: String = "/ws/calls",
    var iceServers: String = "stun:stun.l.google.com:19302",
    var turnUsername: String = "",
    var turnCredential: String = ""
)
