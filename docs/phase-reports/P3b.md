# P3b 阶段报告：界面（设置二级页、搜索、收藏 / 最近观看、虚拟分组、三种视图、排序、TV 双栏）+ K1 / K14 / K20

日期：2026-11-18　应用：StreamBox Player（debug 包 `com.streambox.player.debug`）

P3 拆为 P3a（数据层，已完成）、**P3b 界面（本次）**、P3c 列表编辑器（未开始，待用户确认）。P3b 提交：
- `749c740` P3b part 1：K1 / K14 的失败测试（先写测试，配空实现；CI run 44257372126 按预期失败 7 项）
- `c88a11f` P3b part 2：修 K1、K14；设置「播放」拆为二级页（K8）（CI run 44257886749 全绿）
- `4ac076d` P3b part 3：搜索、收藏 / 最近观看页、虚拟分组、三种视图、排序、TV 双栏、按来源切台、路由带 uniqueKey（K20）
- `d73695e` P3b part 4：FTS4 前缀写法修正（CI 上 `ChannelDaoBrowseTest` 发现）（CI run 44261053492 全绿）
- 文档提交见 `docs/DEVELOPMENT-STATUS.md`

## 用户决定（本阶段开工时确认）
1. **重命名后的名字也要能搜到**：不升 schema，对 `customName` 做 LIKE 补查（仍是 Room v2）。
2. **虚拟分组内的切台**（上下键、通知 / 耳机上下曲、PiP 上一个 / 下一个、自动播放下一个）按列表显示顺序进行并**循环**（自动播放下一个仍**不循环**）；**虚拟分组内输入台号 = 组内从 1 开始的序号**。
3. **PlayerRoute 带 uniqueKey（及 playlistId）**，刷新后进入播放页仍能找到频道（完成 K20），保持 `@Serializable` 类型安全路由。

## 做了什么

### 1. 设置「播放」拆为二级页（K8）
- 设置首页：外观、「播放」入口行（带摘要）、关于。新路由 `PlaybackSettingsDestination`（feature:settings，非一级页面，底栏 / TV 抽屉隐藏）。
- 「播放」页：单选项（缓冲档位、重连次数、默认画面比例、字幕大小 / 颜色 / 背景）各占**一行**（标题 + 当前值），点开是对话框，**焦点落在当前选项**，选完即关；开关项原地切换。TV 进入页面自动聚焦第一行，顶栏有返回按钮。原来平铺 20 多行单选，现在约 14 行。

### 2. 全局搜索（说明书 4.2）
- 频道页顶栏「搜索」→ `SearchRoute`。输入即搜，**最后一次按键 300 ms 后**执行（`SearchViewModel.DEBOUNCE_MS`，`debounce` + `mapLatest`，相同文本去重）。
- 频道：`channels_fts`（FTS4）对 `name` / `tvgName` 做**词前缀**匹配（每个词一个 `"词*"`，全部须匹配；只取字母数字，用户输入的引号 / `OR` / `NEAR` 等不会破坏表达式）；**`customName` LIKE 包含匹配（用户决定 1）**；查询含非 ASCII 字符（如中文）时**另对 `name` 做 LIKE 包含匹配**——FTS4 的 simple 分词器把一串汉字当成一个词，"新闻"按前缀找不到"凤凰新闻台"。最多 200 条，按播放列表 / 导入顺序。
- 分组：显示名 LIKE 包含匹配（分组数量少，不走 FTS）。点分组 → 回到频道页并选中该分组；点频道 → 播放（来源「全部」）；长按 / 菜单键可收藏。
- 隐藏频道、停用列表、旧代数据都不会出现。按"当前节目名"搜索依赖 EPG，留到 P4。

### 3. 收藏页（替换占位）与最近观看
- 底部导航「收藏」页两个标签：**收藏** / **最近观看**（选中标签保存在 SavedStateHandle）。
- 收藏：每行可点播放（来源「收藏」）；**上移 / 下移**按钮（手机、TV 通用，遥控器按右键到按钮）把当前显示顺序写成自定义顺序；删除；顶栏「按收藏时间排序」恢复默认顺序。没做手机长按拖动（任务说明为可选，见"没做什么"）。
- 最近观看：最新在前（最多 50），显示"x 分钟前观看"；单条移除；「清空记录」带确认对话框。
- 收藏 / 取消收藏入口：频道列表（任一视图）**长按**（触屏）、**长按 OK 或菜单键**（遥控器）弹出菜单；播放面板新增「加入收藏 / 取消收藏」；搜索结果同样支持。列表中已收藏频道显示星标。

### 4. 虚拟分组「全部 / 收藏 / 最近观看」与按来源切台（用户决定 2）
- 频道页分组条（手机）/ 左栏（TV）最前面固定三项：全部（N）、收藏（N）、最近观看（N），后面是播放列表分组。选择保存在 SavedStateHandle（`selectedGroup` / `selectedVirtual`），进程重建后恢复；分组刷新后消失仍回落到「全部」。
- 领域模型 `ChannelSource`（All / Group / Favorites / Recent）。`ChannelRepository.adjacentChannel / nextChannel / channelIdByNumber` 改为接收 `ChannelSource` 与排序方式：
  - 全部 / 分组：一条 SQL 取出与分页**完全相同顺序**的 id 列表（`ChannelDao.orderedIds`，与 `pagingSource` 共用 ORDER BY），再由纯 Kotlin 的 `ChannelSequence` 求上一个 / 下一个（循环）/ 下一个（不循环）。台号仍按"显示台号"（tvg-chno，否则列表位置），分组内优先，否则全局。
  - 收藏：收藏顺序（自定义拖动顺序，否则加入时间），按名称 / 台号排序时与浏览器一致；台号 = 组内序号。
  - 最近观看：最新在前；台号 = 组内序号。**播放器打开时冻结这份顺序**（`ChannelZapper` 按 key 走快照）：否则每切到一个频道它就跳到最前，上下键会在两个频道间来回弹。快照里已被刷新删除的频道自动跳过。
- 播放器的上下键、CHANNEL_UP/DOWN、手势切台、通知 / 耳机 / 蓝牙上下曲、PiP 上一个 / 下一个都走同一个 `PlayerViewModel.zap` → `ChannelZapper`；自动播放下一个走 `ChannelZapper.next`（不循环）；台号走 `ChannelZapper.byNumber`。无来源的路由（启动续播）保持 P2b 行为：切台走「全部」，自动下一个在频道自己的分组内。
- 播放器按浏览器当前的排序方式切台（设置变化即时生效）。

### 5. 播放路由带稳定键（K20，用户决定 3）
- `PlayerRoute(channelId, groupName?, source?, playlistId = -1, uniqueKey?)`，仍是 `@Serializable` 类型安全路由；`navigateToPlayer(channelId, source, key)`；`PlayerSource` 负责 `ChannelSource` ↔ 路由字段的编码。
- 播放器初始化时：路由 id 对应的频道 key 与路由 key 不一致（或 id 已不存在）→ 用 `channelIdByKey` 重新解析；当前频道的 key 也写入 SavedStateHandle，进程重建后同样按 key 恢复。

### 6. 三种视图、排序、TV 双栏
- 视图（顶栏按钮，DataStore `browse_channel_view_mode`）：**列表**（台号 + 台标 + 名称 + 星标）、**网格**（自适应列宽 148 dp 的台标卡片）、**紧凑**（无台标的单行）。
- 排序（顶栏按钮，DataStore `browse_channel_sort_order`）：**默认顺序**（播放列表自身顺序：按列表、列表内导入顺序）/ **名称**（显示名，忽略 ASCII 大小写，跨列表）/ **频道号**（显示台号，跨列表）/ **自定义**（用户顺序 `customSortOrder`，P3c 编辑器写入；收藏为拖动顺序）。同值时一律按列表、导入顺序兜底，顺序全序且稳定。最近观看始终按时间。
- **TV 默认双栏**：左侧 280 dp 分组栏（OK 选中，不随焦点移动刷新列表），右侧频道区（三种视图都可用）。右键进入频道、左键回分组再左键开抽屉；两栏都用 `focusRestorer` 回到上次焦点；进入页面聚焦当前分组；**从播放页返回时焦点回到刚播放的频道**（不是分组栏）。TV 上不显示悬浮"添加"按钮（播放列表页可添加）。所有可点元素有焦点环与 contentDescription / 文本语义。

### 7. Bug 修复（先写失败测试，CI run 44257372126 失败 7 项，修后转绿）
- **K1**：台标请求遵守按列表的明文开关。`core:common` 的 `CleartextImagePolicy`（请求头 `X-StreamBox-Allow-Cleartext`）；`core:network` 的 `CleartextHeaderCallFactory` 供 Coil 使用：把请求头转为 `CleartextGuardInterceptor` 的策略标签并去掉该头，**每一跳（含重定向）**都检查，缺省按"不允许"；频道页对不允许明文的列表根本不请求 http 台标；媒体通知的封面同样遵守。测试：`CleartextImagePolicyTest` 4 项、`CleartextHeaderCallFactoryTest` 4 项（MockWebServer）。
- **K14**：前台播放页处理遥控器媒体键：`MEDIA_PLAY_PAUSE` / `HEADSETHOOK` 切换，`MEDIA_PLAY` 继续（新增 `PlaybackController.resume()`，无播放器 / 出错时重试），`MEDIA_PAUSE` / `MEDIA_STOP` 暂停，并显示覆盖层；按下与抬起都消费，避免半个按键落到 MediaSession。测试：`PlayerMediaKeysTest` 3 项、`PlayerViewModelTest` 1 项。

### 依赖 / 权限 / 模块
- 新增 artifact：`io.coil-kt.coil3:coil-network-core` **3.6.3**（与已有 Coil 同版本，用于逐请求 `NetworkHeaders`，K1），写入 `libs.versions.toml` 与 implementation-plan 版本表。无新外部库版本、无新权限、无新模块；Room 仍为 v2（无迁移）。
- 新增 DataStore 键：`browse_channel_view_mode`（String，默认 List）、`browse_channel_sort_order`（String，默认 Default）。
- 新增矢量图标：搜索、网格、紧凑、排序、空心星、历史、关闭、更多、右箭头。

## 设计取舍
- **切台用"有序 id 列表"而非键集分页查询**：四种排序 × 四种来源若都写成"上一个 / 下一个"键集 SQL，查询会翻倍且容易与分页顺序不一致；改为与分页共用同一 ORDER BY 取 id 列表，在 Kotlin 里定位，保证"看到的顺序 = 切台顺序"。代价：每次切台读一次该来源的 id 列表（10 万频道时约几十到一两百毫秒，见已知问题）。P3a 的 `position / nextAfter / previousBefore / first / last` 已删除。
- **"默认顺序"= 播放列表自身顺序，不含用户自定义顺序**；"自定义"才使用 `customSortOrder`。这样四个选项各不相同。P3c 编辑器排序后是否自动切到"自定义"需用户决定（见下方问题）。
- 分组搜索用 LIKE：分组少，FTS 表也不含分组名；无需迁移。

## 验证结果
- CI：run **44261053492**（`d73695e`）**全绿**：ktlint / detekt、`test`、`:app:lintDebug`（0 error、4 warning，与 D10 相同）、`:app:assembleDebug` + `:app:assembleRelease`。
- 单元测试 **468 项、0 失败**（+71，自 397；从该 run 的 `reports` artifact 统计 debug 单测）：app 10、core:common 16（+4）、core:data 49（+15）、core:database 37（+10）、core:datastore 13（+1）、core:domain 24（+8）、core:network 12（+4）、core:parser 60、core:player 94、feature:channels 20（+14）、feature:onboarding 3、feature:player 103（+15）、feature:playlist 14、feature:settings 13。
  - 新测试类：`ChannelDaoBrowseTest`（四种排序、分页与切台 id 同序、FTS 前缀、中文子串、重命名可搜、隐藏 / 停用 / 旧代排除、分组搜索；Robolectric）、`DefaultChannelRepositoryTest`、`SearchQueryTest`、`ChannelSequenceTest`、`PlayerViewModelSourcesTest`（各来源切台 / 台号 / 自动下一个 / 最近观看快照 / K20 / 收藏开关）、`FavoritesViewModelTest`、`SearchViewModelTest`（300 ms 防抖）、`ChannelsViewModelTest` 新增 5 项、K1 / K14 测试。
- 本地（aarch64 沙箱）：只跑了 ktlint-cli 1.8.0 与 detekt-cli 1.23.8（与 Gradle 插件同版本同配置）；Kotlin 编译、单测、APK 构建以 CI 为准（沙箱内存不足以跑 Gradle）。
- debug APK（CI artifact `streambox-debug-apk`，`d73695e`，交付名 `StreamBox-P3b-debug.apk`）：**21,658,386 字节，SHA-256 `f02c6edc704f988083be784d769536017a113569ad3945107c80cb62fff47092`**。文档提交只改文档，APK 内容相同（字节可能因重新构建而不同）。

## 没做什么
- 列表编辑器（隐藏 / 显示分组与频道、重命名、自定义分组、频道排序、播放列表编辑 / 排序 / 启用停用）：P3c。
- 收藏的**手机长按拖动排序**：用上移 / 下移按钮实现（手机与 TV 一致、可用遥控器）；拖动手势未做。
- 按"当前节目名"搜索：P4（EPG）。
- 频道页数字直输：用户决定不做。
- 搜索没有搜索历史 / 联想；结果不分页（频道最多 200 条、分组 50 条）。
- 没有 Compose UI 测试（D3 仍在）；TV 焦点、视图、菜单均需真机验收。

## 已知问题
1. **切台性能**：每次切台读取当前来源（含排序）的全部 id；「全部」+ 10 万频道在低端电视盒子上可能有 100 ms 级延迟（未实测）。如真机明显卡顿，可改为对默认 / 自定义顺序用键集查询、对名称 / 台号排序缓存 id 列表。
2. 名称排序只忽略 ASCII 大小写，中文按 Unicode 码位排，不是拼音顺序；收藏的名称排序（内存）与数据库（SQLite NOCASE）对非 ASCII 大小写的处理略有差异。
3. FTS4 simple 分词器：英文按词前缀匹配（"overy" 搜不到 "Discovery"）；中文查询额外走 LIKE 包含匹配（全表扫描，10 万频道时可能慢些）。
4. 最近观看在播放页打开时冻结顺序：播放期间新增的观看记录不会进入本次切台序列（返回列表后刷新）。
5. 收藏移动后会把当前显示的全部收藏写成自定义顺序；当时因刷新而缺失的收藏频道失去自定义位置，回来后排在末尾（按加入时间）。
6. TV 上频道页不再显示悬浮"添加播放列表"按钮（到"管理播放列表"添加）；DropdownMenu 在 TV 上的焦点表现、长按 OK 弹出菜单需真机确认。
7. 从播放页返回时焦点恢复到刚播放的频道，前提是该项仍在已加载的分页里；否则焦点留在默认位置。
8. 全部改动未经真机验证。

## 真机验收步骤（需用户）
设备：手机 realme GT6 + Android TV。覆盖安装 `StreamBox-P3b-debug.apk`（数据库仍为 v2，无迁移）。

### 手机
- [ ] 设置：首页只有外观、「播放」入口、关于；进入「播放」页，缓冲 / 重连 / 画面比例 / 字幕三项各为一行，点开对话框选择后立即生效并显示新值；开关项正常；返回键回设置首页。
- [ ] 频道页分组条最前是「全部（N）/ 收藏（N）/ 最近观看（N）」；选「收藏」「最近观看」显示对应列表，空时显示提示。
- [ ] 顶栏视图按钮切换列表 / 网格 / 紧凑；退出 App 再进仍保持。
- [ ] 顶栏排序：默认顺序 / 名称 / 频道号 / 自定义，列表顺序随之变化；进入播放后上下滑切台顺序与列表一致并循环。
- [ ] 长按频道 → 「加入收藏」，星标出现，「收藏」计数 +1；再长按 → 「取消收藏」。
- [ ] 从「收藏」进入播放：上下滑、通知栏上一曲 / 下一曲、耳机按键、PiP 小窗上一个 / 下一个都只在收藏内循环。
- [ ] 从「最近观看」进入播放：连续向上 / 向下切台，按打开时的列表顺序走，不会在两个频道间来回跳。
- [ ] 播放面板「加入收藏 / 取消收藏」可用，返回后频道页星标同步。
- [ ] 收藏页「收藏」标签：上移 / 下移改变顺序，频道页「收藏」分组顺序一致；删除；「按收藏时间排序」恢复。
- [ ] 收藏页「最近观看」标签：显示"x 分钟前观看"；单条移除；「清空记录」弹确认，确认后清空。
- [ ] 搜索：输入英文频道名前几个字母、分组名、中文名中间几个字都能搜到；把某频道重命名后（P3c 前可跳过）用新名能搜到；点分组回到频道页并选中该分组；点频道播放。
- [ ] K1：某播放列表**关闭**"允许明文"且台标是 http 地址时不显示台标（显示首字）；打开开关后显示；媒体通知封面同理。
- [ ] K20：在播放列表刷新（导入）进行中点进频道，导入完成后仍正常播放（不再"频道不存在"）。
- [ ] 回归：添加 / 刷新 / 删除播放列表、后台播放、PiP、手势、睡眠定时、启动续播、自动下一个。

### TV
- [ ] 频道页为双栏：左分组（含全部 / 收藏 / 最近观看），右频道；进入时焦点在当前分组；OK 选分组，右键进入频道，左键回分组，再左键打开抽屉；上键到顶栏按钮；无焦点卡死。
- [ ] 三种视图在右栏都能用方向键遍历，网格左右移动正常，最左列按左键回分组栏。
- [ ] 从播放页按返回，焦点回到刚播放的频道。
- [ ] 频道上长按 OK 或按菜单键弹出「加入收藏 / 取消收藏」。
- [ ] 设置「播放」页进入即聚焦第一行；对话框打开时焦点在当前选项。
- [ ] K14：播放中按遥控器播放 / 暂停键能暂停与继续（前台）；单独的播放键 / 暂停键（若有）也有效。
- [ ] 在「收藏」「最近观看」内按数字键：输入 2 跳到该列表第 2 个频道；超出数量提示"没有 N 号频道"。在普通分组内数字键仍按台号。
- [ ] 收藏页：标签可用方向键切换，上移 / 下移 / 删除按钮可用右键到达。
- [ ] 搜索页可用遥控器输入（系统输入法），结果可用方向键选择。
