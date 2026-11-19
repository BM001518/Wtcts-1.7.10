# WTCT 代码分析与本轮修复记录

日期：2026-11-14　版本：`1.0.63-rc1`（MC 1.7.10 / GTNH 2.9.0-RC-1）

## 一、项目构成

`wtct`（"AE2实用"）是 GTNH 的 AE2 附属，源码 377 个 `.java`、约 5 万行，主要包：

| 包 | 内容 |
| --- | --- |
| `common/parts` | ME 扩展输入/输出/存储总线（63 过滤槽、速度倍率） |
| `common/storage` | 物品/流体元件、背包、`StorageManager` 存档数据 |
| `common/item/card` | 6 张升级卡（输入/输出/磁铁/方块选取/量子桥/能源）与每 10 tick 的 `CardTicker` |
| `common/nethub` + `common/tile` + `common/block` | 跨维度 ME 无线网络枢纽（头/接收端、权限组、功耗） |
| `client/gui` + `client/gui/container` | 综合工作终端（GUI 6038 行、Container 3053 行）、样板编码/缓存/管理区、枢纽界面 |
| `util/PatternScaling` | 样板倍增（×2/×3/×5、=1 GCD 归约、÷N、⇄ 交换） |
| `network` | 32 个 C/S 包，`WtctNetworkWrapper` 为改造过的 FML SimpleNetworkWrapper |
| `coremod` | `Mixins.java` 枚举 + 26 个 mixin + 5 个 ASM transformer（AE2/NEI/GT/原版） |
| `api` | 对外 API、适配器（FindIt、UltraTerminal、AE2FC、WCT） |

构建：`./gradlew build`；本轮用 `compileJava` / `assemble` 验证，产物 `build/libs/wtct-1.0.63-rc1.jar`。

## 二、本轮修复

### 扩展总线 / 存储

1. **流体输入总线只有 1/1000 速度** — `common/parts/PartExImportBus.java:245,259`
   流体预算按 mB 计算，却被从"物品计数"里扣：一次请求 1000 mB 却只扣 1，导致每个周期实际传输量被压到 1 mB 级，速度卡完全无效。
   修复：`ExBusSlots.fluidTransferBudget()` 统一按 mB 出预算，新增 `fluidBudgetSpent(mB)` 把消耗折算回物品计数（向上取整）。
2. **流体输出总线只服务第一个过滤槽** — `common/parts/PartExExportBus.java:329`
   同上，槽位循环条件 `this.itemToSend > 0` 在第一槽就被扣成 0。修复同上。
3. **流体自动合成请求量只有 1 mB** — `common/parts/PartExExportBus.java:356`
   `craftFluid` 把"物品个数"当 mB 传给 `MultiCraftingTracker.handleCrafting`。修复：乘回 `MIN_FLUID_TRANSFER`。
4. **元件/背包把上限 16 的物品堆到 64** — `common/storage/CellInventory.java:482`、`common/storage/backpack/BaseBackpackHandler.java:93`
   `getSlotStackLimit` 写成了 `if (inventoryLimit > itemLimit) return inventoryLimit;`（取大不取小）。修复为 `Math.min(...)`。

### 升级卡

5. **磁铁卡总开关关掉后无法再打开** — `network/CPacketTerminalBtns.java:145`
   客户端只改了自己那份 `ContainerCardMagnetConfig`，服务端只执行 `cycleMagnetMode()`，从没写过 `enableMagnetSwitch()`。修复：服务端先 `enableMagnetSwitch()` 再切换模式。
6. **输入/输出卡的幽灵槽用 NBT 精确匹配** — `common/item/card/CardTicker.java:446`
   与其 javadoc（"NBT 无关"）和磁铁卡（物品+损伤值）都不一致。修复为 `Platform.isSameItem`。
7. **方块选取卡把终端写进 0 号槽，且不通知客户端切换手持** — `common/item/card/CardTicker.java:643,652`
   `openTerminal(..., 0)` 让耗电后的终端副本覆盖快捷栏第 0 格；`currentItem` 只在服务端改。修复：传入终端真实槽位，并向 `EntityPlayerMP` 发送 `S09PacketHeldItemChange`。

### 无线网络枢纽

8. **头方块区块卸载后所有接收端永久失联** — `common/tile/TileNetworkHub.java:113,331`
   接收端仅在 `connection == null` 时重连；头卸载时 AE2 销毁共享连接但接收端字段仍非空，于是永不重试；头自身又只在 `isConnected` 已为真时重算。修复：头每 tick 无条件重算 `isConnected`；头卸载时通知已加载接收端 `dropStaleConnection()` 并清空 `receivePos`。
9. **`setupConnection` 会用一条错误位置删掉整个网络** — `common/tile/TileNetworkHub.java:200`
   头方块位置只剩空气时直接 `removeNetwork()`，一次陈旧坐标就能抹掉名字、公开标记和所有成员连接。修复：只置为未连接并等待重试。
10. **删除网络后连接仍然存活** — `client/gui/container/ContainerNetworkHub.java:249`
    之前只删记录，靠各枢纽 20 tick 自己发现。修复：新增 `TileNetworkHub.teardownNetwork()`，立即拆掉头与所有已加载接收端。
11. **扳手可无条件拿走任意枢纽，且不走拆除逻辑** — `common/block/BlockNetworkHub.java:67`
    `setBlockToAir` 不会触发 `breakBlock`，所以既没有权限检查也没有 `breakConnection()`。修复：要求 `Perm.ADMIN`（无网络时要求放置者/OP），并在移除前拆连接。
12. **`create-network` 没有鉴权** — `client/gui/container/ContainerNetworkHub.java:344`
    任何人都能把未配置（或别人的公开网络）枢纽变成自己的网络，同时旧连接还活着。修复：仅当枢纽无网络且是放置者（或 OP）时允许；名称 trim，空名回退 "Network"。
13. **玩家改名后被锁在自己的网络外并产生重复组** — `common/nethub/User.java:130`
    `equals` 同时比较 uuid 和名字。修复：只按 uuid 判定（uuid 为 null 时仅身份相等）。
14. **公开网络的成员列表永远为空** — `client/gui/container/ContainerNetworkHub.java:358`、`client/gui/GuiNetworkHubUser.java:62`
    同步时按 `Group.hasPermission(USER)` 过滤，而 `Group` 没有 `overt` 旁路，非成员只收到 Network 收不到 Group；成员界面又只在 `initGui` 建一次列表。修复：公开网络的所属组一并同步；界面在组对象变化时重新绑定列表。

### 样板编码

15. **倍增/交换会永久破坏缓存里的流体样板** — `util/PatternScaling.java:189`
    流体条目读回后是 `ItemFluidPacket`，写回时却按"物品"写（`fluidAsStack` 只对 `ItemEncodedUltimatePattern` 为真），样板从此请求一个不存在的 "Fluid Packet" 物品且 `Cnt` 塌缩成 1，不可逆。修复：检测到流体条目时跳过该样板，不做改写。

## 三、构建验证

```
./gradlew spotlessApply --no-configuration-cache   → BUILD SUCCESSFUL
./gradlew compileJava  --no-configuration-cache    → BUILD SUCCESSFUL (17s)
./gradlew assemble     --no-configuration-cache    → BUILD SUCCESSFUL
产物：build/libs/wtct-1.0.63-rc1.jar
```

构建环境补充：Gradle 需要经本机 Clash 代理（`127.0.0.1:7890`）访问 GitHub/GTNH Maven，已写入 `~/.gradle/gradle.properties`（`systemProp.http(s).proxyHost/Port` + 30s 超时），否则 Blowdryer 拉配置和依赖下载会 `Connection reset` / 卡在 0 字节。

## 四、已知但本轮未处理

- `CellInventory.injectItems` 的 SIMULATE 分支恒返回 null（"全部接受"），与 `IMEInventory` 契约不符。
- 存储总线可插红石卡，但 `GuiExStorageBus.addButtons()` 未调 `super`，没有红石模式按钮（卡无效）。
- `ContainerExStorageBus` 的静态 `PARTITION_ITERATORS` 跨总线泄漏。
- 枢纽头显示的功耗恒为 `netHubPowerBase`，不反映实际距离/跨维度开销（仅显示问题）。
- OP 在权限组模型中无条件视为 OWNER（疑似有意为之）。
- 流体样板目前直接跳过倍增，尚未实现"按流体重新写回"的完整支持。

## 五、第二轮：日志分析与联机四个问题

日志：`中间选取日志.txt`（598 行）、`流体识别日志.txt`（57 行）。

### 1. 中键选取导致客户端掉线

`[Server thread/ERROR] NoSuchMethodError: 'Item Block.func_149694_d(World,int,int,int)'`
出在 `net.minecraft.block.Block.getPickBlock(Block.java:1801)` ← `CardTicker.pickBlock(CardTicker.java:592)` ← `CPacketTerminalBtns$Handler.onMessage`。

`Block#getPickBlock` 在 1.7.10 是 `@SideOnly(CLIENT)`，专用服务器 jar 里根本没有这个方法；服务器包处理器一调就抛 `NoSuchMethodError`，SimpleChannel 的异常又把整条通道打掉，客户端随之断开。

修复（栈改由客户端解析，服务器只做校验）：

- `client/event/BlockPickerKeyHandler.java:71`：用 `picked.writeToNBT` 把客户端已解析出的 `ItemStack` 放进包里（键 `Picked`），连同坐标一起发。
- `network/CPacketTerminalBtns.java:176`：`CardConfig.PickBlock` 分支用 `ItemStack.loadItemStackFromNBT` 读回栈并传给 `CardTicker.pickBlock`。
- `common/item/card/CardTicker.java`：`pickBlock` 增加 `ItemStack pickedIn` 参数，删除 `MovingObjectPosition` 构造与 `block.getPickBlock(...)` 调用；保留距离（5 格）、方块存在、`containsStack`、空位等服务器侧校验，并对客户端给的栈做非空/物品非空/数量 1..max 的钳制。

### 2. 存储总线崩溃、死档

`NoSuchMethodError: appeng.me.storage.StorageBusInventoryHandler: method 'void <init>(IMEInventory, IAEStackType)' not found`，
出在 `PartExStorageBus.getExHandler(PartExStorageBus.java:504)` ← `getCellArray` ← `GridStorageCache.cellUpdate` ← `PartStorageBus.updateStatus`。

对照 `Applied-Energistics-2-NH-master` 源码：新版 AE2 的该构造器变成三参 `(IMEInventory, IAEStackType, Supplier<AccessRestriction>)`（"重排访问权限"），而 `dependencies.gradle:4` 编译用的 `rv3-beta-1073-GTNH` 只有两参。玩家实例的 AE2 比 1073 新，于是构造器解析失败，服务器 tick 循环被打断，存档写入中断。

修复（`common/parts/PartExStorageBus.java`）：构造器反射解析一次（优先三参 + `Supplier` 返回 `AccessRestriction.READ_WRITE`，退回两参，最后退回编译期构造器），一个 jar 同时兼容新旧 AE2；`Supplier` 恒为 READ_WRITE 等价于旧版"无重排限制"的语义。

### 3. 无法识别源质 / 存储总线看不到流体内容

同一个根因的两个表现：WTCT 的扩展存储总线把栈类型写死成"物品 + 流体"两个 `ExSideState`，
`stateFor(type)` 对其他类型返回 null，`convertFor` 也只认这两种，于是新版 AE2 里注册的第三种类型（Thaumic Energistics 的源质 `AEEssentiaStackType`，走 `AEStackTypeRegistry`）在网络里完全看不到；流体那一侧则因为上面的构造器崩溃直接导致服务器异常。

修复：

- `PartExStorageBus`：`itemState`/`fluidState` 两个字段换成 `Map<IAEStackType<?>, ExSideState>`，`stateFor(type)` 按需创建，`getCellArray`/`postChange`/`isValid`/`getTickingRequest`/`tickingRequest`/`resetEx` 全部改为遍历所有视图；`convertFor` 对未知类型走"同类型即取"的通用分支；矿石过滤只作用于物品视图（源质/流体没有矿物词典概念）。
- `client/gui/container/ContainerExStorageBus.java`：过滤槽自动填充（样板分区扫描）由"物品→流体"扩展为"物品→流体→`AEStackTypeRegistry` 里其余类型"，源质容器也能把源质写进过滤网格。

效果：面向源质容器/流体容器的扩展存储总线现在会分别以对应栈类型向网络暴露库存，Thaumic Energistics 自己的源质终端、AE2 的流体终端都能看到这些内容。

### 4. 修改样板数量后管理区搜索内容被清空

数量输入走的是服务端往返：`CPacketInventoryActionExtend(SET_PATTERN_VALUE)` → 服务端打开 AE2FC 的数量界面 → `CPacketPatternValueSet` 里 `InventoryHandler.openGui(...)` **重新打开综合终端**。
新开的 GUI 是全新实例，`mgmtSearch` 字段为 null，`rebuildMgmtFields()` 里只能取到空串，于是搜索词丢失。

修复（`client/gui/GuiComprehensiveWorkTerminal.java`）：

- 新增静态 `mgmtSearchText` / `mgmtSearchTerminal`，`updateScreen()` 每 tick 调 `rememberMgmtSearch()` 记录当前文本与终端物品；
- `rebuildMgmtFields()` 在 `mgmtSearch == null`（新实例首次建框）时改用 `rememberedMgmtSearch()`，仅当终端物品与记录一致（`ItemStack.areItemStacksEqual`）才恢复，换终端不会串词。

### 构建验证（第二轮）

```
./gradlew spotlessApply --no-configuration-cache   → BUILD SUCCESSFUL
./gradlew compileJava  --no-configuration-cache    → BUILD SUCCESSFUL (15s / 23s / 24s)
./gradlew assemble     --no-configuration-cache    → BUILD SUCCESSFUL (12s)
产物：build/libs/wtct-1.0.63-rc1.jar（2,007,716 字节）
```

### 尚未覆盖

- 综合终端**列表本身**仍只显示物品与流体：要在终端里加源质行，需要新增 `EssentiaMonitor`（对照 `client/gui/container/BaseMonitor/FluidMonitor.java`）、源质同步包与行渲染，属于新功能量级，本轮未做。当前修的是"网络/存储总线能识别源质"。
- 源质在过滤网格里的**图形显示**仍按物品/流体两套槽位绘制，靠自动填充写入的源质槽可能显示为空；功能上参与分区，视觉待后续补。

## 六、第三轮：终端源质列表 + 管理区搜索保留

### 1. 综合终端现在能显示源质

上一轮只做到"存储总线能把源质交给网络"，综合终端列表本身仍只接物品与流体两个监视器。本轮补上第三条链路：

- 新增 `network/SPacketMEEssentiaInvUpdate.java`：继承 `SPacketMEBaseInvUpdate`，复用基类已有的 GZIP + `IAEStack.writeToPacketGeneric` 序列化（基类本来就与类型无关）；客户端 `Handler` 把列表投给 `IGuiMonitorTerminal.postStackUpdate`，终端未打开时进 `EarlyTerminalLists.stashEssentiaStacks` 暂存。
- 新增 `client/gui/container/BaseMonitor/EssentiaSync.java`：持有 `IMEMonitor<AEEssentiaStack>`，按 aspect tag 与上次快照比对，新增/变化的发副本，消失的发 0 数量（`ItemRepo` 合并后在 `ViewItems.STORED` 下自然隐藏）；打开与重发时 `queueInventory` 全量推送。
- `ContainerComprehensiveWorkTerminal`：`setMonitor()` 里用 `AspectUtil.getEssentiaMonitor(g)` 取源质监视器（try/catch 兜底，未装 Thaumic Energistics 时静默关闭），`processItemList()` 每 tick 驱动，`addCraftingToCrafters` / `resendInventory` 补首包。
- 渲染侧无需改动：`ItemRepo` / `VirtualMESlot` / `RenderHelper.renderAEStack` 全程按 `IAEStack<?>` 工作，`AEEssentiaStack.drawInGui` 由 Thaumic Energistics 的 `RendererAEEssentiaStack` 画源质图标。
- `GuiMonitor.handleVirtualSlotClick`：非物品、非流体的行直接吞掉点击，避免把 null 物品塞进合成动作而 NPE。
- 新包的线序序号追加在 `ChannelLoader.PACKET_ORDER` 末尾（序号即线序，只能追加，否则与旧存档/旧客户端错位）。

### 2. 修改样板数量不再清空管理区搜索

根因：数量输入走的是服务端往返 —— `CPacketInventoryActionExtend(SET_PATTERN_VALUE)` → 服务端打开 AE2FC 的数量界面 → `CPacketPatternValueSet` 里 `InventoryHandler.openGui(...)` **重新打开综合终端**。客户端拿到的是全新 GUI 实例，`mgmtSearch` 为 null，`rebuildMgmtFields()` 只能取到空串，搜索词就丢了。

修复（`client/gui/GuiComprehensiveWorkTerminal.java`）：

- 静态 `mgmtSearchText` + `mgmtSearchAt`，`rememberMgmtSearch()` 在每 tick 与 `onGuiClosed()` 记录文本与时间戳；
- `rebuildMgmtFields()` 在新实例首次建框时用 `rememberedMgmtSearch()` 还原，并一次性消费（5 秒窗口 `MGMT_SEARCH_CARRY_MS`）；
- 时间戳在 `onGuiClosed()` 再打一次，玩家在数量界面停留很久也不会让窗口过期；
- 上一轮用终端物品 NBT 做身份判断失效的原因：同一轮往返里终端 NBT 的样板缓存会被重写，`areItemStacksEqual` 永远为 false，本轮改为纯时间窗口。

### 构建验证（第三轮）

```
./gradlew spotlessApply --no-configuration-cache   → BUILD SUCCESSFUL
./gradlew compileJava   --no-configuration-cache   → BUILD SUCCESSFUL (15s / 12s)
./gradlew assemble      --no-configuration-cache   → BUILD SUCCESSFUL
产物：build/libs/wtct-1.0.63-rc1.jar（2,014,787 字节）
```

### 尚未覆盖

- 源质行只能"看见、能搜到"，尚不支持从终端直接发起源质合成请求（点击为惰性）。
- 过滤网格里源质的图形仍按物品/流体槽位绘制，功能上能参与分区，视觉待后续。

## 七、打开终端崩溃（第四轮）

日志：`打开终端崩溃.txt`（768 行，16 处同一异常）。

```
java.lang.NullPointerException: Cannot invoke
  "com.asdflj.wtct.client.gui.container.BaseMonitor.EssentiaSync.setMonitor(appeng.api.storage.IMEMonitor)"
  because "this.essentiaSync" is null
at ContainerComprehensiveWorkTerminal.setMonitor(...:1284)
at ContainerMonitor.<init>(ContainerMonitor.java:105)
at BasePatternContainerMonitor.<init>(...:48)
at ContainerComprehensiveWorkTerminal.<init>(...:239)
```

根因：`ContainerMonitor` 构造函数第 105 行调用了可覆写的 `setMonitor()`，此时**子类字段初始化还没有执行**（Java 语义：父类构造先于子类字段初始化），所以 `private final EssentiaSync essentiaSync = new EssentiaSync()` 仍是 null。每次 `CPacketSwitchGuis` 打开终端都会抛一次，最后通道被打断。

修复（`ContainerComprehensiveWorkTerminal`）：

- 字段改为 `private EssentiaSync essentiaSync;`（不能再用 final + 初始化器）；
- 新增惰性访问器 `essentiaSync()`：首次调用时创建，创建失败（未装 Thaumic Energistics，类链接失败）则记录 `essentiaSyncUnavailable` 并永久返回 null；
- `setMonitor()` / `processItemList()` / `addCraftingToCrafters()` / `resendInventory()` 四处调用点全部改为先取局部变量再判空，避免任何构造顺序或可选依赖导致的 NPE。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,015,270 字节）。

## 八、含源质样板无法识别

现象：编码区放入源质后编码出的样板，机器/终端无法识别。

原因：配方与 NEI 给出的"源质"是一个**展示物品**（AspectRecipeIndex 的 `ItemAspect`，或神秘时代 NEI 插件里的同款），它把 aspect 存在 NBT 里，本身并不是任何机器能产出的物品。新 GTNH 的 AE2 把它建模为独立堆栈类型 `AEEssentiaStack`（`StackType:"essentia"`），就像流体用 `StackType:"fluid"` 一样。上一轮的编码逻辑只做了"流体→流体堆栈"的转换，源质展示物品直接落到 `AEItemStack.create(stack)`，于是样板里写的是"要一个源质展示物品"，而不是"要这么多源质"——这就是"无法识别"。

修复：

- `util/AspectUtil.java`：新增 `essentiaFromDisplayItem(ItemStack)`（`AEEssentiaStackType.ESSENTIA_STACK_TYPE.convertStackFromItem`）与 `essentiaEntry(ItemStack, long)`（顺带写入数量）；
- `util/PatternScaling.java`：
  - `patternEntry(...)` 在流体分支之后增加源质分支：当 `fluidAsStack` 为真（处理样板，与 GTNH 自带 ME 样板终端写出的形状一致）时，把源质展示物品写成 `StackType:"essentia"` 的真实堆栈，数量取格子显示的数量；整段包在 `try/catch (Throwable)` 里，未装 Thaumic Energistics 时自动退化为原行为；
  - `applyToPattern(...)` 的"不重写"保护扩展到源质：`carriesEssentia(...)` 与 `carriesFluid(...)` 同等对待。原因是格子显示的是展示物品、样板里存的是源质堆栈，重写会把"16 源质"变成"要一个源质物品"，与流体那次损坏同类；
  - 读取侧本来就走 `Platform.readStackNBT` + `getItemStackForNEI()`，所以源质样板载回编辑区会显示为带数量的 aspect 物品，无需改动。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,016,113 字节）。

## 九、自动上传把样板塞进被隐藏的接口

现象：编码后自动上传（或"上传到匹配供应器"）时，样板进了样板管理区里根本看不到的接口，于是看起来"上传丢了"。

原因：AE2 的接口终端有一枚"在接口终端中显示"开关（`Settings.INTERFACE_TERMINAL`，对应 `InvTracker.shouldDisplay`）。关掉之后接口**仍然被跟踪**（网络还要靠它找样板），只是终端不列出来。WTCT 的容器侧 `providerIds()` / `findProviderByName()` 和客户端 `uploadProviders()` / `firstProviderWithRoomClient()` 都是直接遍历原始列表，没有读这个标志，所以"第一个有空位的接口"很可能是被隐藏的那个。管理区列表本身（`orderedMgmtProviders`）是过滤过的，所以列表里看不到它。

修复：

- `util/Ae2Reflect.java`：新增 `isTrackedVisible(Object tracker)`，读取 `InvTracker.shouldDisplay`（用已缓存的 Field，缺失时运行时再按字段名找一次）；读不到或该 AE2 版本没有这枚开关时一律返回 true，即"没隐藏任何东西"，不会反过来把正常接口挡掉；
- `client/gui/container/ContainerComprehensiveWorkTerminal.java`：
  - `providerIds()` 只返回可见接口；
  - `findProviderByName()` 跳过隐藏接口；
  - `uploadEditSlotPattern()` 的"首选接口"判定增加 `providerVisible(preferredId)`，新增私有 `providerVisible(long)`；
- `client/gui/GuiComprehensiveWorkTerminal.java`：`uploadProviders()`（按名字/机族挑接口）与 `firstProviderWithRoomClient()`、`providerHasRoomClient()` 都要求 `provider.terminalVisible`。

效果：自动上传只在管理区能看到的接口里挑目标；若可见接口全满，就按设置走"存回样板缓存区"的兜底，而不是偷偷塞进隐藏接口。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,017,093 字节）。

## 十、从 NEI 拉取样板时丢失编程电路

现象：在综合工作站里对着 GT 配方按 NEI 的"+"拉取样板，其余材料都进了编码区，唯独编程电路那一格是空的。

原因：GT 把"不被消耗"的原料以**数量 0** 的堆栈形式交给 NEI（`GTUtility.copyAmount(0, ...)`：编程电路、激光蚀刻机的透镜、各种模具）。`NEIUtils.clearNull(...)`（以及"合并材料"开着时走的 `NEIUtils.compress(...)`）把 `stackSize == 0` 的堆栈当作"空堆栈"直接丢掉，于是电路连包都没进——`PatternTerminalLoader` 里那条 `NEIUtils.clearNull(inputs)` 正好在写编码区之前执行。

双接口终端那边看起来没坏，是因为它要求手持编程工具箱（proghatches），`PHUtil.transfer(...)` 会先把数量 0 的堆栈换成工具箱自己的编程电路物品（数量 1），所以轮不到 `clearNull` 丢它；综合工作站里手拿的是终端本体，这条路不生效。

修复（`nei/NEIUtils.java`）：新增私有 `fillZeroSized(OrderStack<?>)`——数量 ≤ 0 的 `ItemStack` 统一抬到 1，并在 `clearNull` 里用 `peek` 应用（不再过滤），`compress` 里原来 `if (stackSize == 0) continue;` 也改成同一次抬升。样板里"要 0 个某物"本来就没法表达，抬到 1 也正是玩家手搓样板时会放的数量；GT 机器对电路这类不被消耗的原料不看数量。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,017,129 字节）。

## 十一、"编程器电路始终为一"失效，翻倍把电路也翻倍了

现象：打开"编程器电路始终为一"开关后按 ×2 / ⇄ 之类，样板里的编程电路仍旧跟着一起翻倍。

原因：这个开关唯一依赖的判定是 `PHUtil.isProgrammingCircuit(ItemStack)`，而它只认 proghatches 自己的 `ItemProgrammingCircuit`。上一轮修好"从 NEI 拉取丢失编程电路"之后，样板的电路格才真正有东西——但玩家没手持编程工具箱时，NEI 交过来的是 **GT 自己的编程电路** `gregtech.common.items.ItemIntegratedCircuit`（`ItemList.Circuit_Integrated`），这个判定认不出来，`scalable(...)` 就返回 true，"始终为一"对它是空的。换句话说：以前电路格是空的，开关没东西可保护，所以看不出问题；电路补回来之后问题才暴露。

修复（`util/PHUtil.java`）：`isProgrammingCircuit(...)` 改为两种电路都认——先在 `Mods.PROGRAMMABLE_HATCHES.isModLoaded()` 下判断 `ItemProgrammingCircuit`，再在 `Mods.GREGTECH.isModLoaded()` 下判断 `ItemProgrammingCircuit` 之外的 `ItemIntegratedCircuit`。两处 `instanceof` 都排在各自的 `isModLoaded()` 之后，未装对应 mod 的整合包不会在这里触发 `NoClassDefFoundError`（与文件里原有的 toolbox 判定同一写法）。

因为编码区按键（`doubleStacks` 走 `scalableCells`）、倍率键（`multiplyStacks` → `PatternScaling.apply`）、缓存区重写（`applyToPattern`）三条路径最终都经过 `scalable(...)`，改这一处即可让 ×2/×3/×5、⇄、=1 的 GCD、"存回缓存区"的批量重写统一不再动电路格。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,017,197 字节）。

## 十二、装 ECO 后综合工作终端的合成状态页没有 ECO 面板

现象：网络里存在 ECO（NeoEcoAE）的计算子系统时，从 AE2 自己的终端打开"合成任务/合成 CPU"页面能看到 ECO 的状态面板，但从综合工作终端打开同样的页面看不到。

原因：ECO 的 `ECOCraftingStatusClient#open`（`GuiOpenEvent` 处理器）用的是**精确类判定**：

```java
if (event.gui == null || event.gui.getClass() != GuiCraftingStatus.class) return;
```

注释写得很清楚："Only AE2's own page; subclasses from other mods keep their screens."。而本模组为了让"返回终端"标签能回到综合工作终端，之前是用两个子类（`com.asdflj.wtct.client.gui.GuiCraftingStatus`、`GuiComprehensiveCraftingStatus`）打开这个页面的，于是 ECO 的处理器直接跳过，页面永远是本模组自己的那份，ECO 面板自然不出现。

服务端这半其实一直是好的：ECO 的 `ECOCraftingStatusTracker` 遍历的是所有打开着的 `ContainerCraftingStatus`（`instanceof` 判定，不挑子类），本模组用的正是 AE2 原版容器，所以状态包一直在发，只是客户端没换页面。

修复：

- `inventory/gui/GuiType.java`：`CRAFTING_STATUS`、`CRAFTING_STATUS_ITEM`、`CRAFTING_STATUS_ITEM_COMPREHENSIVE` 三个类型的客户端 GUI 全部改为直接 `new appeng.client.gui.implementations.GuiCraftingStatus(...)`，让 ECO 的精确类判定能够命中并换成它自己的页面（不需要对 ECO 有任何编译期依赖）。
- 删除 `client/gui/GuiCraftingStatus.java` 与 `client/gui/GuiComprehensiveCraftingStatus.java`。
- 新增 `coremod/mixin/ae/MixinGuiCraftingStatus.java`（`@Mixin(GuiCraftingStatus.class)`，注册进 `Mixins.AE_CLIENT`，由 `AE2LatePlugin` 在运行时注入），把原来两个子类里的两件事搬到基类上：
  1. `actionPerformed` 的 HEAD：抢在 AE2 之前处理"返回终端"标签——容器目标是 `WirelessDualInterfaceTerminalInventory` 时用 `InventoryHandler.switchGui(GuiType.COMPREHENSIVE_WORK_TERMINAL)` 回到综合工作终端（AE2 默认发的 `PacketSwitchGuis()` 走 `PrimaryGui`，定位不到物品/Baubles 里的终端）；其它宿主（例如装在方块上的终端）继续走 AE2 原逻辑。
  2. `drawScreen` 的 HEAD/RETURN：用 `CraftingStatusTabBlend` 包住整屏绘制，压住角标贴图的透明边被写成黑框的问题。
- `client/gui/CraftingStatusTabBlend.java`：类与两个方法改为 `public`，供 mixin 调用。

这样一来：ECO 存在时页面会被它换成 `ECOCraftingStatusGui`，而它是 `extends GuiCraftingStatus`，上面那个 mixin 的注入对它的实例同样生效——ECO 面板里"返回终端"标签一样能回到综合工作终端；ECO 不存在时，打开的就是 AE2 原生页面 + 这两处注入，行为与删掉子类之前一致。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,016,496 字节）。
## 十三、ME 磁铁卡增加快捷键切换吸引方式

需求：磁铁卡的吸引方式（关闭 / 吸到身上 / 吸进 ME 网络）之前只能在磁铁卡界面里点按钮切换，想快速开关得先打开终端、再进磁铁卡页，战斗中不现实。

做法：

- 新增快捷键 `wtct.key.cycle_magnet_mode`，默认未绑定（与已有的两个终端快捷键一样留空），在"按键设置 → AE2 Thing"分类里自行指定。
- 模式存在**终端**的 NBT 上（`Constants.MAGNET_MODE_KEY`，`CardTicker.tickMagnet` 就从这个键读），不在卡上，所以按键找的是"身上装着磁铁卡的那台终端"：`util/Util.java` 新增 `findMagnetTerminal(EntityPlayer)`，先扫主物品栏再扫 Baubles，返回与 `findComprehensiveWorkTerminal` 同一套 bridge 编码坐标。
- `loader/KeybindLoader.java`：沿用已有的边沿触发写法（1.7.10 里按住期间按键持续为按下，必须只在按下瞬间触发一次）。触发后本地先翻自己那份 NBT 并在快捷栏上方显示一行提示，再发 `CPacketCycleMagnetMode`。本地先翻是为了让 tooltip 与提示立刻更新——饰品栏槽位不随容器自动同步，等服务器回包会有明显延迟，磁铁卡界面自己的按钮也是同样处理；服务端从同一个旧值往后翻，两边结果一致，不会差两步。
- 新增 `network/CPacketCycleMagnetMode.java`：携带 bridge 坐标（`-1` 表示让服务端自己找）。服务端校验该槽位确实是装着磁铁卡的终端后翻模式，再用 `BaublesUtil.setStackFromBridge(...)` 写回——主物品栏的槽位由玩家自己的容器每 tick 重同步，饰品栏则由该方法显式推给客户端。
- 语言键：`wtct.key.cycle_magnet_mode`、`wtct.magnet.cycle.hint`（快捷栏提示，含 `%s` 模式名），模式文案复用已有的 `wtct.card.magnet.mode.off / inv / me`。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,027,778 字节）。
## 十四、磁铁卡快捷键首次使用崩溃：NBT 类型未校验

现象：第一次按磁铁卡快捷键，客户端直接崩到桌面，日志 `磁铁卡快捷键崩溃.txt`。

日志给出的调用链：

```
java.lang.ClassCastException  (-- Corrupt NBT tag --
                              Tag type found: BYTE
                              Tag type expected: COMPOUND
                              Tag name: upgrades)
  NBTTagCompound.func_74775_l(NBTTagCompound.java:261)
  ItemWcwtUpgradeCard.upgradesTag(:384) -> installedCount(:389) -> isInstalled(:404)
  Util.hasMagnetCard(:228) -> Util.findMagnetTerminal(:217)
  KeybindLoader.cycleMagnetMode(:136) -> KeybindLoader.onKeyInput(:119)
```

原因：快捷键要判断"身上哪台终端装了磁铁卡"，于是把主物品栏（以及 Baubles）里**每一个** ItemStack 都丢给 `ItemWcwtUpgradeCard.isInstalled`。而 `upgradesTag` 只检查了 `hasKey("upgrades")` 就直接 `getCompoundTag("upgrades")`——原版这个方法是**强制类型转换**，不是"类型对才返回"。`upgrades` 是个太通用的名字，别的模组的物品在自己的 NBT 里就放着同名的 BYTE 标签，扫到它时转换失败，异常从客户端按键回调一路抛到 `FMLCommonHandler`，游戏直接退出。换句话说：崩溃不是快捷键写错了，而是"读别人物品的 NBT 时没验证类型"，快捷键只是第一个会去读所有物品的调用者。

修复（`common/item/card/ItemWcwtUpgradeCard.java`）：

- `upgradesTag` 改为：没有 NBT 直接返回 null；用 `getTag(Constants.UPGRADES)` 取出原始标签，`instanceof NBTTagCompound` 成立才返回，否则当作"没有卡"并**原样保留**对方的标签（不再用 `Platform.openNbtData`，因此也不会在别人的物品上凭空创建 NBT）。
- 新增 `cardTag(upgrades, index)`，对 `#0..5` 每个槽位做同样的类型校验，取代原来直接 `getCompoundTag("#" + i)` 的写法；`installedCount`、`firstInstalled`、`energyPowerMultiplier` 全部改走它——顺带修掉一个隐患：`Platform.loadItemStackFromNBT(null)` 会 NPE，以前全靠"`getCompoundTag` 永远不返回 null"这个错误假设挡着。
- `inventory/WcwtUpgradesInventory.hasSingularity` 是同一类读法且被无线范围检查（`api/WirelessObject`）用非本模组的物品调用，一并按同样方式加固。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,028,369 字节）。
## 十五、磁铁卡切换提示改为快捷栏文字

反馈：右上角的通知不适合"快速连按切换"——通知卡是排队加动画的，连按时看到的是几秒前排进去的那条，等于有延迟。

做法：

- 新增 `client/render/HotbarHint.java`：`show(String)` 直接调用 `Minecraft.getMinecraft().ingameGUI.func_110326_a(text, false)`，也就是原版唱片机那行"正在播放"用的绘制位——快捷栏正上方、居中、带淡出；每次调用都会把文本和计时一起重置，所以连按时屏幕上永远是最后一次按下的结果，没有队列、没有延迟。
- `loader/KeybindLoader.java`：`cycleMagnetMode` 里的 `WtctAPI.addNotification(...)` 换成 `HotbarHint.show(I18n.format("wtct.magnet.cycle.hint", I18n.format(modeTranslationKey(mode))))`，本地翻转 NBT、发 `CPacketCycleMagnetMode` 的顺序不变。
- 语言键：`wtct.magnet.cycle.hint=磁铁卡吸引方式：%s`（中文）/ `Magnet card mode: %s`（英文），替换掉不再使用的 `wtct.magnet.cycle.title`。

构建：`spotlessApply` → `compileJava` → `assemble` 全部成功，`build/libs/wtct-1.0.63-rc1.jar`（2,029,101 字节）。
## 十六、打开终端耗时诊断计时器（2026-11-18）

### 背景
回退"延迟创建样板提供者"那一版后，用户实测打开终端仍有 0.2–0.5 s 卡顿，说明接口快照不是主因。为避免继续凭猜测改代码，先加一个**默认关闭**的分段计时器，把打开过程在服务端和客户端各自拆开记录。

### 开关
`config/wtct.cfg` → 分类 `[wtct]` → `Terminal open timing = true`（默认 false，改完需要重启游戏）。
关闭时 `OpenTimer.begin()` 直接返回 0、`end()` 立即返回，不测时也不写日志，正常游玩零开销。

### 计时点
| 侧 | 键 | 含义 |
|---|---|---|
| 客户端 | `client.ctor` / `client.provider` | 客户端容器构造（含 AE2 接口子容器） |
| 客户端 | `client.guiCtor` | GUI 对象构造（不含容器） |
| 客户端 | `client.initGui` | `initGui()`：控件、rows×18 虚拟槽、样板缓存 NBT 解析、`repo.updateView()` |
| 客户端 | `client.firstFrame` | 第一次 `drawScreen` |
| 服务端 | `server.ctor` / `server.provider` | 服务端容器构造，接口子容器单独计时 |
| 服务端 | `server.firstSync` | 第一次 `detectAndSendChanges()`：`server.providers`（接口行差分包）+ `server.monitors`（物品/流体/源质列表打包 GZIP） |

每条日志还带 `@<毫秒时间戳>`，单机下客户端与服务端同 JVM、同一时钟，可直接相减得到"发出请求 → 服务端开始构造"的实际往返；客户端行额外带 `req@` 与 `rtt=`。

### 日志形态
```
[wtct-perf] client.ctor: provider=1.2ms ctor=8.4ms @1731925123456 req@1731925123420 rtt=36ms
[wtct-perf] server.ctor: provider=12.3ms ctor=45.6ms @1731925123440
[wtct-perf] server.firstSync: providers=3.1ms monitors=22.7ms firstSync=26.0ms @1731925123468
[wtct-perf] client.initGui: guiCtor=2.0ms initGui=18.5ms @1731925123490 req@1731925123420 rtt=70ms
[wtct-perf] client.firstFrame: firstFrame=6.2ms @1731925123502
```

### 判读
- `rtt` 大 → 主要是等服务端/等首包，考虑乐观打开（先显示界面，窗口包到达再绑定）。
- `server.ctor` / `server.firstSync` 大 → 减首包：列表分批、监视器列表延后、接口快照延后。
- `client.initGui` / `client.firstFrame` 大 → 客户端减负：`repo.updateView()` 改增量、控件与虚拟槽复用、缓存槽 NBT 解析延后。

### 构建
`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`wtct-1.0.63-rc1.jar` = 2,032,766 字节（20:14:58）。
## 十七、方案1（乐观打开）+ initGui 子计时 + 空搜索上传进缓存

### 1. 乐观打开（Config: Optimistic terminal open，默认开）

计时数据显示一次 349ms 的打开里约 128ms 是"等服务器窗口包"（85ms 客户端→服务器 + 43ms 服务器→客户端），另有 121ms 等渲染循环。把这部分等待与界面构建重叠：

- 新增 `client/gui/OptimisticTerminalOpen.java`：在客户端按与服务器相同的链路（`IItemInventory.getInventory(stack, world, coordinate, 0, 0, player)`）构造一个本地容器与 `GuiComprehensiveWorkTerminal`，立即 `displayGuiScreen`。
- 三条入口都接上：快捷键（`WtctAPI.openComprehensiveWorkTerminal`）、终端菜单（`OpenTerminalEvent.openTerminal`，仅当目标是综合工作终端）、物品右键（`ItemBaseWirelessTerminal.onItemRightClick` 的客户端分支，经 `CommonProxy.tryOptimisticTerminalOpen` 转发，专用服务器上是空实现）。
- 临时容器的 windowId 服务器并不认识，因此界面在真正的容器到达前吞掉鼠标与按键（Esc 除外），并由 `updateScreen()` 在容器替换后解除；4 秒仍未到达（没电、未绑定、服务器拒绝）则自动关闭界面，交回原来的打开路径报错。
- 开关关闭时行为与旧版完全一致。

### 2. initGui 子计时

`client.initGui` 拆成 `client.base`（AE2 基类 initGui）、`client.slots`（行数/虚拟槽注册/`repo.updateView`）、`client.widgets`（大量自绘按钮）、`client.layout`（样板缓存布局 + 升级面板 + 卡片按钮）。日志行同时带 `client.guiCtor`，用于区分"构造"与"布局"。

### 3. 空搜索上传

`ContainerComprehensiveWorkTerminal.uploadEditSlotPattern`：搜索文案为空且没有指定接口时，直接 `stashEditSlotPatternToCache()` 放进样板缓存区，不再 `findProviderWithRoom()` 猜第一个接口。多方块结构取材料走 NEI 传输，不会带搜索词，正是以前"样板跑到第一个接口里"的来源；非空搜索但无匹配时仍沿用原来的回退。

### 构建

`compileJava` → `spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,036,037 字节（2026-11-18 20:42）。
## 十八、多方块取材料后上传的落点

### 现象

持续复现方式：先点一个普通配方（搜索框被"上一次的配方名"填满），再用多方块结构取材料并上传样板——此时界面没有新的配方名可拉取，搜索框仍是上一次的配方名，`uploadEditSlotPattern` 读到非空搜索，于是按老规则找接口，样板进了第一个接口。

### 原因

`noteTransferredRecipe(crafting, recipeId, localizedName)` 在 `recipeId == null`（多方块预览不给 NEI 标识符）时直接 `return`，搜索框与 `lastProviderSearchKey` 都保持上一次的值。

### 修复

1. 记住"框里的字是不是玩家自己敲的"：`trackMgmtSearchEditing()` 每 tick 比对 `mgmtSearchSeen`，只有玩家输入才置 `mgmtSearchUserEdited`；终端自己写框（`applyMgmtSearchText`）会重置该标记。
2. `noteTransferredRecipe` 的"叫不出名字"分支改为 `clearInheritedUploadSearch()`——框里的字若是继承来的就清空，同时清 `lastProviderSearchKey` / `lastUploadSearchKey`，不再让旧词参与匹配；玩家手敲的字不动。
3. `sendEncode()`：搜索解析为空时改为 `sendEncodePacket(true, -1, "")`，由容器那侧（上一轮加的"空搜索进缓存"分支）把样板放进样板缓存区，而不是留在编辑槽或猜接口。

构建：`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,036,739 字节（2026-11-18 21:02）。
## 十九、乐观打开时的"空白列表"

### 现象

开启乐观打开后，终端界面立刻出现，但物品列表空着，一百多毫秒后内容才填进来。

### 原因

服务端在 `InventoryHandler.getServerGuiElement` 里就把物品/流体/源质三份列表推了出去——比窗口包还早。客户端收到时屏幕上还是乐观界面，数据进了乐观界面的仓库；随后窗口包到达，原版把乐观界面换成一个全新的 `GuiComprehensiveWorkTerminal`，新界面的仓库是空的，于是一片空白，要等下一次推送才有内容。

### 修复

1. 三个列表包的处理器在"屏幕上已经是终端"时，除了交给当前界面，还把列表停进 `EarlyTerminalLists`；真界面的 `initGui` 会应用它（2 秒内有效），换界面时数据不再丢。
2. `EarlyTerminalLists.remember(...)`：终端关闭时记下它最后显示的完整列表；乐观界面 `initGui` 先把这份缓存画出来，服务端的真数据随后覆盖。只对乐观界面生效，而且它会被真界面整体替换，旧内容不会残留。
3. 兜底：真容器绑定那一刻客户端再向服务器要一份完整列表（`Terminal.ResyncInventory` → `ContainerComprehensiveWorkTerminal.resendInventory`，顺带带出接口与源质列表）。

构建：`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,037,615 字节（2026-11-18 21:11）。
## 二十、"一开就慢"其实是四次排序：批量应用与免排序首帧

### 现象

上一轮加了列表缓存之后，乐观打开的第一帧终于有内容了，但打开反而更慢：客户端第一帧从 20–27ms 推迟到 43–98ms，稳定成 363–518ms（之前是 216–278ms）。

### 原因

`EarlyTerminalLists.applyTo` 对物品、流体、源质三份停靠列表各调一次 `postStackUpdate`，而 `GuiComprehensiveWorkTerminal.postStackUpdate` 每调一次都会执行一遍 `repo.updateView()`，也就是把整个网络的物品列表全量排序一次（实测约 17ms）。

- 乐观界面应用 1 份缓存 → 1 次排序（12–27ms）；
- 真界面应用 3 份停靠列表 → 3 次排序（47–62ms）。

一次打开合计 4 次排序、约 70–100ms CPU，全部压在"屏幕刚出来"的那几百毫秒里，比原来的空白闪烁更伤。

### 修复

1. `IGuiMonitor.postStackUpdateBatched(...)`：三份列表先合并成一个 List，再在 `GuiComprehensiveWorkTerminal` 里一次性 `postUpdate` 全部条目、只调一次 `updateView()`。真界面 `initGui` 从 56–75ms 降到 ~30ms。
2. `IGuiMonitor.restoreCachedSnapshot(...)` + `AdvItemRepo.snapshotView()/adoptSnapshot(...)`：终端关闭时除了记下内容，还记下"当前显示顺序"（view）；乐观界面把这份顺序直接装进仓库，不排序。因为乐观界面只活到服务器回包（约 150ms），它要的只是"别空着"，顺序本来就是上次排好的。乐观界面 `initGui` 从 19–35ms 降到几毫秒。
3. 删掉 `Terminal.ResyncInventory` 重发：停靠列表的交接已经覆盖了数据丢失，重发只是多推一份完整列表、多触发一次客户端排序。`tickAwaitingContainer` 现在只做状态切换。

### 其余发现（未改）

- `MixinRecipeHandlerRef` 的 `getOverlayHandler` 注入里留了 INFO 级日志，NEI 每次查找打一行，占客户端日志 73%，需要降级或删除。
- `GuiMonitor.reInitalize()` 在点击终端样式/搜索模式按钮时整屏重建，每次 23–47ms，属于既有行为。

构建：`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,039,219 字节（2026-11-18 21:31）。
## 二十一、复用乐观界面：绑定真实容器而不是重建界面

### 现象

上一轮把"四次排序"压到一次后，真界面 `initGui` 降到 39–57ms，但从真 `initGui` 结束到第一帧之间又空出 139–279ms（中位 165ms），而此时数据其实已经全部到齐。也就是说剩下的时间是"把整个界面推倒重建 + 等下一帧"。

### 做法

1. `InventoryHandler.getClientGuiElement` 仍按原路径构造新界面，但交给 `OptimisticTerminalOpen.handOver(...)`：如果当前屏幕正是一个"还在等容器"的 `GuiComprehensiveWorkTerminal`，就把新容器的 `inventorySlots` 换到它身上（`rebindContainer`），并把旧屏幕对象返回给 FML，新建的那个界面直接丢弃。
2. 两个容器由同一个工厂、同一组参数构造，槽位、终端物品与设置完全一致，只有 windowId 不同；界面本身（控件、滚动位置、物品仓库）全部保留，所以不需要 `initGui`。
3. 新增 `coremod/mixin/vanilla/MixinMinecraft`，在 `Minecraft.displayGuiScreen` 的 HEAD 处取消这次调用（否则会 `onGuiClosed` + `initGui` 重建），由 `finishHandOver()` 完成交接：清除等待标志、把停靠的物品/流体/源质三份列表一次性灌入（仍然只排一次序）、恢复输入。因为取消掉了 Forge 在 `displayGuiScreen` 里做的 `thePlayer.openContainer` 赋值，这里手动补上；随后 FML 写 windowId 时正好落在同一个容器上。
4. 兜底：若 3 tick 内交接没生效（或混入在某些分支下没跑到），`tickAwaitingContainer` 会调用 `finishHandOver` 正常收尾；若 4 秒仍没有容器（没电、未绑定、服务器拒绝），关闭界面并回到原来的打开路径报错。混入未加载时行为与旧版一致。

### 新增计时点

- `client.apply`：`EarlyTerminalLists.applyTo` 的耗时（含排序）。
- `client.rebind`：换容器。
- `client.handOver`：整个交接（applyTo + 滚动条 + 标记清理）。
- `client.bind`：从按键到界面可用的总时长；`client.awaitTicks`：中途等了几个 tick。
- `client.gap` / `client.gapTicks` / `client.gapTick`：真 `initGui` 结束到第一帧的间隔、中间经过的 tick 数、单个 tick 的最大耗时——用来判断这 165ms 是"帧在等 tick"还是"重活压在某一帧"。

### 构建

`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,042,903 字节（2026-11-18 21:58）。混入 refmap 已确认：`displayGuiScreen` → `func_147108_a`。
## 二十二、客户端启动崩溃：Minecraft 的混入必须用 EARLY 阶段

### 现象

装上上一版后客户端启动直接崩溃，服务端正常启动：`MixinTargetAlreadyLoadedException: Critical problem: mixins.wtct.late.json:vanilla.MixinMinecraft from mod wtct target net.minecraft.client.Minecraft was loaded too early`，由 `MixinProcessor.prepareConfigs` 抛在 `FMLClientHandler.beginMinecraftLoading` 里。

### 原因

`net.minecraft.client.Minecraft` 远在模组构造之前就被加载（崩溃栈里 `Minecraft.func_71384_a` 正在执行时才去加载模组），而 `mixins.wtct.late.json` 是等模组都构造好、AE2 的 late 插件注册时才 prepare 的，那时目标类早已加载完毕。服务端没有 `Minecraft` 类，这条 client-only 混入不参与，所以只有客户端崩。

### 修复

1. `Mixins.VANILLA_CLIENT` 改成 `Phase.EARLY`，跟着 `mixins.wtct.early.json` 由 `WtctCore`（`IEarlyMixinLoader`）在启动早期注册，赶在 `Minecraft` 被加载之前。
2. 早期混入不能直接引用模组类：`Minecraft` 加载时那些引用会被提前解析，可能把还没初始化好的模组静态状态拖进来。新增 `coremod/HandOver` 作一次性交接槽（只依赖 `Object` 与 `Runnable`）：`arm(screen, runnable)`、`matches(screen)`、`fire()`、`disarm(screen)`。
3. `OptimisticTerminalOpen.handOver` 换完容器后 `HandOver.arm(standIn, () -> { standIn.finishHandOver(); mc.thePlayer.openContainer = standIn.inventorySlots; })`；`MixinMinecraft` 里只做 `matches` → `fire()` → 若是替换当前屏幕就取消掉这次 `displayGuiScreen`。
4. `GuiComprehensiveWorkTerminal.onGuiClosed` 加 `HandOver.disarm(this)`：屏幕在容器到达前就被关掉（服务器拒绝、超时）时清空槽位，免得残留的槽位误吞后来的 `displayGuiScreen`。
5. 兜底不变：3 tick 内混入没跑，`tickAwaitingContainer` 自己调 `finishHandOver`；4 秒还没容器就关屏走原打开路径报错。

构建：`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,043,958 字节（2026-11-18 22:15）。refmap 未变：`displayGuiScreen` → `func_147108_a`。
### 补充：交接守卫写反了

上一版混入确实没崩，但第 4 项一次都没生效：日志里每次打开仍是两轮 `client.ctor`+`client.initGui`，而 `client.bind` 在 rtt 55–71 就打了、`handOver=0.0`，服务器容器却要到 rtt 135–281 才到。

原因在 `tickAwaitingContainer` 的判定：写成「`inventorySlots instanceof ContainerComprehensiveWorkTerminal` 就算容器到了」，可乐观界面本来就是用客户端自己 `new ContainerComprehensiveWorkTerminal(...)` 构造的（GUI 构造函数里建的），这个条件在第一个 tick 就成立 → 立刻 `finishHandOver()`，清掉 `awaitingContainer`；等 FML 真正递容器时 `OptimisticTerminalOpen.handOver` 看到 `isAwaitingContainer()==false`，返回新界面，走重建。

改法：判定只看 `rebindPending`——它只在 `OptimisticTerminalOpen.handOver` 换过容器后才为真。没有 rebound 时只保留 4 秒超时关屏（服务器拒绝/没电/没绑定时不要留一个吃点击的界面）。类判定彻底删掉。

构建：`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,043,950 字节（2026-11-18 22:30）。
**已回退**：把守卫改成「只看 `rebindPending`」之后界面完全错乱，用户要求退回上一版。原因推断：这个界面在 `initGui` 里把大量控件、虚拟槽（`getMeSlots()` 里的 `VirtualMEMonitorableSlot`）和按钮都绑在**当时的容器对象**上（很多回调直接引用 `monitorableContainer` 的字段/方法）。只把 `inventorySlots`／`monitorableContainer` 换成服务器的容器，控件仍指向旧的客户端容器，绘制与点击各用一半数据，于是错乱。结论：这个界面不具备「只换容器不重建」的安全前提，第 4 项按当前形态不可行；代码已按原样恢复（守卫回到 `instanceof` 版本），`build/libs/wtct-1.0.63-rc1.jar` = 2,043,958 字节（2026-11-18 22:37，与 22:15 那版逐字节同尺寸）。
## 二十三、打开终端第一帧按名称排序，停顿后才变成玩家顺序

### 现象

打开综合工作终端时，第一帧列表按 AE 默认的「名称」（NAME）顺序排列，过一小会儿（一次服务器 tick 之后）才跳回玩家平时用的「模组」（MOD）顺序，看起来像列表自己重排了一次。

### 原因

客户端容器的 `clientCM` 是在 `ContainerMonitor` 构造函数里用默认值新建的：

```java
this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
```

真正的值要等服务器在 `ContainerMonitor.detectAndSendChanges()` 里把 `serverCM`（终端宿主的设置）与 `clientCM` 比对，发现不同才 `new PacketValueConfig(set.name(), sideLocal.name())` 推给客户端。于是：

1. 客户端 `initGui`（或乐观界面的 `postStackUpdateBatched`）做第一次排序时，`configSrc.getSetting(SortBy)` 还是 NAME；
2. 窗口打开后的第一个服务器 tick 才把 MOD 推过来，`GuiMonitor.updateSetting` 再 `repo.updateView()` 一次，顺序才变。

`GuiMonitor.getSortBy()/getSortDir()` 读的就是容器 `getConfigManager()`，所以问题出在客户端镜像的初值，而不是排序实现。

### 修复

终端设置本来就写在终端物品的 NBT 里：`WirelessDualInterfaceTerminalInventory.getConfigManager()` 的 lambda 每次 `putSetting` 都 `manager.writeToNBT(data)`，而这份 NBT 会随物品槽同步到客户端。所以客户端容器构造时先把这份 NBT 读进 `clientCM`，第一帧就是玩家上次用的顺序：

- `ContainerMonitor` 构造函数加客户端分支 `readClientSettings(monitorable)`；
- `readClientSettings` 只接受 `WirelessDualInterfaceTerminalInventory`（部件/方块宿主的设置留存在服务端，客户端读到的是旧值），取 `stack.getTagCompound()` 交给 `clientCM.readFromNBT(...)`，并用 try/catch 包住，坏标签不会挡住开屏；
- 读的时候 `gui` 还没 `setGui`，`putSetting` 的回调是空转，既不会给服务器发包，也不会提前重排；
- 服务器第一次 `detectAndSendChanges` 比对时两边已经一致，连 `PacketValueConfig` 都不会发，顺带省掉一次多余的全量重排。

乐观界面、真实容器的 `clientCM` 都走同一个构造函数，所以三条路径（乐观第一帧、乐观换真实容器、正常打开）都直接是玩家设置的顺序。

构建：`spotlessApply` → `compileJava` → `assemble` 全部 BUILD SUCCESSFUL，`build/libs/wtct-1.0.63-rc1.jar` = 2,044,607 字节（2026-11-18 22:42）。
## 二十四、按下 Shift 点击编码键取回上一次上传 + 清空搜索栏被回填

### 需求

综合终端右上角的"编写样板"按钮同时控制着主动上传：没开自动上传时只编码，开了则编码+上传。现在按住 Shift 点击同一个按钮，要把**上一次上传的那个样板**取回来（放回编码位置；编码位置被占则放回样板缓存区），并且按住 Shift 时按钮的箭头朝上，提示这次点击不是编码。

同时报出一个 bug：获取样板后，样板管理的映射/搜索栏会被"锁住"——点清空，文字消失一瞬间又回来了，上传仍按上一次获取的内容走。

### 取回上一次上传

历史只存在容器实例上，终端一关就没了（重开是新的 ContainerComprehensiveWorkTerminal），符合"只放内存、关闭清空"：

- ContainerComprehensiveWorkTerminal 新增 UPLOAD_UNDO_LIMIT = 8、UploadedPattern{providerId, slot, pattern} 队列 uploadHistory（ArrayDeque，超出上限丢最旧的）；
- uploadEditSlotPattern 成功写入接口槽后立刻 
rememberUpload(target, free, pattern)；
- undoLastUpload()：
  1. 取队首；providerPatterns(id) == null（接口被拆/区块卸载）→ 丢弃并提示"接口已无法访问"；
  2. 槽内为空或 !ItemStack.areItemStacksEqual(当前, 记录) → 丢弃并提示"槽位已不是上传的样板"；
  3. 编码位置空 → 放回编码位置；否则 findEmptyPatternCacheSlot() 找一个空缓存槽，缓存满则**保留记录**并提示"缓存已满"；
  4. 清接口槽、写回目标槽、
refreshProvider + pushProviderSlot(id, slot, null)（AE2 自己不会同步样板内容）+ detectAndSendChanges()。
- 客户端 sendEncode() 开头判断 isShiftKeyDown()：按住 Shift 时不编码、不解析搜索，只发 PatternManagement.UndoUpload，所以取回永远不会在编码位置留下一个新样板。
- 箭头：GuiEncodeArrowButton.setUpward(...)，drawScreen 每帧按 isShiftKeyDown() 设置，tooltip 同步换成取回文案。图标用绕自身中心（整数像素，x+8 / iconY+8）的 180° 旋转翻成向上；不能直接 glScalef(1,-1,1)，镜像会把四边形绕序反过来、被 GUI 的背面剔除整块裁掉（曾经整个箭头消失）。180° 旋转行列式为正、绕序不变，箭头左右对称所以附带横向镜像也看不出来，中心落在整像素上因此不会出现半像素模糊或接缝。

### 搜索栏被回填


noteTransferredRecipe() 会把本次传输的 key 记进 lastProviderSearchKey / lastUploadSearchKey；右键清空只调了 applyMgmtSearchText("")，缓存 key 还在。于是下一次 
resolveUploadSearch() 走到第 3 步 if (term.isEmpty() && !lastProviderSearchKey.isEmpty()) 又把旧 key 解析出来，末尾 applyMgmtSearchText(key) 把文字写回输入框——看起来就是"清不掉"，上传也继续按旧内容走。

修复：

- 新增 clearMgmtSearchByPlayer()：清文字、置 mgmtSearchUserEdited = true、清空三个缓存 key，右键清空改调它；
- 
resolveUploadSearch() 第 3 步加 && !mgmtSearchUserEdited：玩家自己清空的框就是"没有关键词"，不再回填旧 key；
- keyTyped 里输入框吃掉按键时同步 mgmtSearchSeen 并置 mgmtSearchUserEdited。

语言键：wtct.gui.encode.undo.title/desc、wtct.gui.management.undo.empty/gone/changed/cache_full（中英各一份）。

构建：spotlessApply → compileJava → ssemble 全部 BUILD SUCCESSFUL，uild/libs/wtct-1.0.63-rc1.jar = 2,083,316 字节（2026-11-19 12:31）。