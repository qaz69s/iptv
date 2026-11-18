# 开发状态总览

> 更新于 2026-11-18（P2 完成后、P3 开工前）。本文件汇总：已开发什么、还要开发什么、所有已知问题与技术债、待用户真机验收的清单、下一步操作。
> 细节以各阶段报告 `docs/phase-reports/*.md` 为准；需求以 `docs/requirements.md` 为准。

## 0. 一眼看懂
| 阶段 | 状态 | 关键提交 | 单测数（累计） | 真机验收 |
|---|---|---|---|---|
| P0 脚手架 | ✅ 完成 | `4ef6a2c`、`8b4b6f9`（minSdk 24） | 22 | ❌ 未做 |
| P1 导入 + 频道 + 基础播放 | ✅ 完成 | `5173eda`（P1a）、`b1348f3`（P1b） | 168 | ❌ 未做 |
| P2a 播放稳定性 + 操控 | ✅ 完成 | `99572cc` | 255 | ❌ 未做 |
| P2b 后台 / PiP / 手势 / 定时 | ✅ 完成 | `3e14a71`、`82bd0d1`、`7d25565`、`a669bf0`（文档） | 348 | ❌ 未做 |
| P3 搜索 / 收藏 / 历史 / 编辑器 | ⏳ 下一阶段 | — | — | — |
| P4 EPG | 未开始 | — | — | — |
| P5 回看 / 时移 | 未开始 | — | — | — |
| P6 Xtream（直播 + 点播） | 未开始 | — | — | — |
| P7 设置 / 家长控制 / 备份 / 定时更新 / 多语言 | 未开始 | — | — | — |
| P8 投屏 / 变现接口 / 性能 / 发布 | 未开始 | — | — | — |

- **重要**：P0–P2 全部只在无模拟器的 aarch64 沙箱里经过「编译 + JVM 单测 + ktlint + detekt」验证，**从未在真机或模拟器上运行过**。
- GitHub CI：仓库在 P2 结束后才首次推送，CI 第一次运行发生在 2026-11-18（提交 `a669bf0` 及之后的文档提交）。CI 还会跑 `:app:lintDebug` 和 `:app:assembleRelease`（R8），这两项在沙箱中**从未跑过**，首次结果需要确认（见第 5 节）。
- 用户的真机：**手机 realme GT6（Android 14 / 15）+ 一台 Android TV**。

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

### 当前尚未存在的东西（供核对）
- 模块：`core:catchup`、`feature:vod`、`feature:cast`、`feature:monetization`（或 `core:monetization`）尚未创建；`feature:epg` 只有占位页。
- 依赖：WorkManager、Retrofit、Cast、Billing、Ads、UMP 已在 catalog 中，但**没有模块使用**。
- 收藏页（底部导航「收藏」）目前是空状态占位（`feature:channels` 的 `FavoritesScreen`）。
- `ChannelFtsEntity`（FTS4）已建表，但没有搜索查询和 UI。
- `ChannelEntity` / `ChannelGroupEntity` 的 `hidden`、`locked` 列存在但恒为默认值，`sortOrder` 只是导入顺序（无用户排序）；`PlaylistEntity` 有 `epgUrl`（导入时已写入，未被使用）、`updateMode`、`updateIntervalHours`、`enabled`、`username` / `passwordEncrypted`、`headersJson`，均未被功能使用。
- 仓库没有任何 `androidTest` 源码（无 DAO / 迁移 / Compose UI 测试）。

## 2. 还要开发什么（P3–P8）
阶段依赖：`P3 → P4 → P5`；`P6` 依赖 P3（收藏 / 历史键）、P4（EPG 存储与匹配）、P5（xc 回看）；`P7` 汇总设置 / 备份，需在 P3–P6 的数据结构稳定后做；`P8` 收尾。若用户要求调整顺序，以用户为准。

### P3 —— 搜索、收藏、历史、分组管理、列表编辑器
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
- **FFmpeg 软解扩展**（R5）：用户决定暂不接入。
- **UDP / RTP 组播**（R6）：`CHANGE_WIFI_MULTICAST_STATE` + MulticastLock、RTP 剥头 DataSource，"尽力而为"；尚未排入任何阶段，建议在 P8 或用户指定时做。
- **扫码 / 局域网推送**（说明书 4.1 第 4 种）：说明书标为二期，不在 P0–P8 范围。
- 系统播放恢复（Android 11+ 媒体控件）、Android Auto / `MediaLibraryService`：P2b 明确不做。

## 3. 已知问题与技术债（汇总）
"建议阶段"为建议，开工前可与用户确认。

### 3.1 功能性已知问题
| # | 问题 | 来源 | 建议阶段 |
|---|---|---|---|
| K1 | Coil 台标请求不受"按列表明文开关"约束（http 台标总会加载；只影响图片） | P1 KI1 | **P3** |
| K2 | TV 上 NavigationDrawer 常驻（非一级页面内容为空），引导 / 添加 / 播放页焦点待真机确认 | P1 KI4 | 真机验收后决定（P3） |
| K3 | 手机频道 / 播放列表页底部可能多一段系统栏 inset（Scaffold 嵌套） | P1 KI5 | 真机验收后修（P3） |
| K4 | 播放页手机 / 平板强制横屏，无竖屏观看 | P1 KI6 | 视用户反馈 |
| K5 | 外挂字幕在部分 DASH / 直播流可能时间错位（`MergingMediaSource`） | P2a KI1 | 视真机结果 |
| K6 | 外挂字幕"（外挂）"标记依赖 `Format.id` 前缀 `streambox-ext-` | P2a KI2 | 低优先 |
| K7 | 播放页切画面比例会同时改"默认画面比例"，无"仅本频道"比例 | P2a KI3 / P2b | P3 或 P7 |
| K8 | **TV 设置「播放」分组过长**（单选平铺 + P2b 又加 3 项） | P2a KI4、P2b KI10 | **P3 开头（拆二级页）** |
| K9 | 返回键删台号位只有单测，`BackHandler` 拦截无 UI 测试 | P2a KI5 | 补 Compose UI 测试时 |
| K10 | 「仅软件解码」实为软件优先：无对应软解时仍用硬解 | P2a KI8 | 接受（FFmpeg 暂不接） |
| K11 | 从最近任务划掉 App 即停止播放（`onTaskRemoved` release） | P2b KI1 | 接受 / 视反馈 |
| K12 | 后台回到前台画面 1–2 s 后才出现（视频轨被禁用后需等关键帧） | P2b KI2 | 视反馈 |
| K13 | 后台播放开启时，前台播放也显示媒体通知 | P2b KI3 | 视反馈 |
| K14 | **前台播放页未处理 TV 遥控器 MEDIA_PLAY_PAUSE 等媒体键**（后台经 MediaSession 可用） | P2b KI4 | **P3 顺手修** |
| K15 | 画中画小窗内仍显示字幕 | P2b KI6 | 低优先 |
| K16 | Fill / Stretch 下 PiP `sourceRectHint` 为近似，进入动画可能跳变 | P2b KI7 | 低优先 |
| K17 | 带 DVR 的直播不能拖进度 / 双击快进退 | P2b KI8 | **P5** |
| K18 | **重新导入播放列表后"上次频道"记录失效**（存的是频道 id） | P2b KI9 | **P3**（改用 uniqueKey） |
| K19 | 睡眠定时不持久化、无默认时长设置 | P2b 没做 | P7 |
| K20 | 进入播放页时频道已被刷新删除会显示"频道不存在" | P1 KI8 | 随 P3 uniqueKey 改造 |

### 3.2 技术债与文档 / 实现不一致
| # | 问题 | 建议 |
|---|---|---|
| D1 | P0 报告称"广告/内购仅接口与空实现"已做，**实际仓库没有 monetization 代码或模块** | P8 按用户要求建接口 + NoOp；或更早补上并修正 P0 报告 |
| D2 | P1 报告称 DAO / 迁移测试在 androidTest，**实际仓库没有任何 androidTest 源码**；core:database、core:model、core:designsystem、core:ui、feature:epg 无单测 | P3 起补 Room DAO / Migration 测试（androidTest 需模拟器，可在 CI 加模拟器 job，或引入 Robolectric——新依赖需先问用户） |
| D3 | Compose UI 测试（说明书第 10 节：添加列表、搜索、切台、设置修改）一个都没有 | 分阶段补；CI 需要模拟器 job |
| D4 | `app/build.gradle.kts` 注释引用 `docs/RELEASE.md`，文件不存在 | P8 补写（签名步骤目前见 `docs/BUILD-AND-ENV.md`） |
| D5 | CI 的 `:app:lintDebug`、`:app:assembleRelease`（R8）在沙箱从未跑过；CI 首次运行结果待确认 | 现在就看 CI，失败则先修 |
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
| P2b | `docs/phase-reports/P2b.md`「P2b 验收清单」 | 通知权限；后台播放与媒体通知、锁屏、耳机 / 蓝牙切台、拔耳机暂停；PiP 自动进入 / 按钮 / 三按钮 / 比例；手势（亮度 / 音量 / 切台 / 拖进度 / 双击）；睡眠定时淡出；启动续播；自动下一个；TV 上设置项隐藏与媒体键（K14） |

用户反馈的问题应先写失败的测试再修，修复记入对应阶段报告或下一阶段报告。

## 5. 下一步的具体操作步骤
1. **读文档**：按 `AGENTS.md` 第 2 节的顺序读完。
2. **确认构建与 CI**：
   - 本地：`./gradlew ktlintCheck detekt test :app:assembleDebug --continue` 全绿（应为 348 项测试）。
   - GitHub：打开 `https://github.com/qaz69s/iptv/actions`，看 `main` 最新一次 CI（含 lintDebug、assembleRelease）是否通过。失败则先修（这属于 P2 收尾），修完向用户汇报。
3. **跟进真机验收**：问用户 P1 / P2a / P2b 真机验收是否已做、有什么问题；有问题先修（先写失败测试）。用户也可以选择边做 P3 边验收——由用户决定。
4. **P3 开工前**：用中文向用户提交 P3 计划，至少包含：
   - 是否把 P3 拆为子阶段（建议：P3a 数据层——Room v2 迁移、收藏 / 历史 / 覆盖表、以 uniqueKey 为键、上次频道改造、DAO 测试；P3b 界面——搜索、收藏页、最近观看、虚拟分组接入切台 / 台号 / 自动下一个 / 通知上下曲、三种视图、TV 双栏；P3c 编辑器——分组 / 频道隐藏、重命名、排序、自定义分组、播放列表编辑 / 排序 / 启停）。
   - 先做"设置「播放」拆二级页"（K8）。
   - 顺手修 K1、K14、K18。
   - DAO 测试的运行方式（androidTest + CI 模拟器，或 Robolectric 新依赖），需要用户确认。
   - 需要用户回答的问题（如频道页是否也支持数字直输、收藏跨播放列表的排序规则）。
5. **等用户确认计划后再动手**，按 `AGENTS.md` 第 3.2 节的流程完成每个子阶段：测试 → 阶段报告 → 更新 README / ARCHITECTURE / implementation-plan / 本文件 → 推送并检查 CI → 中文汇报 + 交付 debug APK 与源码 zip → 等确认。
