# 构建与环境

## 1. 环境要求
| 项 | 版本 / 说明 |
|---|---|
| JDK | **17**（AGP 9 最低要求；CI 用 Temurin 17） |
| Gradle | 9.9.1（用仓库自带 Wrapper `./gradlew`，无需单独安装） |
| Android Gradle Plugin | 9.5.1（使用 AGP 9 内置 Kotlin 支持） |
| Kotlin / KSP | 2.4.21 / 2.3.14 |
| Android SDK | compileSdk 37、targetSdk 37、minSdk 24（platform 37 + 对应 build-tools） |
| IDE | Android Studio 最新稳定版（可选） |

所有依赖版本集中在 `gradle/libs.versions.toml`。SDK 级别也在其中（`compileSdk` / `targetSdk` / `minSdk`），由 `build-logic` 约定插件读取，**不要在模块里单独写**。

本地需要 `local.properties`（`sdk.dir=...`，Android Studio 会自动生成，已被 `.gitignore` 忽略）或设置 `ANDROID_HOME`。

`gradle.properties` 已开启并行构建、构建缓存和 configuration cache，JVM 堆 `-Xmx4g`。

## 2. 常用命令
```bash
./gradlew :app:assembleDebug             # 调试包 → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug                  # 所有模块的 debug 构建
./gradlew :app:assembleRelease           # release 包（R8 混淆 + 资源压缩；无签名配置时产出未签名 APK）
./gradlew test --continue                # 全部 JVM 单元测试（Android 模块的 testDebugUnitTest + JVM 模块的 test）
./gradlew :feature:player:testDebugUnitTest   # 单个 Android 模块
./gradlew :core:parser:test              # 单个 JVM 模块
./gradlew ktlintFormat                   # 自动修正格式
./gradlew ktlintCheck detekt --continue  # 代码风格 + 静态检查（CI 必须通过）
./gradlew :app:lintDebug                 # Android Lint（CI 会跑）
```

**阶段收尾的完整校验**（用户要求，每阶段 / 子阶段结束都要全绿）：
```bash
./gradlew ktlintFormat
./gradlew ktlintCheck detekt test :app:assembleDebug --continue
```
建议再跑一次 `:app:lintDebug :app:assembleRelease`，与 CI 保持一致。

测试报告：`<模块>/build/reports/tests/`，原始结果 `<模块>/build/test-results/**/TEST-*.xml`（可用来统计测试数）。

调试包：applicationId `com.streambox.player.debug`、versionName `0.1.0-debug`，可与 release 包共存。交付时把 `app-debug.apk` 复制并改名为 `StreamBox-<阶段>-debug.apk`，源码包用 `git archive --format=zip -o streambox-player-<阶段>-src.zip HEAD`。

## 3. CI（GitHub Actions）
文件：`.github/workflows/ci.yml`。触发：push 到 `main`、所有 pull request。同一 ref 的新运行会**取消**进行中的旧运行（`concurrency: cancel-in-progress`）。

单个 job `build`（ubuntu-latest，超时 45 分钟），依次：
1. checkout → Temurin JDK 17 → `gradle/actions/setup-gradle@v4`（Gradle 缓存）
2. `./gradlew ktlintCheck detekt --continue`
3. `./gradlew test --continue`
4. `./gradlew :app:lintDebug`
5. `./gradlew :app:assembleDebug :app:assembleRelease`
6. 上传 artifact：`streambox-debug-apk`（debug APK）；`reports`（所有 `build/reports/` 与 `build/test-results/`，失败时也上传）

CI 没有签名 secrets，因此 release 包未签名，仅验证 R8 构建能通过。CI 目前不跑仪器测试 / Compose UI 测试（没有模拟器 job，仓库也还没有 androidTest 源码）；需要时可加 `reactivecircus/android-emulator-runner` 一类的 job（新增前先告知用户）。

**阶段结束推送后必须检查 CI**：`https://github.com/qaz69s/iptv/actions`。失败时下载 `reports` artifact 看具体任务。

## 4. 签名配置（Release）
- 模板：`keystore.properties.template`。复制为仓库根目录的 `keystore.properties`（**已被 git 忽略，绝不提交**），填写：
  ```properties
  storeFile=release.jks        # 相对仓库根目录的路径；*.jks / *.keystore 也已被忽略
  storePassword=...
  keyAlias=streambox
  keyPassword=...
  ```
- 或使用环境变量（CI 推荐，配合 GitHub Secrets）：`STREAMBOX_KEYSTORE_FILE`、`STREAMBOX_KEYSTORE_PASSWORD`、`STREAMBOX_KEY_ALIAS`、`STREAMBOX_KEY_PASSWORD`。`keystore.properties` 中的值优先。
- 都没有时，`release` 不设签名配置，产出未签名 APK。
- 读取逻辑在 `app/build.gradle.kts`。（其中注释提到的 `docs/RELEASE.md` 尚未编写，计划在 P8 补齐。）
- 提交前检查：
  ```bash
  git ls-files | grep -E 'local.properties|keystore.properties$'   # 必须无输出
  git status --short                                              # 确认没有 .jks / keystore 文件
  ```

## 5. 已知环境坑与建议（通用）
1. **AAPT2 偶发 I/O error**：资源编译 / 链接偶尔报 I/O 错误，与代码无关（在原开发环境中多次出现）。处理：删掉出错模块的资源中间产物后重跑该任务：
   ```bash
   rm -rf <模块>/build/intermediates/processed_res <模块>/build/intermediates/linked_resources_binary_format
   # 仍失败就 rm -rf <模块>/build
   ```
2. **`--rerun` 只作用于命令行上紧挨在它前面的那一个任务**（例如 `:core:player:test --rerun`），不是全局开关。要强制重跑多个任务，必须在每个任务后各加一个 `--rerun`，或用 `--rerun-tasks`（会重跑整个任务图，很慢）。
3. **确认测试真的跑了**：有构建缓存时任务会显示 `UP-TO-DATE` / `FROM-CACHE`。汇报测试数前，核对 `**/build/test-results/**/TEST-*.xml` 的时间戳是本轮产物；在日志里找任务状态时要精确匹配任务名（`> Task :x:test` 会先匹配到 `:x:testClasses`）。
4. **ARM64（aarch64）Linux 主机**：Google 只发布 x86_64 Linux 的 AAPT2 / build-tools，在 aarch64 Linux 上资源编译会失败。可在**本机** `~/.gradle/gradle.properties`（不是仓库里的 gradle.properties）设置 `android.aapt2FromMavenOverride=<社区编译的 aarch64 aapt2 路径>`；最可靠的完整构建是 CI 的 x86_64 runner 或 macOS / Windows / x86_64 Linux 开发机。
5. **长时间构建与命令超时**：首次构建要下载依赖、编译 build-logic，耗时较长。如果你的执行环境对单条命令有时间上限，请把 Gradle 放到后台运行（`nohup ./gradlew ... > build.log 2>&1 &`），把退出码写到文件里再轮询，不要在会被超时杀掉的前台命令里等待；每次构建用新的日志文件名，避免读到上一次的结果。
6. **没有模拟器时**：只能验证编译 + JVM 单测 + 静态检查。播放、PiP、MediaSession、手势、TV 焦点等必须由用户在真机（realme GT6 + Android TV）上验收，阶段报告里要写清楚可勾选的验收步骤。
7. **detekt 1.23.8 + Kotlin 2.4**：只跑不依赖类型解析的 `detekt` 任务（不是 `detektMain`）。出现兼容错误时先说明，再考虑 detekt 2.x。
8. **Robolectric（P3a 起，core:database 单测）**：首次运行会从 Maven Central 下载对应 SDK 的 `android-all-instrumented` jar（约 200 MB，SDK 由 `core/database/src/test/resources/robolectric.properties` 固定为 35）。需要能访问 Maven Central；若所在网络的代理需要认证而测试 JVM 下载失败（报 407），可先手动把该 jar 下载到 `~/.m2/repository/org/robolectric/android-all-instrumented/<版本>/`，Robolectric 会优先用本地文件。该模块单测用 JUnit 4 运行（见 AGENTS.md 4.5）。
9. **configuration cache**：新写的 Gradle 逻辑（约定插件、自定义任务）要兼容 configuration cache，否则会报错。

## 6. 仅限原开发沙箱（Hark 环境）的细节
以下只对最初开发本项目的沙箱有意义，其他环境可忽略：
- 环境脚本 `/workspace/tools/env.sh`（JDK 17、Android SDK、Gradle 9.9.1、`GRADLE_USER_HOME=/workspace/tools/gradle-home`，其中 gradle.properties 配了 aarch64 `aapt2FromMavenOverride`）。
- 单条命令约 110 s 会被杀，后台构建用 `setsid nohup /workspace/tools/bg.sh <唯一日志名> <gradle 任务…> >/dev/null 2>&1 &` 单独启动，再另起命令 `/workspace/tools/wait.sh <日志名> 100` 轮询（输出 `EXIT=n` 或 `RUNNING`）；启动前删掉同名 `/workspace/tools/<名>.log.exit`。
- 推送：`git -c http.sslCAInfo="$SSL_CERT_FILE" push origin main`（凭据由 Hark 的 GitHub 连接注入）。
- 沙箱里的 aapt2 无法 dump APK，没有模拟器。
