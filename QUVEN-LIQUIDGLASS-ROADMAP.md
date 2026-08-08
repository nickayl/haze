# Quven Liquid Glass Roadmap

This file tracks Quven-only work on the public fork. Do not open upstream issues, pull requests, review comments, or maintainer-facing discussions from this branch. All follow-up work stays in the Quven fork until Quven decides otherwise.

## Current Fork Baseline

- Branch: `quven/liquidglass-main-spike`.
- Maven group: `tv.quven.forks.haze`.
- Version: `2.0.1-quven-SNAPSHOT`.
- Base: upstream `main` after the Android Liquid Glass blur, depth, retained-output, and trim-memory fixes.
- Quven app status: back on official Haze 1.x. The Haze 2 integration behind the Quven wrappers was
  done and reverted on 2026-08-08 (see the freeze note below) - it is not work still to be invented.

## Confirmed State

- Android Liquid Glass no longer matches the early `2.0.0-alpha03` limitation. The current branch has a two-pass Android runtime path with a blurred underlay, refractive overlay, and optional output mask.
- The upstream Android depth follow-up is implemented in code even though `docs/superpowers/plans/2026-07-01-android-liquid-glass-depth-spike.md` still reads like a pending checklist.
- Retained output now has an explicit opt-out and moderate-or-stronger trim-memory cleanup.
- Liquid Glass remains experimental and source-only upstream. The Quven fork is intentionally publishing local snapshot artifacts for controlled Quven experiments.
- `haze-liquidglass-materials` contains initial presets, but those presets are demo-grade for Quven: `depth` and blur radii are high enough to require careful device performance validation.

## Frozen on 2026-08-08

Quven is pinned back to the official `dev.chrisbanes.haze:haze:1.7.2`. The result did not reach the
owner's quality bar against the Apple reference, so the app returns to the painted glass while this
branch keeps the work.

Nothing here is abandoned. Everything below is committed and pushed, with the reasoning and the
rejected experiments, so resuming does not repeat today's dead ends. To resume: point the Quven
catalogue at `tv.quven.forks.haze`, restore the scoped `mavenLocal`, and migrate the four call
sites that sit behind the Quven wrappers.

What still separates the result from the reference, in order of impact: the surface profile is a
heuristic curve rather than a defined lens geometry; thickness is constant, so there is no spherical
aberration; and Apple applies the material to small individual elements, where the refraction zone
covers most of the surface, rather than to full panels.

## Refraction rewritten as physics on 2026-08-08

The Android overlay path no longer approximates. The surface slope gives a normal, Snell bends the
ray at the front face, it crosses the slab and straightens at the flat back face, and lands on the
content plane. Dispersion gives each channel its own index. The interface splits energy with the
exact Fresnel equations for unpolarised light, and the transmitted share weighs the content, so the
rim closes into a bright line while the centre stays clear. Tint is Beer-Lambert absorption over
the path actually travelled.

Rejected with reasons, so they are not retried:

- Schlick's approximation for reflectance. It keeps its angular term when the two media match and
  reports a bright rim for glass with an index of one, which is air.
- A single interface. It exaggerates the bend and never straightens the ray on the way out.
- `SurfaceProfile.Squircle` for the Quven style: it reintroduces a visible inner boundary.
- Raising blur to hide a legible backdrop: an erased backdrop has no structure left to bend, so the
  refraction that names the material becomes invisible. Blur and lensing trade against each other.

The debug sample draws on a neutral grey rather than a blue-to-cyan wash. Dispersion separates
channels, and a backdrop with almost no red energy has nothing to separate, so fringes could never
appear there no matter how strong the setting.

## Fixed on 2026-08-08

Five defects in the Android overlay path, each reproduced in `LiquidGlassDebugSample` on a physical
device before it was touched. The sample now carries a `6. Quven chrome` card with the exact style
the app applies, which is how an app-only symptom was reproduced away from the app.

1. Interior and refracted band composed differently. The interior weighed content by `1 - depth`
   while the band used `(1 - depth)(1 - refraction) + refraction`, so a typical style made the band
   nearly twice as opaque and the step drew a hard rectangle inset by the refraction height.
2. The tint rode on that content weight, so a dark tint faded out as depth rose and a dark panel
   turned milky. It now keeps its own alpha over the blurred underlay.
3. Specular and fresnel had no edge falloff. Along a straight edge the normal stays aligned with the
   light for the whole run, so both saturated into a white band. Both now follow the rim falloff,
   which also makes the two branches meet continuously.
4. The refraction zone was unbounded, so on a pill the whole cap sat at maximum height and lit up.
   It was first capped at a quarter of the shorter side; that cap was then removed once the rim
   masks fixed the blow-out at its source, because on a wide surface it compressed the refraction
   into a strip and made `refractionHeight` inert. The bound is now the shorter half-extent, which
   only keeps the profile defined.
5. Refraction displacement was a fixed pixel amount defaulting to 12, so the lensing was invisible
   on anything larger than a chip - the sample's own instruction to expect obvious warping did not
   hold. It is now proportional to the refraction zone, and `refractionScale` is a dimensionless
   multiplier.

Rejected after measuring: `SurfaceProfile.Squircle` for the Quven style. It reintroduces a visible
inner boundary with diagonal corners; `Circle` stays correct here.

Still open: per-element glass rather than per-panel, and a rim highlight that follows curvature
rather than a uniform falloff. Both are needed before the result reads like the iOS reference.

## Reviewed after the freeze on 2026-08-08

A read of the frozen code rather than a test run. Six defects, all fixed on this branch.

1. `refractionScale` became a dimensionless multiplier and neither consumer outside the shader
   followed. The delegate still scaled it by the input scale, thinning the glass in proportion to
   the downsample, and `calculateLayerBounds` took its margin from `refractionStrength *
   refractionScale`, which is under one pixel: the captured backdrop no longer reached as far as the
   shader samples, which is the smear along the long sides. The margin now comes from the refraction
   zone in pixels. The render-params test still asserted the old pixel semantics.
2. The flat interior left out the interface physics the refracted band applies, so the two branches
   disagreed by up to depth times the reflectance and the boundary of the refraction zone drew
   itself as a rectangle again. The interior now evaluates the same terms at normal incidence.
3. The Fresnel term followed the rim falloff in whole. Its content half is present on the flat
   interior too, so only the shape half follows the rim.
4. Roughness scatter did not fade with the curvature, so the softening stopped dead at that same
   boundary and drew it as a ring.
5. The single-index displacement was computed in every mode and read only by the blurred underlay.
6. Three doc comments sat above the wrong function, one of them twice.

## Suspended Work

1. Fork artifact completeness.
   - Keep `haze`, `haze-utils`, `haze-blur`, `haze-liquidglass`, and `haze-liquidglass-materials` publishable under `tv.quven.forks.haze`.
   - Do not publish signed Maven Central artifacts from this fork.

2. Quven material layer.
   - Do not adopt the current `HazeLiquidGlassMaterials` presets directly in Quven production.
   - Add Quven-named wrappers or fork presets only after visual comparison against the iPad/iPhone reference.
   - Start with lower-cost Android styles for chrome and controls: lower `depth`, modest blur, low chromatic aberration, and restrained edge softness.

3. Android visual parity.
   - Compare Android API 33+ screenshots against iPad/iPhone captures for phone, tablet, modal, bottom sheet, floating controls, and shell chrome.
   - Tune the Android `OverlayWithExternalUnderlay` alpha and depth math only with screenshot evidence.
   - Treat the fallback delegate as visually insufficient for Apple-like Liquid Glass. It is a tinted fill plus highlight and rim, not a true glass effect.

4. Device performance.
   - Measure API 33+ devices with `depth > 0f`, progressive blur, large bars, and nested chrome.
   - Measure API 31/32 separately because progressive blur can be materially more expensive.
   - Keep API 30 and below on a conservative fallback unless a real blur fallback is proven acceptable.

5. Retained-output policy.
   - For Quven surfaces that can expose private or stale media content, default wrappers should set `retainOutputWhenSourceUnavailable = false`.
   - Keep the default Haze behavior unchanged in the fork unless Quven app evidence requires a fork-level default.

6. Quven integration.
   - Migrate Quven Android wrappers to Haze 2 names and module boundaries before swapping dependencies.
   - Test a single low-risk surface first, preferably a shell menu or contained modal.
   - Avoid mixing Haze 1 and Haze 2 artifacts in the same Quven build because package names overlap.

## Implementation Order

1. Validate fork artifacts.
   ```powershell
   .\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
     :haze-utils:publishToMavenLocal `
     :haze:publishToMavenLocal `
     :haze-blur:publishToMavenLocal `
     :haze-liquidglass:publishToMavenLocal `
     :haze-liquidglass-materials:publishToMavenLocal
   ```

2. Lock current Liquid Glass behavior.
   ```powershell
   .\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
     :haze-liquidglass:compileAndroidMain `
     :haze-liquidglass:testAndroid `
     :haze-liquidglass:jvmTest `
     :haze-liquidglass:metalavaCheckCompatibility `
     :haze-liquidglass-materials:compileAndroidMain `
     :haze-liquidglass-materials:metadataCommonMainClasses `
     :haze-liquidglass-materials:metalavaCheckCompatibility
   ```

3. Add Quven-specific preset candidates.
   - Use names that mirror the Swift/SwiftUI mental model in Quven wrappers.
   - Keep the Haze fork API experimental until device evidence proves stable.
   - Prefer adding presets in the Quven app wrapper layer first; move them into the fork only if multiple Quven surfaces need the same validated style.

4. Build the comparison matrix.
   - iPhone reference screenshots.
   - iPad reference screenshots.
   - Android phone API 33+.
   - Android tablet API 33+.
   - Android API 31/32 fallback/progressive behavior.
   - Android API 30 or below fallback behavior.

5. Tune shader behavior.
   - Focus first on `LiquidGlassShaders.ContentMode.OverlayWithExternalUnderlay`.
   - Check whether `baseCoeff`, `refractedCoeff`, and `overlayAlpha` match the iOS visual weight.
   - Keep tests around `depth = 0f`, mid-depth, and `depth = 1f`.

## Gates Before Quven App Adoption

- Local Maven artifacts publish cleanly with `--no-scan`.
- Android Liquid Glass and materials modules compile with Apple targets disabled.
- Screenshot evidence shows Android phone/tablet parity is close enough to the iOS/iPad reference for the selected surface.
- Performance traces show no unacceptable frame-time regression on representative Android hardware.
- Quven wrappers isolate all experimental API usage and retained-output defaults.
