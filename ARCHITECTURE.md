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
- **core:domain**：仓库接口（Settings / Playlist / Channel）与用例（CompleteOnboardingUseCase、AddPlaylistUseCase），不依赖 Android；`PagingData` 来自纯 Kotlin 的 paging-common。
- **core:parser**（JVM）：M3U/M3U8 流式解析器（逐行读取，不整体载入内存），无第三方运行时依赖，单测覆盖各类样例与 10 万行性能。
- **core:network**（JVM）：OkHttp 客户端与 `PlaylistDownloader`；`CleartextGuardInterceptor` 作为网络拦截器在每一跳（含重定向）按播放列表开关拦截明文 http；证书校验始终开启。MockWebServer 测试。
- **core:database**（Android）：Room（Playlist / ChannelGroup / Channel + FTS4），schema 导出到 `core/database/schemas`；频道按 `generation` 分代，导入写新一代、校验通过后原子切换，失败则丢弃，旧数据不受影响。
- **core:datastore**：DataStore Preferences 读写；读失败回退默认值。
- **core:data**：仓库实现与导入管线（下载/SAF 读取 → 流式解析 → 分批写入新一代 → 校验提交），导入在应用级作用域运行，离开页面不中断；Hilt 绑定。
- **core:player**（Android）：`PlaybackController` 应用级单例（Media3 ExoPlayer），按 URL 选择 HLS / DASH / RTSP / Progressive（无扩展名的 URL 先按 TS/MP4 嗅探，容器不识别时自动改用 HLS 一次）；按播放列表注入 User-Agent / Referer 与明文开关；`PlaybackErrorMapper` 把 Media3 错误码翻译为 HTTP 状态码 / 超时 / 网络 / 解码 / 格式等原因。
- **core:designsystem**：Material 3 主题（浅色 / 深色 / AMOLED 纯黑 / 动态取色）、TV 主题、图标、焦点环、空/加载/错误状态组件。
- **core:ui**：设备形态判断（手机 / 平板 / TV）、AppError 的本地化文案。
- **feature:onboarding / channels / epg / settings**：各自的界面、ViewModel、导航入口。
- **feature:playlist**：添加播放列表（URL / SAF 本地文件，Xtream 占位）、导入进度页、播放列表管理（刷新、删除、单列表明文开关）。
- **feature:player**：全屏播放页 `PlayerRoute(channelId, groupName?)`。覆盖层（频道号 + 名称、返回、切台按钮、提示）4 秒自动隐藏、点击/OK 显示；缓冲指示；错误原因 + 重试；手机上下滑动 / TV 方向键上下与 CHANNEL_UP/DOWN 切台（在来源分组内循环）；播放时保持亮屏；进入时隐藏系统栏并（非 TV）横屏，离开恢复；ON_STOP 释放播放器、回到前台重新播放，ViewModel 销毁时 release()。

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
- 播放：频道页只回调 `onChannelClick(channelId, groupName)`，由 app 模块导航到 feature:player 的 `PlayerRoute`；当前分组经路由参数传入（频道页自身的分组保存在 SavedStateHandle key `selectedGroup`），播放页据此在同一分组内切台。

## 播放生命周期
`PlaybackController` 是单例（不绑 Activity，配置变更不重建）。播放页 ViewModel 在 `onStart` 时 `play()`，`onStop` / 离开页面时 `release()`（释放解码器与网络；直播回到前台从直播点重新开始），`onCleared` 再次 `release()` 兜底。MainActivity 声明了 orientation 等 configChanges，横竖屏切换不会重建 Activity。P2 将加入轨道、重连策略、PiP、后台播放（MediaSession）。

## 单 APK 多形态
同一 APK 同时声明 `LAUNCHER` 和 `LEANBACK_LAUNCHER`，触摸屏与 leanback 均为非必需特性，并提供 TV banner。TV 判断依据系统 UI 模式 / leanback 特性，而非屏幕尺寸。
