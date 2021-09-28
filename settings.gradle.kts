pluginManagement {
  repositories {
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.meowool.gradle.toolkit") version "0.1.0-SNAPSHOT"
}

gradleToolkitWithMeowoolSpec()

dependencyMapper {
  libraries {
    map(
      "net.andreinc:mockneat",
      // TODO Remove when meowool-sweekt released.
      "com.meowool.toolkit:sweekt",
      "com.meowool.gradle:toolkit",
    )
  }
}

importProjects(rootDir)