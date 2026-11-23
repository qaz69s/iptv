# P8 阶段报告：Chromecast、变现接口、性能与发布准备

> **未经编译验证**：代码没有构建或运行过，单元测试和仪器测试也没有实际执行。

## 做了什么
- **Chromecast（新增 `feature:cast`）**：
  - 使用 Default Media Receiver，不需要注册接收端应用。
  - 投屏按钮在播放页顶部栏。连接后显示播放/暂停、音量加减和停止按钮。
  - 会话开始时把当前流（直播或点播，点播从当前进度开始）发送到电视，本机暂停；断开后本机继续播放；会话恢复时，如果电视端已经在播就不重复加载。
  - 需要自定义 UA / Referer / 请求头的流，以及 udp:// 之类的非 HTTP 流，会弹出“无法投屏”的提示。
  - 没有 Google Play 服务的设备上不显示投屏按钮。
  - 因为 `MediaRouteButton` 的要求，`MainActivity` 改为继承 `FragmentActivity`。
- **变现（新增 `feature:monetization`）**：`AdsProvider`（含 GDPR/UMP 同意接口）和 `BillingProvider` 两个接口，以及空实现；Hilt 默认绑定空实现，所以**广告和内购都是关闭的**。横幅插槽只在浏览类页签，播放页没有。
- **性能与低端设备**：
  - Coil 的内存缓存（15%，低内存设备 8%）和磁盘缓存（128MB / 32MB）都有上限；低内存设备上关闭图片淡入。
  - 低内存设备上不使用“稳定”缓冲档。
  - debug 包集成 LeakCanary。
- **发布准备**：
  - 签名：读取 `keystore.properties`，模板在 `docs/signing.properties.template`。
  - R8 规则：FFmpeg JNI、序列化、Retrofit、Cast OptionsProvider，release 包去掉 Log.d / Log.v。
  - 文档：`docs/RELEASE.md`（构建步骤和 Play 管理中心的声明）、`docs/PRIVACY_POLICY.md`（中英文隐私政策模板）、`docs/STORE_LISTING.md`（中英文商店文案草稿）。
  - CI：静态检查 + 单元测试 + debug/release 构建，并上传测试报告。
- **测试**：
  - `CastRulesTest` 3 个。
  - Room 仪器测试 `ImportAndOverridesTest`：导入中止时旧数据不受影响；刷新列表后重命名、隐藏和自定义分组都保留。
  - Compose UI 测试 `StatesTest`：空状态和错误状态组件。

## 没做 / 限制
- **投屏代理转发**（让带自定义请求头的流也能投屏）：没做。要实现，需要在手机上开一个本地 HTTP 服务转发流量，并改写 HLS 播放列表里的分片地址，工作量大，而且手机在投屏期间必须一直在线，所以这一版只做了明确提示。
- **HLS 投屏需要 CORS**：Chromecast 播放 HLS 时要求流服务器返回 CORS 头；很多 IPTV 服务器不返回，这种情况下投屏会失败，属于服务端限制。
- **真实的广告和内购 SDK**（AdMob / UMP / Play Billing v7）：没有接入，按你的要求默认关闭，只留了接口。
- **Baseline Profile、Macrobenchmark**：没做，冷启动和滚动帧率需要在真机上测量。
- **说明书 §10 要求的部分 Compose UI 测试**（添加列表流程、搜索、切台、改设置）：没写，这些需要 Hilt 测试运行器和假数据仓库。目前只有基础组件测试。
- **Room 迁移测试**：数据库还没发布过，版本一直是 1，暂时没有迁移可测（见下方已知问题）。
- **扫码 / 局域网推送**：说明书标为二期，未做。

## 已知问题
- **数据库版本**：一直是 1，P2~P6 都改过表结构。正式发布前要冻结当前 schema（`core/database/schemas`），以后每次改表都写 Migration 并补迁移测试。
- **静态检查**：ktlint / detekt 规则已经放宽，第一次编译前请先运行 `./gradlew ktlintFormat`；detekt 如果还有剩余问题，可以先用 `./gradlew detektBaseline` 生成基线。

## 手动验收清单（P8 / 总清单 §10）
- [ ] 全新安装后是空状态，没有任何内置内容
- [ ] M3U 网址、本地文件、Xtream 三种方式都能导入
- [ ] 10 万行列表导入时界面不卡死、不 OOM
- [ ] HLS / TS / DASH 能正常播放；断网后能自动重连
- [ ] 轨道、字幕、画面比例切换正常
- [ ] EPG 匹配正确；TV 上网格 EPG 能滚动且焦点正确
- [ ] 不同回看类型、不同时区下回看地址正确
- [ ] 刷新列表后收藏、隐藏、排序都保留
- [ ] 家长控制锁定生效
- [ ] Chromecast 能投屏，并能暂停、调音量、停止
- [ ] 手机、平板、TV 模拟器上都能完整使用
- [ ] detekt / ktlint 通过，release 构建成功，LeakCanary 没有报警
