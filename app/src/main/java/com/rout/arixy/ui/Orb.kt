package com.rout.arixy.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rout.arixy.core.AssistantState
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ARIXY orb: a real-time state-driven liquid-glass orb.
 * Every visual state maps 1:1 to the live AssistantState - nothing is faked.
 */
@Composable
fun ArixyOrb(state: AssistantState, modifier: Modifier = Modifier, size: Dp = 190.dp) {
    val transition = rememberInfiniteTransition(label = "orb")

    // Slow breathing for idle; faster pulse when speaking.
    val breathDuration = when (state) {
        AssistantState.SPEAKING -> 900
        AssistantState.LISTENING -> 1400
        AssistantState.THINKING -> 1100
        AssistantState.EXECUTING -> 1600
        AssistantState.STOPPED -> 4000
        AssistantState.IDLE -> 2800
    }
    val breath by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(breathDuration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    // Rotation for the thinking arc and slow glass swirl.
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (state == AssistantState.THINKING) 1600 else 9000, easing = LinearEasing)),
        label = "spin"
    )
    // Listening rings expansion.
    val ringPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "rings"
    )

    Canvas(modifier = modifier.size(size)) {
        val c = center
        val base = this.size.minDimension / 2f * 0.62f
        val r = base * breath

        val coreColor = when (state) {
            AssistantState.IDLE -> ArixyColors.Accent.copy(alpha = 0.16f)
            AssistantState.LISTENING -> ArixyColors.AccentBlue.copy(alpha = 0.22f)
            AssistantState.THINKING -> ArixyColors.AccentViolet.copy(alpha = 0.22f)
            AssistantState.SPEAKING -> ArixyColors.Accent.copy(alpha = 0.26f)
            AssistantState.EXECUTING -> ArixyColors.AccentBlue.copy(alpha = 0.24f)
            AssistantState.STOPPED -> Color(0xFF3A3A40).copy(alpha = 0.18f)
        }
        val rimColor = when (state) {
            AssistantState.IDLE -> ArixyColors.Border
            AssistantState.LISTENING -> ArixyColors.AccentBlue.copy(alpha = 0.8f)
            AssistantState.THINKING -> ArixyColors.AccentViolet.copy(alpha = 0.85f)
            AssistantState.SPEAKING -> ArixyColors.Accent.copy(alpha = 0.9f)
            AssistantState.EXECUTING -> ArixyColors.AccentBlue.copy(alpha = 0.9f)
            AssistantState.STOPPED -> Color(0xFF55555C)
        }

        // Reactive listening rings (only while actually listening).
        if (state == AssistantState.LISTENING) {
            for (i in 0..2) {
                val phase = (ringPhase + i / 3f) % 1f
                drawCircle(
                    color = ArixyColors.AccentBlue.copy(alpha = (1f - phase) * 0.35f),
                    radius = r + phase * base * 0.7f,
                    center = c,
                    style = Stroke(width = 2f)
                )
            }
        }

        // Liquid-glass body: radial gradient core.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreColor, coreColor.copy(alpha = coreColor.alpha * 0.35f), Color.Transparent),
                center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                radius = r * 1.35f
            ),
            radius = r, center = c
        )

        // Flowing glass reflection streaks.
        val streakAngle = Math.toRadians(spin.toDouble())
        val sx = (c.x + cos(streakAngle) * r * 0.35f).toFloat()
        val sy = (c.y + sin(streakAngle) * r * 0.35f).toFloat()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = if (state == AssistantState.STOPPED) 0.05f else 0.14f), Color.Transparent),
                center = Offset(sx, sy), radius = r * 0.55f
            ),
            radius = r * 0.55f, center = Offset(sx, sy)
        )

        // Thin glass rim.
        drawCircle(color = rimColor, radius = r, center = c, style = Stroke(width = 2.5f))

        // Thinking: rotating arc. Executing: progress-style arc sweep.
        if (state == AssistantState.THINKING || state == AssistantState.EXECUTING) {
            drawArc(
                color = rimColor,
                startAngle = spin,
                sweepAngle = if (state == AssistantState.THINKING) 90f else 140f,
                useCenter = false,
                topLeft = Offset(c.x - r - 8, c.y - r - 8),
                size = androidx.compose.ui.geometry.Size((r + 8) * 2, (r + 8) * 2),
                style = Stroke(width = 3f)
            )
        }

        // Stopped: a clear pause mark inside the orb.
        if (state == AssistantState.STOPPED) {
            val bw = r * 0.12f; val bh = r * 0.5f
            drawRect(Color(0xFF8A8A92), topLeft = Offset(c.x - bw * 1.6f, c.y - bh / 2), size = androidx.compose.ui.geometry.Size(bw, bh))
            drawRect(Color(0xFF8A8A92), topLeft = Offset(c.x + bw * 0.6f, c.y - bh / 2), size = androidx.compose.ui.geometry.Size(bw, bh))
        }
    }
}
