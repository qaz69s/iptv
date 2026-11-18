# 架构

## 分层与模块
```
app ──► feature:* ──► core:ui ──► core:designsystem
 │          │            └──► core:common
 │          ├──► core:domain ──► core:model
 │          └──► core:player（仅 feature:player）──► core:network
 └──► core:data ──► core:datastore / core:database / core:network / core:parser
                └──► core:domain (实现其接口)
core:testing（仅测试）：Fake 仓库、Main 调度器扩展
```
- **core:model**：纯 Kotlin 数据类（AppSettings、Playlist、Channel、ImportStatus、PlaybackItem、PlaybackError…）。
- **core:common**：AppResult / AppError、协程调度器注入、URL 脱敏（日志中隐藏账号密码与 token）。
- **core:domain**：仓库接口（Settings / Playlist / Channel / LastChannel）与用例（CompleteOnboardingUseCase、AddPlaylistUseCase），不依赖 Android；`PagingData` 来自纯 Kotlin 的 paging-common。
- **core:parser**（JVM）：M3U/M3U8 流式解析器（逐行读取，不整体载入内存），无第三方运行时依赖，单测覆盖各类样例与 10 万行性能。
- **core:network**（JVM）：OkHttp 客户端与 `PlaylistDownloader`；`CleartextGuardInterceptor` 作为网络拦截器在每一跳（含重定向）按播放列表开关拦截明文 http；证书校验始终开启。MockWebServer 测试。
- **core:database**（Android）：Room（Playlist / ChannelGroup / Channel + FTS4），schema 导出到 `core/database/schemas`；频道按 `generation` 分代，导入写新一代、校验通过后原子切换，失败则丢弃，旧数据不受影响。
- **core:datastore**：DataStore Preferences 读写；读失败回退默认值。
- **core:data**：仓库实现（含 `DefaultLastChannelRepository`：DataStore 键 `last_played_playlist_id` / `last_played_channel_id`）与导入管线（下载/SAF 读取 → 流式解析 → 分批写入新一代 → 校验提交），导入在应用级作用域运行，离开页面不中断；Hilt 绑定。
- **core:player**（Android）：`PlaybackController` 应用级单例（Media3 ExoPlayer），按 URL 选择 HLS / DASH / RTSP / Progressive（无扩展名的 URL 先按 TS/MP4 嗅探，容器不识别时自动改用 HLS 一次）；按播放列表注入 User-Agent / Referer 与明文开关；`PlaybackErrorMapper` 把 Media3 错误码翻译为 HTTP 状态码 / 超时 / 网络 / 解码 / 格式等原因。P2a 新增组件：
  - `reconnect/`（纯 Kotlin）：`FailureKind`（可重试：网络、超时、HTTP 5xx / 408 / 429、落后直播窗口、直播断流；不可重试：其余 4xx、明文被拒、格式、解码、未知）、`ReconnectPolicy` + `ReconnectConfig`（1 s 起 ×2、上限 30 s、±20% 抖动，默认 5 次，0 = 关闭）、`Reconnector`（重连循环与状态 `ReconnectStatus`；网络恢复立即重试；稳定播放 30 s 计数清零）。
  - `FailureClassifier`：Media3 错误码 + HTTP 状态 / 明文标记 → `FailureKind`。
  - `BufferConfig`：低延迟 / 均衡 / 稳定三档 `DefaultLoadControl` 参数与直播目标延迟（低延迟 3 s、稳定 30 s）。
  - `DecoderFallback`：硬解优先，解码失败时以软件优先 `MediaCodecSelector` 重建一次；「仅软件解码」= 一开始就软件优先。
  - `NetworkMonitor`：默认网络回调 → `Flow<Boolean>`，供重连在网络恢复时立即重试。
  - `PlaybackTracks`：清晰度 / 音轨 / 字幕选项（`TrackId` 为 track group 下标，轨道变化后失效）；外挂 SRT / VTT 以 `MergingMediaSource` 合并，`Format.id` 前缀 `streambox-ext-` 标识。
  P2b 新增组件：
  - `session/PlaybackService`：Media3 `MediaSessionService`（前台服务 `mediaPlayback` + 媒体通知，不导出），跟随 `PlaybackController.player` Flow（重建时 `MediaSession.setPlayer`，null 时释放会话并 `stopSelf`）；`onTaskRemoved` 释放播放器。由 feature:player 的 `BackgroundPlaybackEffect` 在后台播放开启且有播放器时 `start()`（同时在 Android 13+ 请求一次通知权限）。
  - `session/ChannelNavigationPlayer`（`ForwardingPlayer`）：补齐 SEEK_TO_NEXT / PREVIOUS（及 *_MEDIA_ITEM）命令，把通知 / 锁屏 / 耳机 / 蓝牙的上一曲 / 下一曲映射为 `PlaybackController.channelNavigator.zap()`；`release()` 转给控制器。`NavigationCommandsListener` 手写转发全部 `Player.Listener` 回调，只改写 `onAvailableCommandsChanged` 的命令集。
  - `sleep/SleepTimer`（纯 Kotlin）：`start(minutes)`（截断到 1..600）/ `cancel()`，`state: StateFlow<SleepTimerState?>`；按 `MonotonicClock` 计算剩余时间，最后 10 s 经 `SleepTimerOutput.setVolume` 线性淡出，到点 `onExpired()`（暂停）后恢复音量。
  - `PlaybackController` 新增 `togglePlayPause` / `pause` / `setVolume`（播放器输出音量，重建保留、release 复位）/ `setInBackground`（禁用视频轨只放音频，回前台恢复并续播）/ `channelNavigator`；`PlaybackState.ended`（点播播完；直播结束仍走重连）；媒体项带 `MediaMetadata`（频道名、台标）供通知显示。
- **core:designsystem**：Material 3 主题（浅色 / 深色 / AMOLED 纯黑 / 动态取色）、TV 主题、图标、焦点环、空/加载/错误状态组件。
- **core:ui**：设备形态判断（手机 / 平板 / TV）、AppError 的本地化文案、`supportsPictureInPicture(formFactor)`（非 TV、Android 8.0+、系统支持 PiP）。
- **feature:onboarding / channels / epg / settings**：各自的界面、ViewModel、导航入口。
- **feature:playlist**：添加播放列表（URL / SAF 本地文件，Xtream 占位）、导入进度页、播放列表管理（刷新、删除、单列表明文开关）。
- **feature:player**：全屏播放页 `PlayerRoute(channelId, groupName?)`。覆盖层（频道号 + 名称、返回、切台按钮、画面比例 / 播放设置按钮、提示）4 秒自动隐藏、点击/OK 显示；缓冲指示；重连进度、软解回退提示；错误原因 + 重试；手机上下滑动 / TV 方向键上下与 CHANNEL_UP/DOWN 切台（在来源分组内循环）；播放面板（手机底部 ModalBottomSheet、TV 右侧 Dialog：清晰度、音轨、字幕、外挂字幕、字幕样式、画面比例）；`VideoSurface` 实现六种画面比例，`SubtitleLayer` 用 media3-ui `SubtitleView` 渲染字幕；TV 遥控器：菜单键 / 长按 OK 开面板、左右键快进退（点播）或调音量（直播）、数字键台号输入（`ChannelNumberInput`：1.5 s 超时 / 满 4 位 / OK 提交，返回键删位）；播放时保持亮屏；进入时隐藏系统栏并（非 TV）横屏，离开恢复；ON_STOP / 后台 / PiP 的处理见下文「播放生命周期」。P2b 新增：
  - **PiP**（`PictureInPicture.kt` 的 `PictureInPictureEffect`）：仅 `supportsPictureInPicture` 为真时生效；维护 Activity 的 `PictureInPictureParams`（比例 `pipAspectRatioOf`、`sourceRectHint` = `pipSourceRect`、上一个 / 播放·暂停 / 下一个三个 `RemoteAction`，经 App 私有广播回到 `PlayerViewModel.onPipAction`）；Android 12+ `setAutoEnterEnabled`，8.0–11 由 user-leave hint 进入；面板「画中画」按钮经 `pictureInPictureRequests` 立即进入；离开播放页时关闭自动进入。允许进入的条件：设置开启、有频道、无错误、面板未开。
  - **手势**：规则在纯 Kotlin 的 `PlayerGestures`（分区 `TouchZone`、方向锁定 `DragKind`、进度 / 亮度 / 音量换算、双击 `DoubleTapAction`），接入在 `PlayerTouchGestures.kt`（窗口亮度，离开时恢复；`STREAM_MUSIC` 音量）。竖滑左 1/3 亮度、中 1/3 切台、右 1/3 音量；横滑拖进度（仅 `isSeekable`，整屏 = 120 s，松手跳转）；双击左 −10 s / 中暂停播放 / 右 +10 s。手势关闭、TV、PiP 中不启用（关闭时保留点按与竖滑切台）。
  - **睡眠定时**：`PlayerViewModel` 持有 `SleepTimer`（输出接 `PlaybackController.setVolume` / `pause`），面板首项 `SleepTimerSection`（关 / 15 / 30 / 60 / 90 / 自定义 1–600 分）；后台与 PiP 期间继续计时，不持久化。
  - **上次频道 / 自动下一个**：频道首次开始播放时经 `LastChannelRepository.save` 记录；`PlaybackState.ended` 且设置「自动播放下一个」开启时调用 `ChannelRepository.nextChannel(channelId, 来源分组 ?: 自身分组)`，最后一个之后不绕回。
- **feature:settings**：外观分组；「播放」分组（缓冲档位、仅软件解码、自动重连次数 0/3/5/10、默认画面比例；P2b：后台播放、画中画（仅支持 PiP 的手机 / 平板）、播放手势（非 TV）、启动时继续播放上次频道、自动播放下一个）。播放设置模型 `PlaybackSettings` 位于 core:model，作为 `AppSettings.playback` 由 DataStore 持久化，PlayerViewModel 订阅后经 `PlaybackController.applySettings` 下发。
## 约束（由 build-logic 强制）
- feature 模块之间**禁止互相依赖**，只能通过 core:domain 或导航路由协作；违反时 Gradle 配置阶段直接报错。
- 所有模块的 Android / Kotlin / Compose / 测试配置集中在 `build-logic` 约定插件中。

## UI 状态
ViewModel 对外暴露 `StateFlow<UiState>`，界面用 `collectAsStateWithLifecycle` 收集；界面 Composable 无状态，便于预览和测试。

## 导航
类型安全路由（`@Serializable` 对象）。首次启动进入 onboarding 图，完成后跳到频道页并清除引导页回退栈。
- 手机：底部导航栏；平板 / 大窗口：侧边导航轨（NavigationSuiteScaffold 自动切换）。
- TV：Compose for TV 侧边抽屉，D-pad 左键展开；所有可交互元素有清晰焦点环；进入页面自动聚焦主按钮。
- 返回键：非频道页的一级页面先回到频道页，再退出。
- 启动续播（P2b）：`MainActivity` 仅在冷启动（`savedInstanceState == null`）把 `MainActivityViewModel.channelToResume` 交给 `StreamBoxNavHost`；完成引导后若设置开启且记录的频道仍存在（同一播放列表），在频道页之上 `navigateToPlayer`，返回回到频道页；频道已不存在则清除记录。每个 ViewModel 只回答一次。
- 播放：频道页只回调 `onChannelClick(channelId, groupName)`，由 app 模块导航到 feature:player 的 `PlayerRoute`；当前分组经路由参数传入（频道页自身的分组保存在 SavedStateHandle key `selectedGroup`），播放页据此在同一分组内切台。

## 播放生命周期
`PlaybackController` 是单例（不绑 Activity，配置变更不重建）。MainActivity 声明了 orientation 等 configChanges，横竖屏切换不会重建 Activity。P2a 起：可重试的失败由 `Reconnector` 按退避自动重连（换台、手动重试、release 时重置）；缓冲档位与解码模式在下一次 `play()` 时生效（配置变化时重建 ExoPlayer），重连次数立即生效。

P2b 起播放页（`PlayerEntry` 的 `LifecycleStartEffect`）按以下规则处理：
| 事件 | 处理 |
|---|---|
| ON_START | `onStart()`：若处于后台模式则 `setInBackground(false)`（恢复视频轨、被暂停则续播）；控制器不在播当前台则 `play()`；显示覆盖层。 |
| ON_STOP，且 PiP 窗口仍在显示（如小窗时锁屏） | `onStop(inPictureInPicture = true)`：什么都不变，继续播放。 |
| ON_STOP，整个 App 进入后台（`Activity.isMovingToBackground()`：Home、锁屏、切应用；不含 App 内导航、finish、配置变更），设置「后台播放」开、当前台在播且无错误 | `setInBackground(true)`：禁用视频轨只放音频；`PlaybackService` 保持媒体通知。 |
| 其余 ON_STOP / 离开页面 / 后台播放关闭 / 出错 | `release()`：释放解码器与网络，`player` 变为 null → `PlaybackService` 释放会话并停止；直播回到前台从直播点重新开始。 |
| 进入 PiP | 隐藏覆盖层 / 面板 / 台号输入，手势停用，播放继续。 |
| 离开 PiP：展开 | 显示覆盖层，回到全屏。 |
| 离开 PiP：关闭小窗（Activity 已不在 STARTED） | 按"App 进入后台"处理：后台播放开则只放音频，否则 release。 |
| 从最近任务划掉 | `PlaybackService.onTaskRemoved`：release 并停止服务。 |
| ViewModel `onCleared` | 取消睡眠定时，`release()` 兜底。 |

`PlaybackService` 在设置「后台播放」开启且有播放器时就已启动（前台播放也有通知）。睡眠定时由 PlayerViewModel 持有，后台与 PiP 期间继续计时。

## 单 APK 多形态
同一 APK 同时声明 `LAUNCHER` 和 `LEANBACK_LAUNCHER`，触摸屏与 leanback 均为非必需特性，并提供 TV banner。TV 判断依据系统 UI 模式 / leanback 特性，而非屏幕尺寸。
