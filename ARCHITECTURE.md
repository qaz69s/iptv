# 架构与关键设计决策

## 1. 分层与模块
- presentation（feature:* 的 Screen + ViewModel，UDF：ViewModel 暴露 `StateFlow`，UI 只发事件）
- domain/data：`core:data` 的仓库类是唯一的数据入口；实体直接用 Room Entity 作为领域模型（取舍：省去一层映射，代价是 UI 依赖 Room 类型）。
- feature 之间**不互相依赖**；跨 feature 的组合（例如播放页上的投屏按钮）由 `app` 通过 Composable 插槽参数注入。
- 解析器 / 回看 / 重连 / PIN 策略等纯逻辑放在纯 Kotlin 模块（`core:parser`、`core:common`、`feature:catchup`），JUnit5 直接跑。

## 2. 播放器
- `PlayerController` 是 `@Singleton`，持有 ExoPlayer，不绑定 Activity（§8.8）；旋转 / 配置变更不重建。
- `PlaybackService`（MediaSessionService）从 Controller 取同一个播放器实例 → 通知栏、锁屏、耳机/蓝牙按键、后台音频；Service 从不释放播放器。
- 解码模式 / 缓冲档位变化时，在下一次 `play()` 时重建 ExoPlayer，并通过 `playerFlow` 通知 UI 和 Service 重新绑定。
- 硬解优先：`EXTENSION_RENDERER_MODE_ON` + `setEnableDecoderFallback(true)`；仅软解：`EXTENSION_RENDERER_MODE_PREFER` + 只返回软件 MediaCodec 的 `MediaCodecSelector`。FFmpeg 扩展来自 Jellyfin 预编译包（版本须与 Media3 一致）。
- 每次 `play()` 重新构建 `OkHttpDataSource.Factory`，注入该频道的 UA / Referer / Header（§8.4）。
- 重连：`ReconnectPolicy`（纯 Kotlin，可单测）+ `NetworkMonitor`（网络恢复立即重试）；只对网络/超时/5xx/408/429 重试，4xx、解码错误直接报错。`BEHIND_LIVE_WINDOW` 自动跳回直播点。
- 屏幕常亮仅在 `Playing` 状态持有，离开页面必清除（§8.7）。

## 3. 列表更新与用户编辑
- 导入写入 `pending=1` 的行 → 成功后在一个事务里删除旧行、提升新行，再执行 `applyOverrides`；失败时只删除 pending 行，旧数据不受影响（§8.2）。
- 用户编辑（隐藏、重命名、排序、锁定、移动到自定义分组）都存在 `channel_override` / `group_override` 表里，用稳定标识作键：频道用 `playlistId + uniqueKey`（tvg-id → URL SHA-1 兜底），分组用原始组名。每次刷新后用 SQL 重新套用，所以编辑和收藏都不会丢（§4.1 / §4.7）。
- 列表行（`ChannelListItem`）在 SQL 里一次算出“是否收藏”“分组是否锁定”，避免逐行查询；P4 起还会带上当前节目。
- 排序在 SQL 里用 `CASE` 表达式切换：默认（导入顺序）、名称、频道号、自定义（用户调整后的 sortOrder）。

## 4. EPG
- XMLTV 用 XmlPull 流式解析（设备上用 Android 自带解析器，单测用 kXML2）；`core:parser` 只在编译期依赖 kXML2（`compileOnly`），所以不会和 Android 系统里的同名类冲突。
- 节目按播放列表存储，导入流程和频道一样：先写 pending 行，成功后原子替换，只保留 `[now−1 天, now+N 天]`。
- 自动识别 `.xml.gz`（读文件开头的 gzip 魔数），因为这类文件通常不带 `Content-Encoding` 响应头。
- 匹配顺序：tvg-id（区分大小写 → 不区分）→ tvg-name 与 display-name → 规范化名称（去掉国家前缀、画质后缀和标点，保留 “+1”）→ 编辑距离 ≤ 1（名称至少 6 个字符，按前 3 个字符分桶以控制计算量）。手动指定的匹配永远不会被自动匹配覆盖。
- 三套时间：数据库存 EPG 源里的原始时间（UTC 毫秒）；`EpgClock` 负责“真实时间 = 源时间 + 用户偏移”；界面按设备时区显示。查询时先把 now 换算成源时间，所以偏移设置改了立即生效，不需要重新导入。
- 提醒用 WorkManager 的一次性任务（不需要精确闹钟权限，代价是可能晚触发几分钟）；应用在前台且勾选了“自动切台”时，通过 `DeepLinkBus` 直接切过去。

## 5. 回看 / 时移
- `feature:catchup` 是纯 Kotlin 模块（只依赖 `java.time`），被 `core:data` 的 `StreamResolver.resolveCatchup` 调用。这里偏离了“feature 之间不依赖”的字面要求：它其实是一个没有界面的领域库，按说明书 §6 放在 feature 目录下。
- 回看用的起止时间都是**真实 UTC**（EPG 偏移已在 `EpgClock` 里加上）；只有人类可读的占位符（`{Y}`、`${start:pattern}`）才按时区格式化；Xtream 的 `YYYY-MM-DD:HH-MM` 使用可配置的服务器时区（默认 UTC）。
- 时移：用“当前绝对时间点 + 偏移量”生成 `[t, now]` 的回看流；超过 now−15 秒就回到直播；最早只能退到回看天数允许的位置。

## 6. Xtream Codes
- 通过 Retrofit 调用 `player_api.php`，响应体用 `@Streaming` 加 `Json.decodeToSequence` 逐条解码，几万条的直播或点播列表也不会一次性读进内存。
- 解析器很宽容：数字和字符串混用、空串、null，以及本该是数组却返回 `{}` 或按 id 分键的对象，都能处理（`XtreamParsers` 有单测覆盖）。
- 凭据安全：密码用 Keystore AES-GCM 加密后存储；数据库里的直播地址只存 `xtream:live/{id}`，EPG 地址只存 `xtream:xmltv` 这个标记，播放或下载时才拼出带凭据的完整 URL，所以带密码的地址永远不会落盘（日志也会脱敏）。
- 直播频道直接写进现有的频道表（共享收藏、编辑器、EPG、回看逻辑），唯一键为 `xc:{stream_id}`；`tv_archive=1` 的频道映射为 `catchup=xc`，回看天数取 `tv_archive_duration`，时区用面板返回的 `server_info.timezone`。
- 点播目录（分类、电影、剧集）同样用 pending 行原子替换；分集信息按需在线获取，内存里缓存最近 20 部剧。播放进度和“已看”标记分别以 `movie:{id}`、`episode:{id}` 为键保存。

## 7. 设置、家长控制、备份
- 设置存 DataStore（`AppSettings` 可序列化，直接放进备份）。界面语言放在 SharedPreferences，因为 `attachBaseContext` 必须同步读取；Android 13 以上还会自动生成 `locales_config`，可在系统的“应用语言”里切换。
- PIN：PBKDF2（API 26+ 用 SHA-256，更低版本用 SHA-1，哈希里记录所用算法）加随机盐；连续输错 5 次后锁定 30 秒，之后每多错一次锁定时间翻倍，最长 30 分钟（`PinPolicy` 是纯 Kotlin，可单测）。输对一次后 5 分钟内免输。
- PIN 生效范围：点击锁定的频道或分组、提醒和通知直达、启动自动续播（锁定的频道不会自动续播）、频道上下切换和数字直输（跳过锁定频道）、播放列表编辑器、设置页。
- 备份是 JSON，包含播放列表（不含密码）、收藏、编辑覆盖和设置。恢复时作为新播放列表追加、不覆盖现有列表，并重新映射播放列表 id；Xtream 列表需要重新输入密码。
- 明文 HTTP：网络层的拦截器默认拒绝 http 请求，只有用户为该播放列表打开开关后才放行；播放器的数据源也使用对应的客户端。系统层面的网络安全配置允许明文，这样每个列表单独的开关才能起作用；证书校验始终开启。
- 定时更新：每个播放列表可选手动、每次启动（一次性任务）、每 N 小时（周期任务）；失败时指数退避重试，最多 5 次；“不允许计费网络”时只在不计费网络下执行。

## 8. 投屏、变现、性能
- **Chromecast**：使用 Default Media Receiver（不需要注册自定义接收端）。会话开始或恢复时加载当前流，同时暂停本地播放，断开后恢复本地播放。Default Receiver 无法发送自定义 UA / Referer / 请求头，遇到这类流会明确提示无法投屏。本地代理转发（§4.8 的可选方案）没有实现，见 P8 报告。没有 Google Play 服务的设备（很多电视盒子）上不显示投屏按钮。
- **变现**：`feature:monetization` 只暴露 `AdsProvider` / `BillingProvider` 接口；当前绑定的是空实现，所以默认关闭。横幅插槽只出现在浏览类页签，播放页没有。
- **性能**：
  - 列表：分页（每页 60 条）、稳定的 key、Logo 按显示尺寸的 2 倍降采样解码、低内存设备上关闭淡入动画。
  - Coil 的内存缓存和磁盘缓存都有上限，低内存设备上更小。
  - 低内存设备上不使用“稳定”缓冲档（它的缓冲区最大）。
  - 解析和入库都是流式、分批进行。
- **发布**：release 开启 R8 和资源压缩；签名读取被 git 忽略的 `keystore.properties`；debug 包集成 LeakCanary。

## 已知取舍
见各阶段 `docs/P*_REPORT.md` 的「已知问题」。
