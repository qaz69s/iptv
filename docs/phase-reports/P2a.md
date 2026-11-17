# P2a 阶段报告：重连、缓冲、解码回退、轨道 / 字幕、画面比例、TV 遥控器、播放设置

日期：2026-11-17　应用：StreamBox Player（debug 包 `com.streambox.player.debug`）

P2 拆为两部分：**P2a**（本次，播放稳定性与播放中的操控）与 **P2b**（手势、PiP、后台播放等，见"没做什么"）。

## 做了什么
### core:player：重连策略（`reconnect/` 包，纯 Kotlin，可独立测试）
- **`FailureKind`**：把播放失败归为"值得重试"与"不重试"两类。
  - 重试：网络错误（连接失败 / 重置 / DNS）、超时、HTTP 5xx（另含 408、429）、落后于直播窗口（回到直播点）、直播源中途断流（`SOURCE_ENDED`，含直播流意外"播完"）。
  - 不重试：其余 HTTP 4xx（401 / 403 / 404…）、明文被拒、格式不支持 / 损坏、解码失败（交给解码回退处理）、未识别错误。
- **`ReconnectPolicy` / `ReconnectConfig`**：指数退避，首次 1 s、每次 ×2、上限 30 s，再加 ±20% 随机抖动（避免大量客户端同时打向刚恢复的服务器）；默认最多 5 次，0 = 关闭重连。
- **`Reconnector`**：一路流的重连循环（计时在协程作用域内，测试用虚拟时间）。
  - 失败 → 按退避排下一次，覆盖层显示"正在重连（第 n/N 次）…"；次数用尽或不可重试 → 显示错误。
  - **网络恢复立即重试**：正在等待的重试马上触发；已放弃的可重试失败重新开始一轮。
  - **稳定播放 30 s 后计数清零**。
  - 手动"重试"、换台、释放时重置。
- **`FailureClassifier`**：Media3 错误码 + 原因链中的 HTTP 状态码 / 明文标记 → `FailureKind`。
- **`NetworkMonitor`**：`ConnectivityManager` 默认网络回调包装成 `Flow<Boolean>`，仅在播放器存在时注册。
- 新增 `PlaybackError.StreamEnded`（"直播流已中断，重连未能恢复"）。

### core:player：缓冲、解码、轨道
- **`BufferConfig`**：三档缓冲（`DefaultLoadControl` 参数 + 直播目标延迟）。
  | 档位 | min / max 缓冲 | 起播 / 卡顿后起播 | 直播目标延迟 |
  |---|---|---|---|
  | 低延迟 | 4 s / 15 s | 1 s / 2 s | 3 s |
  | 均衡（默认） | 15 s / 50 s | 2.5 s / 5 s | 跟随清单 / Media3 默认 |
  | 稳定 | 30 s / 90 s | 5 s / 10 s | 30 s |
- **`DecoderFallback`**：默认硬解优先；解码器初始化或解码失败时，用"软件解码器优先"重建播放器一次，覆盖层提示"已切换为软件解码"。设置中的「仅软件解码」实际为**软件优先**（`MediaCodecSelector` 把软件解码器排在前面，设备没有对应软解时仍会用硬解），开启后不再有回退。
- **`PlaybackTracks`**（`TrackId` / `VideoTrackOption` / `MediaTrackOption`）：清晰度（自动 / 指定分辨率，显示码率）、音轨、字幕（关闭 / 内嵌 / 外挂）。
- **外挂字幕**：通过 SAF 选择 SRT / VTT 文件（代码也识别 SSA / ASS），以 `MergingMediaSource` 合并到当前流并自动选中；点播保留当前位置重新起播。外挂字幕的 `Format.id` 带前缀 `streambox-ext-`，界面据此标"（外挂）"。
- `PlaybackController` 新增：`applySettings`、`selectVideoTrack` / `selectAudioTrack` / `selectSubtitleTrack`、`addExternalSubtitle`、`seekBy`、`adjustVolume`；`PlaybackState` 新增 `reconnect`、`isSeekable`、`softwareDecoderFallback`、`tracks`。

### feature:player
- **播放面板**（「播放设置」）：手机为底部弹出面板（ModalBottomSheet），TV 为右侧面板（Dialog）。内容：清晰度、音轨、字幕（含"加载字幕文件"）、字幕大小 / 颜色 / 背景、画面比例。
- **字幕样式**：大小（小 / 中 / 大 / 特大）、颜色（白 / 黄 / 绿 / 青）、背景（无 / 半透明 / 不透明），持久化。
- **六种画面比例**：自适应、16:9、4:3、填充（裁切）、拉伸、原始；覆盖层顶栏按钮可循环切换，面板可直接选择。
- **TV 遥控器**：
  - 菜单键（或 SETTINGS 键）开 / 关面板；没有菜单键的遥控器**长按 OK** 打开面板。
  - 左 / 右键：点播（可拖进度）时快退 / 快进 10 s；直播时调系统媒体音量（显示系统音量条）。MEDIA_REWIND / FAST_FORWARD 同左 / 右。
  - 数字键（含小键盘）输入台号：右上角显示"切换到 N 号频道"；1.5 s 无输入、满 4 位或按 OK 时提交；台号不存在时提示"没有 N 号频道" 2 s。在来源分组内查找（台号 = tvg-chno，没有则为列表内序号）。
  - 输入台号时按**返回键删除一位**（DEL 键同），删空后返回键恢复退出播放页。
  - 覆盖层底部提示随直播 / 点播切换。
- 覆盖层状态：重连进度、已切换软解、台号输入 / 未找到。

### 设置页「播放」分组
- 缓冲档位（低延迟 / 均衡 / 稳定）、仅软件解码（软件优先）、自动重连次数（关闭 / 3 / 5 / 10，默认 5）、默认画面比例。
- 字幕样式与画面比例也由播放页写入同一设置。重连次数在写入和读取时都截断到 0..20。
- DataStore 新键：`playback_buffer_profile`、`playback_software_decoding_only`、`playback_max_reconnect_attempts`、`playback_default_aspect_ratio`、`playback_subtitle_size`、`playback_subtitle_color`、`playback_subtitle_background`。
- 缓冲档位与解码模式从下一次起播生效；重连次数立即生效。

### 其他
- `ChannelRepository.channelIdByNumber(number, groupName)`（DAO `idByNumber`：显式台号优先于序号）。
- 新依赖 `androidx.media3:media3-ui`（字幕渲染 `SubtitleView`，与其他 Media3 组件同为 1.11.1）。
- 新图标：画面比例、字幕。中英文字符串。

## 验证结果（沙箱）
`./gradlew assembleDebug test ktlintCheck detekt` 全部通过。
- 单元测试 **255 项，0 失败**（P1 结束时 168 项，+87）：app 3、core:common 12、core:data 24、core:datastore 8、core:domain 10、core:network 8、core:parser 60、core:player 77、feature:channels 6、feature:onboarding 3、feature:player 21、feature:playlist 14、feature:settings 9。
  - core:player 新增：ReconnectPolicy（退避 / 上限 / 抖动 / 0 次）、Reconnector（虚拟时间：排程、次数用尽、网络恢复、稳定 30 s 清零、重置）、PlaybackPolicy（FailureClassifier、BufferConfig、DecoderFallback）。
  - feature:player 新增：ChannelNumberInput（超时 / 满位 / OK 提交 / 删位）、PlayerViewModel（台号跳转、左右键、面板、画面比例、外挂字幕）。
- ktlint、detekt 全部通过。
- APK：`work/StreamBox-P2a-debug.apk`，SHA-256 `1625b4a19524f2de336bb170dc8ec1b49f7c372dc5fddfcbf482f4e03827ed44`。

## 没做什么（P2b）
- 手势：亮度 / 音量 / 进度滑动、双击快进快退。
- 画中画（PiP）。
- MediaSession + 前台服务的后台播放（含通知控制）；目前离开播放页或切后台仍立即释放播放器。
- 睡眠定时、自动继续播放（回到上次频道 / 位置）、自动下一集。
- 仍按原计划后移：EPG 覆盖层信息（P4）、RTMP（R5）、UDP 组播 MulticastLock（R6）、FFmpeg 软解扩展（R5）。

## 已知问题
1. **外挂字幕在部分 DASH / 直播流上可能不对齐**：`MergingMediaSource` 按各自时间轴合并，直播或带时间偏移的 DASH 时字幕时间与画面可能错位。
2. **外挂字幕识别依赖 `format.id` 前缀**：界面用 `Format.id` 是否含 `streambox-ext-` 判断"外挂"；若 Media3 合并后改写 id，标记可能丢失（不影响播放与选择）。
3. **播放页切换画面比例会同时改默认值**：当前没有"仅本频道"的比例，播放页的选择直接写入设置的"默认画面比例"。
4. **TV 上「播放」分组单选列表较长**：各选项平铺为单选列表，遥控器需要多次下移；后续可改为二级页或下拉。
5. **返回键删位无 UI 测试**：`ChannelNumberInput.backspace()` 有单测，但 `BackHandler` 拦截返回键的行为只经编译验证。
6. **全部未真机验证**：沙箱无模拟器，以上功能只经编译、单测和静态检查；Media3 实际行为（重连、软解回退、字幕渲染、比例、音量）需真机确认。
7. **AAPT 间歇 I/O error（仅沙箱）**：aarch64 沙箱中资源编译偶发 I/O 错误，清掉对应 build 目录后重跑即可，与代码无关。
8. 「仅软件解码」实际是软件优先：设备没有对应格式的软件解码器时仍会使用硬件解码。
9. P1 的已知问题（Coil 台标明文、TV 抽屉焦点、手机底部 inset 等）仍然存在，见 `P1.md`。

## P2a 验收清单（真机，需用户）
手机（realme GT6）：
- [ ] 安装 `StreamBox-P2a-debug.apk`（可覆盖安装 P1 版本），打开一个正常频道。
- [ ] **重连**：播放中关闭 Wi-Fi / 移动数据 → 覆盖层出现"正在重连（第 n/5 次）…"，间隔大致 1 s、2 s、4 s…；几秒内重新打开网络 → 立即重连并恢复播放。
- [ ] 断网超过约 1 分钟（5 次用完）→ 显示网络错误 + 重试；恢复网络后应自动再开始一轮重连。
- [ ] 播放一个 404 地址：**不重连**，直接显示"服务器返回 HTTP 404"。
- [ ] 设置 → 播放 → 自动重连次数改为"关闭"，断网：不重连、直接报错；改为 3 次后再试。
- [ ] 缓冲档位分别切到低延迟 / 稳定，重新进入频道：低延迟起播更快、与直播更接近；稳定起播稍慢、卡顿更少。
- [ ] 开启「仅软件解码」后播放 1080p / 4K 频道，确认能播（4K 可能卡顿或回到硬解，属预期）。
- [ ] 点覆盖层"播放设置"：底部面板出现；切换清晰度（自动 / 具体分辨率）、音轨（多音轨源）、字幕开关。
- [ ] 加载一个本地 SRT 和一个 VTT 文件：字幕出现、标"（外挂）"；点播源上时间对齐（直播 / DASH 源记录是否错位，已知问题 1）。
- [ ] 字幕大小 / 颜色 / 背景切换即时生效，重进播放页后保持。
- [ ] 画面比例六种逐一切换（顶栏按钮循环 + 面板直选），画面变化正确；返回设置页看"默认画面比例"已同步（已知问题 3）。
- [ ] 上下滑动切台、覆盖层按钮仍正常（P1 功能回归）。

TV（遥控器）：
- [ ] 进入播放：菜单键打开右侧面板，方向键可在面板内移动并选择，菜单键 / 返回键关闭。
- [ ] 无菜单键时长按 OK 打开面板；短按 OK 仍为显示覆盖层。
- [ ] 直播频道：左 / 右键调音量并出现系统音量条。
- [ ] 点播内容（如 MP4）：左 / 右键快退 / 快进 10 s。
- [ ] 数字键输入台号：右上角显示数字；停顿约 1.5 s 跳台；输满 4 位立即跳；输入后按 OK 立即跳；输入不存在的台号提示"没有 N 号频道"。
- [ ] 输入台号过程中按返回键删除一位；删空后再按返回键退出播放页。
- [ ] 断网重连、错误时"重试"按钮自动获得焦点（P1 回归）。
- [ ] 设置 → 播放分组：用遥控器逐项操作单选列表，记录是否过长难用（已知问题 4）。
