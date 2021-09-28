plugins { `kotlin-dsl`; kotlin("plugin.serialization") version "1.5.31" }

dependencies.implementationOf(
  Libs.Ktor.Jsoup,
  Libs.Ktor.Client.OkHttp,
  Libs.Ktor.Client.Logging,
  Libs.Ktor.Client.Serialization,
  Libs.KotlinX.Serialization.Json,
  Libs.Square.OkHttp3.Logging.Interceptor,
  Libs.Meowool.Gradle.Toolkit,
  Libs.ByteBuddy.Byte.Buddy,
  Libs.Andreinc.Mockneat,
)

val packageName = "com.meowool.gradle.dependencentral"
val internalMarkers = "$packageName.InternalDependencyCentralApi"

publication {
  data {
    val baseVersion = "0.1.0"
    version = "$baseVersion-LOCAL"
    // Used to publish non-local versions of artifacts in CI environment
    versionInCI = "$baseVersion-SNAPSHOT"

    displayName = "Gradle Dependency Central"
    artifactId = "dependencentral"
    groupId = "com.meowool.gradle"
    description = "Another elegant way of dependency management in Gradle."
    url = "https://github.com/meowool-toolkit/gradle-dependencentral"
    vcs = "$url.git"
    developer {
      id = "rin"
      name = "Rin Orz"
      url = "https://github.com/RinOrz/"
    }
  }
  pluginClass = "$packageName.DependencentralPlugin"
}

metalava {
  hiddenAnnotations(internalMarkers)
  hiddenPackages("$packageName.internal")
}

optIn(internalMarkers)