package com.recharge.client.features.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.recharge.client.core.theme.AppColors

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "auth-background")
    val orbShift by transition.animateFloat(
        initialValue = -18f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-shift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(0.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.PrimaryDark,
                        AppColors.Primary.copy(alpha = 0.94f),
                        AppColors.Background
                    ),
                    startY = 0f,
                    endY = 900f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = orbShift
                    translationY = orbShift * 0.35f
                }
        ) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.minDimension * 0.28f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.08f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.minDimension * 0.20f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.18f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(30.dp),
                        clip = false
                    ),
                shape = RoundedCornerShape(30.dp),
                color = AppColors.Surface.copy(alpha = 0.985f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun AuthFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppColors.SurfaceWarm.copy(alpha = 0.38f),
    unfocusedContainerColor = AppColors.Background.copy(alpha = 0.52f),
    disabledContainerColor = AppColors.Background.copy(alpha = 0.34f),
    errorContainerColor = AppColors.Error.copy(alpha = 0.04f),
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = Color(0xFFE5E0D7),
    focusedLabelColor = AppColors.PrimaryDark,
    cursorColor = AppColors.PrimaryDark
)

@Composable
fun AuthSectionTitle(
    title: String,
    subtitle: String
) {
    androidx.compose.material3.Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = AppColors.TextPrimary
    )
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
    androidx.compose.material3.Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextSecondary
    )
}
