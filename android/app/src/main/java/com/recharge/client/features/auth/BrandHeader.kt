package com.recharge.client.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.recharge.client.R
import com.recharge.client.core.theme.AppColors

@Composable
fun MpayBrandHeader(compact: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.mpay_logo),
            contentDescription = "mPay logo",
            modifier = Modifier.size(if (compact) 72.dp else 82.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "mPay",
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(AppColors.SurfaceWarm)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = AppColors.PrimaryDark,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Secure wallet • Recharge • Pay",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.PrimaryDark
            )
        }
    }
}
