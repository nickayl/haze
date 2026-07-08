// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class CornerRadiiTest {

  @Test
  fun toCornerRadiiPx_normalizesOversizedPillCornersToHalfShortestSide() {
    val radii = RoundedCornerShape(999.dp).toCornerRadiiPx(
      layerSize = Size(width = 200f, height = 40f),
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
    )

    assertThat(radii).isEqualTo(CornerRadii(20f, 20f, 20f, 20f))
  }

  @Test
  fun normalizedFor_scalesAllCornersToFitAdjacentSides() {
    val radii = CornerRadii(
      topLeft = 80f,
      topRight = 40f,
      bottomRight = 60f,
      bottomLeft = 20f,
    ).normalizedFor(Size(width = 100f, height = 80f))

    assertThat(radii).isEqualTo(
      CornerRadii(
        topLeft = 64f,
        topRight = 32f,
        bottomRight = 48f,
        bottomLeft = 16f,
      ),
    )
  }

  @Test
  fun normalizedFor_discardsInvalidGeometry() {
    assertThat(
      CornerRadii(10f, 10f, 10f, 10f).normalizedFor(Size(Float.NaN, 40f)),
    ).isEqualTo(CornerRadii.zero)

    assertThat(
      CornerRadii(Float.NaN, Float.POSITIVE_INFINITY, -1f, 4f)
        .normalizedFor(Size(width = 40f, height = 40f)),
    ).isEqualTo(CornerRadii(0f, 0f, 0f, 4f))
  }
}
