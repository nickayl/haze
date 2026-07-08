// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.VerifyQuvenForkPublicationConfigurationTask
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy

plugins {
  id("dev.chrisbanes.root")

  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.baselineprofile) apply false
  alias(libs.plugins.cacheFixPlugin) apply false
  alias(libs.plugins.android.lint) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.mavenpublish) apply false
  alias(libs.plugins.metalava) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.poko) apply false
  alias(libs.plugins.dokka)
}

subprojects {
  tasks.withType<Copy>().configureEach {
    if (name.endsWith("TestDevelopmentExecutableCompileSync")) {
      // Kotlin/JS copies duplicate Skiko runtime files from main and test resources.
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
  }

  dependencies {
    components {
      listOf(
        "coil-core-iosarm64",
        "coil-core-iossimulatorarm64",
        "coil-core-js",
        "coil-core-jvm",
        "coil-core-wasm-js",
      ).forEach { module ->
        withModule("io.coil-kt.coil3:$module") {
          allVariants {
            withDependencies {
              removeIf { it.group == "org.jetbrains.skiko" && it.name == "skiko" }
            }
          }
        }
      }
    }
  }
}

val quvenForkPublishedModules = mapOf(
  "haze-utils" to "haze-utils",
  "haze" to "haze",
  "haze-blur" to "haze-blur",
  "haze-liquidglass" to "haze-liquidglass",
  "haze-liquidglass-materials" to "haze-liquidglass-materials",
)
val quvenForkPublicationFiles = quvenForkPublishedModules.keys.map { moduleName ->
  layout.projectDirectory.file("$moduleName/gradle.properties") to
    layout.projectDirectory.file("$moduleName/build.gradle.kts")
}
val quvenForkReleaseWorkflowFiles = listOf(
  ".github/workflows/build.yml",
  ".github/workflows/close-issues.yml",
  ".github/workflows/release.yml",
).map(layout.projectDirectory::file)

tasks.register<VerifyQuvenForkPublicationConfigurationTask>("verifyQuvenForkPublicationConfiguration") {
  group = "verification"
  description = "Verifies Quven fork coordinates, local publishing, and release gates."

  projectDirectory.set(layout.projectDirectory)
  rootGradlePropertiesFile.set(layout.projectDirectory.file("gradle.properties"))
  settingsGradleFile.set(layout.projectDirectory.file("settings.gradle.kts"))
  moduleGradlePropertiesFiles.from(quvenForkPublicationFiles.map { (propertiesFile, _) -> propertiesFile })
  moduleBuildScriptFiles.from(quvenForkPublicationFiles.map { (_, buildScriptFile) -> buildScriptFile })
  releaseWorkflowFiles.from(quvenForkReleaseWorkflowFiles)

  expectedRootProperties.put("GROUP", "tv.quven.forks.haze")
  expectedRootProperties.put("VERSION_NAME", "2.0.1-quven-SNAPSHOT")
  expectedRootProperties.put("mavenCentralAutomaticPublishing", "false")
  expectedRootProperties.put("mavenCentralPublishing", "false")
  expectedRootProperties.put("signAllPublications", "false")
  expectedRootProperties.put("haze.includeMavenLocal", "false")
  expectedModuleArtifactIds.putAll(quvenForkPublishedModules)
  forbiddenForkReleaseTokens.addAll(
    "MAVEN_CENTRAL",
    "GPG_KEY",
    "mavenCentralUsername",
    "mavenCentralPassword",
    "signingInMemoryKey",
    "Deploy to Sonatype",
    "publish --no-configuration-cache",
  )
}
