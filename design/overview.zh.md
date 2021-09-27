## 概述

通过预先的声明来为依赖生成可访问的 **Java** 静态类成员（***Field***），以便在任何上下文中轻松导入依赖，这一过程称之为映射。生成的字段值不会包含依赖版本，它们通过动态获取，这有助于遵循统一依赖管理的原则。



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



## YAML 初稿：

```yaml
refereces:
  kotlin: 1.5.31


libraries:
  # Do not check the snapshot versions
  rejectSnapshot: true

  com.google.guava:guava:
    mapped: Guava
    version:
      99.0-SNAPSHOT
      100.0
      101.1-SNAPSHOT
      102.1-SNAPSHOT
    # Override to allow check snapshot versions
    rejectSnapshot: false
    
  org.jetbrains.kotlin:kotlin-stdlib:
    version: ${kotlin}
  org.jetbrains.kotlin:kotlin-reflect:
    # Allow checking milestone versions
    rejectMilestone: false
    version: 
      ${kotlin}
      1.6.0-M1
    
  com.mycompany:other:
    version:
      1.0
      1.1

  org.apache.commons:commons-lang3:
    # Do not check any version for this dependency
    rejectAll: true
    version: 3.12.0

plugins:
  com.github.ben-manes.versions:
    mapped: Gradle.VersionsPlugin
    version:
      0.21
      0.35
      0.36
      0.38
    # Only three latest versions are allowed
    versionLimit: 3
```

###### 结构说明：

- `refereces` 相当于一个 Map<String, String> 的变量块，用于声明多个共用版本

- `libraries` 拥有 Maven 坐标相关依赖的版本数据块

  - 数据块中包含了每个 **Gradle** 坐标依赖的数据

- `plugins` 与插件依赖 ID 相关的版本数据块

  - 数据块中包含了每个 **Gradle** 插件 ID 的数据

------

- `mapped` 储存了映射后的 Java 成员路径
- `rejectAll` 拒绝这个依赖的任何版本的检查
- `rejectSnapshot` `rejectMilestone` `reject*` 拒绝不同版本的检查，[参考1](https://en.wikipedia.org/wiki/Software_release_life_cycle)， [参考2](https://stackoverflow.com/questions/2107484/what-is-the-difference-between-springs-ga-rc-and-m2-releases)
- `versionLimit` 表示要限制的最新版本结果
- `version` 所有可用的新版本，一行代表一个版本，第一个则表示正在使用的版本，这意味着更新版本只需要将新版本之上的行都删除即可方便地完成更新

这些数据会在首次 gradle sync 或执行**版本刷新任务**  `./gradlew checkVersions `(~~任务名暂定~~)  时自动生成或补充。

指的注意的是，如果 `libraries` 或 `plugins` 中出现了未在 `settings.gradle.kts / build.gradle.kts` 中声明的依赖，则需要根据 `mapped` 属性（如果没有声明此属性则通过[格式化器](mapping.zh.md/#格式化)）来自动生成 Java 映射成员，这与 [version catalogs](https://docs.gradle.org/current/userguide/platforms.html#sub:central-declaration-of-dependencies) 的行为一致。

> [幕后细节](version.zh.md)



## Gradle DSL 初稿

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

但这有一个问题，使用此插件的用户将在第一次映射后被缓存，这将失去了即使查找最新组织项目依赖的特性，所以我们更应该提供的是一个获取之后的依赖列表，然后通过调用 `LibraryDeclaration.map` 来映射它们，为了保持最新，我们可以通过此项目中的 **Github Action** 在每天的某个时刻拉取组织下的所有依赖，并输出成依赖列表文件。

> [幕后细节](mapping.zh.md)



## 版本更新机器人

检查新版本需要手动执行**版本刷新任务**来完成，为了更加方便，我们应该通过 Github Action 来定时完成，这与 [dependabot](https://dependabot.com/), [renovatebot](https://renovatebot.com/) 类似。

1. 定时执行**版本刷新任务**
2. 排查出所有新版本可用的依赖并更改 **yml** 文件以更新版本
3. 为 Github 项目创建 PR，并在 PR 描述中列出所有版本变更的依赖

更一步的是自动化完成更新，而不是手动合并 PR，暂定 `auto-update` 机器人选项：

1. 更新依赖
2. 编译并执行测试
    - 测试成功：将更新后的 **yml** 文件直接 `commit` 并 `push` 到主分支
    - 测试失败：创建一个 **issue**，并列出所有版本变更的依赖，并指向测试失败的 **Workflow** 日志



## 待补充..