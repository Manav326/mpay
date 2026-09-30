package com.recharge.backend.repository

import com.recharge.backend.domain.AppSettingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AppSettingRepository : JpaRepository<AppSettingEntity, String>
