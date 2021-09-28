# 版本相关设计

------

## 配置文件定义

配置文件使用 **YAML** 语法来定义依赖、依赖版本、版本的更新选项，这很大程度上作为一个中心点，**Gradle** 每次运行时都从此文件中解析对应依赖的版本信息并使用。（配置结构一定程度上参考了 [Github Action](https://docs.github.com/cn/actions/learn-github-actions/introduction-to-github-actions)）

- 以下代码展示了一个 `yml` 配置文件的示例：

```yaml
refereces:
  kotlin: 1.5.31
  androidx-compose: 1.0.1

libraries:
  rejectSnapshot: true
  rejectMilestone: true
  rejectReleaseCandidate: true

  org.apache.commons:commons-lang3:
    rejectAll: true
    version: 3.12.0
    
  org.jetbrains.kotlin:kotlin-stdlib:
    version: ${kotlin}
    
  org.jetbrains.kotlin:kotlin-reflect:
    rejectMilestone: false
    version: 
      ${kotlin}
      1.6.0-M1
    
  androidx.compose.ui:ui:
    version: ${androidx}

plugins:
  com.github.ben-manes.versions:
    versionLimit: 3
    version:
      0.21
      0.35
      0.36
      0.38
```

------

### YAML 结构

##### 顶层对象

- 配置拥有三个特定的一级对象（顶层属性），它们都是可选的

| 名称        | 描述                                                         | 默认值                                         |
| ----------- | ------------------------------------------------------------ | ---------------------------------------------- |
| `refereces` | 用于定义通用版本变量，嵌套的每一行都是一个版本键值           | 无                                             |
| `libraries` | 用于定义 / 储存所有拥有 Maven 坐标（`groupId:artifactId`）的依赖 | 无，但每次 Gradle 运行时都可能会更新或插入内容 |
| `plugins`   | 用于定义 / 储存所有 **Gradle** 插件依赖的 Id，既通常用于 Gradle 脚本文件中的 `plugins {}` | 无，但每次 Gradle 运行时都可能会更新或插入内容 |

------

##### 子层属性

- 在 `libraries` 和 `plugins` 中都提供了一些特定的可选属性来控制版本的检查行为，除此之外的所有属性都会被视作[依赖对象](#依赖对象)

| 名称             | 描述                                              | 类型     | 示例                                                         | 默认值  |
| ---------------- | ------------------------------------------------- | -------- | ------------------------------------------------------------ | ------- |
| `rejectAll`      | 拒绝检查所有版本                                  | Boolean  | `rejectRelease: true`                                        | `false` |
| `rejectRelease`  | 拒绝检查稳定版本                                  | Boolean  | `rejectRelease: true`                                        | `false` |
| `rejectSnapshot` | 拒绝检查快照版本                                  | Boolean  | `rejectSnapshot: true`                                       | `true`  |
| `rejectSuffix`   | 拒绝检查给定后缀（忽略大小写）的版本              | String   | `rejectSuffix: -RC` （排除版本后缀为 RC 的结果，例如 1.2.0-rc） | 无      |
| `rejectKeyword`  | 拒绝检查包含给定关键字（忽略大小写）的版本        | String   | `rejectKeyword: test` （排除版本中包含 test 的结果，例如 0.5-TEST-SNAPSHOT） | 无      |
| `limitVersons`   | 限制检测到的新版本的数量                          | Int      | `limitVersions: 3`（只保留最新的三个版本）                   | `10`    |
| ...              | 未指定的属性，表示一个嵌套的[依赖对象](#依赖对象) | 复合对象 | `dependency:`<br />   `...`<br />   `...`                    | 无      |

> 注意：所有特定属性都可以被[依赖对象](#依赖对象)继承
>
> ```yaml
> libraries:
>   limitVersions: 1
>   com.a:b:
>     # 覆盖父层级的 `limitVersions: 1`
>     limitVersions: 5
>     
> plugins:
>   rejectAll: true
>   com.a:b:
>     # 覆盖父层级的 `rejectAll: true`
>     rejectAll: false
> ```

------

###### 依赖对象

- 依赖对象仅存在一个特定的属性（**版本**），既是整个文件的重点。除此之外，此对象还可以继承来自父层级的所有特定[属性](#子层属性)

| 名称     | 描述                       | 类型       | 示例                                                    |
| -------- | -------------------------- | ---------- | ------------------------------------------------------- |
| versions | 列出了所有可用的版本的数组 | 字符串数组 | `version`|<br />  `- 1.0`<br />  `- 1.1`<br />  `- 1.2` |

> 需要注意的是，数组的第一位版本表示着依赖正在使用的版本，此外往后的每一个版本都表示着可用的新版本，它们不会被使用，如果需要更新，则将前面的版本删除即可

------

## 工作流程

##### Gradle Sync

1. 当 **Gradle** 运行时，本项目插件将解析出所有配置依赖项（*`implementation`*, *`api`*...），并将未声明的依赖写入 [**yml** 配置文件](#配置文件定义) 中

   > 这一部分应该可以参考 [gradle-versions-plugin](https://github.com/ben-manes/gradle-versions-plugin) 和 [refreshVersions](https://github.com/jmfayard/refreshVersions/) 实现

2. 将 [**yml** 配置文件](#配置文件定义) 序列化为 **Kotlin** 对象结构

3. 找出所有 [依赖对象](#依赖对象) 中正在使用的版本

4. 将版本对应的注入到 **Gradle**

   > 这样就动态地完成了版本替换

------

##### 版本检查任务 `./gradlew checkVersions`

1. 将 [**yml** 配置文件](#配置文件定义) 序列化为 **Kotlin** 对象结构

2. 找出所有声明的 [依赖对象](#依赖对象) 

3. 通过 **Gradle** 中定义的储存库来查找对应依赖的新版本

   > 所有高于当前使用的版本都会被记录

4. 将所有新版本附加到 [依赖对象](#依赖对象) 的 `versions` 数组

------

##### 版本自动更新机器人（[组合运行步骤草案](https://docs.github.com/cn/actions/creating-actions/creating-a-composite-run-steps-action)）

1. 定时或手动通过 [gradle-build-action](https://github.com/gradle/gradle-build-action) 运行 `checkVersions` 来更新 [**yml** 配置文件](#配置文件定义)

2. 运行内部的机器人专用 **Gradle Task**: `updateVersions`

   - 将 [**yml** 配置文件](#配置文件定义) 序列化为 **Kotlin** 对象结构

   - 找出所有新版本可用的 [依赖对象](#依赖对象) 

   - 将所有依赖的旧版本与新版本记录到 **文件 ➊** 中 

     > com.foo.bar:baz [1.0] -> [2.0]

   - 将 [依赖对象](#依赖对象) 的 `versions` 数组清空并替换为最新版本

   - 反序列化回 [**yml** 配置文件](#配置文件定义) 

     > 这样就代表着整个更新过程已经完成

3. 通过用户指定的任务，例如 `gradle build` 来对更新后的版本进行编译测试

4. 如果测试过程失败则生成 **issue** 内容，这同样可以由内部 **task** 完成：

   - 列出 **文件 ➊** 中记录的每一行版本变更后的依赖信息
   - 添加一个链接以指向测试失败的 **Workflow** 日志
   - 通过 [Github Action](https://github.com/marketplace?type=actions&query=issue+create+) 创建一个 **issue**

5. 如果测试成功则使用 **git** 推送新的 [**yml** 配置文件](#配置文件定义) 到 Github 仓库分支

   > 另一个机器人选项是 `pull-request:`，与 [dependabot](https://dependabot.com/), [renovatebot](https://renovatebot.com/) 相似，通过 **PR** 来通知新版本变化以让项目维护人员安全地手动同意更新，而不是自动化更新。

------

##### 版本自动更新机器人（[Dockfile 草案](https://docs.github.com/cn/actions/creating-actions/creating-a-docker-container-action)）

使用 [Docker + Kotlin](https://github.com/DRSchlaubi/docker-kotlin) 来完成 [复合 Action](#版本自动更新机器人（复合 Action 草案）) 的所有操作

------

##### 版本自动更新机器人（[JavaScript 草案](https://docs.github.com/cn/actions/creating-actions/creating-a-javascript-action)）

使用 JS 语言来完成 [复合 Action](#版本自动更新机器人（复合 Action 草案）) 的所有操作

------

> 在  [dependabot](https://dependabot.com/), [renovatebot](https://renovatebot.com/) 对 Gradle 的特性没有完全支持前，造个自动更新机器人的轮子无疑很有吸引力