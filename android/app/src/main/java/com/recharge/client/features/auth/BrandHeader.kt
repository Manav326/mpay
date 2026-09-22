package com.recharge.client.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.recharge.client.R
import com.recharge.client.core.theme.AppColors

@Composable
fun MpayBrandHeader(compact: Boolean = false) {
    val logoSize = if (compact) 64.dp else 74.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (compact) 78.dp else 90.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(25.dp),
                    clip = false
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AppColors.PrimaryDark,
                            AppColors.Primary,
                            AppColors.Accent
                        )
                    ),
                    shape = RoundedCornerShape(25.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(25.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 58.dp else 68.dp)
                    .background(
                        Color.White.copy(alpha = 0.94f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.mpay_logo),
                    contentDescription = "mPay logo",
                    modifier = Modifier.size(logoSize)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "mPay",
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            color = AppColors.TextPrimary
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .background(
                    color = AppColors.SurfaceWarm.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = AppColors.PrimaryDark,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Secure • Simple • Smart",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.PrimaryDark
            )
        }
    }
}
