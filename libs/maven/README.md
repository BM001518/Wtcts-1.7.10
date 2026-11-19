# libs/maven — 本地 Maven 仓库

这里放的是**不在公开 Maven 仓库里**、但构建必须用到的依赖，以 Maven 目录结构存放，由
`repositories.gradle` 里的 `vendored` 仓库提供给 Gradle。

## scala-parser-combinators_2.11:1.0.1

- 坐标：`org.scala-lang:scala-parser-combinators_2.11:1.0.1`
- 用途：GTNH 构建工具链反编译 Minecraft 启动器（`compileMcLauncherJava`）时的启动器依赖。
- 来源：<https://gregtech.overminddl1.com/org/scala-lang/scala-parser-combinators_2.11/1.0.1/>
  （旧的 GTNH Maven 镜像）。Maven Central 与 GTNH nexus 都没有这个版本，而这个旧镜像从 GitHub
  Actions 的 runner 上连接会超时，所以在这里留一份副本。
- 许可：BSD 3-Clause（Scala 项目的标准许可），版权归 EPFL / Scala 项目所有，原样分发，未做修改。

目录结构：

```
org/scala-lang/scala-parser-combinators_2.11/1.0.1/
├── scala-parser-combinators_2.11-1.0.1.jar
└── scala-parser-combinators_2.11-1.0.1.pom
```
