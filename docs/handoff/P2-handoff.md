# StreamBox Player P2 交接（给 P3 开发者）

> 原为 P2 结束时（2026-11-18）写给下一位开发者的代码级交接，已去掉原开发沙箱的路径。总体状态与后续计划见 `docs/DEVELOPMENT-STATUS.md`，工作流见 `AGENTS.md`，构建见 `docs/BUILD-AND-ENV.md`。

- 仓库：`https://github.com/qaz69s/iptv`，分支 `main`
- **P2 最终提交：`a669bf0`**（`a669bf085badf7568e42ca077a64651b45b197c2`）"P2b: docs and phase report"
- P2 各提交：P2a `99572cc`；P2b `3e14a71`（后台播放 + PiP）、`82bd0d1`（手势）、`7d25565`（睡眠定时 / 启动续播 / 自动下一个）、`a669bf0`（文档）。上一阶段 P1 `b1348f3`。
- 交付给用户的 APK：`StreamBox-P2-debug.apk`（debug，`com.streambox.player.debug`，versionName `0.1.0-debug`，21,404,227 字节，SHA-256 `9089a03c7ac0a9bdc4366f1b4cdfede6244bd549f3e8db3fa29de1c8b1bcd303`）；源码包 `streambox-player-P2-src.zip`（`git archive HEAD` @ `a669bf0`）。二者不在仓库中。
- 状态：348 项单测全过、ktlint / detekt 绿、`:app:assembleDebug` 通过；**从未真机运行**。阶段报告 `docs/phase-reports/P2a.md`、`P2b.md`（含已知问题与验收清单）。架构见 `ARCHITECTURE.md`（「播放生命周期」一节有 P2b 的生命周期表）。

## P3 范围（docs/implementation-plan.md）
搜索、收藏、历史、分组管理、列表编辑器、更新后保留收藏 / 排序。验收：DAO / 仓库测试 + 交互验收。

## 1. 与 P3 直接相关的现状
### 数据库（core:database，Room，schema version 1，导出在 `core/database/schemas/`）
- 实体：`PlaylistEntity`、`ChannelGroupEntity`、`ChannelEntity`、`ChannelFtsEntity`（FTS4，content = channels，列 `name`、`tvgName`；**已建表但还没有搜索查询 / UI**）。
- **分代导入**：导入写新 `generation`，校验后原子切换 `activeGeneration` 并删除旧代 → **每次刷新 / 重新导入频道 `id` 都会变**。
  - `ChannelEntity.uniqueKey`（tvg-id 优先、否则 URL 哈希，同列表内重复会去重加后缀；有索引 `(playlistId, uniqueKey)`）就是为 P3 收藏 / 历史跨刷新保持而留的。**收藏、历史请以 `(playlistId, uniqueKey)` 为键，不要存频道 id。**
  - `ChannelEntity` / `ChannelGroupEntity` 已有 `hidden`、`locked`、`sortOrder` 列（目前恒为默认值，未被 UI 使用），分组管理 / 列表编辑器可直接用；但它们随代重建，用户改动需要单独表保存并在导入时套用（"更新后保留排序"）。
- 加表需升级 schema 版本并写 Migration（`exportSchema` 已开）；DAO 查询都按 `p.activeGeneration` 过滤，新查询照做。
- `ChannelDao` 现有：`pagingSource(groupName)`、`observeGroups`、`observeChannelCount`、`playbackRow`（P2b 增 `playlistId`、`groupName` 列）、`position` / `nextAfter` / `previousBefore` / `first` / `last`（切台、自动下一个）、`idByNumber`（台号）。

### 上次频道（P2b）——P3 做历史时注意
- `core:domain` `LastChannelRepository`（`lastChannel: Flow<LastPlayedChannel?>`、`save`、`clear`），实现 `core/data/.../DefaultLastChannelRepository.kt`，存 DataStore 键 `last_played_playlist_id` / `last_played_channel_id`（**存的是频道 id**，重新导入后失效 → 已知问题 9）。
- 记录时机：`PlayerViewModel.rememberPlayingChannel`，频道首次 `isPlaying` 时、每个频道一次。**P3 的"历史"建议挂在同一位置**，并考虑把"上次频道"改为基于 `uniqueKey`（同时修掉已知问题 9）。
- 读取：`MainActivityViewModel.channelToResume()`（冷启动一次，频道不存在或已属其他列表则清除）。测试 `app/src/test/.../MainActivityViewModelTest.kt`。

### 频道导航接口（core:domain `ChannelRepository`）
`groups`、`channelCount`、`pagedChannels(groupName)`、`playbackItem(id)`、`adjacentChannel(id, group, direction)`（循环）、`nextChannel(id, group)`（P2b，不循环）、`channelIdByNumber(number, group)`。播放页的"来源分组"经路由参数 `PlayerRoute(channelId, groupName?)` 传入；**若 P3 加"收藏"虚拟分组，切台 / 台号 / 自动下一个 / 通知上下曲都走这里的 groupName，需要让这些查询认识收藏分组**（或给路由加来源类型）。

## 2. P2b 新增结构（播放相关，P3 一般不必改，但别破坏）
### core:player
- `PlaybackController`（单例，主线程）新增：`togglePlayPause()`、`pause()`、`setVolume(0..1)`（输出音量，重建保留，release 复位）、`setInBackground(Boolean)`（禁用 / 恢复视频轨）、`var channelNavigator: ChannelNavigator?`（`fun interface { fun zap(direction) }`，由 PlayerViewModel 注册）；`PlaybackState.ended`（点播播完）。媒体项带 `MediaMetadata`（频道名、台标）。
- `session/PlaybackService`：`MediaSessionService`，清单在 app 模块（`foregroundServiceType="mediaPlayback"`，不导出）。跟随 `player` Flow；`player == null` 即停止；`onTaskRemoved` → release。`PlaybackService.start(context)` 由 feature:player `BackgroundPlaybackEffect` 调用。
- `session/ChannelNavigationPlayer`（ForwardingPlayer，上一 / 下一 → zap；release → 控制器）+ `NavigationCommandsListener`（手写转发全部 Listener 回调；Media3 升级新增回调时要补）。
- `sleep/SleepTimer`（纯 Kotlin，`MonotonicClock` 注入，最后 10 s 淡出，到点 `onExpired`）。
- 测试：`ChannelNavigationPlayerTest`（5）、`SleepTimerTest`（12）。Fake：`feature/player/src/test/.../FakePlaybackController.kt`——**改 `PlaybackController` 接口时同步它**。

### feature:player
- `PlayerViewModel` 依赖：`SavedStateHandle`、`ChannelRepository`、`PlaybackController`、`SettingsRepository`、`LastChannelRepository`。持有 `SleepTimer`；`init` 注册 `channelNavigator`、订阅设置、记录上次频道、监听 `ended` 做自动下一个。
- 生命周期入口：`onStart()`、`onStop(appInBackground, inPictureInPicture)`、`onPictureInPictureModeChanged(inPip, stopped)`、`requestPictureInPicture()`、`onPipAction(action)`、`onCleared()`（取消定时 + release）。规则表见 `ARCHITECTURE.md`「播放生命周期」。`Activity.isMovingToBackground()` 在 `BackgroundPlayback.kt`。
- 手势：`PlayerGestures.kt`（纯规则，JVM 测试 13 项）+ `PlayerTouchGestures.kt`（亮度 / 音量接入）；`PlayerScreen.kt` 中 `pointerInput` 接入，TV / PiP / 设置关闭时停用。
- PiP：`PictureInPicture.kt`（`PictureInPictureEffect`、`pipAspectRatioOf`、`pipSourceRect`，JVM 测试 10 项）；小窗按钮走 App 私有广播 `${packageName}.action.PIP_CONTROL`。
- 面板：`PlayerPanel.kt` 首项 `SleepTimerSection.kt`，其后「画中画」（支持且开启时）、清晰度、音轨、字幕…
- 测试：`PlayerViewModelTest`（37）、`PlayerViewModelSleepAndNextTest`（15）、`PlayerGesturesTest`（13）、`PictureInPictureTest`（10）、`SleepTimerInputTest`（3）、`ChannelNumberInputTest`（6）。
- 代码注释小瑕疵：`PlayerViewModel.kt` 类注释与 `PlayerViewModelSleepAndNextTest.kt` 文件注释曾误写 "P2c"，已于交接文档提交中改为 P2b（仅注释）。

### app
- 清单：MainActivity `supportsPictureInPicture="true"`、`resizeableActivity="true"`；权限 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS`；`PlaybackService` 声明。
- `MainActivity` 冷启动把 `channelToResume` 传入 `StreamBoxApp` → `StreamBoxNavHost(resumeChannel)`，引导完成后 `navigateToPlayer(channelId, groupName = null)` 叠在频道页之上。

### core:ui
- `Context.supportsPictureInPicture(formFactor)`（非 TV、API 26+、`FEATURE_PICTURE_IN_PICTURE`），播放页与设置页共用。

## 3. 设置键（DataStore Preferences）
文件：`core/datastore/.../SettingsDataSource.kt`（`Keys`；类上 `@Suppress("TooManyFunctions")`）；模型 `core/model/.../PlaybackSettings.kt`；接口 `core/domain/.../SettingsRepository.kt`；Fake `core/testing/.../FakeSettingsRepository.kt`、`FakeLastChannelRepository.kt`、`FakeChannelRepository.kt`（P2b 加了 `nextChannel`）。

| 键 | 类型 | 默认 |
|---|---|---|
| `theme_mode` / `dynamic_color` | String / Boolean | System / false |
| `onboarding_welcome_seen` / `onboarding_disclaimer_accepted` | Boolean | false |
| `playback_buffer_profile` | String（`BufferProfile.name`） | Balanced |
| `playback_software_decoding_only` | Boolean | false |
| `playback_max_reconnect_attempts` | Int（0..20） | 5 |
| `playback_default_aspect_ratio` | String | Fit |
| `playback_subtitle_size` / `_color` / `_background` | String | Medium / White / SemiTransparent |
| `playback_background_playback`（P2b） | Boolean | **true** |
| `playback_picture_in_picture`（P2b） | Boolean | **true** |
| `playback_gestures`（P2b） | Boolean | **true** |
| `playback_resume_last_channel`（P2b） | Boolean | false |
| `playback_auto_play_next`（P2b） | Boolean | false |
| `last_played_playlist_id` / `last_played_channel_id`（P2b） | Long | 无 |

设置页「播放」分组在 TV 上已经很长（P2a 已知问题 4、P2b 已知问题 10）。**P3 再加设置项前，建议先把「播放」拆成二级页**（`feature/settings/.../SettingsScreen.kt`）。收藏 / 历史这类结构化数据放 Room，不要放 DataStore。

## 4. P2b 遗留（见 `docs/phase-reports/P2b.md` 已知问题）
划掉任务即停止播放；后台回前台画面 1–2 秒才出；后台播放开启时前台也有通知；前台时 TV 遥控播放 / 暂停键未处理；系统播放恢复 / Android Auto 不支持；PiP 中保留字幕；Fill / Stretch 下 sourceRectHint 为近似；带 DVR 的直播不能拖进度；重新导入后上次频道记录失效；TV 设置「播放」分组偏长；全部未真机验证。

## 5. 构建与测试数
- 构建、CI、签名、环境坑：见 `docs/BUILD-AND-ENV.md`。
- 完整校验：`ktlintCheck detekt`、`test`、`:app:assembleDebug`。APK 在 `app/build/outputs/apk/debug/app-debug.apk`，交付复制为 `StreamBox-<阶段>-debug.apk`。
- 测试数（P2 结束，348）：app 9、core:common 12、core:data 24、core:datastore 11、core:domain 10、core:network 8、core:parser 60、core:player 94、feature:channels 6、feature:onboarding 3、feature:player 84、feature:playlist 14、feature:settings 13（core:database、core:model、core:testing、core:designsystem、core:ui、feature:epg 暂无单测；P3 的 DAO 测试将是 core:database 的第一批）。
- 没有模拟器的环境只能验证编译与 JVM 测试；真机验证需用户（手机 realme GT6 + TV）。
- 提交前确认 `local.properties`、`keystore.properties` 未被跟踪（均在 .gitignore）。
