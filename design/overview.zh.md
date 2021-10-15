## 概述

通过预先的声明来为依赖生成可访问的 **Java** 静态类成员（***Field***），以便在任何上下文中轻松导入依赖，这一过程称之为映射。生成的字段值不会包含依赖版本，它们通过动态获取，这有助于遵循统一依赖管理的原则。



------



## 依赖声明

支持远程依赖项以快捷生成并即时更新依赖映射，换句话来说，我们可以通过定义一个键值来从 [Maven Central Repository](https://search.maven.org/), [Google Maven Repository](https://maven.google.com/web/index.html) 等储存库来找到所有匹配的依赖，并将它们映射为 Java 成员。这样的好处是不用再费时费力的输入各种各样的常见依赖，例如 *Android* 开发中常用的 **androidx**, **kotlin** 依赖，并且可以随时保持远端最新的依赖组，就像 **Apache** 组织新发布的项目依赖。

与 [version catalogs](https://docs.gradle.org/current/userguide/platforms.html#sub:central-declaration-of-dependencies) 相似，我们通过脚本与配置文件来声明、储存依赖信息：

- **Gradle** 脚本  `settings.gradle.kts / build.gradle.kts`

> 指定依赖映射、搜索远程依赖、格式化代码块等复杂声明
>
> 生成的 Java 映射成员将取决于这些声明

- **YAML** 配置文件 `dependency-versions.yml`

  > 此配置文件尽可能保持简洁，以用于声明 / 储存依赖版本
  >
  > Gradle 的依赖都将使用此配置文件中的版本

因为 **Gradle** 脚本结构并不适用于配置（储存依赖的动态版本），所以需要一个 **YAML** 配置文件来保存配置，但我认为这不是弊端，因为它们的侧重点不同，储存版本的地方应该尽可能的简洁。（~~也许有更好的方案~~）

------



## YAML

完整示例：

```yaml
references:
  kotlin: 1.5.31


libraries:
  # Do not check the snapshot versions
  rejectSnapshot: true
  # Do not check the milestone versions
  rejectSuffixes: [-M]

  com.google.guava:guava:
    versions:
      - 99.0-SNAPSHOT
      - 100.0
      - 101.1-SNAPSHOT
      - 102.1-SNAPSHOT
    # Override to allow check snapshot versions
    rejectSnapshot: false
    
  org.jetbrains.kotlin:kotlin-stdlib:
    versions: ${kotlin}
  org.jetbrains.kotlin:kotlin-reflect:
    # Clear suffix array to allow checking milestone versions
    rejectSuffixes: []
    versions: 
      - ${kotlin}
      - 1.6.0-M1
    
  com.mycompany:other:
    versions:
      - 1.0
      - 1.1

  org.apache.commons:commons-lang3:
    # Do not check any version for this dependency
    rejectAll: true
    versions: 3.12.0

plugins:
  com.github.ben-manes.versions:
    versions:
      - 0.21
      - 0.36
      - 0.38
    # Only two latest versions are allowed
    limitVersions: 2
```

这些数据会在首次 Gradle 运行或手动执行**版本刷新任务** `./gradlew checkVersions ` 时自动生成或补充。

值得注意的是，如果 `libraries` 或 `plugins` 中出现了尚未在 `settings.gradle.kts / build.gradle.kts` 中声明的依赖，则需要通过 [格式化器](mapping.zh.md#格式化) 来自动生成 Java 映射成员，这与 [version catalogs](https://docs.gradle.org/current/userguide/platforms.html#sub:central-declaration-of-dependencies) 的行为一致。

> [幕后设计细节](version.zh.md)

------



## Gradle DSL

```kotlin
dependencyCentral { // DependencyCentralDeclaration
  
  // Generate access maps for all projects
  projects()
  
  // Generate a root class named Libs
  libraries("Libs") { // LibraryDeclaration
    // Fully specify the mapped path of the Java field
    map(
      "com.tfowl.ktor:ktor-jsoup" to "Ktor.Jsoup",
      "com.github.ben-manes.caffeine:caffeine" to "Caffeine",
    )
    ...
  }
  
  plugis { // PluginDeclaration
    ...
  }
}
```

为了保证开箱即用的原则，我们应该提供预设的配置，例如常见的 **androidx**, **kotlin** 等库的依赖:

```kotlin
/** 添加指定的和远程的 Kotlin Maven 依赖 */
fun LibraryDeclaration.addKotlinDependencies() {
  // Search all prefixes matching dependencies from the Maven Central
  searchPrefixes(
    "org.jetbrains.markdown",
    "org.jetbrains.annotations",
    "org.jetbrains.kotlin",
    "org.jetbrains.kotlinx",
    "org.jetbrains.compose",
    "org.jetbrains.dokka",
    "org.jetbrains.exposed",
    "org.jetbrains.kotlin-wrappers",
    "org.jetbrains.intellij",
    "org.jetbrains.anko",
    "org.jetbrains.spek",
    "org.jetbrains.lets-plot",
    "org.jetbrains.skiko",
    "org.jetbrains.teamcity",
  ) { 
    fromMavenCentral()
    // Skip deprecated dependencies
    filterNot {
      it.artifact == "kotlinx-serialization-runtime-jsonparser"
    }
  }
}

/** 添加和远程的 Gradle 插件 ID 依赖 */
fun PluginDeclaration.addGradleDependencies() {
  map(
    "com.gradle.publish" to "Gradle.Publish",
    "com.gradle.build-scan" to "Gradle.BuildScan",
    "org.gradle.crypto.checksum" to "Gradle.Crypto.Checksum",
    "org.gradle.android.cache-fix" to "Gradle.AndroidCacheFix",
  )

  searchPrefixes(
    "org.gradle.kotlin",
    "com.gradle.enterprise",
  ) { fromGradlePluginPortal() }
}


/** 添加默认的格式化器 */
fun DependencyCentralDeclaration.addDefaultFormatter() {
  format {
    // Keep the first letter lowercase
    notCapitalize { name ->
      // Ignore specific platform name
      name.startsWith("ios", ignoreCase = true) ||
      name.startsWith("wasm32", ignoreCase = true) ||
      name.startsWith("wasm64", ignoreCase = true)
    }
    onEachName {
      it.replace("androidx", "AndroidX")
        .replace("kotlinx", "KotlinX")
        ...
    }
    onStart {
      // Replace mapped path
      it.replace("org.jetbrains.kotlin", "kotlin")
        .replace("org.jetbrains.intellij", "intellij")
        .replace("com.google.android", "google")
        .removePrefix("com.")
        .removePrefix("net.")
        .removePrefix("org.")
        ...
    }
  }
}

...
```

但这有一个问题，使用此插件的用户将在第一次映射后被缓存，这将失去了及时查找最新组织项目依赖的特性，所以我们更应该提供的是一个获取之后的依赖列表，然后通过调用 `LibraryDeclaration.map` 来映射它们，为了保持最新，我们可以通过此项目中的 **Github Action** 在每天的某个时刻拉取组织下的所有依赖，并输出成依赖列表文件。

> [幕后设计细节](mapping.zh.md)

------



## 版本更新机器人

检查新版本需要手动执行**版本刷新任务**来完成，为了更加方便，我们应该通过 **Github Action** 来定时完成，这与 [dependabot](https://dependabot.com/), [renovatebot](https://renovatebot.com/) 类似。

> [幕后设计细节](version.zh.md#版本自动更新机器人组合运行步骤草案)

------



## 待补充..
