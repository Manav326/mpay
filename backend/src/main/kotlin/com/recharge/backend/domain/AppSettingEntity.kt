package com.recharge.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "app_settings")
class AppSettingEntity(
    @Id
    @Column(name = "setting_key", nullable = false, length = 120)
    var key: String = "",
    @Column(name = "boolean_value", nullable = false)
    var booleanValue: Boolean = false,
    @Column(name = "updated_by_user_id")
    var updatedByUserId: Long? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
