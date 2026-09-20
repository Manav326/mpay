package com.recharge.client.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.network.ApiConfig
import com.recharge.client.core.security.TokenStore

@Composable
fun ProfileAvatar(user: CurrentUserResponse?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val token = TokenStore(context).accessToken()
    val relative = user?.profileImageUrl
    val url = when {
        relative.isNullOrBlank() -> null
        relative.startsWith("http") -> relative
        else -> ApiConfig.BASE_URL.trimEnd('/') + relative
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey("profile:$url")
                    .diskCacheKey("profile:$url")
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
                    .crossfade(true)
                    .build(),
                contentDescription = "Profile photo",
                modifier = Modifier.matchParentSize().clip(CircleShape)
            )
        } else {
            Icon(Icons.Default.Person, contentDescription = "Profile photo", modifier = Modifier.size(size * 0.45f))
        }
    }
}
