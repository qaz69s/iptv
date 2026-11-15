# StreamBox Player

一款纯 IPTV 播放器（Android 手机 / 平板 / Android TV）。**应用不内置、不提供任何内容**，用户自行添加 M3U/M3U8、Xtream Codes 播放列表和 XMLTV 节目单。

> 当前进度：**P0 脚手架**（工程结构、主题、导航、首次启动引导与免责声明、设置页）。播放列表、播放器等功能按阶段推进，见 `docs/implementation-plan.md`。

## 技术栈
Kotlin 2.4 · Jetpack Compose (Material 3) · Compose for TV · Hilt · DataStore · Navigation Compose（类型安全路由）· 多模块 Clean Architecture。版本统一在 `gradle/libs.versions.toml`。

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
