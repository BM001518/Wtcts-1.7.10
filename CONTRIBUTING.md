# 参与开发

欢迎 issue 与 PR。改动越大越建议先开 issue 说清楚要解决什么，避免白写。

## 报告问题

开 issue 时请附上：

1. **游戏日志** —— `logs/fml-client-latest.log`（崩溃则再附 `crash-reports/` 里对应的文件）。
2. **触发步骤** —— 尽量具体到「按了哪个键 / 放进哪个槽」，能稳定复现的步骤比截图更有用。
3. **环境** —— GTNH 整合包版本、本模组版本、是否单机/服务端、装了哪些与 AE2 相关的附属。
4. **存档相关的问题** —— 说明是新建档就坏，还是旧档升级后坏；如果是后者，请说明旧档用过的版本。

如果问题涉及界面或列表，日志里搜 `wtct`、`wtct-perf` 往往能直接定位到相关代码。

## 提交代码

### 环境

- **JDK 25** 跑 Gradle（`gradle/gradle-daemon-jvm.properties` 指定）；源码按 Java 8 编写，由 JVM Downgrader 降级到 Java 8 字节码。
- 首次构建需要能访问 GTNH Maven / GitHub。
- **`libs/` 目录必须保留**：`hbmaeaddon-1.4-dev.jar` 与 `programmablehatches-0.1.3p50-dev.jar` 不在 Maven 上。

### 常用命令

```bash
./gradlew spotlessApply              # 格式化（先跑这个）
./gradlew compileJava                # 编译验证
./gradlew build --offline            # 完整构建，产物在 build/libs/
./gradlew runClient                  # 起一个开发客户端
```

改代码的推荐顺序是 **`spotlessApply` → `compileJava` → （改完）`compileJava` → `build`**。
顺序反了会有麻烦：`spotless` 会删掉编译不过的 import 之外的格式内容，先编译后格式化容易看到假错误。

### 改之前请先读

| 想改的东西 | 先看 |
|---|---|
| 综合终端的界面/交互 | [docs/wcwt-gui-reference.md](docs/wcwt-gui-reference.md) |
| 升级卡的行为 | [docs/wcwt-upgrade-cards.md](docs/wcwt-upgrade-cards.md)、[docs/wtlib-upgrade-slots.md](docs/wtlib-upgrade-slots.md) |
| 与 WCWT 1.21 的差距与既有计划 | [docs/wcwt-gap-analysis.md](docs/wcwt-gap-analysis.md)、[docs/wcwt-todo-checklist.md](docs/wcwt-todo-checklist.md) |
| 本分支已修过什么、怎么修的 | [docs/wtct-bugfix-pass.md](docs/wtct-bugfix-pass.md) |

### 硬约束

- **不要改 `modid`（`wtct`）与包名（`com.asdflj.wtct`）。** 改了会让所有旧存档里的终端、网络、存储元件全部失联。
- **不要重命名已注册的物品 / 方块 / 配方。** 同上，注册名是存档的一部分。
- 新代码按 **Java 8** 语法与 API 编写（构建会降级字节码，但运行时是 Java 8）。
- 新加的客户端 mixin 注意阶段：`net.minecraft.client.Minecraft` 必须在 **EARLY** 阶段由 `WtctCore` 注册
  （`mixins.wtct.early.json`），否则客户端启动会 `MixinTargetAlreadyLoadedException`。
- 早期 mixin 不能直接引用本模组的类，需要跨阶段通信时用 `coremod/HandOver` 这类只依赖 `Object`/`Runnable` 的槽位。
- 面向玩家的文本要同时给 `zh_CN` 与 `en_US`（`src/main/resources/assets/wtct/lang/`）。
- 提交前请确保 `./gradlew build` 通过、`spotlessApply` 已跑过。

### PR

- 一个 PR 做一件事；顺手的大范围格式化请单独开 PR。
- 描述里写清「问题是什么 / 怎么修的 / 怎么验证的」。
- 存档格式有变化时（新增 NBT 字段等）务必在描述里说明兼容处理，并在 `CHANGELOG.md` 里记一笔。
- 修 bug 的 PR 请更新 `CHANGELOG.md` 的 `[Unreleased]` 段落（没有就加一个）。

## 许可

本仓库以 **GPL-3.0** 分发（见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)）。提交 PR 即表示你同意以同一许可发布你的改动，
并且你有权这样做（不要粘贴来源不明或许可不兼容的代码）。
