package com.recharge.client.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.recharge.client.R
import com.recharge.client.core.theme.AppColors

@Composable
fun MpayBrandHeader(compact: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.mpay_logo),
            contentDescription = "mPay logo",
            modifier = Modifier.size(if (compact) 84.dp else 104.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "mPay",
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text("Recharge • Pay • Grow", color = AppColors.TextSecondary)
    }
}
