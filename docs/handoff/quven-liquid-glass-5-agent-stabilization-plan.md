# Quven Liquid Glass 5-Agent Stabilization Plan

This handoff coordinates five autonomous agents working on the Quven Haze fork.
The goal is to move the fork toward a production-grade Liquid Glass foundation for
Quven, with Android phone/tablet visuals approaching the iPhone/iPad Apple Liquid
Glass experience as closely as the platform permits.

Do not contact upstream maintainers. Do not open upstream issues, pull requests,
review comments, or maintainer-facing discussions. All work stays in the Quven
public fork until Quven explicitly decides otherwise.

## 1. Baseline

- Repository: `C:\Users\Domenico\Desktop\Lavoro\haze`
- Required branch: `quven/liquidglass-main-spike`
- Current fork head when this plan was written: `1b041438 Fix Liquid Glass runtime geometry`
- Upstream base included in this branch: `f7893d92 Update roborazzi to v1.67.0 (#1029)`
- Do not base work on `2.0.0-alpha03`; it is behind `main`.
- Treat stale experiment branches as references only:
  - `cb/opengl-blur`
  - `cb/hardwarebuffer`
  - `cb/render-effect-cache-key`
  - `cb/rotation-sample`
  - `copilot/add-colorfilter-support-hazetint`
  - `agent/2d4c-brainstorm-wheth`
  - `v1`
  - `v1.8`

The Quven branch already contains the important runtime geometry fix that separates
the expanded rendering layer from the real effect bounds. Preserve that shape.

## 2. Product Objective

Build a credible Liquid Glass layer for Quven:

- Android phone and Android tablet should visually track iPhone/iPad Liquid Glass
  behavior as closely as practical.
- iPad/iPhone style is the reference: cinematic, translucent, refractive, softly
  dimensional, and restrained rather than loud.
- Prefer GPU-backed paths for expensive operations:
  - Android API 33+: `RuntimeShader` / AGSL plus `RenderEffect` composition.
  - Android API 31/32: `RenderEffect` blur where available, conservative shader
    and progressive behavior.
  - Android API 30 and below: conservative fallback; do not pretend it is premium
    Apple-like Liquid Glass.
  - Skiko targets: keep GPU shader paths aligned with Android semantics.
- CPU-heavy fallbacks are acceptable only as degraded fallback paths, never as the
  premium path for compatible devices.
- The public Haze API should remain controlled and experimental until visual,
  performance, and stability gates prove it is stable enough.

## 3. Shared Rules For All Agents

Read these files before editing:

1. `QUVEN-FORK.md`
2. `QUVEN-LIQUIDGLASS-ROADMAP.md`
3. `docs/effects/liquid-glass.md`
4. `docs/architecture.md`
5. `docs/performance.md`
6. `gradle.properties`
7. `settings.gradle.kts`
8. The files listed in your agent-specific section.

Before editing, each agent must run:

```powershell
git status --short --branch
git branch --show-current
git log --oneline --decorate -5
```

Required branch:

```text
quven/liquidglass-main-spike
```

If the branch is different, stop and report the mismatch. Do not switch branches
unless the user explicitly asks.

Worktree and commit rules:

- Work directly on `quven/liquidglass-main-spike`.
- Do not create branches or worktrees.
- Do not run destructive git commands.
- Do not run `git add -A`.
- Commit only files owned by your agent section.
- Other agents may have unrelated dirty files. Do not revert them.
- If a commit fails because the git index is locked, wait 20 seconds and retry.
- If your owned file has unexpected edits from another agent, stop and report the
  conflict instead of overwriting.
- Keep docs, code comments, and commit messages in English.

Use this staging pattern:

```powershell
git diff --name-only
git add -- <only-your-owned-paths>
git diff --cached --name-only
git commit -m "<area>: <concise change summary>"
```

## 4. Coordination Gates

The agents are intentionally split by file ownership. Parallel phase 1 has no
cross-agent file overlap. Phase 2 has explicit wait-stops.

### Gate 0: Start

All agents may start immediately after reading this plan.

### Gate 1: Foundation Commits

Each agent must finish its phase 1 task and make one commit:

- Agent A commit prefix: `build:`
- Agent B commit prefix: `liquidglass:`
- Agent C commit prefix: `materials:`
- Agent D commit prefix: `screenshots:` or `benchmarks:`
- Agent E commit prefix: `docs:` or `platform:`

### Gate 2: Integration Wait

Agents B, C, and D have phase 2 dependency waits:

- Agent C phase 2 waits until Agent B has committed the runtime shader changes.
- Agent D phase 2 waits until Agent B and Agent C have committed their phase 1
  work.
- Agent E docs finalization waits until Agents A, B, C, and D have committed
  their phase 1 work.

To wait without user intervention:

```powershell
while (-not (git log --oneline -20 | Select-String -Quiet "<expected prefix or commit text>")) {
  Start-Sleep -Seconds 30
}
git status --short --branch
```

If the required commit never appears but your independent work is complete, make
your phase 1 commit and stop. Do not ask the user for instructions.

## 5. File Ownership Matrix

### Agent A: Fork Artifact, Build, Publishing, And Release Gates

Owns:

- `gradle.properties`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/**`
- `haze/build.gradle.kts`
- `haze-blur/build.gradle.kts`
- `haze-liquidglass/build.gradle.kts`
- `haze-liquidglass/gradle.properties`
- `haze-liquidglass-materials/build.gradle.kts`
- `haze-liquidglass-materials/gradle.properties`
- `haze-utils/build.gradle.kts`
- `.github/workflows/**`

Must not edit:

- Liquid Glass Kotlin implementation files.
- Screenshot golden images.
- `docs/**`, except this plan if a mechanical fix is needed.

Objective:

- Make Quven fork artifacts locally publishable and release-safe without enabling
  upstream Maven Central publishing.
- Keep coordinates under `tv.quven.forks.haze`.
- Remove or gate non-hermetic build risks where practical, especially global
  `mavenLocal()` usage.
- Keep Apple targets disabled only when explicitly requested by project property;
  do not break normal multiplatform configuration.
- Keep Liquid Glass modules source/publish behavior consistent with the Quven fork
  objective.

Phase 1 tasks:

1. Verify which modules can publish to Maven Local.
2. Ensure `haze-liquidglass` and `haze-liquidglass-materials` are publishable under
   Quven coordinates without enabling Maven Central signing.
3. Gate `mavenLocal()` so it is not always active for every consumer build.
4. Add or adjust Gradle properties only if they improve reproducibility.
5. Keep changes narrow and testable.

Suggested validation:

```powershell
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze-utils:publishToMavenLocal `
  :haze:publishToMavenLocal `
  :haze-blur:publishToMavenLocal `
  :haze-liquidglass:publishToMavenLocal `
  :haze-liquidglass-materials:publishToMavenLocal
```

Commit when phase 1 is done.

Phase 2 tasks:

1. After all phase 1 commits exist, run a narrow build/publish verification.
2. If build logic broke because of another agent's changes, fix only build-owned
   files and coordinate by commit message.

### Agent B: GPU Liquid Glass Runtime And Shader Parity

Owns:

- `haze-liquidglass/src/commonMain/**`
- `haze-liquidglass/src/androidMain/**`
- `haze-liquidglass/src/skikoMain/**`
- `haze-liquidglass/src/commonTest/**`
- `haze-liquidglass/src/jvmTest/**`
- `haze-liquidglass/api/**`

Must not edit:

- `haze-liquidglass-materials/**`
- `haze-screenshot-tests/**`
- Gradle build files.
- General docs.

Objective:

- Improve the runtime Liquid Glass effect so Android GPU-compatible devices get a
  more Apple-like glass result without regressing Skiko behavior.
- Preserve the Quven geometry fix: shader math must use effect bounds, not expanded
  layer bounds, for masks, refraction zones, corner radii, and light position.
- Favor GPU composition over CPU fallbacks.
- Do not make API-stabilizing promises unless tests prove the behavior.

Phase 1 tasks:

1. Read the existing Android and Skiko factories:
   - `LiquidGlassRenderEffectFactory.android.kt`
   - `LiquidGlassRenderEffectFactory.skiko.kt`
   - `RuntimeShaderLiquidGlassDelegate.kt`
   - `LiquidGlassShaders.kt`
2. Audit Android `OverlayWithExternalUnderlay` against Skiko dual-input semantics.
3. Tune or refactor shader parameter names only if it improves correctness and
   readability.
4. Improve refraction, depth, overlay alpha, light falloff, and edge mask behavior
   with deterministic unit tests.
5. Keep fallback behavior explicit and conservative.

Required tests to add or strengthen:

- `depth = 0f` must not distort content.
- Mid-depth must refract inside the effect bounds.
- Expanded layer bounds must not change corner radii or light geometry.
- Android external-underlay parameters must remain deterministic.
- Fallback should be visibly marked as fallback in implementation semantics, not
  silently considered equivalent to runtime Liquid Glass.

Suggested validation:

```powershell
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze-liquidglass:compileAndroidMain `
  :haze-liquidglass:testAndroid `
  :haze-liquidglass:jvmTest `
  :haze-liquidglass:metalavaCheckCompatibility
```

Commit when phase 1 is done.

Phase 2 tasks:

1. After Agent D adds screenshot/perf scenarios, review failures related to shader
   behavior.
2. Fix only runtime-owned files.
3. Commit any follow-up as `liquidglass: tune gpu glass parity`.

### Agent C: Quven Liquid Glass Materials And Apple-Language Presets

Owns:

- `haze-liquidglass-materials/src/**`
- `haze-liquidglass-materials/api/**`
- `haze-liquidglass-materials/src/commonTest/**`
- `haze-liquidglass-materials/src/jvmTest/**`

Must not edit:

- `haze-liquidglass/src/**`
- `haze-screenshot-tests/**`
- Gradle build files.
- General docs.

Objective:

- Replace demo-grade presets with a small, coherent Quven Liquid Glass material
  vocabulary that maps to Apple-like usage patterns while remaining platform-safe.
- Names should be close to SwiftUI/Apple mental models where practical, but must
  remain honest Compose/Kotlin APIs.
- Presets must be conservative enough for Android GPU paths and must not make API
  30 fallback look like premium Liquid Glass.

Phase 1 tasks:

1. Audit `HazeLiquidGlassMaterials`.
2. Add or tune presets for the main Quven use cases:
   - floating control chrome
   - modal/sheet surface
   - navigation/sidebar bar
   - media overlay pill/control cluster
   - tablet/iPad-style large glass panel
3. Keep depth, blur, chromatic aberration, tint, and edge softness restrained.
4. Add tests that prove presets are stable and intentionally differentiated.
5. Keep all APIs `@ExperimentalHazeApi` unless Agent A and Agent E have explicitly
   prepared a stable-release path.

Suggested validation:

```powershell
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze-liquidglass-materials:compileAndroidMain `
  :haze-liquidglass-materials:metadataCommonMainClasses `
  :haze-liquidglass-materials:jvmTest `
  :haze-liquidglass-materials:metalavaCheckCompatibility
```

Commit when phase 1 is done.

Phase 2 wait:

- Wait for Agent B's phase 1 `liquidglass:` commit.

Phase 2 tasks:

1. If Agent B changes runtime parameter semantics, update material presets to use
   the corrected model.
2. Do not update screenshot tests; Agent D owns them.
3. Commit any follow-up as `materials: align presets with gpu runtime`.

### Agent D: Screenshot, Visual Matrix, And Performance Gates

Owns:

- `haze-screenshot-tests/**`
- `benchmark/**`
- `baselineprofile/**`
- `sample/**` only when adding isolated visual/perf scenarios

Must not edit:

- `haze-liquidglass/src/**`
- `haze-liquidglass-materials/src/**`
- Gradle build files unless Agent A has explicitly committed a needed test wiring
  hook and the change is within a test-owned file.
- General docs.

Objective:

- Build the evidence layer: screenshots, visual scenarios, and performance gates
  that can prove whether Android phone/tablet is approaching the iPhone/iPad
  Liquid Glass reference.
- Prefer deterministic synthetic scenes when real iOS captures are unavailable.
- Do not claim visual parity from Robolectric alone; mark device-only gates clearly.

Phase 1 tasks:

1. Inventory existing Liquid Glass screenshot tests.
2. Add missing Android phone/tablet scenarios:
   - modal/sheet
   - bottom bar / navigation bar
   - floating playback controls
   - media overlay pill
   - large tablet glass panel
   - nested glass over media-like content
3. Cover API 35 runtime shader and API 32/28 fallback behavior separately.
4. Add screenshot cases for Agent C material presets only after Agent C commits.
5. Add benchmark or baseline-profile scenarios for large bars, nested chrome,
   progressive blur, and high depth on compatible APIs if the existing project
   structure supports it without broad Gradle churn.

Suggested validation:

```powershell
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze-screenshot-tests:test
```

If screenshot generation changes goldens, review the image set before committing.
Commit only test sources and intentional goldens.

Phase 2 wait:

- Wait for Agent B phase 1 commit.
- Wait for Agent C phase 1 commit.

Phase 2 tasks:

1. Add screenshot coverage for final runtime + preset combinations.
2. If tests reveal shader/material bugs, do not fix implementation files. Commit
   the failing evidence or stop with a clear report naming the owner agent.
3. Commit any follow-up as `screenshots: cover liquid glass parity matrix`.

### Agent E: Platform Stability, Memory Policy, And Documentation Truth

Owns:

- `haze/src/appleMain/**`
- `haze/src/iosMain/**`
- `haze/src/macosMain/**`
- `haze/src/jvmMain/**`
- `haze/src/jsMain/**`
- `haze/src/wasmJsMain/**`
- `docs/**`
- `README.md`
- `CHANGELOG.md`
- `QUVEN-FORK.md`
- `QUVEN-LIQUIDGLASS-ROADMAP.md`

Must not edit:

- `haze-liquidglass/src/**`
- `haze-liquidglass-materials/src/**`
- `haze-screenshot-tests/**`
- Gradle build files.

Objective:

- Make platform support claims honest.
- Improve or document retained-output memory policy on Apple/JVM targets.
- Align docs with the latest `main` plus Quven branch, not `2.0.0-alpha03`.
- Preserve the message that Liquid Glass is experimental until evidence says
  otherwise.

Phase 1 tasks:

1. Audit `TrimMemoryCallback` implementations.
2. Implement a narrow memory-pressure improvement on Apple targets if feasible
   without adding dependencies or destabilizing KMP.
3. If implementation is not feasible, document the no-op truth explicitly in the
   platform docs and Quven roadmap.
4. Update docs that still imply `2.0.0-alpha03` is the current target.
5. Update Liquid Glass docs to distinguish:
   - Android API 33+ GPU runtime path
   - Android API 31/32 partial GPU path
   - Android API 30 and below fallback
   - Skiko shader path
   - Web/Wasm limitations
6. Keep README platform claims conservative and evidence-backed.

Suggested validation:

```powershell
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze:compileKotlinJvm `
  :haze:jvmTest
```

Commit when phase 1 is done.

Phase 2 wait:

- Wait for Agents A, B, C, and D phase 1 commits.

Phase 2 tasks:

1. Re-read the final diffs from the other agents.
2. Update docs and roadmap so they match what was actually implemented.
3. Do not embellish. If visual parity still needs manual iPad/iPhone proof, say so.
4. Commit any follow-up as `docs: align liquid glass stabilization status`.

## 6. Visual Parity Direction

The target look is not "more blur." It is a controlled optical surface:

- crisp silhouette
- soft inner depth
- subtle refraction
- readable content under chrome
- controlled tint
- minimal color fringing
- light direction that feels consistent across surfaces
- no muddy dark overlay
- no heavy frosted-card look
- no unstable shimmer during movement

Android should avoid trying to mimic iOS by making everything translucent and
expensive. The premium path should use GPU primitives to achieve a composed result
that feels native on Android while remaining visually close to iPad/iPhone.

## 7. Final Integration Checklist

After all agents commit, the last available agent should run:

```powershell
git log --oneline --decorate -15
git status --short --branch
.\gradlew.bat --no-daemon --no-scan --project-prop=haze.disableAppleTargets=true `
  :haze-liquidglass:compileAndroidMain `
  :haze-liquidglass:testAndroid `
  :haze-liquidglass:jvmTest `
  :haze-liquidglass-materials:compileAndroidMain `
  :haze-liquidglass-materials:metadataCommonMainClasses `
  :haze-liquidglass-materials:jvmTest `
  :haze-screenshot-tests:test
```

If the final command is too slow or blocked by local environment constraints, run
the largest passing subset and document exactly what was and was not verified.

## 8. Agent Prompt Template

Use one of these prompts:

```text
Tu sei Agente A. Esegui solo la sezione Agente A del piano:
C:/Users/Domenico/Desktop/Lavoro/haze/docs/handoff/quven-liquid-glass-5-agent-stabilization-plan.md
```

```text
Tu sei Agente B. Esegui solo la sezione Agente B del piano:
C:/Users/Domenico/Desktop/Lavoro/haze/docs/handoff/quven-liquid-glass-5-agent-stabilization-plan.md
```

```text
Tu sei Agente C. Esegui solo la sezione Agente C del piano:
C:/Users/Domenico/Desktop/Lavoro/haze/docs/handoff/quven-liquid-glass-5-agent-stabilization-plan.md
```

```text
Tu sei Agente D. Esegui solo la sezione Agente D del piano:
C:/Users/Domenico/Desktop/Lavoro/haze/docs/handoff/quven-liquid-glass-5-agent-stabilization-plan.md
```

```text
Tu sei Agente E. Esegui solo la sezione Agente E del piano:
C:/Users/Domenico/Desktop/Lavoro/haze/docs/handoff/quven-liquid-glass-5-agent-stabilization-plan.md
```
