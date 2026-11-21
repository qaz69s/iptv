# 开发状态总览

> 更新于 2026-11-21（**P3c 列表编辑器完成，CI run 44875405245 全绿**，报告 `docs/phase-reports/P3c.md`；等用户确认后进入 P4 EPG）。本文件汇总：已开发什么、还要开发什么、所有已知问题与技术债、待用户真机验收的清单、下一步操作。
> 细节以各阶段报告 `docs/phase-reports/*.md` 为准；需求以 `docs/requirements.md` 为准。

## 0. 一眼看懂
| 阶段 | 状态 | 关键提交 | 单测数（累计） | 真机验收 |
|---|---|---|---|---|
| P0 脚手架 | ✅ 完成 | `4ef6a2c`、`8b4b6f9`（minSdk 24） | 22 | ❌ 未做 |
| P1 导入 + 频道 + 基础播放 | ✅ 完成 | `5173eda`（P1a）、`b1348f3`（P1b） | 168 | ❌ 未做 |
| P2a 播放稳定性 + 操控 | ✅ 完成 | `99572cc` | 255 | ❌ 未做 |
| P2b 后台 / PiP / 手势 / 定时 | ✅ 完成 | `3e14a71`、`82bd0d1`、`7d25565`、`a669bf0`（文档） | 348 | ❌ 未做 |
| P3a 数据层（收藏 / 历史 / 覆盖表 / 稳定键续播 / 台号 2 s） | ✅ 完成 | `460ac22`、`3a57202`（+ 文档提交） | 397 | ❌ 未做 |
| P3b 界面（搜索 / 收藏页 / 最近观看 / 虚拟分组 / 视图 / 排序 / TV 双栏 / 设置二级页 / K1 K14 K20） | ✅ 完成 | `749c740`、`c88a11f`、`4ac076d`、`d73695e`（+ 文档提交） | 468 | ❌ 未做 |
| K26 音频修复（P3c Part 0） | ✅ 代码完成，CI 全绿，待真机验收 | 1936849 | ✅ | `StreamBox-audiofix-debug.apk` |
| P3c 列表编辑器（Room v3 自定义分组 / 隐藏 / 重命名 / 排序 / 播放列表启用与排序） | ✅ 完成 | `4373b6e`、`6bf6465`、`8bf3e37`、`c810d98`（+ 文档提交） | 444 @Test（源码统计） | ❌ 未做 |
| P4 EPG | 未开始 | — | — | — |
| P5 回看 / 时移 | 未开始 | — | — | — |
| P6 Xtream（直播 + 点播） | 未开始 | — | — | — |
| P7 设置 / 家长控制 / 备份 / 定时更新 / 多语言 | 未开始 | — | — | — |
| P8 投屏 / 变现接口 / 性能 / 发布 | 未开始 | — | — | — |

- **重要**：P0–P2 全部只在无模拟器的 aarch64 沙箱里经过「编译 + JVM 单测 + ktlint + detekt」验证，**从未在真机或模拟器上运行过**。
- GitHub CI：**P3b `d73695e`（run 44261053492）全绿**，468 项单测；P3b 的失败测试先行提交 `749c740`（run 44257372126）按预期失败 7 项。P3a `3a57202`（run 44248545012）全绿，397 项。更早：首次运行（`a669bf0`）与 `71ca385` 在 `:app:lintDebug` 失败（D5）。**`9c76878`（run 44242732138）已全绿**：ktlint / detekt、单测、`:app:lintDebug`（0 error、4 warning）、`:app:assembleDebug`、`:app:assembleRelease`（R8 首次通过）。debug APK 可从该 run 的 `streambox-debug-apk` artifact 下载。
- 用户的真机：**手机 realme GT6（Android 14 / 15）+ 一台 Android TV**。
- **用户决定（2026-11-18，P3 计划确认时）**：
  - P3 拆为 P3a（数据层）/ P3b（界面）/ P3c（编辑器），每个子阶段完整汇报。
  - **真机验收与 P3 并行进行**（不阻塞开发）；用户反馈的问题先写失败测试再修。
  - 引入 **Robolectric**（4.18.1）跑 Room DAO / 迁移的 JVM 单测（core:database 用 JUnit 4，见 AGENTS.md 4.5）。
  - **收藏排序**：跨播放列表按加入时间排序；可选的自定义（拖动）顺序在设置后优先。
  - **台号直输超时 2 秒**（说明书 4.2；P2a 为 1.5 s，P3a 已改）。
  - **频道页不加数字直输**，只保留播放页的台号输入。
- **用户决定（2026-11-18，P3b 开工时）**：
  - **重命名后的名字也要能搜到**：不改 schema（仍 Room v2），搜索时对 `customName` 做 LIKE 补查。
  - **虚拟分组内的切台**（上下键、通知 / 耳机上一 / 下一、PiP 上一个 / 下一个、自动播放下一个）**按列表显示顺序并循环**（自动播放下一个仍**不循环**）；**虚拟分组内输入台号 = 组内 1 起的序号**。
  - **PlayerRoute 带 uniqueKey（与 playlistId）**，刷新后进入播放页仍能找到频道（完成 K20），保持 `@Serializable` 类型安全路由。
- **用户决定（2026-11-18，P3c 确认时）**：
  1. **编辑器入口**：播放列表管理里按播放列表进入编辑器；另在频道长按菜单提供快速「隐藏 / 重命名」。
  2. **隐藏项**：编辑器内以灰色显示并带开关；区分「自身被隐藏」与「因所在分组被隐藏」。
  3. 在编辑器里**重排序后，浏览排序自动切到「自定义」**。
  4. **自定义分组**（Room v3 迁移，含测试）：一个频道可属于多个自定义分组，允许跨播放列表；自定义分组作为虚拟分组显示，参与切台与台号输入（按组内顺序，规则同收藏）。
  5. **停用播放列表保留数据**（只是隐藏）；播放列表排序用**上移 / 下移**（TV 友好）。
  6. **分组重命名为已存在的分组名时在显示上合并**（与数据层一致）。
  7. **P3c 不做 PIN 锁**（推迟到 P7）。
  - 范围：分组 / 频道隐藏、重命名、排序；播放列表编辑 / 排序 / 启用停用；TV 焦点友好、中英字符串、contentDescription、UDF ViewModel、JUnit5 + Fake、Robolectric DAO 与 v2→v3 迁移测试；新依赖只用最新稳定版。
- **用户要求（2026-11-18）**：先修真机 bug **K26**（见 3.1），单独提交，再做 P3c。**FFmpeg 扩展的决定由「暂不接入」改为「接入音频解码」**（仅用于设备 MediaCodec 无法解码的音频，许可证影响须写入文档）。

## 1. 已开发了什么

### P0 —— 工程脚手架（2026-11-15，`4ef6a2c`；`8b4b6f9` 把 minSdk 改为 24 并升级 Navigation 2.10.2 / DataStore 1.3.0）
- 多模块 Clean Architecture：`app`、`core:{common,model,domain,data,datastore,designsystem,ui,testing}`、`feature:{onboarding,channels,epg,settings}`；`build-logic` 约定插件统一 SDK / Compose / Hilt / 测试 / ktlint / detekt，并禁止 feature 互相依赖。
- Version Catalog（`gradle/libs.versions.toml`）。
- 首启：欢迎页 → 免责声明（必须勾选同意），状态存 DataStore。
- 主界面骨架：频道 / 节目单 / 收藏 / 设置四个入口；手机 / 平板 NavigationSuite，TV 侧边 NavigationDrawer + 焦点高亮。
- 设计系统：浅 / 深 / AMOLED 主题、Android 12+ 动态取色、空 / 加载 / 错误组件、自绘矢量图标。
- 设置页：主题、动态取色、版本号、查看免责声明。
- `UrlRedactor` 日志脱敏。
- TV Leanback 入口 + banner；同一 APK 覆盖手机 / 平板 / TV。
- 中英文字符串、自适应图标、商店图标 `docs/store/icon-512.png`、可选 release 签名（`keystore.properties.template`）。
- GitHub Actions CI（`.github/workflows/ci.yml`）。
- 测试 22 项。交付物：`StreamBox-P0-debug.apk`、`streambox-player-P0-src.zip`。
- ⚠️ P0 报告写了"广告/内购：仅接口与空实现"，但**仓库里实际没有任何 monetization 模块或代码**（见技术债 D1）。

### P1 —— 播放列表导入、频道浏览、基础播放（2026-11-16）
**P1a `5173eda`**
- `core:parser`（JVM）：M3U/M3U8 流式解析器（`#EXTM3U` 头属性、`#EXTINF` 属性、`#EXTGRP`、`#EXTVLCOPT` UA/Referer、catchup 属性、BOM / CRLF / 坏行计数），60 项测试含 10 万行性能测试。
- `core:network`：OkHttp 下载（gzip / 重定向 / UA）、`CleartextGuardInterceptor` 按播放列表在每一跳拦截明文 http（证书校验始终开启），MockWebServer 测试。
- `core:database`：Room（Playlist / ChannelGroup / Channel + FTS4），schema v1 已导出；**频道按 generation 分代导入，校验后原子切换**。
- `core:data`：仓库实现、导入管线（下载或 SAF → 流式解析 → 分批写入 → 校验提交），应用级作用域运行、可取消。
- `core:player`：`PlaybackController` 单例（Media3），按 URL 选 HLS / DASH / RTSP / Progressive，无扩展名 URL 不识别时改走 HLS 一次；按列表注入 UA / Referer 与明文开关；错误码映射。
- `feature:playlist`：添加（URL / 本地文件；Xtream 占位）、导入进度页、播放列表管理（刷新、删除、明文开关）。
- `feature:channels`：空状态、分组 chips、分页频道列表（台标、频道号）。

**P1b `b1348f3`**
- `feature:player`：`PlayerRoute(channelId, groupName?)`；全屏 `ContentFrame`；覆盖层（频道号 + 名称、返回、切台按钮，4 秒自动隐藏）；缓冲转圈；错误原因 + 重试；手机上下滑 / TV 方向键与 CHANNEL_UP/DOWN 在来源分组内循环切台；亮屏、沉浸、手机横屏。
- 修复：选中分组刷新后消失时列表为空。
- 测试 168 项。交付物：`StreamBox-P1a-debug.apk`、`StreamBox-P1-debug.apk`、`streambox-player-P1-src.zip`。

### P2a —— 重连、缓冲、解码回退、轨道 / 字幕、画面比例、TV 遥控器（2026-11-17，`99572cc`）
- 重连（`core:player/reconnect`，纯 Kotlin）：`FailureKind` 可重试 / 不可重试分类；指数退避 1 s ×2 上限 30 s ±20% 抖动，默认 5 次（0 = 关）；网络恢复立即重试；稳定 30 s 计数清零。
- 三档缓冲（低延迟 / 均衡 / 稳定）；硬解失败回退软解一次；「仅软件解码」= 软件优先。
- 清晰度 / 音轨 / 字幕选择；外挂 SRT / VTT（SAF，`MergingMediaSource`）；字幕大小 / 颜色 / 背景。
- 六种画面比例（自适应 / 16:9 / 4:3 / 填充 / 拉伸 / 原始）。
- 播放面板：手机底部 ModalBottomSheet，TV 右侧 Dialog。
- TV 遥控器：菜单键 / 长按 OK 开面板；左右键直播调音量、点播 ±10 s；数字键台号（1.5 s 超时、满 4 位、OK 提交、返回键删位）。
- 设置「播放」分组：缓冲档位、仅软件解码、重连次数 0/3/5/10、默认画面比例。
- 新依赖 `media3-ui` 1.11.1。测试 255 项。交付物：`StreamBox-P2a-debug.apk`（SHA-256 `1625b4a1…7ed44`）、`streambox-player-P2a-src.zip`。

### P2b —— 后台播放、画中画、手势、睡眠定时、启动续播、自动下一个（2026-11-18）
- `3e14a71`：`PlaybackService`（Media3 `MediaSessionService`，前台服务 `mediaPlayback`，媒体通知）；`ChannelNavigationPlayer` 把通知 / 锁屏 / 耳机 / 蓝牙上下曲映射为切台；拔耳机暂停；后台只放音频（禁用视频轨）；Android 13+ 通知权限；**PiP**（Android 8.0+ 手机 / 平板，自动进入、面板按钮、小窗三按钮、比例与 sourceRectHint）。
- `82bd0d1`：手机 / 平板手势（左 1/3 亮度、中 1/3 切台、右 1/3 音量；横滑拖进度仅点播；双击 ±10 s / 中间暂停）。
- `7d25565`：睡眠定时（15/30/60/90/自定义 1–600 分钟，最后 10 s 淡出后暂停）；启动续播上次频道（默认关）；自动播放下一个（默认关，点播播完，不绕回）。
- `a669bf0`：文档与阶段报告。
- 新依赖 `media3-session` 1.11.1；新权限 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS`。
- 测试 **348 项**（app 9、core:common 12、core:data 24、core:datastore 11、core:domain 10、core:network 8、core:parser 60、core:player 94、feature:channels 6、feature:onboarding 3、feature:player 84、feature:playlist 14、feature:settings 13）。
- 交付物：`StreamBox-P2-debug.apk`（21,404,227 字节，SHA-256 `9089a03c7ac0a9bdc4366f1b4cdfede6244bd549f3e8db3fa29de1c8b1bcd303`）、`streambox-player-P2-src.zip`（`git archive` @ `a669bf0`）。

> 交付物（APK / 源码 zip）在原开发环境中交付给用户，**不在 Git 仓库里**。需要 debug APK 时可从 GitHub Actions 的 `streambox-debug-apk` artifact 下载，或本地 `./gradlew :app:assembleDebug`。

### P3a —— 数据层（2026-11-18）
- `460ac22`：Room schema **v2** + `Migration1To2`（`2.json` 已提交）。新表 `favorites`、`watch_history`、`channel_overrides`、`group_overrides`，均以 `(playlistId, uniqueKey)` / `(playlistId, 分组名)` 为键；覆盖值物化到 `channels` / `channel_groups` 的 `hidden`、`customName`、`customSortOrder`（导入提交前 `OverrideDao.applyToGeneration`，用户修改时同一事务重写），读查询用显示名 `COALESCE(customName, name)` 与有效顺序 `COALESCE(customSortOrder, sortOrder)`。core:domain `FavoritesRepository` / `WatchHistoryRepository`（50 条）/ `ChannelOverridesRepository` 与用例 `ToggleFavorite` / `RecordPlaybackStart` / `ResolveLastChannel`，core:data 实现 + core:testing Fake。**上次频道改为 playlistId + uniqueKey**（DataStore `last_played_channel_key`），修 K18；`PlayerViewModel.rememberPlayingChannel` 同时写观看历史。Robolectric 4.18.1 + 约定插件 `streambox.android.robolectric`，core:database 首批 27 项 DAO / 迁移测试。
- `3a57202`：TV 台号输入超时 1.5 s → **2 s**（先写失败测试）。
- 无新界面、无新字符串、无新权限、无新模块。详见 `docs/phase-reports/P3a.md`。

### P3b —— 界面（2026-11-18）
- `749c740`：K1 / K14 失败测试（配空实现，CI 按预期失败）。`c88a11f`：修 K1（`CleartextImagePolicy` + `CleartextHeaderCallFactory`，台标与通知封面遵守按列表明文开关，含重定向）、K14（前台 `MEDIA_PLAY_PAUSE` / `PLAY` / `PAUSE` / `STOP` / `HEADSETHOOK`，新增 `PlaybackController.resume()`）；设置「播放」拆为二级页 `PlaybackSettingsDestination`（K8）。
- `4ac076d`：全局搜索（`SearchRoute`，FTS4 词前缀 + customName LIKE + 中文 LIKE，分组 LIKE，300 ms 防抖）；收藏页（收藏：上移 / 下移 / 删除 / 恢复时间序；最近观看：移除 / 清空）；虚拟分组「全部 / 收藏 / 最近观看」；`ChannelSource` + 排序贯穿 `ChannelRepository` 的切台 / 下一个 / 台号（`ChannelSequence`、`ChannelSorting`；播放器 `ChannelZapper`，最近观看打开时冻结顺序）；`PlayerRoute` 带 source + playlistId / uniqueKey（K20）；列表 / 网格 / 紧凑与默认 / 名称 / 频道号 / 自定义排序（DataStore `browse_channel_view_mode` / `browse_channel_sort_order`）；TV 双栏；播放面板收藏开关。
- `d73695e`：FTS4 前缀必须写成 `"词*"`（`"词"*` 是 FTS5 写法，FTS4 永不匹配；CI 上的 Robolectric DAO 测试发现）。
- 新 artifact `coil-network-core` 3.6.3（同 Coil 版本）；无新模块、无新权限、Room 仍 v2。测试 **468 项**（+71）。交付物：`StreamBox-P3b-debug.apk`（21,658,386 字节，SHA-256 `f02c6edc704f988083be784d769536017a113569ad3945107c80cb62fff47092`，CI `d73695e`）、`streambox-player-P3b-src.zip`。详见 `docs/phase-reports/P3b.md`。

### 当前尚未存在的东西（供核对）
- 模块：`core:catchup`、`feature:vod`、`feature:cast`、`feature:monetization`（或 `core:monetization`）尚未创建；`feature:epg` 只有占位页。
- 依赖：WorkManager、Retrofit、Cast、Billing、Ads、UMP 已在 catalog 中，但**没有模块使用**。
- `ChannelEntity` / `ChannelGroupEntity` 的 `hidden`（P3a 起由覆盖表物化）、`customName`、`customSortOrder` 已接入查询（P3b：搜索含 customName、排序「自定义」用 customSortOrder），但还没有编辑界面（P3c）；`locked` 恒为默认值；`PlaylistEntity` 有 `epgUrl`（导入时已写入，未被使用）、`updateMode`、`updateIntervalHours`、`enabled`、`username` / `passwordEncrypted`、`headersJson`，均未被功能使用。
- 仓库没有任何 `androidTest` 源码（无 Compose UI 测试）；DAO / 迁移测试自 P3a 起在 core:database 的 Robolectric 单测中。

## 2. 还要开发什么（P3–P8）
阶段依赖：`P3 → P4 → P5`；`P6` 依赖 P3（收藏 / 历史键）、P4（EPG 存储与匹配）、P5（xc 回看）；`P7` 汇总设置 / 备份，需在 P3–P6 的数据结构稳定后做；`P8` 收尾。若用户要求调整顺序，以用户为准。

### P3 —— 搜索、收藏、历史、分组管理、列表编辑器（P3a 数据层、P3b 界面已完成；剩余 P3c 列表编辑器，原始范围记录如下）
**范围（说明书 4.1 列表编辑器、4.2、4.7 收藏 / 历史）**
- 全局搜索：频道名、分组名，**输入即搜、防抖 300 ms，基于 Room FTS**（`channels_fts` 已有，列 `name`、`tvgName`）。"按当前节目名搜索"依赖 EPG，留到 P4 补。
- 收藏：任意频道加 / 取消收藏；收藏页（替换现有占位）；**以 `(playlistId, uniqueKey)` 为键，不得存频道 id**——每次刷新 / 重新导入频道 id 都会变（分代导入）。`uniqueKey` 规则：tvg-id 优先，否则 URL 哈希，列表内重复加后缀；已有索引 `(playlistId, uniqueKey)`。
- 观看历史：最近 50 条，可清空；同样以 `(playlistId, uniqueKey)` 为键。记录时机建议挂在 `PlayerViewModel.rememberPlayingChannel`（频道首次真正开始播放时，与"上次频道"同处）。
- 虚拟分组：「全部 / 收藏 / 最近观看」。
- 三种视图：列表 / 网格 / 紧凑；TV 默认左侧分组 + 右侧频道双栏。
- 排序：默认顺序 / 名称 / 频道号 / 自定义。
- 分组管理 / 列表编辑器：隐藏 / 显示分组、隐藏频道、重命名、自定义分组、频道排序；播放列表的编辑 / 排序 / 启用停用（说明书 4.1，目前只有刷新 / 删除 / 明文开关）。
- **更新后保留收藏、隐藏、排序**：`hidden` / `sortOrder` / `locked` 列随代重建，用户改动必须存在独立的覆盖表（以 `(playlistId, uniqueKey)` 或分组名为键），导入提交新一代时套用。
- Room：升到 schema v2 + `Migration(1, 2)`，新 schema json 一起提交。

**验收要点**：DAO / 仓库测试（建议本阶段补上第一批 Room 测试，见技术债 D2）；刷新 / 重新导入后收藏、历史、隐藏、排序都保留；搜索 300 ms 防抖；TV 双栏焦点正确；收藏 / 最近观看分组内切台正确。

**注意事项**
- 「收藏」/「最近观看」虚拟分组会影响**切台、台号直输、自动播放下一个、通知 / 耳机上下曲、PiP 上一个 / 下一个**——它们都走 `ChannelRepository` 的 `adjacentChannel(id, groupName, dir)`、`nextChannel(id, groupName)`、`channelIdByNumber(number, groupName)`，来源分组经路由 `PlayerRoute(channelId, groupName?)` 传入。需要让这些查询认识虚拟分组（或给路由加"来源类型"参数），并为每种来源补测试。收藏可能跨播放列表，切台顺序要定义清楚。
- 顺手修 P2b 已知问题 9：把"上次频道"（`LastChannelRepository`，DataStore `last_played_playlist_id` / `last_played_channel_id`）改为基于 `uniqueKey`，重新导入后仍能续播。
- **建议先把设置「播放」分组拆成二级页**（`feature/settings/.../SettingsScreen.kt`），再加任何设置项；TV 上该分组已过长（P2a KI4 / P2b KI10）。
- 顺手修 P1 已知问题 1（Coil 台标不受按列表明文开关约束），原计划就在 P3。
- 收藏 / 历史这类结构化数据放 Room，不放 DataStore。
- 说明书 4.2 的"频道号直输"在播放页已实现；是否也要在频道页支持，开工前问用户。

### P4 —— EPG（XMLTV）+ 匹配 + 网格视图 + 提醒
**范围（说明书 4.4）**
- XMLTV：xml / xml.gz / URL / 本地文件；**流式 Pull 解析**，放 `core:parser`（JVM）；入库批量事务（每 500–1000 条）；只保留"前 1 天 ~ 后 N 天"（可配）。EPG 地址来源：`PlaylistEntity.epgUrl`（P1 导入时已把 M3U 头 `url-tvg` / `x-tvg-url` / `tvg-url` 的第一个地址写入 `epgUrl`）或用户手动添加。Xtream 内置 EPG 在 P6。
- `Program(id, epgChannelId, start, end, title, description, category, iconUrl)`，索引 `(epgChannelId, start)`；`EpgMapping(channelId, epgChannelId, manual)`——注意 channelId 会随导入变化，映射建议以 `(playlistId, uniqueKey)` 为键。
- 匹配：`tvg-id` → `tvg-name` → 规范化名称模糊匹配；允许手动指定。
- 视图：频道内节目列表（今日 / 明日 / 一周）；**网格 EPG（横向时间、纵向频道，滚动与 TV 焦点导航）**；频道列表项显示当前 / 下一节目；节目详情（标题、描述、起止、分类、海报）。
- 播放覆盖层补齐 P1 推迟项：台标、当前 / 下一节目与进度条、时钟。
- 搜索补"当前节目名"。
- 提醒：未来节目本地通知 + 可选自动切台（WorkManager / 非精确闹钟，**不申请 `SCHEDULE_EXACT_ALARM`**）。
- 全局 EPG 时间偏移：±12 小时，0.5 小时步进。

**验收要点**：XMLTV 解析单测（含畸形数据、gz、大文件流式）、匹配单测；网格 EPG 在 TV 上滚动与焦点正确；所有时间 UTC 毫秒入库、展示时转时区。

**注意**：EPG 更新的定时任务可以在本阶段先做手动 / 打开时更新，WorkManager 定时策略在 P7 统一（或本阶段一起做，开工前与用户确认）。

### P5 —— 回看 / 时移
**范围（说明书 4.5）**
- 新建 `core:catchup`（JVM，纯 Kotlin）：类型 `default`、`append`、`shift`、`flussonic`、`xc`、`vod`，以及自定义 `catchup-source` 模板（`${start}` `${end}` `${timestamp}` `${offset}` `${duration}` `{utc}` `{lutc}` 等占位符，格式化规则可配置）。
- 频道已有 `catchupType`、`catchupSource`、`catchupDays`、`timeshift` 列（P1 解析器已解析）。回看天数取 `catchup-days` 或 Xtream 的 `tv_archive_duration`。
- 入口：EPG 已播出节目点"回看"；播放中进度条向左拖（时移）。
- **时间计算严格区分 UTC / 本地时区 / EPG 偏移三套时间**。

**验收要点**：**≥ 20 个单测用例，覆盖夏令时、跨日、负偏移**；真实源验证。

**注意**：P2b 已知问题 8（带 DVR 的直播不能拖进度）在此阶段解决——手势 / 双击快进退目前只对 `isSeekable`（非直播）生效。

### P6 —— Xtream Codes（直播 + 点播）
**范围（说明书 4.1 第 3 种添加方式、4.6）**
- Retrofit（3.0.0）+ kotlinx.serialization 的 Xtream API（`player_api.php`）：直播 / 电影 / 剧集分类与列表；Xtream 内置 EPG；`xc` 回看。
- 凭据：密码用 **Android Keystore 加密**存 `PlaylistEntity.passwordEncrypted`；日志用 `UrlRedactor` 脱敏（已支持 Xtream 路径凭据）。添加页的 Xtream 入口目前是占位提示。
- 新建 `feature:vod`：电影 / 剧集（季 / 集）浏览，详情页（海报、简介、评分、时长）。
- **点播断点续播（记录进度）在本阶段做**（P2b 只做了"启动续播上次频道"）、已看标记、自动下一集（可关，复用 P2b 的"自动播放下一个"设置或单独开关，开工前确认）。
- 数据模型：`VodItem / Series / Episode / Progress`。

**验收要点**：API / 映射单测（MockWebServer）；真实账号验证（需用户提供测试账号）。

### P7 —— 设置、家长控制、备份恢复、定时更新、多语言
**范围（说明书 4.1 自动更新、4.7 家长控制、4.9）**
- 设置：外观（主题、主色、字体缩放）、播放（剩余项：网络类型限制等）、EPG（更新频率、保留天数、时间偏移）、播放列表更新策略。
- 定时更新：每个列表"手动 / 每次启动 / 每 N 小时"，**WorkManager** 执行，失败指数退避重试，更新时保留收藏与排序（依赖 P3 的覆盖表）。`PlaylistEntity.updateMode` / `updateIntervalHours` 已预留。
- 家长控制：4–6 位 PIN；锁定指定分组 / 频道、设置页、播放列表编辑器；多次失败短暂冷却。`locked` 列已预留。
- 备份与恢复：导出 / 导入 JSON（列表配置、收藏、设置），**不含明文密码**。
- 多语言：简中 + English，App 内切换语言（`locales_config` 已有）。

**验收要点**：PIN 逻辑单测、备份序列化单测；交互验收。

### P8 —— Chromecast、变现接口、性能、发布准备
**范围（说明书 4.8、4.10、第 5、10、11 节）**
- Chromecast（Cast 23.0.0，**仅手机 / 平板**）：发现设备、投射当前流、遥控（暂停 / 音量 / 停止）、断线恢复；需要自定义 Header 的流给出提示（本地代理转发作为可选项，风险 R10）。
- 广告 / 内购：**只做接口 + 空实现（NoOp），不接入任何 SDK**（用户决定）；业务代码只依赖接口；广告不得出现在播放界面；如将来接入须有 UMP 同意弹窗与隐私政策入口。
- 性能：冷启动 < 2 s；1 万频道 60 fps；稳态内存 < 250 MB；播放 24 h 无泄漏（LeakCanary）；同源 HLS 切台首帧 < 2 s；1 GB 电视盒子降级策略。
- Release：R8 混淆、`proguard-rules.pro` 检查、签名说明；补 `docs/RELEASE.md`（`app/build.gradle.kts` 注释已引用但文件不存在）。
- 商店文案草稿、隐私政策模板（说明书第 11 节），首启与商店说明中的"仅为播放器"声明。
- **最终交付须注明 minSdk 24（说明书原为 23）**以及其他与说明书的偏差（应用名、Billing 用 9.1.0 而非 v7、FFmpeg 未接入等）。
- 说明书第 10 节完整手动验收清单逐条打勾（手机 / 平板 / TV）。

### 未排期 / 需要用户决定
- **RTMP**（R5）：等 Media3 RTMP 扩展与当前 Media3 版本对齐的稳定版。
- **FFmpeg 软解扩展**（R5）：原决定暂不接入；**2026-11-18 用户要求为修 K26 接入 FFmpeg 音频解码**（视频仍用 MediaCodec）。
- **UDP / RTP 组播**（R6）：`CHANGE_WIFI_MULTICAST_STATE` + MulticastLock、RTP 剥头 DataSource，"尽力而为"；尚未排入任何阶段，建议在 P8 或用户指定时做。
- **扫码 / 局域网推送**（说明书 4.1 第 4 种）：说明书标为二期，不在 P0–P8 范围。
- 系统播放恢复（Android 11+ 媒体控件）、Android Auto / `MediaLibraryService`：P2b 明确不做。

## 3. 已知问题与技术债（汇总）
"建议阶段"为建议，开工前可与用户确认。

### 3.1 功能性已知问题
| # | 问题 | 来源 | 建议阶段 |
|---|---|---|---|
| K1 | ✅ **P3b 已修**（`c88a11f`）：台标与通知封面遵守按列表明文开关（请求头 → 守卫策略，含重定向） | P1 KI1 | 完成（待真机验证） |
| K2 | TV 上 NavigationDrawer 常驻（非一级页面内容为空），引导 / 添加 / 播放页焦点待真机确认 | P1 KI4 | 真机验收后决定（P3） |
| K3 | 手机频道 / 播放列表页底部可能多一段系统栏 inset（Scaffold 嵌套） | P1 KI5 | 真机验收后修（P3） |
| K4 | 播放页手机 / 平板强制横屏，无竖屏观看 | P1 KI6 | 视用户反馈 |
| K5 | 外挂字幕在部分 DASH / 直播流可能时间错位（`MergingMediaSource`） | P2a KI1 | 视真机结果 |
| K6 | 外挂字幕"（外挂）"标记依赖 `Format.id` 前缀 `streambox-ext-` | P2a KI2 | 低优先 |
| K7 | 播放页切画面比例会同时改"默认画面比例"，无"仅本频道"比例 | P2a KI3 / P2b | P3 或 P7 |
| K8 | ✅ **P3b 已修**（`c88a11f`）：「播放」拆为二级页，单选项一行 + 对话框 | P2a KI4、P2b KI10 | 完成（待真机验证） |
| K9 | 返回键删台号位只有单测，`BackHandler` 拦截无 UI 测试 | P2a KI5 | 补 Compose UI 测试时 |
| K10 | 「仅软件解码」实为软件优先：无对应软解时仍用硬解 | P2a KI8 | 接受（FFmpeg 暂不接） |
| K11 | 从最近任务划掉 App 即停止播放（`onTaskRemoved` release） | P2b KI1 | 接受 / 视反馈 |
| K12 | 后台回到前台画面 1–2 s 后才出现（视频轨被禁用后需等关键帧） | P2b KI2 | 视反馈 |
| K13 | 后台播放开启时，前台播放也显示媒体通知 | P2b KI3 | 视反馈 |
| K14 | ✅ **P3b 已修**（`c88a11f`）：前台处理 PLAY_PAUSE / PLAY / PAUSE / STOP / HEADSETHOOK | P2b KI4 | 完成（待真机验证） |
| K15 | 画中画小窗内仍显示字幕 | P2b KI6 | 低优先 |
| K16 | Fill / Stretch 下 PiP `sourceRectHint` 为近似，进入动画可能跳变 | P2b KI7 | 低优先 |
| K17 | 带 DVR 的直播不能拖进度 / 双击快进退 | P2b KI8 | **P5** |
| K18 | ✅ **P3a 已修**（`460ac22`）：上次频道改存 playlistId + uniqueKey，刷新后仍能续播 | P2b KI9 | 完成（待真机验证） |
| K19 | 睡眠定时不持久化、无默认时长设置 | P2b 没做 | P7 |
| K20 | ✅ **P3b 已修**（`4ac076d`）：`PlayerRoute` 带 playlistId + uniqueKey，id 失效时按 key 解析；频道真的被删除时仍显示"频道不存在" | P1 KI8 | 完成（待真机验证） |
| K21 | 切台每次读取当前来源（含排序）的全部 id；「全部」+ 10 万频道在低端盒子上可能有 100 ms 级延迟（未实测） | P3b KI1 | 视真机结果（键集查询 / 缓存） |
| K22 | 名称排序为 Unicode 码位 + ASCII 忽略大小写，中文不是拼音序 | P3b KI2 | 视用户反馈 |
| K23 | 英文搜索为词前缀（"overy" 搜不到 "Discovery"）；中文查询走 LIKE 全表扫描 | P3b KI3 | 视反馈（可升 FTS 分词器，需迁移） |
| K24 | 最近观看在打开播放页时冻结顺序；收藏移动会把当时缺失的收藏移出自定义顺序 | P3b KI4/5 | 接受 |
| K26 | ✅ **已修复待真机验收**（提交 4b462f3 + 1936849）：接入 NextLib FFmpeg 音频扩展（GPL-3.0；含 AAC/AC-3/E-AC-3/DTS/TrueHD/MP3，**不含 MP2**）、默认 MediaCodec 优先 FFmpeg 兜底、音轨回退、关闭 tunneling/offload、无法播放时提示、播放面板「流信息」+ logcat 标签 `StreamBoxPlayer`。原描述：真机（realme GT6 手机和 / 或 TV）**部分频道有画面没有声音**（暂无样例 URL）。初步诊断：IPTV 的 TS / HLS 常用 AC-3 / E-AC-3 / MP2 / DTS 音频，设备 MediaCodec 不支持时 Media3 的轨道选择器不选任何音轨，画面照常播放、无报错（`DefaultRenderersFactory` 未开扩展渲染器）。计划：① 接入与 Media3 1.11.1 对齐的预编译 FFmpeg 音频解码扩展（候选 `io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.18.0`，**GPL-3.0**，需在文档记录许可证影响），默认 MediaCodec 优先、FFmpeg 兜底（「仅软件解码」时 FFmpeg 优先）；② 轨道选择回退到可播放的音轨，显式关闭 tunneling / audio offload；③ 有音轨但都无法播放时显示非致命提示（中英）；④ 播放面板「流信息」与 logcat 显示音视频编码与解码器，便于用户反馈。先写失败测试再修 | 真机反馈（2026-11-18） | **P3c Part 0（当前）** |
| K25 | TV 频道页不再显示悬浮"添加"按钮（在"管理播放列表"添加）；DropdownMenu / 长按 OK 菜单的 TV 焦点待真机确认 | P3b KI6 | 真机验收后决定 |

### 3.2 技术债与文档 / 实现不一致
| # | 问题 | 建议 |
|---|---|---|
| D1 | P0 报告称"广告/内购仅接口与空实现"已做，**实际仓库没有 monetization 代码或模块** | P8 按用户要求建接口 + NoOp；或更早补上并修正 P0 报告 |
| D2 | 部分解决（P3a）：core:database 已有 27 项 Robolectric DAO / 迁移测试；仍无 androidTest；core:model、core:designsystem、core:ui、feature:epg 无单测 | 后续阶段按需补 |
| D3 | Compose UI 测试（说明书第 10 节：添加列表、搜索、切台、设置修改）一个都没有 | 分阶段补；CI 需要模拟器 job |
| D4 | `app/build.gradle.kts` 注释引用 `docs/RELEASE.md`，文件不存在 | P8 补写（签名步骤目前见 `docs/BUILD-AND-ENV.md`） |
| D5 | ✅ **已修（`9c76878`）**：`:app:lintDebug` 报 `Instantiatable`（`PlaybackService must extend android.app.Service`）——app 的 lint 类路径上没有 `media3-session`。`fc5f0ad` 让 app 直接依赖 `core:player` 无效（media3-session 是 core:player 的 implementation）。最终修法：把 `<service>` 与 `FOREGROUND_SERVICE*` / `POST_NOTIFICATIONS` 权限移到 `core/player/src/main/AndroidManifest.xml`，由清单合并带入 APK，并撤回 app → core:player 依赖。CI 新增「Print lint report」步骤，把 lint 文本报告打印到日志 | 完成；剩余 4 个 warning 见 D10 |
| D10 | lint 4 个 warning：`localeConfig` 仅 API 33+ 生效（预期）；`network_security_config` base-config 允许明文（设计如此，由 `CleartextGuardInterceptor` 按列表拦截）；`allowBackup` 建议补 `dataExtractionRules`；缺 `drawable-hdpi/mdpi/xxhdpi`（仅 tv_banner） | P7（备份）补 `dataExtractionRules`；其余接受 |
| D6 | detekt 1.23.8 与 Kotlin 2.4 只跑非类型解析规则（R4） | 观察；detekt 2.x 稳定后升级 |
| D7 | `NavigationCommandsListener` 手写转发全部 `Player.Listener` 回调，Media3 升级新增回调时需补 | 升级 Media3 时检查 |
| D8 | `PlaybackController` 接口每次变更都要同步 `FakePlaybackController` | 约定（见 AGENTS.md） |
| D9 | 说明书 4.1 的 `#EXTVLCOPT` 之外的自定义 Header（`headersJson`）尚未用于播放 / 下载 | P6 / P7 |

## 4. 待用户真机验收清单
设备：**手机 realme GT6（Android 14 / 15）+ Android TV**。安装最新 debug APK（`com.streambox.player.debug`，可覆盖安装）。
完整逐条步骤在各阶段报告末尾，这里是索引与重点：

| 阶段 | 清单位置 | 重点 |
|---|---|---|
| P0 | `docs/phase-reports/P0.md` 末尾 | 手机 / TV 冷启动、免责声明、自适应导航、TV banner |
| P1 | `docs/phase-reports/P1.md`「P1 验收清单」 | https / http（明文开关）/ 本地文件导入；全屏播放、覆盖层、切台循环、404 错误、亮屏；分组刷新回落；底部空白（K3）；TV 焦点（K2） |
| P2a | `docs/phase-reports/P2a.md`「P2a 验收清单」 | 断网重连节奏与网络恢复；404 不重连；重连次数设置；缓冲档位；仅软件解码；清晰度 / 音轨 / 字幕 / 外挂 SRT·VTT / 字幕样式；六种比例；TV 菜单键 / 长按 OK / 左右键 / 数字台号 / 返回删位 |
| P3a | `docs/phase-reports/P3a.md`「真机验收步骤」 | **覆盖安装**验证 v1→v2 迁移；刷新播放列表后启动续播；TV 台号 2 秒 |
| P3b | `docs/phase-reports/P3b.md`「真机验收步骤」 | 设置二级页；虚拟分组与组内切台 / 台号；三视图与排序；收藏 / 最近观看页；搜索（含中文）；TV 双栏焦点与返回焦点；K1 http 台标；K14 媒体键；K20 |
| P2b | `docs/phase-reports/P2b.md`「P2b 验收清单」 | 通知权限；后台播放与媒体通知、锁屏、耳机 / 蓝牙切台、拔耳机暂停；PiP 自动进入 / 按钮 / 三按钮 / 比例；手势（亮度 / 音量 / 切台 / 拖进度 / 双击）；睡眠定时淡出；启动续播；自动下一个；TV 上设置项隐藏与媒体键（K14） |

用户反馈的问题应先写失败的测试再修，修复记入对应阶段报告或下一阶段报告。

## 5. 下一步的具体操作步骤
1. **读文档**：按 `AGENTS.md` 第 2 节的顺序读完。
2. ~~**修 CI（P2 收尾，先于 P3）**~~ ✅ 2026-11-18 已完成（`9c76878`，CI 全绿）：
   - 本地：`./gradlew ktlintCheck detekt test :app:assembleDebug --continue` 全绿（应为 348 项测试）。
   - 修技术债 D5（`:app:lintDebug` 的 `MissingClass`），顺带看那 4 个 lint warning；本地跑 `./gradlew :app:lintDebug :app:assembleRelease` 通过。修复属于代码改动，按 AGENTS.md 的流程先向用户说明再做。
   - 推送后在 `https://github.com/qaz69s/iptv/actions` 确认 `main` 的 CI 全绿（含 lintDebug、assembleRelease），向用户汇报。
3. ~~**P3 计划**~~ ✅ 2026-11-18 用户已确认（决定见第 0 节）。
4. ~~**P3a 数据层**~~ ✅ 2026-11-18 完成（`460ac22`、`3a57202`，CI 全绿，报告 `docs/phase-reports/P3a.md`）。
5. ~~**P3b 界面**~~ ✅ 2026-11-18 完成（`749c740`–`d73695e`，CI run 44261053492 全绿，报告 `docs/phase-reports/P3b.md`）。原计划如下（已全部完成，用户决定见第 0 节）：
   - 先把设置「播放」拆二级页（K8），顺手修 K1（Coil 台标明文）、K14（前台媒体键）。
   - 搜索（FTS 防抖 300 ms）：重命名后的名字不在 FTS 里——需决定"LIKE 补查 customName"还是"升 schema v3 把 customName 加进 FTS"（建议前者，零迁移）。
   - 收藏页 / 最近观看页 + 收藏按钮（`ToggleFavoriteUseCase`），历史清空入口（`WatchHistoryRepository.clear`）。
   - 虚拟分组「全部 / 收藏 / 最近观看」接入切台、台号、自动下一个、通知 / 耳机 / PiP 上下一个：需要定义收藏 / 最近观看内的切台顺序（建议：与列表显示顺序一致、循环；台号在虚拟分组内按列表序号）——**需用户确认**。
   - 播放页路由带 uniqueKey（修 K20 剩余部分）。
   - 三种视图（列表 / 网格 / 紧凑）、TV 左分组右频道双栏与焦点。
6. ~~等用户确认 P3c~~ ✅ 2026-11-18 用户已确认（7 项决定见第 0 节）。**当前顺序**：
   1. **Part 0：修 K26（有画面无声音）**，单独提交：先写失败测试（不支持音频的检测、FFmpeg 渲染器位置、轨道选择参数、ViewModel 提示）→ CI 按预期失败 → 接入 FFmpeg 音频扩展 + 轨道回退 + 提示 + 流信息 → CI 全绿。若比 P3c 早很多完成，另交付中间包 `StreamBox-audiofix-debug.apk`。
   2. **P3c 列表编辑器**：Room v3（自定义分组）+ 迁移测试 → 仓库 / 用例 → 编辑器界面（播放列表管理入口、频道长按快速隐藏 / 重命名）→ 文档与阶段报告 `docs/phase-reports/P3c.md`（含 K26 一节与验证音频修复的真机步骤）。
   - ✅ 2026-11-21 P3c 完成（`c810d98`，CI run 44875405245 全绿，报告 `docs/phase-reports/P3c.md`）。
   3. **下一步：等用户确认 P3c，再提 P4（EPG）计划**（范围、XMLTV 解析器放 core:parser、新依赖、需要用户确认的问题）。
7. 真机验收与开发并行：用户反馈的问题先写失败测试再修，记入当期阶段报告。
8. 每个子阶段按 `AGENTS.md` 第 3.2 节完成：测试 → 阶段报告 → 更新 README / ARCHITECTURE / implementation-plan / 本文件 → 推送并检查 CI → 中文汇报 + 交付 debug APK 与源码 zip → 等确认。
