# Release 构建说明

1. **签名**：把 `docs/signing.properties.template` 复制成项目根目录下的 `keystore.properties` 并填好（这个文件已加入 .gitignore）。没有这个文件时，release 包不签名，只能用于本地检查。
2. **版本号**：在 `app/build.gradle.kts` 里修改 `versionCode` / `versionName`。
3. **构建**：
   - `./gradlew ktlintCheck detekt test testDebugUnitTest`
   - `./gradlew connectedDebugAndroidTest`（需要接上设备或开着模拟器：会跑 Room 导入/覆盖测试和 Compose 状态组件测试）
   - `./gradlew bundleRelease`（上架 Google Play 用的 AAB）或 `./gradlew assembleRelease`（APK）
4. **R8**：release 开启了代码混淆和资源压缩，规则在 `app/proguard-rules.pro` 和 `feature/cast/consumer-rules.pro`。release 包请至少冒烟测试一遍：导入列表、播放、Xtream、投屏、备份恢复。
5. **内存检查**：debug 包集成了 LeakCanary，release 包不包含。说明书要求的“播放 24 小时无泄漏”需要在真机上验证。
6. **商店素材**：文案草稿见 `docs/STORE_LISTING.md`，隐私政策模板见 `docs/PRIVACY_POLICY.md`（需要发布到一个公开 URL，再填进 Play 管理中心）。
7. **Play 管理中心的声明**：
   - 数据安全表单：不收集任何数据。
   - 广告：本版本**不含广告**。
   - 前台服务类型选 `mediaPlayback`，用途是后台播放音频。
   - 目标受众：不面向儿童。
