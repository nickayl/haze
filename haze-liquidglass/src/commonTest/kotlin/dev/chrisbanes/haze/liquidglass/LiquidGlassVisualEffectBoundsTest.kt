// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The captured backdrop has to reach as far as the shader samples. Where it does not, `clampCoord`
 * pins the sample to the border pixel and replicates it, which is the smear along the long sides.
 */
@OptIn(ExperimentalHazeApi::class)
class LiquidGlassVisualEffectBoundsTest {

  @Test
  fun layerBoundsFollowTheRefractionZoneInPixels() {
    val effect = LiquidGlassVisualEffect().apply {
      edgeSoftness = 0.dp
      refractionHeight = 0.25f
      refractionScale = 0.85f
    }

    val rect = Rect(0f, 0f, 400f, 200f)
    val bounds = effect.calculateLayerBounds(rect, Density(1f))

    // The zone is a fraction of the shorter side and the glass is that zone times the scale:
    // 200 * 0.25 * 0.85. refractionScale carries no pixels of its own.
    assertEquals(42.5f, rect.left - bounds.left)
    assertEquals(42.5f, bounds.right - rect.right)
    assertEquals(42.5f, rect.top - bounds.top)
    assertEquals(42.5f, bounds.bottom - rect.bottom)
  }

  @Test
  fun layerBoundsDoNotCollapseToTheDimensionlessProduct() {
    val effect = LiquidGlassVisualEffect().apply {
      edgeSoftness = 0.dp
      refractionStrength = 0.7f
      refractionHeight = 0.25f
      refractionScale = 0.85f
    }

    // refractionStrength * refractionScale was the margin while refractionScale was a pixel amount.
    // As a dimensionless multiplier the same product is 0.595 of a pixel, which is no margin at all.
    val margin = effect.calculateLayerBounds(Rect(0f, 0f, 400f, 200f), Density(1f)).left * -1f
    assertTrue(margin > 1f, "the margin collapsed to $margin px")
  }

  @Test
  fun layerBoundsRespectTheZoneCapTheShaderApplies() {
    val effect = LiquidGlassVisualEffect().apply {
      edgeSoftness = 0.dp
      // Larger than the half-extent the shader clamps the zone to.
      refractionHeight = 0.9f
      refractionScale = 1f
    }

    // The shader bounds the zone by the shorter half-extent, so the margin must stop there too or
    // the layer grows for a thickness the glass never has.
    val bounds = effect.calculateLayerBounds(Rect(0f, 0f, 400f, 200f), Density(1f))
    assertEquals(100f, -bounds.left)
  }

  @Test
  fun layerBoundsAddEdgeSoftnessOnTopOfTheRefractionReach() {
    val effect = LiquidGlassVisualEffect().apply {
      edgeSoftness = 12.dp
      refractionHeight = 0.25f
      refractionScale = 0.85f
    }

    // Softness is a Dp, so it enters in pixels at the density in force.
    val bounds = effect.calculateLayerBounds(Rect(0f, 0f, 400f, 200f), Density(2f))
    assertEquals(24f + 42.5f, -bounds.left)
  }
}
