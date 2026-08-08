// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import kotlin.test.Test
import kotlin.test.assertTrue

class LiquidGlassShadersTest {

  @Test
  fun shader_refracts_by_snells_law_through_both_faces() {
    val shader = LiquidGlassShaders.build()

    // Displacement comes from Snell's law applied to the surface slope, not from an offset along
    // the distance field scaled by a constant. A slab has two faces, and modelling only the first
    // exaggerates the bend and never straightens the ray on its way out.
    assertThat(shader).contains("vec3 entering = refract(incident, normal, 1.0 / ior);")
    assertThat(shader).contains("vec3 leaving = refract(entering, backNormal, ior);")
    assertThat(shader).contains("vec2 slope = surfaceGradient(coord);")
    assertThat(shader).contains("vec3 normal = normalize(vec3(-slope, 1.0));")

    // Energy is split at the interface, using the exact equations: Schlick keeps its angular term
    // when the two media match and would report a bright rim for glass with an index of one.
    assertThat(shader).contains("float fresnelReflectance(vec3 normal, float ior)")
    assertThat(shader).contains("float transmittance = 1.0 - reflectance;")
    assertThat(shader).doesNotContain("r0 + (1.0 - r0) * pow(1.0 - cosTheta, fresnelExponent)")

    // Absorption follows Beer-Lambert over the path actually travelled through the slab.
    assertThat(shader).contains("float absorption = exp(-tintAlpha * 1.6 * pathLength);")

    // The old direction-only construction is gone.
    assertThat(shader).doesNotContain("vec2 centerFallbackDir = vec2(1.0, 0.0);")
    assertThat(shader).doesNotContain("float displacementMagnitude =")
  }

  @Test
  fun shader_keepsEveryWavelengthAtOrAboveTheVacuumIndex() {
    val shader = LiquidGlassShaders.build()

    // Dispersion spreads the index around the base one. Unbounded, a weak refraction or a strong
    // aberration pushes the long wavelength below one, which is a medium thinner than vacuum: the
    // ray then bends the wrong way and the fringes swap sides. The spread is capped by the headroom
    // above one, so air disperses nothing and glass disperses at most down to air.
    assertThat(shader).contains("min(base * 0.35 * clamp(chromaticAberrationStrength, 0.0, 1.0), base - 1.0)")
  }

  @Test
  fun shader_honoursTheChromaticAberrationModeItAdvertises() {
    val shader = LiquidGlassShaders.build()

    // The mode is a public property with a default. After the model rewrite its uniform was still
    // sent while nothing read it, so choosing the spectral mode changed nothing at all. Spectral
    // now refracts four more wavelengths in their own right rather than blending the primaries.
    assertThat(shader).contains("if (chromaticAberrationMode == 1 && spread > 0.0001)")
    assertThat(shader).contains("refractionOffsetAt(coord, thickness, base - spread * 0.66)")
    assertThat(shader).contains("refractionOffsetAt(coord, thickness, base + spread * 0.66)")

    // The samplers the rewrite orphaned are gone; leaving them lengthened every shader compile on
    // device for code no path could reach.
    assertThat(shader).doesNotContain("sampleChromaFull")
    assertThat(shader).doesNotContain("sampleChromaSimple")
  }

  @Test
  fun shader_boundsTheTermsThatCanRunAway() {
    val shader = LiquidGlassShaders.build()

    // Roughness derived from an exponent approaching zero, and a reflection at a grazing angle,
    // both diverge; each is clamped rather than left to produce a division blow-up on device.
    assertThat(shader).contains("clamp(sqrt(2.0 / (max(specularExponent, 2.0) + 2.0)), 0.02, 1.0)")
    assertThat(shader).contains("clamp(thickness / max(abs(mirrored.z), 0.2), 0.0, thickness * 4.0)")
    assertThat(shader).contains("if (sinT >= 1.0) return 1.0;")
  }

  @Test
  fun shader_avoidsReservedTypeNamesAsIdentifiers() {
    val shader = LiquidGlassShaders.build()

    // AGSL reserves these as types. Declaring one as a variable compiles here and fails on the
    // device at the first draw, taking the app down with it.
    for (reserved in listOf("half", "half2", "half3", "half4", "short", "sampler")) {
      val declaration = Regex("""\b(vec[234]|float|int|bool)\s+$reserved\b""")
      assertTrue(!declaration.containsMatchIn(shader), "$reserved is declared as a variable")
    }
  }

  @Test
  fun shader_contains_flat_interior_early_out() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)
    assertThat(shader).contains("if (distToEdge >= refractionZone)")
    assertThat(shader).contains("return vec4(finalColor, base.a);")
  }

  @Test
  fun shader_contains_corner_weighted_dispersion() {
    val shader = LiquidGlassShaders.build()
    // Dispersion gives each channel its own index, so fringes follow the whole perimeter. The old
    // corner weight was |x*y|, which is zero along both centre axes and could only tint the four
    // extreme corners.
    assertThat(shader).doesNotContain("cornerWeight")
    assertThat(shader).contains("refractionOffsetAt(coord, thickness, base - spread)")
    assertThat(shader).contains("refractionOffsetAt(coord, thickness, base + spread)")
  }

  @Test
  fun shader_contains_color_grading() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("uniform float contrast;")
    assertThat(shader).contains("uniform float whitePoint;")
    assertThat(shader).contains("uniform float chromaMultiplier;")
    assertThat(shader).contains("vec4 applyColorGrading(vec4 color)")
    assertThat(shader).contains("clamp((color.rgb - 0.5) * (1.0 + contrast) + 0.5, 0.0, 1.0)")
  }

  @Test
  fun shader_exposes_magic_numbers_as_uniforms() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).doesNotContain("* 12.0;")
    assertThat(shader).contains("uniform float refractionScale;")
    assertThat(shader).contains("* refractionScale")
    assertThat(shader).doesNotContain("mix(shapeNormal, contentNormal, 0.15)")
    assertThat(shader).contains("mix(shapeNormal, contentNormal, contentNormalBlend)")
    assertThat(shader).contains("uniform float specularExponent;")
    assertThat(shader).contains("uniform float fresnelExponent;")
  }

  @Test
  fun shader_uses_smootherstep_edge_mask() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)
    assertThat(shader).contains("float edgeMask(float sd)")
    assertThat(shader).contains("if (sd > 0.0) return 0.0;")
    assertThat(shader).contains("float distToEdge = max(-sd, 0.0);")
    assertThat(shader).contains("return smootherstep(e);")
    assertThat(shader).contains("float edge = edgeMask(sd);")
  }

  @Test
  fun shader_usesEffectBoundsForShapeGeometry() {
    val shader = LiquidGlassShaders.build()

    assertThat(shader).contains("uniform float2 effectOffset;")
    assertThat(shader).contains("uniform float2 effectSize;")
    assertThat(shader).contains("vec2 halfSize = effectSize * 0.5;")
    assertThat(shader).contains("vec2 effectCoord = coord - effectOffset;")
    assertThat(shader).contains("vec2 centeredCoord = effectCoord - halfSize;")
    assertThat(shader).doesNotContain("vec2 halfSize = layerSize * 0.5;")
  }

  @Test
  fun shader_usesSafeLightDirection() {
    val shader = LiquidGlassShaders.build()

    assertThat(shader).contains("safeNormalize(lightPosition - coord, vec2(0.0, -1.0))")
    assertThat(shader).doesNotContain("normalize(lightPosition - coord)")
  }

  @Test
  fun shader_uses_linear_space_saturation() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("srgbToLinear")
    assertThat(shader).contains("linearToSrgb")
    assertThat(shader).contains("vec3(0.2126, 0.7152, 0.0722)")
  }

  @Test
  fun shader_flat_interior_has_ambient_lighting() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("fresnelExponent")
    assertThat(shader).contains("vec3 normal = computeContentNormal(coord);")
  }

  @Test
  fun shader_flat_interior_skips_surface_gradient() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)
    val earlyOutSection = shader.substringAfter("if (distToEdge >= refractionZone) {")
      .substringBefore("return vec4(finalColor, base.a);")
    // The flat interior needs no slope: with no curvature there is nothing to bend.
    assertThat(earlyOutSection).doesNotContain("refractionOffset(")
  }

  @Test
  fun shader_flat_interior_mixesBlurredContentForDepth() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)
    val earlyOutSection = shader.substringAfter("if (distToEdge >= refractionZone)")
      .substringBefore("return vec4(finalColor, base.a);")

    assertThat(earlyOutSection).contains("vec4 blurred = sampleBlurredContent(coord);")
    assertThat(earlyOutSection).contains("mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0))")
  }

  @Test
  fun shader_singleInputVariantDoesNotApproximateBlurredContent() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = false)

    assertThat(shader).doesNotContain("uniform shader blurredContent;")
    assertThat(shader).doesNotContain("uniform float blurRadius;")
    assertThat(shader).doesNotContain("sampleBlurredContent")
    assertThat(shader).doesNotContain("radius * 0.5")
    assertThat(shader).doesNotContain("radius * 0.35")
    assertThat(shader).doesNotContain("vec2 farAxis")
    assertThat(shader).contains("float overlayAlpha =")
  }

  @Test
  fun shader_singleInputVariantPremultipliesOverlayColor() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = false)

    assertThat(shader).contains("vec4(overlayColor, base.a * overlayAlpha) * edgeMask(sd)")
  }

  @Test
  fun shader_overlayModeStopsAtTheShapeEdge() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = false)

    // Both overlay returns mask by the edge. Without it the glass drew past its own shape and bled
    // outwards, which reads as a halo rather than as glass.
    assertTrue(Regex("""\* edgeMask\(sd\)""").findAll(shader).count() == 2)
  }

  @Test
  fun shader_overlayInteriorAndRefractedBandShareTheSameOpacity() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = false)

    // Both branches must weigh re-emitted content by (1 - depth) * (1 - tintAlpha) and add the tint
    // at its own alpha. When they disagreed, the step between them drew a hard rectangle inset by
    // the refraction height.
    assertThat(shader).contains(
      "float contentAmount = (1.0 - clamp(depth, 0.0, 1.0)) * (1.0 - tintAlpha);",
    )
    assertThat(shader).contains("float contentAmount = (1.0 - depthAmount) * absorption * transmittance;")
    assertThat(shader).contains("float overlayAlpha = contentAmount + tintAlpha;")
    assertThat(shader).contains("float overlayAlpha = baseCoeff + refractedCoeff + tintWeight + reflectance;")
  }

  @Test
  fun shader_rimLightingFadesToNothingInTheInterior() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = false)

    // Unmasked, both terms saturate along every straight edge into a white band.
    assertThat(shader).contains("specularIntensity * heightNorm")
    assertThat(shader).contains("fresnelExponent) * heightNorm")
  }

  @Test
  fun shader_multiInputVariantSamplesBlurredContentSeparately() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)

    assertThat(shader).contains("uniform shader blurredContent;")
    assertThat(shader).contains("return blurredContent.eval(clampCoord(coord));")
    assertThat(shader).contains("vec4 blurred = sampleBlurredContent(refractCoord);")
  }

  @Test
  fun shader_multiInputVariantUsesBlurredRefractionForDepth() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)

    val refractedSection = shader
      .substringAfter("vec4 refracted = sampleChroma(refractCoord, chromaOffset);")
      .substringBefore("vec2 grad = surfaceGradient(coord);")
    val returnSection = shader
      .substringAfter("vec3 tinted = mix(graded, tintColor.rgb, tintColor.a);")
      .substringBefore("return vec4(finalColor, base.a) * edge;")

    assertThat(refractedSection).contains("vec4 blurred = sampleBlurredContent(refractCoord);")
    assertThat(refractedSection).contains(
      "vec3 refractedColor = applyColorGrading(vec4(mix(refracted.rgb, blurred.rgb, clamp(depth, 0.0, 1.0)), 1.0)).rgb;",
    )
    assertThat(returnSection).contains("mix(tinted, refractedColor, refractionStrength)")
  }

  @Test
  fun shader_refractedColorPathAppliesColorGrading() {
    val overlayShader = LiquidGlassShaders.build(hasBlurredContent = false)
    val dualInputShader = LiquidGlassShaders.build(hasBlurredContent = true)
    val singleInputShader = LiquidGlassShaders.build(
      contentMode = LiquidGlassShaders.ContentMode.SingleBlurredInput,
    )

    assertThat(overlayShader).contains(
      "vec3 refractedColor = applyColorGrading(refracted).rgb;",
    )
    assertThat(dualInputShader).contains(
      "vec3 refractedColor = applyColorGrading(vec4(mix(refracted.rgb, blurred.rgb, clamp(depth, 0.0, 1.0)), 1.0)).rgb;",
    )
    assertThat(singleInputShader).contains(
      "vec3 refractedColor = applyColorGrading(refracted).rgb;",
    )
  }

  @Test
  fun shader_singleBlurredInputVariantTreatsContentAsBlurred() {
    val shader = LiquidGlassShaders.build(
      contentMode = LiquidGlassShaders.ContentMode.SingleBlurredInput,
    )

    assertThat(shader).doesNotContain("uniform shader blurredContent;")
    assertThat(shader).contains("vec3 refractedColor = applyColorGrading(refracted).rgb;")
    assertThat(shader).contains("return vec4(finalColor, base.a);")
    assertThat(shader).contains("return vec4(finalColor, base.a) * edge;")
    assertThat(shader).doesNotContain("return vec4(overlayColor, base.a * overlayAlpha);")
    assertThat(shader).doesNotContain("return vec4(finalColor * overlayAlpha, base.a * overlayAlpha);")
  }

  @Test
  fun outputMaskShaderClipsOutsideRoundedShape() {
    val shader = LiquidGlassShaders.buildOutputMask()

    assertThat(shader).contains("uniform float2 effectOffset;")
    assertThat(shader).contains("uniform float2 effectSize;")
    assertThat(shader).contains("vec2 effectCoord = coord - effectOffset;")
    assertThat(shader).contains("if (sd > 0.0) return 0.0;")
    assertThat(shader).contains("return content.eval(coord) * shapeMask(coord);")
  }
}
