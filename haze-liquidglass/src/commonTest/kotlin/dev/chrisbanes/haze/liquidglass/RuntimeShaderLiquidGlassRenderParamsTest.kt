// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

class RuntimeShaderLiquidGlassRenderParamsTest {

  @Test
  fun renderParams_useVisibleEffectBoundsInsideExpandedLayer() {
    val effect = LiquidGlassVisualEffect().apply {
      shape = RoundedCornerShape(999.dp)
      edgeSoftness = 4.dp
      blurRadius = 8.dp
      refractionHeight = 0.25f
      refractionScale = 0.85f
    }
    val params = effect.buildLiquidGlassRenderParams(
      context = FakeRenderParamsContext(
        size = Size(width = 200f, height = 40f),
        layerSize = Size(width = 260f, height = 100f),
        layerOffset = Offset(x = 30f, y = 20f),
      ),
      scaleFactor = 0.5f,
      layerSize = Size(width = 130f, height = 50f),
    )

    assertThat(params.layerSize).isEqualTo(Size(width = 130f, height = 50f))
    assertThat(params.effectOffset).isEqualTo(Offset(x = 15f, y = 10f))
    assertThat(params.effectSize).isEqualTo(Size(width = 100f, height = 20f))
    assertThat(params.cornerRadii).isEqualTo(CornerRadii(10f, 10f, 10f, 10f))
    assertThat(params.edgeSoftnessPx).isEqualTo(2f)
    assertThat(params.blurRadiusPx).isEqualTo(4f)
    assertThat(params.refractionHeightPx).isEqualTo(5f)
    assertThat(params.refractionScale).isEqualTo(0.85f)
    assertThat(params.lightPosition).isEqualTo(Offset(x = 65f, y = 20f))
  }

  @Test
  fun renderParams_leaveTheDimensionlessRefractionScaleAloneAtEveryInputScale() {
    val effect = LiquidGlassVisualEffect().apply {
      refractionHeight = 0.25f
      refractionScale = 0.85f
    }
    val context = FakeRenderParamsContext(
      size = Size(width = 200f, height = 40f),
      layerSize = Size(width = 260f, height = 100f),
      layerOffset = Offset(x = 30f, y = 20f),
    )

    // Every pixel quantity shrinks with the input scale, including the refraction zone. The scale
    // is a multiplier over that zone, so scaling it as well thins the glass in proportion to the
    // downsample, and the same surface would render differently at Auto and at None.
    val full = effect.buildLiquidGlassRenderParams(context, scaleFactor = 1f, layerSize = Size(260f, 100f))
    val downscaled = effect.buildLiquidGlassRenderParams(context, scaleFactor = 0.5f, layerSize = Size(130f, 50f))

    assertThat(downscaled.refractionScale).isEqualTo(full.refractionScale)
    assertThat(downscaled.refractionHeightPx).isEqualTo(full.refractionHeightPx / 2f)
  }

  @Test
  fun renderParams_mapExplicitLightPositionIntoScaledLayerCoordinates() {
    val effect = LiquidGlassVisualEffect().apply {
      lightPosition = Offset(x = 10f, y = 6f)
    }
    val params = effect.buildLiquidGlassRenderParams(
      context = FakeRenderParamsContext(
        size = Size(width = 200f, height = 40f),
        layerSize = Size(width = 260f, height = 100f),
        layerOffset = Offset(x = 30f, y = 20f),
      ),
      scaleFactor = 0.5f,
      layerSize = Size(width = 130f, height = 50f),
    )

    assertThat(params.lightPosition).isEqualTo(Offset(x = 20f, y = 13f))
  }
}

private data class FakeRenderParamsContext(
  override val size: Size,
  override val layerSize: Size,
  override val layerOffset: Offset,
  private val density: Density = Density(1f),
  private val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun positionOf(area: HazeArea): Offset = area.coordinates.localPosition

  override fun boundsOf(area: HazeArea): Rect? {
    val position = area.coordinates.localPosition
    return if (position.isSpecified && area.size.isSpecified) Rect(position, area.size) else null
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in render params tests")
  override fun requireDensity(): Density = density

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    return when (local) {
      LocalLayoutDirection -> layoutDirection as T
      else -> error("Unexpected composition local: $local")
    }
  }

  override fun requireGraphicsContext(): GraphicsContext = error("Unused in render params tests")
  override fun invalidateDraw() = Unit
}
