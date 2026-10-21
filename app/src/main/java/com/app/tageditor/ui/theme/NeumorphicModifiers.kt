package com.app.tageditor.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Soft UI building blocks.
//
//  * neuRaised  - the element bulges out of the surface: a dark shadow falls to the bottom right
//                 and a soft highlight sits at the top left.
//  * neuInset   - the element is pressed into the surface (text fields, tracks, pressed buttons):
//                 shadows are drawn on the inside of its edges.
//
// Blurred shadows use Paint.setShadowLayer, which the hardware canvas supports from API 28.
// Older devices get a layered approximation, so the UI stays usable (just a little less soft).

private const val NATIVE_SHADOW_API = 28

fun Modifier.neuRaised(
    corner: Dp = 20.dp,
    elevation: Dp = 8.dp,
    body: Color = NeuSurface,
    light: Color = NeuLight,
    dark: Color = NeuDark
): Modifier = this
    .drawBehind {
        val radius = minOf(corner.toPx(), size.minDimension / 2f)
        drawRaisedShadows(radius, elevation.toPx(), body, light, dark)
    }
    .clip(RoundedCornerShape(corner))
    .background(
        Brush.linearGradient(
            colors = listOf(lerp(body, Color.White, 0.05f), lerp(body, Color.Black, 0.12f)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    )

fun Modifier.neuInset(
    corner: Dp = 16.dp,
    depth: Dp = 4.dp,
    fill: Color = NeuInset,
    light: Color = NeuLight,
    dark: Color = NeuDark
): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(fill)
    .drawBehind {
        val radius = minOf(corner.toPx(), size.minDimension / 2f)
        drawInnerShadows(radius, depth.toPx(), fill, light, dark)
    }

/**
 * Raised surface that can be pushed in. [pressProgress] (0 = resting, 1 = fully pressed) is read
 * while drawing, so animating it never recomposes: the surface shrinks a little, its outer shadow
 * collapses and the inner shadows fade in.
 */
fun Modifier.neuPressable(
    pressProgress: () -> Float,
    corner: Dp = 20.dp,
    elevation: Dp = 8.dp,
    body: Color = NeuSurface,
    light: Color = NeuLight,
    dark: Color = NeuDark
): Modifier = this
    .graphicsLayer {
        val scale = 1f - 0.035f * pressProgress().coerceIn(0f, 1f)
        scaleX = scale
        scaleY = scale
    }
    .drawBehind {
        val press = pressProgress().coerceIn(0f, 1f)
        val radius = minOf(corner.toPx(), size.minDimension / 2f)
        drawRaisedShadows(radius, elevation.toPx() * (1f - 0.7f * press), body, light, dark)
    }
    .clip(RoundedCornerShape(corner))
    .background(
        Brush.linearGradient(
            colors = listOf(lerp(body, Color.White, 0.05f), lerp(body, Color.Black, 0.12f)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    )
    .drawBehind {
        val press = pressProgress().coerceIn(0f, 1f)
        if (press > 0.01f) {
            val radius = minOf(corner.toPx(), size.minDimension / 2f)
            drawInnerShadows(radius, 4.dp.toPx(), body, light, dark, press)
        }
    }

private fun DrawScope.drawRaisedShadows(
    radius: Float,
    elevation: Float,
    body: Color,
    light: Color,
    dark: Color
) {
    val blur = elevation * 1.8f
    if (Build.VERSION.SDK_INT >= NATIVE_SHADOW_API) {
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val native = paint.asFrameworkPaint()
            native.isAntiAlias = true
            native.color = body.toArgb()
            native.setShadowLayer(blur, elevation, elevation, dark.copy(alpha = 0.92f).toArgb())
            canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
            native.setShadowLayer(blur, -elevation, -elevation, light.copy(alpha = 0.85f).toArgb())
            canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
        }
    } else {
        drawLayeredShadow(radius, blur, elevation, elevation, dark, 0.55f)
        drawLayeredShadow(radius, blur, -elevation, -elevation, light, 0.35f)
    }
}

private fun DrawScope.drawLayeredShadow(
    radius: Float,
    spread: Float,
    dx: Float,
    dy: Float,
    color: Color,
    strength: Float
) {
    val steps = 7
    val layerAlpha = strength / steps * 1.6f
    for (i in 1..steps) {
        val grow = spread * i / steps
        drawRoundRect(
            color = color.copy(alpha = layerAlpha),
            topLeft = Offset(dx - grow, dy - grow),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(radius + grow)
        )
    }
}

private fun DrawScope.drawInnerShadows(
    radius: Float,
    depth: Float,
    fill: Color,
    light: Color,
    dark: Color,
    strength: Float = 1f
) {
    val k = strength.coerceIn(0f, 1f)
    if (Build.VERSION.SDK_INT >= NATIVE_SHADOW_API) {
        // A big frame with a rounded hole: its shadow falls into the hole, i.e. onto the inside
        // of the element. The caller already clipped drawing to the element's shape.
        val frame = Path().apply {
            fillType = PathFillType.EvenOdd
            val pad = depth * 6f + 40f
            addRect(Rect(-pad, -pad, size.width + pad, size.height + pad))
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
        }
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val native = paint.asFrameworkPaint()
            native.isAntiAlias = true
            native.color = fill.toArgb()
            native.setShadowLayer(depth * 1.6f, depth, depth, dark.copy(alpha = 0.95f * k).toArgb())
            canvas.drawPath(frame, paint)
            native.setShadowLayer(depth * 1.6f, -depth, -depth, light.copy(alpha = 0.85f * k).toArgb())
            canvas.drawPath(frame, paint)
        }
    } else {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    dark.copy(alpha = 0.9f * k),
                    dark.copy(alpha = 0f),
                    light.copy(alpha = 0f),
                    light.copy(alpha = 0.6f * k)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = depth * 1.4f)
        )
    }
}
