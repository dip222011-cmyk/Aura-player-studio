package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import kotlinx.coroutines.isActive

/**
 * Module 4: Rotating Album Artwork
 * - Smooth linear rotation using frame delta ONLY while [isPlaying] is true.
 * - Pauses at the exact current angle when [isPlaying] becomes false.
 * - Resumes seamlessly from the paused angle when [isPlaying] becomes true.
 * - Transitions smoothly & resets angle on track change ([trackKey]).
 * - Battery-efficient: no active coroutine frame loop when paused.
 */
@Composable
fun RotatingAlbumArt(
    artworkUri: String?,
    trackKey: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    degreesPerSecond: Float = 26f,
    onRotationChanged: ((Float) -> Unit)? = null
) {
    val rotation = remember { Animatable(0f) }
    var lastTrackKey by remember { mutableLongStateOf(trackKey) }

    // Smoothly reset rotation on track change, then rotate continuously ONLY while playing
    LaunchedEffect(isPlaying, trackKey) {
        if (lastTrackKey != trackKey) {
            lastTrackKey = trackKey
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
            onRotationChanged?.invoke(rotation.value)
        }
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && isPlaying) {
            val now = withFrameNanos { it }
            val dtSec = ((now - lastFrameNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
            lastFrameNanos = now
            val nextAngle = (rotation.value + degreesPerSecond * dtSec) % 360f
            rotation.snapTo(nextAngle)
            onRotationChanged?.invoke(nextAngle)
        }
    }

    val cyanGlow = LuminousCyan.copy(alpha = if (isPlaying) 0.42f else 0.18f)
    val violetGlow = ElectricViolet.copy(alpha = if (isPlaying) 0.36f else 0.14f)

    Box(
        modifier = modifier
            .size(size)
            .testTag("rotating_album_art")
            .semantics {
                stateDescription = "angle=${rotation.value.toInt()},playing=$isPlaying"
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient glow & cinematic rim lighting
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cyanGlow, violetGlow, Color.Transparent),
                    center = Offset(this.size.width * 0.45f, this.size.height * 0.45f),
                    radius = radius * 1.15f
                ),
                radius = radius * 1.08f
            )
        }

        // Liquid Glass Outer Ring
        Box(
            modifier = Modifier
                .size(size * 0.88f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.04f),
                            LuminousCyan.copy(alpha = 0.20f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            LuminousCyan.copy(alpha = 0.45f),
                            ElectricViolet.copy(alpha = 0.45f)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Rotating Disc with AnimatedContent track transition
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation.value
                    }
                    .clip(CircleShape)
            ) {
                AnimatedContent(
                    targetState = Triple(trackKey, artworkUri, size),
                    transitionSpec = {
                        (fadeIn(tween(320)) + scaleIn(initialScale = 0.92f, animationSpec = tween(320)))
                            .togetherWith(
                                fadeOut(tween(220)) + scaleOut(targetScale = 1.06f, animationSpec = tween(220))
                            )
                    },
                    label = "album_art_track_transition"
                ) { (_, uri, _) ->
                    if (!uri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Playing track album artwork",
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.img_aura_cover_midnight_1795241854682),
                            error = painterResource(id = R.drawable.img_aura_cover_midnight_1795241854682),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_aura_cover_midnight_1795241854682),
                            contentDescription = "Default Aura album artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Vinyl / Liquid Glass specular groove sheen
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = this.size.minDimension / 2f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = r * 0.72f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = r * 0.48f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Liquid Glass Center Hub
            Box(
                modifier = Modifier
                    .size(size * 0.18f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = LuminousCyan,
                    modifier = Modifier.size(size * 0.09f)
                )
            }
        }
    }
}
