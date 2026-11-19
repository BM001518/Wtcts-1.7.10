# AE2实用 (WTCT)

把 AE2 的仓库、手动合成、样板编码与批处理收进一台无线终端，并补上一整套升级卡与可调吞吐的扩展总线。

*Bundles AE2's terminal, crafting, pattern encoding and pattern management into one wireless terminal, plus a full set of upgrade cards and throughput-tunable extended buses.*

面向 **Minecraft 1.7.10 / GT: New Horizons 2.9.0-RC-1** 的 AE2 扩展模组，modid `wtct`。

---

## 关于这个仓库

这是 [233exp / Wtcts-1.7.10](https://github.com/233max258/Wtcts-1.7.10) 的**维护分支**：在上游作者联系不上之后，本分支从上游最后一个提交（`1.0.63-rc1`）接手，修掉一批积累的 bug，并补了几项使用体验上的功能。界面、物品与存档格式与**上游完全兼容**（同一个 modid、同一个包名，直接替换 jar 即可）。

- 版本：**1.1.0**（= 上游 `1.0.63-rc1` + 本节「改动说明」）
- 维护者：**BM001518**
- 项目链：asdflj → [233max258/Wtcts-1.7.10](https://github.com/233max258/Wtcts-1.7.10) → 本仓库
- 许可：[GPL-3.0](LICENSE)，署名与来源见 [NOTICE](NOTICE)

每条改动的现象、根因与改法记录在 [docs/wtct-bugfix-pass.md](docs/wtct-bugfix-pass.md)。

---

## 下载与安装

1. 从 [Releases](https://github.com/BM001518/Wtcts-1.7.10/releases) 下载 `wtct-1.1.0.jar`。
2. 丢进整合包的 `mods/` 目录，启动游戏。
3. 依赖由 **GTNH 2.9.0-RC-1** 整合包自带，不需要额外安装。

> `modid`、包名与物品注册名都与上游一致，所以**不能与上游 `1.0.63-rc1` 的 jar 同时安装**；替换时删掉旧 jar。存档、终端 NBT、网络枢纽数据可直接沿用。

---

## 功能

- **ME 综合工作终端** —— 一件物品装下仓库、手动合成、样板编码、批处理四套界面，需 ME 无线收发器接入网络。
- **样板缓存区与样板管理区** —— 缓存区暂存编码结果、支持收藏置顶与扩展布局；管理区按配方类型搜索供应器，自动上传按机器族归并。
- **ME 扩展总线** —— 输入／输出／存储三种形态，过滤槽 63 格，支持物品与流体，节拍自适应（有货逐级提速到 1 tick，空闲 60 tick）。
- **升级卡** —— 导入／导出／磁铁／方块拾取／量子桥／能源共六张，移植自 WCWT / AE2WTLib。
- **ME 无线收发器** —— 为无线终端提供跨维度网络接入。
- **终端充电** —— 手持终端可在 GregTech 机器上充电（不限电压档），也可走 IndustrialCraft 2 通道。

---

## 改动说明

相对上游 `1.0.63-rc1`，每条一句话。

### 修复 · 扩展总线与存储

- 扩展输入总线的流体速度只有 1/1000，速度卡形同虚设。
- 扩展输出总线只动第一个流体过滤槽，其余槽位永远不动。
- 流体自动合成的请求量恒为 1 mB。
- 存储元件与背包把上限 16 的物品堆到 64。

### 修复 · 升级卡

- 磁铁卡的总开关关掉之后再也打不开。
- 输入／输出卡的幽灵槽按物品 ID 匹配，会带走同 ID 不同 NBT 的物品。
- 方块选取卡把方块写进终端 0 号槽，且不同步手持物。
- 磁铁卡快捷键第一次按下就崩客户端（`upgrades` 标签类型不符）。

### 修复 · 无线网络枢纽

- 头方块所在区块卸载后，所有接收端永久失联。
- `setupConnection` 会在过期位置把整个网络删掉。
- 删除网络后连接仍然存活，要等超时才消失。
- 扳手能无条件拿走枢纽，且不走拆除逻辑。
- `create-network` 没有鉴权，谁都能抢占未组网的枢纽。
- 玩家改名后被锁在自己的网络外，还会产生重复的成员组。
- 公开网络的成员列表永远是空的。

### 修复 · 样板编码与自动上传

- 倍增／交换会把缓存里的流体样板永久写坏（现在直接跳过）。
- 含源质的样板无法识别。
- 自动上传会把样板塞进被隐藏的接口。
- 从 NEI 拉取样板会丢掉编程电路等「不消耗」材料。
- 「编程器电路始终为一」认不出 GT 编程电路，翻倍时还连电路一起翻倍。
- 搜索框为空时上传会塞进第一个有空位的接口（现在直接进样板缓存区）。
- 多方块取材料后上传会沿用上一次的搜索词，把样板送错接口。
- 改样板数量会清空管理区搜索。
- 清空搜索框后文字被旧关键词回填。

### 修复 · 联机、存储与终端

- 中键选取方块会让客户端掉线。
- 扩展存储总线会崩服并留下死档。
- 存储总线看不到流体，也识别不了源质。
- 综合终端打开时空指针崩溃。
- 装了 ECO 之后合成状态页缺少 ECO 面板。
- 打开终端第一帧按名称排序，停顿后才跳回玩家顺序。
- 综合终端列表不能显示源质。

### 新增

- **磁铁卡快捷键**：在「关闭／吸到身上／吸进 ME 网络」之间循环切换，提示画在快捷栏上方。
- **Shift + 编码键**：取回上一次自动上传的样板。
- **乐观打开**（默认开）：先显示界面，窗口包到齐后再绑定真实容器。
- **打开终端提速**：复用已显示的界面，列表批量应用且第一帧免排序。
- **耗时诊断**（默认关）：按阶段输出 `[wtct-perf]` 计时。
- **终端列表支持源质**：可显示、可搜索，暂不支持点击发起合成。

---

## 兼容性

| 项 | 版本 |
|---|---|
| Minecraft | 1.7.10 |
| GT: New Horizons | **2.9.0-RC-1** |
| Applied Energistics 2 (Unofficial) | `rv3-beta-1073-GTNH` |
| GregTech 5 Unofficial | `5.09.54.183` |
| AE2FluidCraft-Rework | `1.5.110-gtnh` |
| GTNHLib | `0.11.51` |
| NotEnoughItems | `2.8.145-GTNH` |
| Baubles-Expanded | `2.2.24-GTNH` |

以上是开发与测试所用的版本。其它 GTNH 2.9.x 小版本、以及不在表里的 AE2 附属原则上可用，但未逐一验证。版本号唯一来源是 `build.gradle.kts` 里的 `extra["modVersion"]`。

---

## 构建

```bash
./gradlew build
```

产物位于 `build/libs/wtct-<版本>.jar`（另有两个 `-dev`／`-sources` 附带产物，发 Release 只需要不带后缀的那个）。

- 需要 **JDK 25** 跑 Gradle daemon（`gradle/gradle-daemon-jvm.properties` 指定），构建脚本用 JVM Downgrader 把字节码降到 Java 8。
- 首次构建需要能访问 GTNH Maven / GitHub 下载依赖；依赖齐了之后可以加 `--offline` 完全离线构建。
- **`libs/` 目录必须保留。** 其中 `hbmaeaddon-1.4-dev.jar` 与 `programmablehatches-0.1.3p50-dev.jar` 是不在 Maven 上的编译期依赖，缺失会直接编译失败。

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/wtct-bugfix-pass.md](docs/wtct-bugfix-pass.md) | 本轮修复的完整记录：现象、根因、改法 |
| [docs/wcwt-gui-reference.md](docs/wcwt-gui-reference.md) | 综合终端界面与控件对照（WCWT 1.21 → 1.7.10） |
| [docs/wcwt-upgrade-cards.md](docs/wcwt-upgrade-cards.md) | 升级卡体系的行为说明 |
| [docs/wtlib-upgrade-slots.md](docs/wtlib-upgrade-slots.md) | 终端升级槽与卡片互斥规则 |
| [docs/wcwt-gap-analysis.md](docs/wcwt-gap-analysis.md) | 与 WCWT 1.3.9 的功能差距分析 |
| [docs/wcwt-todo-checklist.md](docs/wcwt-todo-checklist.md) | 对齐 WCWT 的待办清单（含状态） |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |

---

## 来源与致谢

- **原项目**：`Wtcts` 由 **asdflj** 创建，项目骨架与 Gradle 构建脚本沿用其结构（原仓库已不可访问）。
- **上游**：[233max258/Wtcts-1.7.10](https://github.com/233max258/Wtcts-1.7.10)（作者 233exp），本分支从它的 `1.0.63-rc1` 起步。
- **界面与升级卡体系**：移植自 **WCWT / AE2WTLib**（Minecraft 1.21）的对应实现。
- **依赖的模组**：Applied Energistics 2、GregTech、GT: New Horizons、AE2FluidCraft-Rework、GTNHLib、NotEnoughItems、Baubles-Expanded 等，版权归各自作者。

修 bug 与新增功能由 **BM001518** 完成，上游与原作者的名字保留在作者列表与 [NOTICE](NOTICE) 中。

---

## 许可证

[GNU General Public License v3.0](LICENSE)。本仓库是 GPL-3.0 作品的衍生作品，分发时同样遵循 GPL-3.0；来源与修改声明见 [NOTICE](NOTICE)。
