// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal data class CornerRadii(
  val topLeft: Float,
  val topRight: Float,
  val bottomRight: Float,
  val bottomLeft: Float,
) {
  fun isZero(): Boolean = this == zero

  fun scaled(scaleFactor: Float): CornerRadii {
    return CornerRadii(
      topLeft = topLeft * scaleFactor,
      topRight = topRight * scaleFactor,
      bottomRight = bottomRight * scaleFactor,
      bottomLeft = bottomLeft * scaleFactor,
    )
  }

  fun normalizedFor(size: Size): CornerRadii {
    val width = size.width.sanitizedDimension()
    val height = size.height.sanitizedDimension()
    if (width <= 0f || height <= 0f) return zero

    val positive = CornerRadii(
      topLeft = topLeft.sanitizedRadius(),
      topRight = topRight.sanitizedRadius(),
      bottomRight = bottomRight.sanitizedRadius(),
      bottomLeft = bottomLeft.sanitizedRadius(),
    )

    val scale = minOf(
      1f,
      sideScale(width, positive.topLeft + positive.topRight),
      sideScale(width, positive.bottomLeft + positive.bottomRight),
      sideScale(height, positive.topLeft + positive.bottomLeft),
      sideScale(height, positive.topRight + positive.bottomRight),
    )

    return if (scale < 1f) positive.scaled(scale) else positive
  }

  companion object {
    val zero: CornerRadii = CornerRadii(0f, 0f, 0f, 0f)
  }
}

internal fun RoundedCornerShape.toCornerRadiiPx(
  layerSize: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): CornerRadii {
  val topStartPx = topStart.toPx(layerSize, density)
  val topEndPx = topEnd.toPx(layerSize, density)
  val bottomEndPx = bottomEnd.toPx(layerSize, density)
  val bottomStartPx = bottomStart.toPx(layerSize, density)

  val radii = if (layoutDirection == LayoutDirection.Ltr) {
    CornerRadii(
      topLeft = topStartPx,
      topRight = topEndPx,
      bottomRight = bottomEndPx,
      bottomLeft = bottomStartPx,
    )
  } else {
    CornerRadii(
      topLeft = topEndPx,
      topRight = topStartPx,
      bottomRight = bottomStartPx,
      bottomLeft = bottomEndPx,
    )
  }

  return radii.normalizedFor(layerSize)
}

internal fun CornerRadii.toRoundRect(size: Size): RoundRect = RoundRect(
  left = 0f,
  top = 0f,
  right = size.width,
  bottom = size.height,
  topLeftCornerRadius = CornerRadius(topLeft),
  topRightCornerRadius = CornerRadius(topRight),
  bottomRightCornerRadius = CornerRadius(bottomRight),
  bottomLeftCornerRadius = CornerRadius(bottomLeft),
)

internal fun CornerRadii.toPath(size: Size): Path = Path().apply { addRoundRect(toRoundRect(size)) }

private fun Float.sanitizedRadius(): Float {
  return takeIf { it.isFinite() && it > 0f } ?: 0f
}

private fun Float.sanitizedDimension(): Float {
  return takeIf { it.isFinite() && it > 0f } ?: 0f
}

private fun sideScale(limit: Float, sum: Float): Float {
  return if (sum > limit && sum > 0f) limit / sum else 1f
}
