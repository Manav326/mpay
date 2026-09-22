package com.recharge.client.features.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        initialValue = -28f,
        targetValue = 34f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-shift"
    )

    val cardShape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111827),
                        Color(0xFF1F2937),
                        AppColors.PrimaryDark.copy(alpha = 0.96f),
                        AppColors.Background
                    ),
                    startY = 0f,
                    endY = 1150f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = orbShift
                    translationY = orbShift * 0.30f
                }
        ) {
            drawCircle(
                color = AppColors.Accent.copy(alpha = 0.20f),
                radius = size.minDimension * 0.34f,
                center = androidx.compose.ui.geometry.Offset(
                    size.width * 0.84f,
                    size.height * 0.08f
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.minDimension * 0.23f,
                center = androidx.compose.ui.geometry.Offset(
                    size.width * 0.08f,
                    size.height * 0.24f
                )
            )
            drawCircle(
                color = AppColors.Primary.copy(alpha = 0.12f),
                radius = size.minDimension * 0.20f,
                center = androidx.compose.ui.geometry.Offset(
                    size.width * 0.92f,
                    size.height * 0.68f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(450)) +
                    slideInVertically(
                        animationSpec = tween(550),
                        initialOffsetY = { it / 8 }
                    )
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 26.dp,
                                shape = cardShape,
                                clip = false
                            ),
                        shape = cardShape,
                        color = Color.White.copy(alpha = 0.965f),
                        tonalElevation = 0.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                            content = content
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppColors.SurfaceWarm.copy(alpha = 0.70f),
    unfocusedContainerColor = Color(0xFFF8F7F4),
    disabledContainerColor = Color(0xFFF3F2EF),
    errorContainerColor = AppColors.Error.copy(alpha = 0.05f),
    focusedBorderColor = AppColors.PrimaryDark,
    unfocusedBorderColor = Color(0xFFD9D5CD),
    errorBorderColor = AppColors.Error,
    focusedLabelColor = AppColors.PrimaryDark,
    unfocusedLabelColor = AppColors.TextSecondary,
    focusedLeadingIconColor = AppColors.PrimaryDark,
    unfocusedLeadingIconColor = Color(0xFF8A847B),
    focusedTrailingIconColor = AppColors.PrimaryDark,
    unfocusedTrailingIconColor = Color(0xFF8A847B),
    cursorColor = AppColors.PrimaryDark
)

@Composable
fun AuthSectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.material3.Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.TextPrimary
        )
        androidx.compose.material3.Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
    }
}
