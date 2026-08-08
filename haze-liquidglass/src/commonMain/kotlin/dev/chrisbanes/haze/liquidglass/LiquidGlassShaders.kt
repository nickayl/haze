// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

internal object LiquidGlassShaders {
  enum class ContentMode {
    OverlayWithExternalUnderlay,
    DualInput,
    SingleBlurredInput,
  }

  /**
   * Builds the liquid-glass SKSL/AGSL shader.
   *
   * @param hasBlurredContent When `true` the shader declares and samples a
   *   `blurredContent` uniform. When `false` (the default), the shader emits the
   *   compatibility overlay mode for legacy callers that composite an external
   *   blurred underlay themselves.
   */
  fun build(
    hasBlurredContent: Boolean = false,
  ): String = build(
    contentMode = if (hasBlurredContent) {
      ContentMode.DualInput
    } else {
      ContentMode.OverlayWithExternalUnderlay
    },
  )

  fun build(
    contentMode: ContentMode,
  ): String = """
    uniform shader content;
    ${if (contentMode == ContentMode.DualInput) "uniform shader blurredContent;" else ""}
    uniform float2 layerSize;
    uniform float2 effectOffset;
    uniform float2 effectSize;
    uniform float refractionStrength;
    uniform float specularIntensity;
    uniform float depth;
    uniform float ambientResponse;
    uniform float edgeSoftness;
    uniform float refractionHeight;
    uniform float chromaticAberrationStrength;
    uniform float2 lightPosition;
    uniform vec4 cornerRadii;
    uniform vec4 tintColor;
    // Declared as float because AGSL does not support int uniforms.
    uniform float surfaceProfile;
    // Declared as float because AGSL does not support int uniforms.
    uniform float chromaticAberrationMode;
    uniform float contrast;
    uniform float whitePoint;
    uniform float chromaMultiplier;
    uniform float refractionScale;
    uniform float contentNormalBlend;
    uniform float specularExponent;
    uniform float fresnelExponent;

    vec2 clampCoord(vec2 coord) {
      return clamp(coord, vec2(0.5, 0.5), layerSize - vec2(0.5, 0.5));
    }

    float circleMap(float x) {
      return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    float squircleMap(float x) {
      return pow(1.0 - pow(1.0 - x, 4.0), 0.25);
    }

    float smootherstep(float x) {
      float t = clamp(x, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    float radiusAt(vec2 coord, vec4 radii) {
      if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
      } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
      }
    }

    float sdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      float outside = length(max(cornerCoord, 0.0)) - radius;
      float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
      return outside + inside;
    }

    vec2 safeNormalize(vec2 value, vec2 fallback) {
      float len = length(value);
      return len > 0.0001 ? value / len : fallback;
    }

    vec2 axisSafeSign(vec2 value) {
      return vec2(value.x >= 0.0 ? 1.0 : -1.0, value.y >= 0.0 ? 1.0 : -1.0);
    }

    vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      vec2 coordSign = axisSafeSign(coord);
      if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return coordSign * safeNormalize(max(cornerCoord, 0.0), vec2(0.0));
      } else {
        float edgeBlend = smoothstep(-2.0, 2.0, cornerCoord.x - cornerCoord.y);
        vec2 edgeDir = safeNormalize(
          mix(vec2(0.0, 1.0), vec2(1.0, 0.0), edgeBlend),
          vec2(1.0, 0.0)
        );
        float cornerProximity = smoothstep(-radius, 0.0, cornerCoord.x) * smoothstep(-radius, 0.0, cornerCoord.y);
        vec2 arcDir = safeNormalize(-cornerCoord, vec2(0.70710678, 0.70710678));
        vec2 insideDir = mix(edgeDir, arcDir, cornerProximity);
        return coordSign * safeNormalize(insideDir, edgeDir);
      }
    }

    float evaluateProfile(float t) {
      // t runs from 0 (at the flat interior) to 1 (at the edge). Invert so x=0 at the edge
      // and x=1 at the interior, matching the original circleMap/squircleMap parameterisation.
      float x = 1.0 - clamp(t, 0.0, 1.0);
      if (surfaceProfile == 1) {
        return squircleMap(x);
      } else if (surfaceProfile == 2) {
        return -circleMap(x);
      } else if (surfaceProfile == 3) {
        float convex = circleMap(x);
        float concave = -circleMap(x);
        float blend = smootherstep(clamp(t / 0.7, 0.0, 1.0));
        return mix(convex, concave, blend);
      }
      return circleMap(x);
    }

    float surfaceHeightAt(vec2 coord, float customRadius) {
      vec2 halfSize = effectSize * 0.5;
      vec2 effectCoord = coord - effectOffset;
      vec2 centeredCoord = effectCoord - halfSize;
      float sd = sdRoundedRect(centeredCoord, halfSize, customRadius);
      float distToEdge = max(-sd, 0.0);
      // Bounded by the shorter half-extent so the profile stays defined; the cap blow-out this
      // once guarded against is now handled at its source, by masking the rim lighting.
      float refractionZone = max(min(refractionHeight, min(halfSize.x, halfSize.y)), 0.0001);
      if (distToEdge >= refractionZone) return 0.0;
      float t = clamp(distToEdge / refractionZone, 0.0, 1.0);
      return evaluateProfile(t) * refractionZone;
    }

    float surfaceHeight(vec2 coord) {
      vec2 halfSize = effectSize * 0.5;
      vec2 effectCoord = coord - effectOffset;
      vec2 centeredCoord = effectCoord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      return surfaceHeightAt(coord, radius);
    }

    vec2 surfaceGradient(vec2 coord) {
      // One pixel would alias while the surface scrolls: the height field changes faster than the
      // sample spacing near the rim, and the normal flickers between frames.
      float sampleStep = 3.0;
      float left = surfaceHeight(clampCoord(coord - vec2(sampleStep, 0.0)));
      float right = surfaceHeight(clampCoord(coord + vec2(sampleStep, 0.0)));
      float up = surfaceHeight(clampCoord(coord - vec2(0.0, sampleStep)));
      float down = surfaceHeight(clampCoord(coord + vec2(0.0, sampleStep)));
      // True central difference: dh/dx, dh/dy. The surface normal depends on the real slope, so a
      // scaled-down gradient tilts the normal too little and the refraction never bends much.
      return vec2(right - left, down - up) / (2.0 * sampleStep);
    }

    /**
     * Where a viewing ray lands on the backdrop after entering the glass, in pixels from the
     * sampling point. Ortho view, so the incident ray is straight down; the surface normal comes
     * from the slope of the glass, the ray bends by Snell's law, and it travels the thickness of
     * the glass before reaching the content behind it.
     */
    /**
     * Fresnel reflectance at an air-to-glass interface for unpolarised light.
     *
     * At normal incidence only about 4% of the light reflects and the rest is transmitted; as the
     * surface turns away the reflected share rises until, at grazing angles, the interface behaves
     * as a mirror. This is the partition that makes a real glass edge bright and opaque while its
     * centre stays clear, and it is energy conserving: what reflects does not also refract.
     *
     * The exact equations are used rather than Schlick's approximation because Schlick degenerates
     * when the two media match: it keeps its angular term and reports a bright rim for glass with
     * an index of one, which is air and reflects nothing at any angle.
     */
    float fresnelReflectance(vec3 normal, float ior) {
      float cosI = clamp(normal.z, 0.0, 1.0);
      float sinT = (1.0 / ior) * sqrt(max(0.0, 1.0 - cosI * cosI));
      if (sinT >= 1.0) return 1.0;                       // total internal reflection
      float cosT = sqrt(max(0.0, 1.0 - sinT * sinT));
      float rs = (cosI - ior * cosT) / max(cosI + ior * cosT, 0.0001);
      float rp = (ior * cosI - cosT) / max(ior * cosI + cosT, 0.0001);
      return clamp((rs * rs + rp * rp) * 0.5, 0.0, 1.0);
    }

    /**
     * Where a viewing ray lands on the backdrop, having crossed both faces of the glass.
     *
     * Entering, the ray bends towards the normal by n1/n2; leaving through the flat back face it
     * bends away again by n2/n1. Modelling only the first interface exaggerates the displacement
     * and, worse, keeps bending light that a real slab would have straightened on the way out.
     */
    /**
     * What the rim reflects, sampled from the scene itself.
     *
     * A mirror shows its surroundings, not a grey. With no environment map the nearest honest
     * approximation is the content plane seen along the reflected ray: the surface normal turns the
     * view vector outwards, and the further the ray travels before meeting the plane the further
     * across the backdrop it lands. A flat grey rim is what made the edge read as painted on.
     */
    /**
     * Roughness derived from the specular exponent, so the existing control keeps its meaning: a
     * high exponent is a polished surface and a low one is satin.
     */
    float surfaceRoughness() {
      return clamp(sqrt(2.0 / (max(specularExponent, 2.0) + 2.0)), 0.02, 1.0);
    }

    /**
     * GGX normal distribution: the share of microfacets whose normal points along the half vector.
     * It has the long tail real surfaces show, which Blinn-Phong lacks, so a rough glass keeps a
     * soft halo around its highlight instead of ending abruptly.
     */
    float distributionGGX(float nDotH, float roughness) {
      float a = roughness * roughness;
      float a2 = a * a;
      float d = nDotH * nDotH * (a2 - 1.0) + 1.0;
      return a2 / max(3.14159265 * d * d, 0.0001);
    }

    /**
     * Smith geometry term with the Schlick-GGX approximation: the fraction of microfacets that are
     * neither shadowed nor masked by their neighbours at these angles.
     */
    float geometrySmith(float nDotV, float nDotL, float roughness) {
      float k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
      float gv = nDotV / max(nDotV * (1.0 - k) + k, 0.0001);
      float gl = nDotL / max(nDotL * (1.0 - k) + k, 0.0001);
      return gv * gl;
    }

    /**
     * Cook-Torrance specular for one directional light, without the Fresnel factor: the caller
     * already computed reflectance for the interface and multiplying it twice would double count
     * the energy leaving the surface.
     */
    float microfacetSpecular(vec3 normal, vec3 lightDir, float roughness) {
      vec3 view = vec3(0.0, 0.0, 1.0);
      vec3 half = normalize(lightDir + view);
      float nDotV = max(dot(normal, view), 0.0001);
      float nDotL = max(dot(normal, lightDir), 0.0);
      float nDotH = max(dot(normal, half), 0.0);
      float d = distributionGGX(nDotH, roughness);
      float g = geometrySmith(nDotV, nDotL, roughness);
      return (d * g) / max(4.0 * nDotV * nDotL, 0.0001) * nDotL;
    }

    vec3 reflectedEnvironment(vec2 coord, vec3 normal, float thickness) {
      vec3 view = vec3(0.0, 0.0, -1.0);
      vec3 mirrored = reflect(view, normal);
      // Grazing reflections run nearly parallel to the plane, so the travel is clamped to keep the
      // sample on screen instead of smearing the border pixel along the rim.
      float travel = clamp(thickness / max(abs(mirrored.z), 0.2), 0.0, thickness * 4.0);
      return content.eval(clampCoord(coord + mirrored.xy * travel)).rgb;
    }

    vec2 refractionOffsetAt(vec2 coord, float thickness, float ior) {
      vec2 slope = surfaceGradient(coord);
      vec3 normal = normalize(vec3(-slope, 1.0));
      vec3 incident = vec3(0.0, 0.0, -1.0);
      vec3 entering = refract(incident, normal, 1.0 / ior);
      // Total internal reflection returns zero; fall back to no displacement rather than garbage.
      if (dot(entering, entering) < 0.0001) return vec2(0.0);

      // Travel through the slab to the back face, which is flat and faces the viewer.
      vec2 inside = entering.xy * (thickness / max(-entering.z, 0.05));
      vec3 backNormal = vec3(0.0, 0.0, 1.0);
      vec3 leaving = refract(entering, backNormal, ior);
      if (dot(leaving, leaving) < 0.0001) return inside;

      // Beyond the back face the ray continues to the content plane. Keeping that leg proportional
      // to the slab keeps the whole construction scale free.
      return inside + leaving.xy * (thickness / max(-leaving.z, 0.05));
    }

    vec2 refractionOffset(vec2 coord, float thickness) {
      // refractionStrength selects how dense the glass is: 0 is air and bends nothing.
      return refractionOffsetAt(coord, thickness, mix(1.0, 1.55, clamp(refractionStrength, 0.0, 1.0)));
    }

    /**
     * Dispersion: glass bends short wavelengths harder than long ones, so each channel gets its own
     * index and its own landing point. That is what fringes a rim magenta on one side and cyan on
     * the other, and it follows the whole perimeter because it follows the surface slope.
     */
    vec4 sampleDispersed(vec2 coord, float thickness) {
      float base = mix(1.0, 1.55, clamp(refractionStrength, 0.0, 1.0));
      float spread = base * 0.35 * clamp(chromaticAberrationStrength, 0.0, 1.0);
      vec2 red = coord + refractionOffsetAt(coord, thickness, base - spread);
      vec2 green = coord + refractionOffsetAt(coord, thickness, base);
      vec2 blue = coord + refractionOffsetAt(coord, thickness, base + spread);

      // A rough surface transmits into a cone rather than a single direction, so the view through
      // it softens. This is where a glass gets its haze from; a separate blurred copy mixed in
      // underneath was never part of the material.
      float scatter = surfaceRoughness() * thickness * 0.35;
      vec2 tangent = vec2(-1.0, 1.0) * scatter;
      vec2 bitangent = vec2(1.0, 1.0) * scatter;
      vec4 centre = content.eval(clampCoord(green));
      vec4 spreadA = content.eval(clampCoord(green + tangent));
      vec4 spreadB = content.eval(clampCoord(green - tangent));
      vec4 spreadC = content.eval(clampCoord(green + bitangent));
      vec4 spreadD = content.eval(clampCoord(green - bitangent));
      vec4 scattered = (centre * 2.0 + spreadA + spreadB + spreadC + spreadD) / 6.0;

      return vec4(
        mix(content.eval(clampCoord(red)).r, scattered.r, 0.5),
        mix(centre.g, scattered.g, 0.5),
        mix(content.eval(clampCoord(blue)).b, scattered.b, 0.5),
        centre.a
      );
    }

    float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

    vec3 computeContentNormal(vec2 coord) {
      float l = luma(content.eval(clampCoord(coord + vec2(1.0, 0.0))).rgb);
      float r = luma(content.eval(clampCoord(coord - vec2(1.0, 0.0))).rgb);
      float t = luma(content.eval(clampCoord(coord + vec2(0.0, 1.0))).rgb);
      float b = luma(content.eval(clampCoord(coord - vec2(0.0, 1.0))).rgb);
      vec2 grad = vec2(r - l, b - t);
      return normalize(vec3(grad, 1.0));
    }

    float edgeMask(float sd) {
      if (sd > 0.0) return 0.0;
      if (edgeSoftness <= 0.0) return 1.0;
      float distToEdge = max(-sd, 0.0);
      float e = clamp(distToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0);
      return smootherstep(e);
    }

    vec4 sampleChromaSimple(vec2 coord, vec2 chromaOffset) {
      if (chromaticAberrationStrength <= 0.0001) return content.eval(clampCoord(coord));
      vec2 forward = clampCoord(coord + chromaOffset);
      vec2 backward = clampCoord(coord - chromaOffset);
      vec4 base = content.eval(clampCoord(coord));
      return vec4(content.eval(forward).r, base.g, content.eval(backward).b, base.a);
    }

    /**
     * Full spectral chromatic aberration that samples the refracted content at seven
     * wavelength offsets (red through purple) and blends them with position-dependent
     * intensity. Much more expensive than the simple mode but produces a realistic
     * prismatic edge when chromaticAberrationStrength is high.
     */
    vec4 sampleChromaFull(vec2 coord, vec2 chromaOffset) {
      if (length(chromaOffset) < 0.0001) return content.eval(clampCoord(coord));

      vec4 color = vec4(0.0);

      vec4 red = content.eval(clampCoord(coord + chromaOffset));
      color.r += red.r / 3.5;
      color.a += red.a / 7.0;

      vec4 orange = content.eval(clampCoord(coord + chromaOffset * (2.0 / 3.0)));
      color.r += orange.r / 3.5;
      color.g += orange.g / 7.0;
      color.a += orange.a / 7.0;

      vec4 yellow = content.eval(clampCoord(coord + chromaOffset * (1.0 / 3.0)));
      color.r += yellow.r / 3.5;
      color.g += yellow.g / 3.5;
      color.a += yellow.a / 7.0;

      vec4 green = content.eval(clampCoord(coord));
      color.g += green.g / 3.5;
      color.a += green.a / 7.0;

      vec4 cyan = content.eval(clampCoord(coord - chromaOffset * (1.0 / 3.0)));
      color.g += cyan.g / 3.5;
      color.b += cyan.b / 3.0;
      color.a += cyan.a / 7.0;

      vec4 blue = content.eval(clampCoord(coord - chromaOffset * (2.0 / 3.0)));
      color.b += blue.b / 3.0;
      color.a += blue.a / 7.0;

      vec4 purple = content.eval(clampCoord(coord - chromaOffset));
      color.r += purple.r / 7.0;
      color.b += purple.b / 3.0;
      color.a += purple.a / 7.0;

      return color;
    }

    vec4 sampleChroma(vec2 coord, vec2 chromaOffset) {
      if (chromaticAberrationMode == 1) {
        return sampleChromaFull(coord, chromaOffset);
      }
      return sampleChromaSimple(coord, chromaOffset);
    }

    ${blurredContentSampler(contentMode)}

    vec3 srgbToLinear(vec3 s) {
      return mix(s / 12.92, pow((s + 0.055) / 1.055, vec3(2.4)), step(0.04045, s));
    }

    vec3 linearToSrgb(vec3 l) {
      return mix(l * 12.92, 1.055 * pow(l, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, l));
    }

    vec4 applyColorGrading(vec4 color) {
      // Saturation (linear sRGB for perceptual uniformity)
      if (chromaMultiplier != 1.0) {
        vec3 lin = srgbToLinear(color.rgb);
        float y = dot(lin, vec3(0.2126, 0.7152, 0.0722));
        color.rgb = linearToSrgb(mix(vec3(y), lin, chromaMultiplier));
      }

      // White point
      if (whitePoint != 0.0) {
        vec3 target = (whitePoint > 0.0) ? vec3(1.0) : vec3(0.0);
        color.rgb = mix(color.rgb, target, abs(whitePoint));
      }

      // Contrast
      if (contrast != 0.0) {
        color.rgb = clamp((color.rgb - 0.5) * (1.0 + contrast) + 0.5, 0.0, 1.0);
      }

      return color;
    }

    vec4 main(vec2 coord) {
      vec2 halfSize = effectSize * 0.5;
      vec2 effectCoord = coord - effectOffset;
      vec2 centeredCoord = effectCoord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);

      // Actual SDF for edge mask and distance (preserves exact shape).
      float sd = sdRoundedRect(centeredCoord, halfSize, radius);
      float distToEdge = max(-sd, 0.0);

      // Flat-interior early-out: skip refraction when far from edge.
      // Same bound as surfaceHeightAt: the two must agree or the interior test and the displacement
      // disagree about where the glass edge ends.
      float refractionZone = max(min(refractionHeight, min(halfSize.x, halfSize.y)), 0.0001);
      if (distToEdge >= refractionZone) {
        vec4 base = content.eval(coord);
        ${flatInteriorDepthMix(contentMode)}
        vec3 graded = applyColorGrading(vec4(mixedColor, base.a)).rgb;

        // Deep interior: surface gradient is negligible, use content normal only.
        vec3 normal = computeContentNormal(coord);
        vec2 lightDir2D = safeNormalize(lightPosition - coord, vec2(0.0, -1.0));
        vec3 lightDir = normalize(vec3(lightDir2D, 1.0));
        float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), fresnelExponent);
        float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));

        vec3 tinted = mix(graded, tintColor.rgb, tintColor.a);
        vec3 finalColor = tinted * ambient;
        ${flatInteriorReturn(contentMode)}
      }

      vec4 base = content.eval(coord);

      float h = surfaceHeight(coord);
      float heightNorm = clamp(h / refractionZone, 0.0, 1.0);

      // Snell's law through a glass of this thickness, rather than an offset along the distance
      // field scaled by a constant. The bending now follows the surface slope, so it vanishes on
      // the flat interior and grows into the curve on its own.
      float glassThickness = refractionZone * refractionScale;
      vec2 displacement = refractionOffset(coord, glassThickness);
      vec2 refractCoord = clampCoord(coord + displacement);

      // Dispersion replaces the old corner-weighted offset, which only fringed the extreme corners
      // because its weight was |x*y| and therefore zero along both centre axes.
      vec4 refracted = sampleDispersed(coord, glassThickness);
      ${refractedDepthSample(contentMode)}
      ${refractedColor(contentMode)}

      vec2 grad = surfaceGradient(coord);
      vec3 shapeNormal = normalize(vec3(-grad.x, -grad.y, 1.0));
      vec3 contentNormal = computeContentNormal(coord);
      vec3 normal = normalize(mix(shapeNormal, contentNormal, contentNormalBlend)); // Blend shape + content normals

      ${refractedDepthMix(contentMode)}
      vec3 graded = applyColorGrading(vec4(mixedColor, 1.0)).rgb;
      vec3 tinted = mix(graded, tintColor.rgb, tintColor.a);
      vec2 lightDir2D = safeNormalize(lightPosition - coord, vec2(0.0, -1.0));
      vec3 lightDir = normalize(vec3(lightDir2D, 1.0));
      // Confined to the rim by the same falloff that drives the displacement. Unmasked it saturates
      // into a white band along every straight edge, because the edge normal stays aligned with the
      // light for the whole run of the side.
      // Cook-Torrance rather than a power of the dot product: the highlight now widens and dims
      // together as the surface roughens, instead of only narrowing.
      float spec = microfacetSpecular(normal, lightDir, surfaceRoughness()) * specularIntensity * heightNorm;
      // Fresnel brightens what the glass already emits, so it follows the rim falloff too. Applied
      // flat it multiplies the whole edge run and blows the sides out to white.
      float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), fresnelExponent) * heightNorm;
      float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));
      ${refractedReturn(contentMode)}
    }
    """

  fun buildOutputMask(): String = """
    uniform shader content;
    uniform float2 layerSize;
    uniform float2 effectOffset;
    uniform float2 effectSize;
    uniform float edgeSoftness;
    uniform vec4 cornerRadii;

    float smootherstep(float x) {
      float t = clamp(x, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    float radiusAt(vec2 coord, vec4 radii) {
      if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
      } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
      }
    }

    float sdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      float outside = length(max(cornerCoord, 0.0)) - radius;
      float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
      return outside + inside;
    }

    float shapeMask(vec2 coord) {
      vec2 halfSize = effectSize * 0.5;
      vec2 effectCoord = coord - effectOffset;
      vec2 centeredCoord = effectCoord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      float sd = sdRoundedRect(centeredCoord, halfSize, radius);
      if (sd > 0.0) return 0.0;
      if (edgeSoftness <= 0.0) return 1.0;

      float distToEdge = max(-sd, 0.0);
      float e = clamp(distToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0);
      return smootherstep(e);
    }

    vec4 main(vec2 coord) {
      return content.eval(coord) * shapeMask(coord);
    }
    """

  private fun blurredContentSampler(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput ->
      """
    vec4 sampleBlurredContent(vec2 coord) {
      return blurredContent.eval(clampCoord(coord));
    }
    """

    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> """
    """
  }

  private fun flatInteriorDepthMix(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput ->
      """
        vec4 blurred = sampleBlurredContent(coord);
        vec3 mixedColor = mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0));
    """

    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> """
        vec3 mixedColor = base.rgb;
    """
  }

  private fun flatInteriorReturn(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput,
    ContentMode.SingleBlurredInput,
    -> "return vec4(finalColor, base.a);"

    // The tint belongs to the glass, not to the content behind it, so it keeps its own weight over
    // the blurred underlay while only the re-emitted content follows depth. The refracted branch
    // below composes the same way, which is what keeps the two continuous.
    ContentMode.OverlayWithExternalUnderlay ->
      """
        float tintAlpha = clamp(tintColor.a, 0.0, 1.0);
        float contentAmount = (1.0 - clamp(depth, 0.0, 1.0)) * (1.0 - tintAlpha);
        float overlayAlpha = contentAmount + tintAlpha;
        vec3 overlayColor = graded * ambient * contentAmount + tintColor.rgb * ambient * tintAlpha;
        return vec4(overlayColor, base.a * overlayAlpha) * edgeMask(sd);
    """
  }

  private fun refractedDepthSample(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput -> "vec4 blurred = sampleBlurredContent(refractCoord);"
    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> ""
  }

  private fun refractedColor(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput ->
      "vec3 refractedColor = applyColorGrading(vec4(mix(refracted.rgb, blurred.rgb, clamp(depth, 0.0, 1.0)), 1.0)).rgb;"

    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> "vec3 refractedColor = applyColorGrading(refracted).rgb;"
  }

  private fun refractedDepthMix(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput -> "vec3 mixedColor = mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0));"
    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> "vec3 mixedColor = base.rgb;"
  }

  private fun refractedReturn(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput,
    ContentMode.SingleBlurredInput,
    -> """
      vec3 finalColor = mix(tinted, refractedColor, refractionStrength) * ambient + spec;
      float edge = edgeMask(sd);
      return vec4(finalColor, base.a) * edge;
    """

    ContentMode.OverlayWithExternalUnderlay ->
      """
      float depthAmount = clamp(depth, 0.0, 1.0);
      float refractionAmount = clamp(refractionStrength, 0.0, 1.0);
      float tintAlpha = clamp(tintColor.a, 0.0, 1.0);

      // Energy split at the interface: what reflects cannot also transmit. Near the centre the
      // surface faces the viewer and almost everything passes through; towards the rim the surface
      // turns away and reflectance climbs to one, which is why a real glass edge reads as a bright
      // opaque line rather than as a lit version of the content behind it.
      float reflectance = fresnelReflectance(shapeNormal, mix(1.0, 1.55, refractionAmount));
      float transmittance = 1.0 - reflectance;

      // Depth must attenuate the refracted contribution exactly as it attenuates the base one.
      // Scaling only the base left this band far more opaque than the flat interior, and the step
      // between them drew a hard rectangle inset by the refraction height.
      // Beer-Lambert: a ray crossing the slab loses intensity as exp(-alpha * distance), and the
      // path is longer where the glass is thicker and where the ray runs at an angle. This is what
      // makes a tint deepen towards the rim on its own, instead of lying over the whole surface as
      // a flat veil.
      float pathLength = (1.0 + heightNorm) / max(shapeNormal.z, 0.15);
      float absorption = exp(-tintAlpha * 1.6 * pathLength);
      float contentAmount = (1.0 - depthAmount) * absorption * transmittance;
      float tintWeight = (1.0 - absorption) * (1.0 - reflectance);
      float baseCoeff = contentAmount * (1.0 - refractionAmount);
      float refractedCoeff = contentAmount * refractionAmount;
      float overlayAlpha = baseCoeff + refractedCoeff + tintWeight + reflectance;
      vec3 overlayColor = graded * ambient * baseCoeff +
        refractedColor * ambient * refractedCoeff +
        tintColor.rgb * ambient * tintWeight +
        reflectedEnvironment(coord, shapeNormal, glassThickness) * reflectance +
        spec;
      // The other modes mask their return by the edge; this one did not, so the glass kept drawing
      // past its own shape and bled outwards. Real glass ends where the shape ends.
      return vec4(overlayColor, base.a * overlayAlpha) * edgeMask(sd);
    """
  }
}
