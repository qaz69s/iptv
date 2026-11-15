# 项目技术需求说明书：Android IPTV 播放器（功能对标 Televizo）

> 使用方法：把本文件全部内容作为第一条消息发给 AI（推荐 Claude Code / Cursor 等能直接读写项目文件的工具）。让它按「第 12 节 开发流程」分阶段做，每阶段完成并通过验收后再进入下一阶段。

---

## 1. 角色与目标

你是一名资深 Android 工程师 + 架构师。请从零开发一款 **IPTV 播放器 App**，功能对标 Google Play 上的 Televizo（包名 com.ottplay.ottplay）。

- 产品定位：**纯播放器**，不提供任何视频内容，用户自行添加自己的播放列表（M3U/M3U8/Xtream Codes）和节目单（EPG）。
- 目标设备：手机、平板、Android TV（遥控器/D-pad 操作），同一套代码适配。
- 质量要求：生产级代码，可维护、可测试、无硬编码、无明显内存泄漏。

## 2. 合规与边界（必须遵守）

1. 这是**功能层面的独立实现**，不得复制原 App 的名称、图标、商标、界面素材、文案、代码或反编译内容。应用名、图标、配色请自行原创（暂定名：`StreamDeck Player`，可替换）。
2. App 内**不得内置、预置任何频道源或第三方版权内容**；首次启动是空状态，引导用户添加自己的播放列表。
3. 上架需在应用说明和首启页声明：“本应用仅为播放器，不提供内容，用户需自行承担所添加内容的合法性”。
4. 隐私：默认不收集个人数据；如接入广告/统计，必须有同意弹窗（GDPR/UMP）并提供隐私政策页面入口。

## 3. 技术栈（强制）

| 项 | 要求 |
|---|---|
| 语言 | Kotlin（最新稳定版），全部协程/Flow，禁止 RxJava |
| UI | Jetpack Compose + Material 3；TV 端用 Compose for TV（androidx.tv:tv-material） |
| 架构 | MVVM + Clean Architecture（presentation / domain / data 三层），单向数据流（UDF） |
| 依赖注入 | Hilt |
| 播放内核 | AndroidX Media3（ExoPlayer）+ HLS/DASH/RTSP/Progressive 模块；可选 FFmpeg 软解扩展（用于“仅软件解码”模式） |
| 本地数据库 | Room（含 FTS4/FTS5 全文搜索）；大列表使用 Paging 3 |
| 偏好存储 | DataStore (Preferences/Proto) |
| 网络 | OkHttp + Retrofit（Xtream API）+ kotlinx.serialization；M3U/XMLTV 自写流式解析器 |
| 后台任务 | WorkManager（播放列表/EPG 定时更新） |
| 投屏 | Google Cast SDK（Chromecast） |
| 图片 | Coil（频道 Logo，带磁盘缓存） |
| 构建 | Gradle Kotlin DSL + Version Catalog；minSdk 23，targetSdk 最新稳定版 |
| 测试 | JUnit5 + MockK + Turbine；Compose UI Test；解析器必须有单元测试 |
| 静态检查 | ktlint + detekt，CI 中必须通过 |

## 4. 功能需求

### 4.1 播放列表管理
- 支持多个播放列表，可添加/编辑/删除/排序/启用停用。
- 添加方式：
  1. **URL**（M3U / M3U8 / M3U_plus）
  2. **本地文件**（SAF 文件选择器）
  3. **Xtream Codes**（服务器地址 + 用户名 + 密码，拉取直播/电影/剧集分类与列表）
  4. **扫码/局域网推送**（TV 端：手机打开 App 内置的轻量 HTTP 页面或配套 App 输入 URL，推送到电视）— 二期
- M3U 解析要求（流式，禁止一次性读入内存）：
  - 支持 `#EXTM3U`、`#EXTINF`、`tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、`tvg-chno`、`catchup`、`catchup-source`、`catchup-days`、`timeshift`、`#EXTVLCOPT`（user-agent / referrer）、`#EXTGRP`
  - 支持 gzip 压缩、重定向、自定义 User-Agent / Header
  - 10 万行以上列表解析 < 10 秒，内存峰值 < 150MB
- 自动更新：每个列表可设置“手动 / 每次启动 / 每 N 小时”，由 WorkManager 执行，失败重试（指数退避），更新时保留用户收藏与排序。
- 列表编辑器：隐藏/显示分组、隐藏频道、重命名、自定义分组、频道排序。

### 4.2 频道浏览
- 分组（Group）→ 频道列表，支持“全部 / 收藏 / 最近观看”虚拟分组。
- 三种视图：列表、网格、紧凑；TV 端默认左侧分组 + 右侧频道的双栏。
- 搜索：全局搜索（频道名、分组名、当前节目名），输入即搜，防抖 300ms，基于 Room FTS。
- 排序：默认顺序 / 名称 / 频道号 / 自定义。
- 频道号直输：遥控器数字键切台，超时 2 秒确认。

### 4.3 播放器
- 全屏/小窗/画中画（PiP，手机端），横竖屏自适应，TV 端全屏。
- 协议：HLS、DASH、MPEG-TS（含 UDP/RTP 组播，通过 `udp://` `rtp://`）、RTSP、RTMP（通过扩展）、MP4/MKV。
- 硬解优先，失败自动回退软解；设置里提供「仅软件解码」开关。
- 轨道选择：视频清晰度、音轨、字幕（内嵌 + 外挂 SRT/VTT），字幕样式可调（大小/颜色/背景）。
- 画面比例：自适应 / 16:9 / 4:3 / 填充 / 拉伸 / 原始。
- 手势（手机）：左右滑动调进度（点播）、左侧上下滑亮度、右侧上下滑音量、双击左右各快退/快进 10 秒、双击中间暂停。
- 遥控器（TV）：上下切台、左右调音量/快进、OK 显示频道信息条、菜单键打开轨道/比例设置、数字键直输。
- 播放器覆盖层：频道名、Logo、当前/下一节目与进度条、时钟，3~5 秒自动隐藏。
- 缓冲与容错：可配置缓冲时长（低延迟/均衡/稳定三档）；网络断开自动重连，最大重试次数可配；播放失败给出明确原因（HTTP 状态码、解码错误、超时）。
- 自动播放下一集开关（默认**关闭**）；睡眠定时器（15/30/60/90 分钟/自定义，到点淡出并停止）。
- 记住上次观看频道，启动时可选“自动继续播放”。
- 后台音频播放（MediaSession + 前台通知，支持耳机/蓝牙控制）。

### 4.4 EPG（电子节目单）
- 支持 XMLTV（xml / xml.gz / URL / 本地）和 Xtream 内置 EPG。
- 频道与节目匹配：优先 `tvg-id`，其次 `tvg-name`，再其次规范化名称模糊匹配；允许用户手动指定匹配。
- 流式解析（SAX/Pull），入库批量事务；仅保留「前 1 天 ~ 后 N 天」可配置范围。
- 视图：
  - 频道内节目列表（今日/明日/一周）
  - **网格式 EPG（时间轴）**：横向时间、纵向频道，支持滚动与焦点导航
  - 当前/下一节目显示在频道列表项上
- 节目详情：标题、描述、起止时间、分类、海报（如有）。
- 提醒：对未来节目设置提醒（本地通知 + 可选自动切台）。
- 时区与时间偏移：全局 EPG 时间偏移设置（±12 小时，0.5 小时步进），解决服务商时间错位问题。

### 4.5 回看 Catch-up / Timeshift
- 支持类型：`default`、`append`、`shift`、`flussonic`、`xc`（Xtream）、`vod`，以及自定义 `catchup-source` 模板（支持 `${start}` `${end}` `${timestamp}` `${offset}` `${duration}` `{utc}` `{lutc}` 等占位符，格式化规则可配置）。
- 入口：EPG 中已播出节目点“回看”；播放中进度条向左拖动（时移）。
- 回看天数取自 `catchup-days` 或 Xtream 的 `tv_archive_duration`。
- **重点**：时间计算必须严格区分 UTC / 本地时区 / EPG 偏移三套时间，并为此写单元测试（覆盖夏令时、跨日、负偏移）。

### 4.6 点播（Xtream Codes 模式）
- 电影、剧集（季/集）分类浏览，详情页（海报、简介、评分、时长）。
- 续播（记录进度）、已看标记、自动下一集（可关）。

### 4.7 收藏 / 历史 / 家长控制
- 收藏频道：跨播放列表更新后不丢失（以 `playlistId + 频道唯一键` 标识，唯一键规则：tvg-id → URL 哈希 兜底）。
- 观看历史：最近 50 条，可清空。
- 家长控制：4~6 位 PIN；可锁定指定分组/频道、锁定设置页、锁定播放列表编辑器；失败多次短暂冷却。

### 4.8 投屏
- Chromecast：发现设备、投射当前流、遥控（暂停/音量/停止）、断线恢复；对不可投射的流（需自定义 Header 的）给出提示或走代理转发。

### 4.9 设置
外观（主题：浅/深/跟随系统/AMOLED，主色，字体缩放）、播放（解码模式、缓冲档位、默认比例、自动继续、重连次数、网络类型限制）、EPG（更新频率、保留天数、时间偏移）、播放列表更新策略、语言（至少：中文简体、English）、备份与恢复（导出/导入 JSON：列表配置、收藏、设置，不含敏感密码明文——密码用 Android Keystore 加密）。

### 4.10 变现（可选，默认关闭）
- 免费版含轻量广告（横幅，不得出现在播放界面）；内购“去广告/高级功能”（Google Play Billing v7）。
- 先把广告与内购封装在 `monetization` 模块接口后面，业务代码不直接依赖具体 SDK。

## 5. 非功能需求

| 维度 | 指标 |
|---|---|
| 冷启动 | 中端机 < 2 秒到可交互 |
| 列表滚动 | 1 万频道列表保持 60fps（分页 + 稳定 key + 图片降采样） |
| 内存 | 稳态 < 250MB；播放 24 小时无泄漏（LeakCanary 验证） |
| 切台 | 同源 HLS 切台首帧 < 2 秒（预加载相邻频道可选） |
| 稳定性 | Crash-free sessions ≥ 99.5% |
| 网络安全 | 默认 HTTPS；允许用户为单个播放列表开启“允许明文 HTTP”（因 IPTV 多为 HTTP）；证书校验不得全局关闭 |
| 安全 | 凭据加密存储；日志中脱敏 URL 里的 token/密码；Release 开启 R8 混淆 |
| 无障碍 | TalkBack 可用，TV 焦点环清晰，所有可点击元素有 contentDescription |
| 兼容性 | Android 6.0 ~ 最新；ARM64 / ARMv7；电视盒子低内存设备（1GB）降级策略 |

## 6. 架构与模块划分

```
app/                       应用入口、导航、Hilt 根
core/
  common/                  工具、Result、调度器
  designsystem/            主题、通用组件、TV 焦点组件
  network/                 OkHttp、拦截器、Xtream API
  database/                Room、DAO、迁移
  datastore/               设置存储
  player/                  Media3 封装、PlayerController、轨道管理、重连策略
feature/
  playlist/                播放列表管理、M3U/Xtream 导入
  channels/                分组、频道列表、搜索
  player-ui/               播放界面、手势、遥控器处理
  epg/                     XMLTV 解析、网格 EPG、提醒
  catchup/                 回看 URL 生成器（纯 Kotlin，可单测）
  vod/                     电影/剧集
  settings/                设置、家长控制、备份
  cast/                    Chromecast
  monetization/            广告与内购（接口 + 实现）
```
- feature 之间禁止互相依赖，只通过 domain 接口或导航参数通信。
- 解析器（M3U / XMLTV / Catch-up 模板）放在**纯 Kotlin 模块**，不依赖 Android，便于测试。

## 7. 数据模型（Room，初版）

- `Playlist(id, name, type[M3U_URL|M3U_FILE|XTREAM], url, username, passwordEncrypted, userAgent, headersJson, epgUrl, updateMode, lastUpdated, enabled, sortOrder)`
- `ChannelGroup(id, playlistId, name, hidden, sortOrder, locked)`
- `Channel(id, playlistId, groupId, uniqueKey, name, logoUrl, streamUrl, tvgId, tvgName, number, catchupType, catchupSource, catchupDays, hidden, sortOrder, locked)` + FTS 虚表
- `Program(id, epgChannelId, start, end, title, description, category, iconUrl)`，索引 `(epgChannelId, start)`
- `EpgMapping(channelId, epgChannelId, manual)`
- `Favorite(channelUniqueKey, playlistId, addedAt)`
- `History(channelId, watchedAt, positionMs)`
- `Reminder(programId, channelId, triggerAt, autoSwitch)`
- `VodItem / Series / Episode / Progress`（Xtream）

## 8. 关键实现要点（避免常见坑）

1. M3U/XMLTV 解析必须流式 + 分批入库（每 500~1000 条一个事务），解析在 `Dispatchers.IO`，UI 显示进度。
2. 播放列表更新采用「新数据写临时表 → 校验 → 原子替换」，失败不破坏旧数据。
3. 所有时间用 `Instant`/`java.time`，数据库存 UTC 毫秒；展示时再转时区；回看 URL 生成单独封装并充分单测。
4. Media3 的 `DataSource.Factory` 需按频道注入自定义 User-Agent / Referer / Headers。
5. 重连策略写成独立可测试类（指数退避 + 抖动 + 最大次数 + 网络恢复监听）。
6. TV 焦点：所有列表/按钮必须可聚焦，焦点状态可见，返回键行为一致，禁止焦点陷阱。
7. 屏幕常亮仅在播放时持有，暂停/退出必须释放。
8. 避免把 ExoPlayer 实例绑定到 Activity 生命周期：放入 Service/单例 PlayerController，配置变更不重建。

## 9. UI / UX 要求

- 遵循 Material 3；手机底部导航（频道 / 节目单 / 收藏 / 设置），平板与 TV 用侧边导航。
- 首启引导：欢迎页 → 合规声明 → 添加播放列表（URL / 文件 / Xtream）→ 导入进度 → 进入频道列表。
- 空状态、加载状态、错误状态（含重试按钮和具体原因）每个页面都要有。
- 所有字符串放 `strings.xml`，支持多语言；不得硬编码。

## 10. 测试与验收标准

**自动化**
- 单元测试覆盖：M3U 解析（≥ 30 个样例，含畸形行）、XMLTV 解析、频道-EPG 匹配、Catch-up URL 生成（≥ 20 个用例，含时区/夏令时）、重连策略、家长控制 PIN 逻辑。核心逻辑覆盖率 ≥ 80%。
- Room 迁移测试；Compose UI 测试覆盖：添加列表流程、搜索、切台、设置修改。

**手动验收清单（每阶段完成后逐条自测并在 PR/说明里打勾）**
- [ ] 全新安装后为空状态，无内置内容
- [ ] 添加 M3U URL / 本地文件 / Xtream 均可成功导入
- [ ] 10 万行列表导入不卡死、不 OOM
- [ ] 播放 HLS / TS / DASH 正常，断网后自动重连
- [ ] 轨道切换、字幕、比例切换正常
- [ ] EPG 正确匹配，网格 EPG 可滚动且焦点正确（TV）
- [ ] 回看在不同类型与时区下 URL 正确
- [ ] 列表更新后收藏、隐藏、排序保留
- [ ] 家长控制锁定生效
- [ ] Chromecast 可投射与控制
- [ ] 手机 / 平板 / TV 模拟器均可完整使用
- [ ] detekt / ktlint 通过，release 构建成功，无 LeakCanary 报警

## 11. 交付物

1. 完整可编译的 Android Studio 项目（Git 仓库，规范提交信息 Conventional Commits）。
2. `README.md`：构建步骤、模块说明、架构图（Mermaid）。
3. `ARCHITECTURE.md`：关键设计决策与取舍。
4. 单元/UI 测试与运行说明。
5. 签名配置模板（不含真实密钥）与 Release 构建说明。
6. 隐私政策模板与应用商店文案草稿。

## 12. 开发流程（请严格按阶段执行）

**开始前**：先输出你的实施计划（模块、依赖版本、风险点），并列出你需要我确认的问题；我确认后再动手。

| 阶段 | 内容 | 完成标志 |
|---|---|---|
| P0 | 工程脚手架：多模块、Hilt、导航、主题、CI、lint | 空壳 App 可运行（手机+TV） |
| P1 | 播放列表导入（M3U URL/文件）+ 频道列表 + 基础播放 | 能导入并播放 |
| P2 | 播放器完善：轨道、比例、手势、遥控器、重连、PiP、后台播放 | 验收清单播放项通过 |
| P3 | 搜索、收藏、历史、分组管理、列表编辑器 | 对应项通过 |
| P4 | EPG（XMLTV）+ 匹配 + 网格视图 + 提醒 | EPG 项通过 |
| P5 | 回看/时移 | 回看项通过 + 单测 |
| P6 | Xtream Codes（直播 + 点播） | Xtream 项通过 |
| P7 | 设置、家长控制、备份恢复、定时更新、多语言 | 对应项通过 |
| P8 | Chromecast、广告/内购（可选）、性能与稳定性优化、发布准备 | 全部验收通过 |

**每个阶段结束时你必须**：① 运行并通过全部相关测试；② 汇报做了什么、没做什么、已知问题；③ 更新文档；④ 等我确认再进入下一阶段。

## 13. 对 AI 的行为要求

- 不要一次性生成整个项目；按阶段、按模块小步提交，每步可编译可运行。
- 遇到需求歧义先问我，不要自行假设；做了假设要在说明里明确列出。
- 不使用已废弃的 API；依赖版本使用当前稳定版，并说明版本号。
- 不要写占位/伪代码/TODO 糊弄；做不到的功能明确说明原因和替代方案。
- 所有对外网络请求、权限申请都要说明用途；仅申请必要权限（INTERNET、ACCESS_NETWORK_STATE、FOREGROUND_SERVICE、POST_NOTIFICATIONS 等）。
- 修复 bug 时先复现并写失败的测试，再修复。
