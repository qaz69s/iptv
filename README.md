# StreamBox Player

一款纯 IPTV 播放器（Android 手机 / 平板 / Android TV）。**应用不内置、不提供任何内容**，用户自行添加 M3U/M3U8、Xtream Codes 播放列表和 XMLTV 节目单。

> 当前进度：**P1 完成**（待真机验收）：在 P0 脚手架之上，可添加 M3U/M3U8 播放列表（URL 或本地文件）、流式导入到 Room、按分组浏览频道并全屏播放（HLS / DASH / RTSP / MPEG-TS / MP4 等），手机上下滑动、TV 遥控器上下键切台。轨道、手势调节、重连、PiP、后台播放属 P2。各阶段见 `docs/implementation-plan.md` 与 `docs/phase-reports/`。

## 功能一览（P1）
- 首启引导与免责声明；不内置任何内容，首页引导添加播放列表。
- 添加播放列表：URL（http 需按列表单独允许明文）或 SAF 选择本地文件；可设 User-Agent。导入进度页可取消 / 重试，失败给出具体原因。
- 播放列表管理：刷新、删除、单列表明文开关。
- 频道页：分组筛选（刷新后分组消失会自动回到"全部"）、分页列表、台标。
- 播放页：全屏沉浸（手机横屏）、覆盖层显示频道号与名称（约 4 秒自动隐藏，点击 / OK 显示）、缓冲指示、错误原因 + 重试、播放时保持亮屏，离开或切到后台即释放播放器。

## 技术栈
Kotlin 2.4 · Jetpack Compose (Material 3) · Compose for TV · Hilt · DataStore · Room + Paging · OkHttp · Media3 ExoPlayer · Coil 3 · Navigation Compose（类型安全路由）· 多模块 Clean Architecture。版本统一在 `gradle/libs.versions.toml`。

## 环境要求
- JDK 17
- Android SDK（compileSdk 37 / targetSdk 36 / minSdk 24）
- Android Studio 最新稳定版

## 构建
```bash
./gradlew assembleDebug          # 调试包 → app/build/outputs/apk/debug/
./gradlew test                   # JVM 单元测试
./gradlew ktlintCheck detekt     # 代码风格与静态检查
./gradlew :app:lintDebug         # Android Lint
```
GitHub Actions（`.github/workflows/ci.yml`）在每次 push / PR 时执行以上全部步骤，并上传调试 APK。

## 发布签名
复制 `keystore.properties.template` 为 `keystore.properties`（已被 git 忽略）并填写，或在 CI 中设置环境变量 `STREAMBOX_KEYSTORE_FILE`、`STREAMBOX_KEYSTORE_PASSWORD`、`STREAMBOX_KEY_ALIAS`、`STREAMBOX_KEY_PASSWORD`。没有签名配置时，release 构建产出未签名 APK。

## 文档
- `docs/requirements.md` 需求说明书
- `docs/implementation-plan.md` 实施计划（模块、版本、风险、阶段）
- `ARCHITECTURE.md` 架构说明
- `docs/phase-reports/` 各阶段汇报

## 免责声明
本应用仅为播放器，不提供任何内容。用户需自行承担所添加播放列表及内容的合法性。
