# Quven Haze Fork

This branch is a Quven-owned fork spike. It is not intended for upstream submission.

## Scope

- Base: upstream `main` after the Android Liquid Glass fixes for blur, depth rendering, and retained-layer cleanup.
- Maven group: `tv.quven.forks.haze`.
- Version: `2.0.1-quven-SNAPSHOT`.
- Published modules for local Quven experiments:
  - `tv.quven.forks.haze:haze`
  - `tv.quven.forks.haze:haze-utils`
  - `tv.quven.forks.haze:haze-blur`
  - `tv.quven.forks.haze:haze-liquidglass`
  - `tv.quven.forks.haze:haze-liquidglass-materials`

## Local Validation

Use Android Studio's JBR and the local Android SDK, then run:

```powershell
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze-liquidglass:compileAndroidMain `
  :haze-liquidglass:testAndroid `
  :haze-liquidglass:jvmTest `
  :haze-liquidglass:metalavaCheckCompatibility `
  :haze-screenshot-tests:test
```

Publish the fork artifacts to Maven Local with:

```powershell
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze-utils:publishToMavenLocal `
  :haze:publishToMavenLocal `
  :haze-blur:publishToMavenLocal `
  :haze-liquidglass:publishToMavenLocal `
  :haze-liquidglass-materials:publishToMavenLocal
```

## Upstream Divergences To Re-Check On Rebase

- `lightPosition` is remapped from node-local to layer coordinates (scaled by the input scale
  factor, offset by the effect offset) in `buildLiquidGlassRenderParams`; upstream passes the raw
  value through. Screenshot goldens encode the remapped behavior.

## Quven Integration Rule

Do not point Quven production directly at this branch. Consume it only behind Quven's existing
Liquid Glass wrappers and on one Android surface at a time until device screenshots and perf traces
prove the visual quality is worth shipping.
