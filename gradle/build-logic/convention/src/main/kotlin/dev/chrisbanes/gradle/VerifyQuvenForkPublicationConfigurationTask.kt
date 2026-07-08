// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The task is a lightweight fork release gate.")
abstract class VerifyQuvenForkPublicationConfigurationTask : DefaultTask() {
  @get:Internal
  abstract val projectDirectory: DirectoryProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val rootGradlePropertiesFile: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val settingsGradleFile: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val moduleGradlePropertiesFiles: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val moduleBuildScriptFiles: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val releaseWorkflowFiles: ConfigurableFileCollection

  @get:Input
  abstract val expectedRootProperties: MapProperty<String, String>

  @get:Input
  abstract val expectedModuleArtifactIds: MapProperty<String, String>

  @get:Input
  abstract val forbiddenForkReleaseTokens: ListProperty<String>

  @TaskAction
  fun verify() {
    val rootProperties = loadProperties(rootGradlePropertiesFile.asFile.get())
    expectedRootProperties.get().forEach { (name, expected) ->
      rootProperties.requireValue(name, expected)
    }

    val settings = settingsGradleFile.asFile.get().readText()
    check(settings.contains("haze.includeMavenLocal") && gatedMavenLocalRegex.containsMatchIn(settings)) {
      "settings.gradle.kts must gate mavenLocal() behind haze.includeMavenLocal."
    }

    val rootDirectory = projectDirectory.asFile.get()
    expectedModuleArtifactIds.get().forEach { (moduleName, artifactId) ->
      val moduleDirectory = rootDirectory.resolve(moduleName)
      val moduleProperties = loadProperties(moduleDirectory.resolve("gradle.properties"))
      moduleProperties.requireValue("POM_ARTIFACT_ID", artifactId)

      val buildScript = moduleDirectory.resolve("build.gradle.kts").readText()
      check(buildScript.contains("id(\"com.vanniktech.maven.publish\")")) {
        "$moduleName must apply com.vanniktech.maven.publish for publishToMavenLocal."
      }
    }

    val releaseWorkflowText = releaseWorkflowFiles.files.joinToString(separator = "\n") { workflowFile ->
      workflowFile.readText()
    }
    forbiddenForkReleaseTokens.get().forEach { token ->
      check(!releaseWorkflowText.contains(token)) {
        "Fork release automation must not contain '$token'."
      }
    }
    check(releaseWorkflowText.contains("verifyQuvenForkPublicationConfiguration")) {
      "Build workflow must run verifyQuvenForkPublicationConfiguration."
    }
    check(releaseWorkflowText.contains("publishToMavenLocal")) {
      "Build workflow must verify Maven Local publication instead of remote publishing."
    }
  }

  private fun loadProperties(file: File): Properties {
    check(file.isFile) { "Missing Gradle properties file: ${file.invariantSeparatorsPath}" }
    return Properties().apply {
      file.inputStream().use(::load)
    }
  }

  private fun Properties.requireValue(name: String, expected: String) {
    val actual = getProperty(name)
    check(actual == expected) {
      "$name must be '$expected' but was '${actual ?: "<missing>"}'."
    }
  }

  private companion object {
    private val gatedMavenLocalRegex = Regex(
      pattern = """if\s*\(\s*includeMavenLocal\s*\)\s*\{\s*mavenLocal\(\)\s*}""",
      option = RegexOption.DOT_MATCHES_ALL,
    )
  }
}
