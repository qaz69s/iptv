# 实施计划 v1（待确认）

> 依据：docs/requirements.md（第 12、13 节流程）。版本号于 2026-11-15 从 Google Maven / Maven Central / Gradle Plugin Portal 实时核对，均为当时最新稳定版。

## 1. 模块划分

在说明书第 6 节基础上，把解析器拆成纯 Kotlin/JVM 模块，并给每个 feature 配 domain 接口，避免 feature 互相依赖。

| 模块 | 类型 | 职责 |
|---|---|---|
| `app` | Android app | 入口 Activity（手机 + TV Leanback 入口）、NavHost、Hilt 根、自适应导航（底部栏 / NavigationRail / TV 侧栏） |
| `build-logic/convention` | Gradle included build | 约定插件：android-library、compose、hilt、jvm-library、test、detekt/ktlint，统一 SDK 与编译选项 |
| `core:common` | JVM | Result/AppError、DispatcherProvider、时钟抽象 `Clock`、URL 日志脱敏 |
| `core:model` | JVM | 领域实体（Playlist、Channel、Program、CatchupSpec…） |
| `core:domain` | JVM | Repository 接口 + UseCase（feature 只依赖这里） |
| `core:parser` | JVM | M3U 流式解析器、XMLTV Pull 解析器（纯 Kotlin，配大量单测） |
| `core:catchup` | JVM | 回看 URL 生成器（说明书的 feature/catchup 下沉为纯 Kotlin 核心模块，供 player-ui 与 epg 共用） |
| `core:network` | Android lib | OkHttp（gzip/重定向/自定义 Header/UA、按列表允许明文）、Retrofit Xtream API |
| `core:database` | Android lib | Room 实体/DAO/FTS4、迁移、Paging Source |
| `core:datastore` | Android lib | DataStore 设置；Keystore 加密的凭据存储 |
| `core:data` | Android lib | Repository 实现、导入管线（临时表 → 校验 → 原子替换）、WorkManager Worker |
| `core:player` | Android lib | PlayerController 单例 + MediaSessionService、按频道注入 Header 的 DataSource.Factory、重连策略、轨道管理 |
| `core:designsystem` | Android lib | M3 主题（浅/深/AMOLED/动态色）、通用组件、TV 焦点组件、空/加载/错误状态组件 |
| `core:ui` | Android lib | 共享 UI 工具：WindowSizeClass、设备类型（手机/平板/TV）判断、字符串资源 |
| `feature:onboarding` | Android lib | 欢迎页 → 合规声明 → 添加列表 → 导入进度 |
| `feature:playlist` | Android lib | 列表管理（URL / SAF 文件 / Xtream）、列表编辑器 |
| `feature:channels` | Android lib | 分组/频道（列表/网格/紧凑）、搜索、数字直输 |
| `feature:player` | Android lib | 播放界面、覆盖层、切台（P1）；轨道、字幕、比例、遥控器（P2a 已实现）；手势、PiP、睡眠定时、自动下一个（P2b 已实现）。原计划名 `feature:player-ui`，P1 实际命名为 `feature:player` |
| `feature:epg` | Android lib | 频道节目单、网格 EPG、提醒 |
| `feature:vod` | Android lib | 电影/剧集、续播 |
| `feature:settings` | Android lib | 设置、家长控制、备份恢复 |
| `feature:cast` | Android lib | Chromecast（仅手机/平板） |
| `feature:monetization` | Android lib | 广告/内购接口 + 空实现（`NoOp`）+ 可选真实实现 |

规则：feature 只依赖 `core:*`；跨 feature 跳转只用导航路由（类型安全的 `@Serializable` route）。`core:parser`/`core:catchup`/`core:common`/`core:model`/`core:domain` 无 Android 依赖，可直接在 JVM 上跑测试。

## 2. 依赖与版本（2026-11-15 最新稳定版）

| 组件 | 版本 |
|---|---|
| Gradle | 9.9.1 |
| Android Gradle Plugin | 9.5.1（使用 AGP 9 内置 Kotlin 支持） |
| Kotlin | 2.4.21（Compose Compiler 插件同版本） |
| KSP | 2.3.14 |
| JDK（构建） | 17（AGP 9 最低要求） |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24（用户 2026-11-15 确认由 23 改为 24） |
| Compose BOM | 2026.09.00 |
| Compose for TV（tv-material） | 1.1.0 |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.11.0 |
| Navigation Compose | 2.10.2 |
| Core KTX | 1.19.1 |
| Hilt | 2.60.1；hilt-navigation-compose 1.4.0 |
| Media3（exoplayer/hls/dash/rtsp/datasource-okhttp/session/ui-compose） | 1.11.1（P1 实际使用：exoplayer、exoplayer-hls/dash/rtsp、datasource-okhttp、ui-compose 的 `ContentFrame`；ui（P2a，SubtitleView）；session（P2b，core:player 的 MediaSessionService）） |
| Media3 RTMP 扩展 | 无 1.11 稳定版（最新 1.12.0-beta01），见风险 R5 |
| Room | 2.8.5 |
| Paging | 3.5.3（core:domain 用 paging-common，feature:channels 用 paging-compose） |
| DataStore | 1.3.0 |
| WorkManager | 2.12.0 |
| Coroutines | 1.11.0 |
| kotlinx.serialization | 1.13.0 |
| OkHttp | 5.5.1（mockwebserver3 用于测试） |
| Retrofit | 3.0.0（kotlinx-serialization converter） |
| Coil | 3.6.3（coil-compose + coil-network-okhttp；P3b 加 coil-network-core（同版本，逐请求 NetworkHeaders，K1）） |
| Cast Framework | 23.0.0 |
| Play Billing（可选） | billing-ktx 9.1.0（说明书写 v7，v7 已过期弃用，按最新版） |
| Google Mobile Ads / UMP（可选） | 25.5.0 / 4.0.0 |
| JUnit 5 | 6.1.3（JUnit Jupiter 6，含 JUnit 5 API） |
| MockK / Turbine | 1.14.12 / 1.2.1 |
| Robolectric（P3a 新增，用户同意） | 4.18.1（2026-11-18 核对 Maven Central 最新稳定版；仅 core:database 单测，JUnit 4 运行） |
| ktlint | ktlint-cli 1.8.0，经 org.jlleitschuh.gradle.ktlint 14.2.0 |
| detekt | 1.23.8（见风险 R4） |
| LeakCanary | 2.14（仅 debug） |

## 3. 阶段计划（每阶段结束：跑测试 → 汇报做了/没做/已知问题 → 更新文档 → 等确认）

| 阶段 | 内容 | 本地可验证 | 需用户验收 |
|---|---|---|---|
| P0 | 多模块脚手架、约定插件、Version Catalog、Hilt、导航、主题、TV Leanback 入口、首启空状态、CI（GitHub Actions：ktlint + detekt + 单测 + assembleDebug）、README/ARCHITECTURE | 编译 + 单测 + lint | 手机/TV 模拟器启动空壳 |
| P1 ✅（沙箱完成，待真机验收） | M3U 流式解析器（≥30 样例）、URL/SAF 导入、Room + FTS、频道列表、基础播放 | 解析器单测、10 万行性能测试（JVM） | 实际导入并播放 |
| P2a ✅（沙箱完成，待真机验收） | 重连策略（独立可测）、缓冲档位、硬解失败回退软解、轨道 / 外挂字幕 / 字幕样式、画面比例、TV 遥控器（面板、音量 / 快进退、数字台号）、设置「播放」分组 | 重连策略 / 台号输入 / ViewModel 单测 | 播放清单项 |
| P2b ✅（沙箱完成，待真机验收） | 手势（亮度 / 音量 / 进度、双击）、PiP、MediaSession 后台播放（通知、耳机 / 蓝牙切台）、睡眠定时（淡出）、启动续播上次频道、自动播放下一个 | 定时 / 续播 / 手势 / PiP 比例 / ViewModel 单测 | 播放清单项 |
| P3（拆 P3a / P3b / P3c） | 搜索、收藏、历史、分组管理、列表编辑器、更新后保留收藏/排序。P3a ✅ 数据层（Room v2、收藏 / 历史 / 覆盖表、上次频道改 uniqueKey、台号 2 s）；P3b ✅ 界面（搜索、收藏页、最近观看、虚拟分组接入切台等、三种视图、四种排序、TV 双栏、设置二级页、K1/K14/K20）；P3c ✅ 编辑器（Room v3 自定义分组、隐藏 / 重命名 / 排序、播放列表启用与排序、频道长按快速编辑） | DAO/仓库测试（Robolectric） | 交互验收 |
| P4 | XMLTV 解析、EPG 匹配、网格 EPG、提醒 | 解析/匹配单测 | TV 焦点验收 |
| P5 | 回看/时移（≥20 用例含夏令时/跨日/负偏移） | 全部单测 | 真实源验证 |
| P6 | Xtream 直播 + 点播 | API/映射单测（MockWebServer） | 真实账号验证 |
| P7 | 设置、家长控制、备份恢复、定时更新、多语言（简中/英文） | PIN/备份单测 | 交互验收 |
| P8 | Chromecast、（可选）广告/内购、性能、R8 release、商店文案与隐私政策模板 | release 构建 | 全清单 |

### 阶段状态（2026-11-18）
- **P3c 完成（2026-11-21）**（CI run 44875405245 全绿，待真机验收；报告 `docs/phase-reports/P3c.md`）：Room v3 + `Migration2To3`；列表编辑器两级（分组 / 频道）；「我的分组」；播放列表启用停用与上移 / 下移；频道长按隐藏 / 重命名 / 加入分组。无新外部依赖、无新权限。下一步等用户确认后提 P4 计划。
- **P3c 已获用户确认（2026-11-18）**，决定见 `docs/DEVELOPMENT-STATUS.md` 第 0 节（编辑器入口、隐藏项显示、重排序切「自定义」、自定义分组 Room v3、停用保留数据 + 上移 / 下移排序、重名分组合并显示、不做 PIN）。开工前先修真机 bug **K26（部分频道有画面无声音）**：计划接入与 Media3 1.11.1 对齐的 FFmpeg 音频解码扩展（候选 NextLib `nextlib-media3ext` 1.11.1-0.18.0，GPL-3.0，见 R5）+ 音轨回退 + 不支持提示 + 流信息。
- **P3b 完成**（界面，CI run 44261053492 全绿，单元测试 468 项（+71），待真机验收；报告 `docs/phase-reports/P3b.md`）：设置「播放」二级页（K8）；全局搜索（FTS4 词前缀 + customName LIKE + 中文 LIKE，300 ms 防抖）；收藏页（上移 / 下移、删除、恢复时间序）与最近观看（移除、清空）；虚拟分组「全部 / 收藏 / 最近观看」，切台 / 台号 / 自动下一个 / 通知与 PiP 上下一个按来源与排序进行（用户决定：显示顺序、循环，自动下一个不循环，虚拟分组台号 = 组内序号）；`PlayerRoute` 带 source + playlistId / uniqueKey（K20）；列表 / 网格 / 紧凑三种视图与四种排序（DataStore）；TV 双栏。修 K1（台标明文，`CleartextHeaderCallFactory`）、K14（前台媒体键）。新 artifact：`coil-network-core` 3.6.3（同 Coil 版本）。无新模块、无新权限、Room 仍 v2。P3c 待用户确认后开始。
- **P3a 完成**（数据层，CI run 44248545012 全绿，单元测试 397 项（+49），待真机验收；报告 `docs/phase-reports/P3a.md`）：Room schema v2 + `Migration1To2`；收藏 / 观看历史 / 频道与分组覆盖表（以 `(playlistId, uniqueKey)` / `(playlistId, 分组名)` 为键，物化到频道表）；上次频道改为 uniqueKey（修 K18、K20 的续播部分）；台号输入超时 2 s。新依赖：Robolectric 4.18.1（testImplementation，core:database）；新约定插件 `streambox.android.robolectric`。无新模块、无新权限、无新用户可见字符串。P3b / P3c 待用户确认后开始。
- P0：完成（报告 `docs/phase-reports/P0.md`）。
- P1：完成，沙箱内 `assembleDebug test ktlintCheck detekt` 全绿；真机/TV 验收待用户（报告 `docs/phase-reports/P1.md`）。
- P2：拆为 P2a / P2b。**P2a 完成**，沙箱内 `assembleDebug test ktlintCheck detekt` 全绿，单元测试 255 项；真机（手机 realme GT6 + TV）验收待用户（报告 `docs/phase-reports/P2a.md`）。
- **P2b 完成**（沙箱完成，待真机验收）：沙箱内 `ktlintCheck detekt test :app:assembleDebug` 全绿，单元测试 348 项（+93）；真机（手机 realme GT6 + TV）验收待用户（报告 `docs/phase-reports/P2b.md`）。P2 整体交付包 `StreamBox-P2-debug.apk`。
- P2b 新增依赖：`androidx.media3:media3-session` 1.11.1（core:player）。新权限：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS`。无新模块。
- P2b 范围调整："自动继续播放"本阶段实现为启动时续播上次频道（默认关）；点播断点续播位置随 VOD 在 P6 做。系统播放恢复 / Android Auto 不做。
- P2a 新增依赖：`androidx.media3:media3-ui` 1.11.1（字幕渲染 `SubtitleView`）。无新模块。
- P1 新增依赖（版本见上表）：Room（runtime/ktx/paging/compiler/testing）、Paging（common/compose）、OkHttp + mockwebserver3、Media3（exoplayer、hls、dash、rtsp、datasource-okhttp、ui-compose）、Coil 3（compose + network-okhttp）。新增模块：core:parser、core:network、core:database、core:player、feature:playlist、feature:player。

## 4. 风险点与方案

- **R1 沙箱没有 JDK / Gradle / Android SDK**：我在沙箱内安装 Temurin JDK 17 + Android cmdline-tools（platform 37）+ Gradle Wrapper 9.9.1，用来编译和跑 JVM 单测、ktlint、detekt。模拟器和真机验收无法在沙箱里做，需要你在自己的 Android Studio 上完成。
- **R2 沙箱是 ARM64 Linux（aarch64）**：Google 官方只发布 x86_64 Linux 版的 AAPT2 和 build-tools，所以沙箱里很可能打不出 APK（资源编译这一步会失败）。方案：① 纯 Kotlin 模块（解析器、回看、重连、PIN）和 Robolectric 以外的单测在沙箱照常跑；② 用社区编译的 aarch64 版 aapt2，通过 `android.aapt2FromMavenOverride` 本地覆盖（只放在本机 gradle.properties，不进仓库）尝试完整构建；③ 最可靠的完整构建（assembleDebug/Release、Compose UI 测试）放到 GitHub Actions 的 x86_64 runner 上跑。这也是我建议用 GitHub 交付的主要原因。
- **R3 无法在沙箱跑模拟器**：Compose UI 测试 / 仪器测试写在仓库里，由 CI（Actions 上的模拟器）或你本地运行；每阶段给出可逐条勾选的手动验收清单。
- **R4 detekt 与 Kotlin 2.4**：detekt 稳定版 1.23.8 内置的是较老的 Kotlin 编译器，2.x 仍是 alpha。方案：用 1.23.8 跑不依赖类型解析的规则集（`detekt` 任务而非 `detektMain`）；若出现兼容错误，再切到 detekt 2.0 alpha 并说明。
- **R5 RTMP / FFmpeg 扩展**：Media3 RTMP 扩展目前没有与 1.11.1 对齐的稳定版；FFmpeg 软解扩展 Google 不发布预编译包（需用 NDK 自己编译，或用 Jellyfin 的预编译包 1.9.0+1，与 Media3 1.11 版本不对齐且有 GPL/LGPL 许可问题）。**更新（2026-11-18）**：为修 K26，用户要求接入 FFmpeg **音频**解码；Maven Central 上有与 Media3 1.11.1 对齐的预编译包 NextLib `io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.18.0`（GPL-3.0；其 FFmpeg 为 LGPL v3 构建，含 aac/ac3/eac3/dca/truehd/mp3 等解码器，不含 mp2），Jellyfin 包最新 1.9.0+1 与 1.11 不对齐。以下为原方案：RTMP 等 1.12 稳定版发布后接入；在此之前“仅软件解码”开关先用 Media3 自带的软件解码器优先策略（MediaCodec 选择器优先软件 codec），FFmpeg 扩展作为可选项，等你决定（见问题 5）。
- **R6 UDP/RTP 组播**：Media3 支持 `udp://`，但很多手机网络/路由不转发组播，需 `CHANGE_WIFI_MULTICAST_STATE` 权限并持有 MulticastLock；只在播放组播源时申请锁。RTP 组播需要剥 RTP 头，我会写一个小的 DataSource 封装，标为“尽力而为”。
- **R7 minSdk 24**（原说明书 23，用户确认改 24 以使用最新 Navigation/DataStore）：但 Android 6 不支持 PiP（需 8.0+）和部分 TLS 1.3。PiP 在低版本隐藏入口，不做降级模拟。
- **R8 明文 HTTP**：Network Security Config 只能全局或按域名配置，不能按“播放列表”配置。方案：全局允许明文（IPTV 刚需），由 OkHttp 拦截器 + Media3 DataSource 在代码层按列表开关拦截 http 请求；证书校验始终开启。会在 ARCHITECTURE.md 说明取舍。
- **R9 性能指标**（10 万行 < 10 秒、内存 < 150MB、1 万频道 60fps、冷启动 < 2 秒）：解析器在 JVM 上用生成的 10 万行样本做基准测试；设备上的帧率/冷启动需你用真机或 Macrobenchmark 验证。
- **R10 投屏限制**：需要自定义 Header 的流 Chromecast 不能直接播放；“代理转发”需在手机上起本地 HTTP 代理，耗电且对 TV 无意义。方案：先做提示，代理作为 P8 可选项。Cast 只在手机/平板启用。
- **R11 商标**：暂定名 “StreamDeck Player” 和 Elgato 的注册商标 “Stream Deck” 撞名，上架有被投诉风险，建议换名（见问题 1）。

## 5. 权限（仅必要）

INTERNET、ACCESS_NETWORK_STATE（重连/网络类型限制）、FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK（后台播放）、POST_NOTIFICATIONS（播放通知、EPG 提醒，Android 13+ 运行时申请）、WAKE_LOCK（播放时屏幕/CPU 常亮，由 Media3 管理）、SCHEDULE_EXACT_ALARM 不申请（提醒用 WorkManager/非精确闹钟，误差在几十秒内）、CHANGE_WIFI_MULTICAST_STATE（仅 UDP 组播）。文件导入用 SAF，无需存储权限。

## 6. 默认假设（如不同意请指出）

1. 只做中文简体 + English 两种语言。
2. TV 端用 Compose for TV（tv-material 1.1.0）组件，手机/平板用 M3，共用 ViewModel。
3. Git 提交遵循 Conventional Commits，每阶段一个或多个小提交。
4. 测试用 JUnit 5（Jupiter）跑 JVM 单测；Android 仪器测试和 Compose UI 测试按 AndroidX 要求用 JUnit 4 运行器（Compose UI Test 不支持 JUnit 5）。
