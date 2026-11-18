# AGENTS.md —— 给接手的 AI / 开发者

> 这是本仓库的 **AI 接手入口**。动手之前先读完本文件，再按「阅读顺序」读其他文档。
> 与用户沟通一律使用**中文**。

## 1. 项目是什么
**StreamBox Player**（`com.streambox.player`）：一款 Android IPTV **纯播放器**，功能对标 Televizo，覆盖手机 / 平板 / Android TV（同一个 APK）。
- **不内置、不预置任何频道源或内容**。首次启动是空状态，用户自己添加 M3U / M3U8 / Xtream Codes 播放列表和 XMLTV 节目单。
- 技术栈：Kotlin 2.4 · Jetpack Compose（Material 3）+ Compose for TV · Hilt · Room + Paging · DataStore · OkHttp · Media3 ExoPlayer · Coil 3 · 多模块 Clean Architecture。
- 当前进度：**P0、P1、P2（P2a + P2b）已完成，均未经真机验收**；下一阶段 **P3**。详见 `docs/DEVELOPMENT-STATUS.md`。
- GitHub：`https://github.com/qaz69s/iptv`，分支 `main`，push 触发 GitHub Actions CI。

## 2. 阅读顺序
1. **本文件**（工作流与约定，必须遵守）。
2. `docs/DEVELOPMENT-STATUS.md` —— 已做了什么、还要做什么（P3–P8 逐条）、所有已知问题与技术债、待用户真机验收清单、下一步操作。
3. `docs/requirements.md` —— **需求说明书，唯一的需求来源，不得修改**。
4. `docs/implementation-plan.md` —— 实施计划：模块、依赖版本、风险点 R1–R11、阶段表与阶段状态。
5. `ARCHITECTURE.md` —— 模块分层、各模块职责、播放生命周期、导航。
6. `docs/BUILD-AND-ENV.md` —— 构建、测试、签名、CI、环境坑。
7. `docs/phase-reports/P0.md`、`P1.md`、`P2a.md`、`P2b.md` —— 各阶段的做了 / 没做 / 已知问题 / 真机验收步骤。
8. `docs/handoff/P2-handoff.md` —— P2 结束时写给 P3 开发者的代码级交接（数据库分代、频道导航接口、设置键表、PlaybackController / PlayerViewModel 结构）。
9. `README.md` —— 面向用户的功能一览与构建命令。

## 3. 用户的开发要求与工作流（必须原样遵守）
这些是用户明确提出并确认过的要求，不得自行放宽。

### 3.1 范围
- **严格按 `docs/requirements.md` 开发**；纯播放器，**不内置任何内容**。需求有歧义先问用户，不自行假设；做了假设必须在阶段报告里写明。
- **按阶段 P0–P8 小步推进，不一次性生成整个项目**。每一步都要可编译、可运行。
- 一个阶段可拆成子阶段（如 P2 拆成 P2a / P2b），**每个子阶段同样走完整的汇报流程**。

### 3.2 每个阶段（或子阶段）的流程
1. **开始前**：先用中文向用户说明本阶段计划（范围、拆分、新依赖及版本、风险、需要用户确认的问题）。
2. 开发：小步提交（Conventional Commits 风格，现有提交多为 `Px: ...` / `Px part n: ...`，保持一致即可）。
3. **结束时，按顺序**：
   1. 跑测试并全部通过：**单元测试（`test`）+ `ktlintCheck` + `detekt` + `assembleDebug`**（命令见 `docs/BUILD-AND-ENV.md`）。
   2. 写 `docs/phase-reports/Px.md`，固定包含：**做了什么 / 没做什么 / 已知问题 / 真机验收步骤**（可勾选清单，分手机与 TV）。另写验证结果（测试数、APK 大小与 SHA-256）。
   3. 更新 `README.md`、`ARCHITECTURE.md`、`docs/implementation-plan.md`（阶段状态、新增依赖、范围调整），以及 `docs/DEVELOPMENT-STATUS.md`。
   4. 推送 `main`，**检查 GitHub Actions CI 结果**（CI 还会跑 `:app:lintDebug` 与 `:app:assembleRelease`）。CI 失败要修好再汇报。
   5. **用中文向用户汇报**，并交付 **debug APK** 与 **源码 zip**（`git archive HEAD`）。
   6. **等用户确认后才进入下一阶段。** 用户未确认之前不要开下一阶段的工作。

### 3.3 固定的产品决定
| 项 | 决定 |
|---|---|
| 应用名 | **StreamBox Player**（说明书暂定名 StreamDeck Player 与 Elgato 商标冲突，已改名） |
| applicationId | `com.streambox.player`；debug 构建后缀 `.debug`（`versionNameSuffix -debug`） |
| minSdk | **24**（说明书写 23，用户 2026-11-15 确认改为 24；**最终交付时必须注明这一偏差**） |
| compileSdk / targetSdk | 37 / 37（见 `gradle/libs.versions.toml`） |
| TV | 同一 APK 含 **TV Leanback 入口**（`LEANBACK_LAUNCHER` + banner），触屏与 leanback 均非必需 |
| 广告 / 内购（P8） | **只做接口 + 空实现（NoOp）**，不接入任何广告或计费 SDK |
| FFmpeg 扩展 | **暂不接入**。「仅软件解码」用 Media3 自带软件解码器优先策略实现 |
| RTMP | 等 Media3 RTMP 扩展出与当前 Media3 对齐的稳定版再接（风险 R5） |
| 语言 | 简体中文 + English |

### 3.4 依赖与仓库卫生
- **新依赖先查 Maven（Google Maven / Maven Central / Gradle Plugin Portal）最新稳定版**，写入 `gradle/libs.versions.toml`（Version Catalog），并在 `docs/implementation-plan.md` 的版本表里记录。不用废弃 API，不用 alpha/beta（除非用户同意）。
- **提交前确认 `local.properties`、`keystore.properties` 未被 git 跟踪**（`git ls-files | grep -E 'local.properties|keystore.properties$'` 应无输出）。不提交任何密钥、keystore、`*.jks`。
- 不写 `TODO:` / `FIXME:`（detekt 会拦）；不写占位 / 伪代码糊弄，做不到的在报告里写明原因与替代方案。
- 修 bug：先复现并写失败的测试，再修。
- 新增权限要说明用途，只申请必要权限。

## 4. 代码约定（按仓库实际情况）

### 4.1 模块与分层
```
app                 入口 Activity、NavHost、Hilt 根、自适应导航、启动续播
core:common   (JVM) AppResult / AppError、调度器注入、UrlRedactor（日志脱敏）
core:model    (JVM) 纯数据类（AppSettings、PlaybackSettings、Playlist、Channel、PlaybackItem…）
core:domain   (JVM) 仓库接口 + UseCase；feature 只依赖这里（paging-common）
core:parser   (JVM) M3U 流式解析器（P4 的 XMLTV 解析器也放这里）
core:network  (JVM) OkHttp、PlaylistDownloader、CleartextGuardInterceptor
core:database       Room（schema 导出在 core/database/schemas/，当前 version 1）
core:datastore      DataStore Preferences（SettingsDataSource）
core:data           仓库实现、导入管线、Hilt 绑定
core:player         PlaybackController 单例（Media3）、重连、缓冲、解码回退、MediaSessionService、睡眠定时
core:designsystem   主题、TV 焦点环、空/加载/错误组件、自绘矢量图标（res/drawable/sb_ic_*.xml + StreamBoxIcons）
core:ui             设备形态（手机/平板/TV）判断、AppError 文案、supportsPictureInPicture
core:testing        Fake 仓库、MainDispatcherExtension（仅 testImplementation）
feature:onboarding / channels / playlist / player / settings / epg(占位)
build-logic         约定插件（streambox.android.application / library / compose / feature、streambox.hilt、streambox.jvm.library）
```
- 依赖方向：`app → feature:* → core:*`；`core:data` 实现 `core:domain` 的接口。
- **feature 之间禁止互相依赖**（`streambox.android.feature` 插件在配置阶段直接报错），跨 feature 只能通过 core:domain 接口或导航路由。
- 解析器 / 回看 URL 生成 / 重连 / 定时等纯逻辑放 **JVM 模块或纯 Kotlin 类**，便于单测。说明书的 `feature/catchup` 计划下沉为 `core:catchup`（JVM）；说明书的 `player-ui` 实际命名为 `feature:player`。
- 新模块：在 `settings.gradle.kts` include，用约定插件，不在模块里重复写 SDK / 编译选项。

### 4.2 Clean Architecture / UDF / Hilt
- ViewModel（`@HiltViewModel`）暴露 `StateFlow<XxxUiState>`，界面用 `collectAsStateWithLifecycle` 收集；Composable 尽量无状态（Route 取 VM，Screen 只收 state + 回调）。
- 路由是 `@Serializable` 类型安全路由，放在各 feature 的 `navigation/` 包（`XxxNavigation.kt`：route + `NavGraphBuilder.xxxScreen()` + `NavController.navigateToXxx()`）。
- 仓库接口在 `core:domain/repository`，实现 `core:data/repository/DefaultXxxRepository`，在 `core:data/di/DataModule` 绑定。
- 协程 / Flow，禁止 RxJava；所有时间用 `java.time` / UTC 毫秒入库。
- `PlaybackController` 是应用级单例，**必须主线程调用**；不要把 ExoPlayer 绑在 Activity 上。
- 结构化数据（收藏、历史、映射…）放 **Room**；简单开关放 **DataStore**（键名 `playback_*` 等前缀，见 `docs/handoff/P2-handoff.md` 第 3 节设置键表）。
- Room 加表 / 改表：升 `version` 并写 `Migration`，schema 导出已开，新 json 要一起提交。DAO 查询都要按 `playlists.activeGeneration` 过滤当前代。

### 4.3 Compose / TV
- 手机 / 平板用 Material 3（NavigationSuiteScaffold 自动切底栏 / 侧轨）；TV 用 Compose for TV（`androidx.tv:tv-material`）侧边抽屉。**TV 判断看 UI mode / leanback 特性，不看屏幕尺寸**（`core:ui` 的 formFactor）。
- **TV 焦点注意**：所有可点击元素必须可聚焦且焦点可见（用 designsystem 的焦点环）；进入页面自动聚焦主按钮；返回键行为一致；**禁止焦点陷阱**；长列表的遥控器操作成本要考虑（如设置「播放」分组已过长，见已知问题）。播放页方向键上 / 下被用于切台，面板打开时按键交给面板处理。
- 手势、PiP 在 TV 上不启用；相关设置项在 TV 上隐藏。
- 所有可点击元素要有 `contentDescription`。

### 4.4 字符串（中英双语）
- **所有用户可见文本放 `strings.xml`，不得硬编码**。每个模块同时维护 `src/main/res/values/strings.xml`（English）与 `src/main/res/values-zh-rCN/strings.xml`（简体中文），新增 key 两边必须都加。
- 共享文案（错误原因、播放标签）放 `core:ui`。

### 4.5 测试风格
- JVM 单测用 **JUnit 5（Jupiter，catalog 中 `junit-jupiter` 6.1.3）+ MockK + Turbine + kotlinx-coroutines-test**，由约定插件统一加入。测试名用反引号句子：`` fun `selecting a theme persists it`() = runTest { ... } ``。
- ViewModel 测试：`@JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()`；优先用 `core:testing` 的 **Fake**（`FakeSettingsRepository`、`FakeChannelRepository`、`FakePlaylistRepository`、`FakeLastChannelRepository`）和 `feature/player/src/test/.../FakePlaybackController.kt`，少用 mock。**改接口时同步更新对应 Fake。**
- 计时逻辑用虚拟时间（`runTest` / `advanceTimeBy`），时钟可注入（如 `MonotonicClock`）。
- 仪器测试 / Compose UI 测试按 AndroidX 要求用 JUnit 4（`androidTest`，需模拟器或真机；目前仓库还没有 androidTest 源码）。
- 说明书第 10 节的测试要求（M3U ≥30 样例、XMLTV、EPG 匹配、回看 ≥20 用例含时区 / 夏令时、重连、PIN、Room 迁移、Compose UI）是验收的一部分。

### 4.6 静态检查
- ktlint（ktlint-cli 1.8.0，android 风格）+ detekt 1.23.8（`config/detekt/detekt.yml`，在默认规则上放宽 Compose 相关项）。提交前跑 `ktlintFormat` 再 `ktlintCheck detekt`。
- 合理的 `@Suppress` 可以用（如 `TooManyFunctions`），但要克制。

## 5. 常用命令速查
```bash
./gradlew ktlintFormat                         # 自动格式化
./gradlew ktlintCheck detekt --continue        # 静态检查
./gradlew test --continue                      # 全部 JVM 单测
./gradlew :app:assembleDebug                   # 调试包 → app/build/outputs/apk/debug/app-debug.apk
git ls-files | grep -E 'local.properties|keystore.properties$'   # 应无输出
```
详细说明、签名、CI、环境坑见 `docs/BUILD-AND-ENV.md`。
