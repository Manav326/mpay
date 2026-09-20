package com.recharge.client.core.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

private val moneyFormat = DecimalFormat("#,##0.00")

fun formatMoney(value: BigDecimal): String = moneyFormat.format(value.setScale(2, RoundingMode.HALF_UP))
