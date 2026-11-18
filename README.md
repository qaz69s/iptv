# StreamBox Player

> **给 AI / 开发者：接手本项目前先读 [`AGENTS.md`](AGENTS.md)**（用户的开发要求与工作流、代码约定、文档阅读顺序），再看 [`docs/DEVELOPMENT-STATUS.md`](docs/DEVELOPMENT-STATUS.md)（已完成 / 待开发 / 已知问题 / 下一步）。

一款纯 IPTV 播放器（Android 手机 / 平板 / Android TV）。**应用不内置、不提供任何内容**，用户自行添加 M3U/M3U8、Xtream Codes 播放列表和 XMLTV 节目单。

> 当前进度：**P3b 完成**（界面：全局搜索、收藏页与最近观看、虚拟分组「全部 / 收藏 / 最近观看」及组内切台、列表 / 网格 / 紧凑三种视图、四种排序、TV 左分组右频道双栏、设置「播放」二级页；修 K1 台标明文、K14 前台媒体键、K20 刷新后进入播放），列表编辑器在 P3c。**P3a 完成**（数据层：收藏 / 观看历史 / 分组与频道的隐藏·重命名·排序覆盖表，刷新后保留；上次频道改为按稳定键记录；台号输入超时改为 2 秒）。**P2 完成**（P2a + P2b，待真机验收）：在 P1（M3U 导入、频道浏览、全屏播放）之上，P2a 加入自动重连、三档缓冲、硬解失败回退软解、清晰度 / 音轨 / 字幕（含外挂 SRT/VTT）、字幕样式、六种画面比例、TV 遥控器；P2b 加入后台播放（媒体通知、耳机 / 蓝牙切台）、画中画、播放手势、睡眠定时、启动续播上次频道、自动播放下一个。各阶段见 `docs/implementation-plan.md` 与 `docs/phase-reports/`。

## 功能一览（P2 + P3a + P3b）
- 首启引导与免责声明；不内置任何内容，首页引导添加播放列表。
- 添加播放列表：URL（http 需按列表单独允许明文）或 SAF 选择本地文件；可设 User-Agent。导入进度页可取消 / 重试，失败给出具体原因。
- 播放列表管理：刷新、删除、单列表明文开关。
- 频道页：虚拟分组「全部 / 收藏 / 最近观看」+ 播放列表分组（刷新后分组消失会自动回到"全部"）；列表 / 网格 / 紧凑三种视图；排序：默认顺序 / 名称 / 频道号 / 自定义（视图与排序会记住）；TV 为左分组、右频道双栏；长按（遥控器长按 OK 或菜单键）加入 / 取消收藏；台标遵守按列表的明文开关。
- 全局搜索：频道名（含重命名后的名字）、分组名，输入即搜（300 ms 防抖），基于 Room FTS；中文名支持包含匹配。
- 收藏页：收藏（上移 / 下移排序、删除、恢复按收藏时间）与最近观看（最近 50 条、单条移除、清空）。
- 切台跟随进入播放时的列表（全部 / 分组 / 收藏 / 最近观看）与当前排序，循环；收藏 / 最近观看内数字键 = 组内序号。
- 播放页：全屏沉浸（手机横屏）、覆盖层显示频道号与名称（约 4 秒自动隐藏，点击 / OK 显示）、缓冲指示、错误原因 + 重试、播放时保持亮屏。
- 播放稳定性：网络 / 超时 / 5xx 等可恢复错误自动重连（指数退避，默认 5 次，可选 0/3/5/10；网络恢复立即重试）；缓冲三档（低延迟 / 均衡 / 稳定）；硬解失败自动回退软解。
- 播放面板（手机底部、TV 右侧）：睡眠定时、画中画、清晰度、音轨、字幕（内嵌 / 外挂 SRT·VTT）、字幕大小 / 颜色 / 背景、画面比例（自适应 / 16:9 / 4:3 / 填充 / 拉伸 / 原始）。
- 后台播放（默认开）：切到后台继续出声，媒体通知可暂停 / 切台，耳机 / 蓝牙上下曲切台，拔耳机自动暂停（Android 13+ 首次请求通知权限）。
- 画中画（默认开，Android 8.0+ 手机 / 平板）：按 Home 自动进入小窗，或在面板中点击进入；小窗内上一个 / 播放·暂停 / 下一个。
- 播放手势（默认开，手机 / 平板）：左侧上下滑调亮度、中间上下滑切台、右侧上下滑调音量；左右滑拖进度（仅点播）；双击左 / 右 ±10 秒，双击中间暂停 / 播放。
- 睡眠定时：15 / 30 / 60 / 90 分钟或自定义 1–600 分钟，最后 10 秒淡出后暂停。
- 启动时继续播放上次频道（默认关）；自动播放下一个（默认关，点播播完播放同分组下一个，不绕回）。
- TV 遥控器：上下切台；菜单键或长按 OK 打开面板；左右键直播调音量、点播快退 / 快进 10 秒；数字键输入台号（停顿 2 秒确认，返回键删一位）。
- 观看历史：每个真正开始播放的频道记入最近观看（最多 50 条）；隐藏、重命名、排序的存储已就绪（编辑界面见 P3c）。刷新 / 重新导入播放列表后，上次频道、收藏、历史与这些修改都保留；刷新进行中进入播放也能找到频道。
- 设置「播放」二级页：缓冲档位、自动重连次数、默认画面比例、字幕样式（单选项为一行 + 对话框）、仅软件解码（软件优先）、后台播放、启动续播、自动播放下一个、画中画、播放手势（TV 上不显示画中画与手势）。
- TV 遥控器播放 / 暂停键在前台播放页同样有效。

## 技术栈
Kotlin 2.4 · Jetpack Compose (Material 3) · Compose for TV · Hilt · DataStore · Room + Paging · OkHttp · Media3 ExoPlayer · Coil 3 · Navigation Compose（类型安全路由）· 多模块 Clean Architecture。版本统一在 `gradle/libs.versions.toml`。

## 环境要求
- JDK 17
- Android SDK（compileSdk 37 / targetSdk 37 / minSdk 24）
- Android Studio 最新稳定版

## 构建
```bash
./gradlew assembleDebug          # 调试包 → app/build/outputs/apk/debug/
./gradlew test                   # JVM 单元测试（core:database 用 Robolectric）
./gradlew ktlintCheck detekt     # 代码风格与静态检查
./gradlew :app:lintDebug         # Android Lint
```
GitHub Actions（`.github/workflows/ci.yml`）在每次 push / PR 时执行以上全部步骤（另加 `:app:assembleRelease`），并上传调试 APK。详见 `docs/BUILD-AND-ENV.md`。

## 发布签名
复制 `keystore.properties.template` 为 `keystore.properties`（已被 git 忽略）并填写，或在 CI 中设置环境变量 `STREAMBOX_KEYSTORE_FILE`、`STREAMBOX_KEYSTORE_PASSWORD`、`STREAMBOX_KEY_ALIAS`、`STREAMBOX_KEY_PASSWORD`。没有签名配置时，release 构建产出未签名 APK。

## 文档目录
| 文件 | 内容 |
|---|---|
| `AGENTS.md` | **AI / 开发者接手入口**：项目简介、阅读顺序、用户的开发要求与阶段工作流（必须遵守）、代码约定 |
| `docs/DEVELOPMENT-STATUS.md` | 开发状态：P0–P3b 已完成内容、P3–P8 待开发范围与验收要点、已知问题与技术债汇总、待真机验收清单、下一步 |
| `docs/BUILD-AND-ENV.md` | 构建与环境：JDK / SDK、常用命令、CI、签名配置、已知环境坑 |
| `docs/requirements.md` | 需求说明书（唯一需求来源，不修改） |
| `docs/implementation-plan.md` | 实施计划：模块、依赖版本、风险点、阶段表与阶段状态 |
| `ARCHITECTURE.md` | 架构说明：模块分层、播放生命周期、导航 |
| `docs/phase-reports/` | 各阶段报告（P0、P1、P2a、P2b、P3a、P3b）：做了什么 / 没做什么 / 已知问题 / 真机验收步骤 |
| `docs/handoff/P2-handoff.md` | P2 结束时给 P3 开发者的代码级交接 |

## 免责声明
本应用仅为播放器，不提供任何内容。用户需自行承担所添加播放列表及内容的合法性。


## 第三方许可证说明

音频软解使用 NextLib `io.github.anilbeesetti:nextlib-media3ext`（GPL-3.0，内含 LGPL v3 构建的 FFmpeg）。引入该依赖后，分发的 APK 须遵守 GPL-3.0（公开对应源代码）。
