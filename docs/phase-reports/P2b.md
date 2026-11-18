# P2b 阶段报告：后台播放、画中画、手势、睡眠定时、启动续播、自动下一个

日期：2026-11-18　应用：StreamBox Player（debug 包 `com.streambox.player.debug`）

P2 拆为 **P2a**（重连、缓冲、解码回退、轨道 / 字幕、画面比例、TV 遥控器，见 `P2a.md`）与 **P2b**（本次）。P2b 共三个功能提交：
- `3e14a71` P2b part 1：后台播放（MediaSession）、画中画
- `82bd0d1` P2b part 2：手机 / 平板播放手势
- `7d25565` P2b part 3：睡眠定时、启动续播上次频道、自动播放下一个

## 做了什么
### 后台播放（MediaSession + 前台服务）
- **`core:player` 新增 `session/PlaybackService`**（Media3 `MediaSessionService`，`foregroundServiceType="mediaPlayback"`，不导出）：播放时由 Media3 提升为前台服务并显示**媒体通知**（播放 / 暂停、上一个 / 下一个频道，标题为频道名，台标作为封面）。点通知回到 App（singleTask，即回到播放页）。
- 服务跟随 `PlaybackController.player` Flow：ExoPlayer 重建（缓冲档位 / 解码模式变化、软解回退）时 `MediaSession.setPlayer` 换新实例；`player` 变为 null（控制器 release）时释放会话并停止服务。
- **`ChannelNavigationPlayer`**（`ForwardingPlayer`）：直播频道只有一个媒体项，ExoPlayer 本身不提供上一曲 / 下一曲；包装层补上这些命令（含 `onAvailableCommandsChanged` 回调，经 `NavigationCommandsListener` 改写），把通知、**耳机 / 蓝牙的上一曲 / 下一曲映射为切台**（`PlaybackController.channelNavigator`，由 PlayerViewModel 注册，在来源分组内切台）。`release()` 转给控制器，播放器归控制器所有。
- **拔耳机 / 断开蓝牙自动暂停**（Media3 的 "becoming noisy" 处理）、音频焦点由播放器处理。
- **生命周期**：设置「后台播放」开启时，整个 App 进入后台（Home、锁屏、切到其他应用；区别于 App 内导航离开）的 ON_STOP 不再释放播放器，而是 `setInBackground(true)`：禁用视频轨，只放声音（后台期间重建的播放器同样生效）；回前台 `setInBackground(false)` 恢复视频轨，若期间被通知 / 耳机暂停则恢复播放。关闭该设置、App 内返回、播放出错时仍立即释放（与 P2a 相同）。
- **Android 13+ 通知权限**：开启后台播放并首次进入播放页时请求 `POST_NOTIFICATIONS`（每个进程最多问一次）；拒绝不影响播放，只是没有通知。
- 新权限：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS`。新依赖：`androidx.media3:media3-session` 1.11.1（版本目录已有条目，P2b 起由 core:player 使用）。
- 设置「后台播放」**默认开**。

### 画中画（PiP）
- 条件：手机 / 平板、**Android 8.0+**、系统支持 PiP（`core:ui` 的 `supportsPictureInPicture(formFactor)`）；**TV 永不启用**。清单 MainActivity 加 `supportsPictureInPicture`、`resizeableActivity`。
- **按 Home 自动进入**：Android 12+ 用 `setAutoEnterEnabled`；8.0–11 通过 user-leave hint 进入。只在有频道、无错误、面板未打开时允许进入；离开播放页时关闭自动进入（避免在其他页面按 Home 变小窗）。
- **播放面板「画中画」按钮**：关闭面板后立即进入小窗（系统拒绝时不崩溃）。
- **小窗三个按钮**：上一个频道 / 播放·暂停 / 下一个频道（`RemoteAction` + App 私有广播，`RECEIVER_NOT_EXPORTED`）；出错时播放键为重试。
- 小窗比例：16:9、4:3 模式按该比例，其他模式跟随视频（含像素宽高比），未知时 16:9，限制在系统允许的 1:2.39 ~ 2.39:1；`sourceRectHint` 为视频区域内居中的对应比例矩形，用于进入动画。
- 小窗内隐藏覆盖层、面板、手势与台号输入；出错时显示错误标记。关闭小窗（而非展开）时按"进入后台"处理：后台播放开启则继续出声，否则释放。
- 设置「画中画」**默认开**；不支持的设备与 TV 上设置项隐藏。

### 播放手势（手机 / 平板）
- 纯逻辑在 `PlayerGestures`（JVM 可测），触摸接入在 `PlayerTouchGestures.kt`。
- **竖向滑动按起点分区**：左 1/3 = 屏幕亮度（只改本窗口亮度，离开播放页恢复原值），中 1/3 = 切台（上滑下一台、下滑上一台，与 P1 相同），右 1/3 = 媒体音量（`STREAM_MUSIC`）。滑过整屏高度 = 0→100%。
- **横向滑动 = 拖进度**，仅可拖动节目（`isSeekable`，即点播）；整屏宽度 = 120 秒，松手时跳转，过程中显示"目标时间 / 总时长"。直播上横滑忽略。
- **双击**：左 1/3 = −10 s，中间 = 暂停 / 播放，右 1/3 = +10 s（左右两侧仅可拖动节目有效）；屏幕中央短暂显示反馈。
- 方向在越过触摸阈值后锁定，避免切台与调节互相误触。
- 设置「播放手势」**默认开**；关闭后只保留点按（覆盖层）与任意位置竖滑切台。**TV 不启用**（设置项在 TV 上隐藏），画中画中也不响应。覆盖层底部提示随手势开关变化。

### 睡眠定时
- 入口：播放面板顶部「睡眠定时」（手机、TV 均有）。选项：**关 / 15 / 30 / 60 / 90 分钟 / 自定义（1–600 分钟）**；运行中显示剩余时间，可随时改选或关闭。
- `core:player` 的 `sleep/SleepTimer`：按单调时钟计算剩余时间（不累加 delay，延迟的 tick 不会拉长定时）；**最后 10 秒线性淡出音量**（`PlaybackController.setVolume`，播放器输出音量，非系统音量），到点**暂停**并恢复音量；取消或重设时若正在淡出也恢复音量。
- 定时器在 PlayerViewModel 中，后台播放与画中画期间继续计时；不持久化（离开播放页即取消）。

### 启动续播上次频道
- `LastChannelRepository`（core:domain 接口，core:data 实现，DataStore 键 `last_played_playlist_id` / `last_played_channel_id`）：频道**首次真正开始播放**时记录（播放列表 id + 频道 id）。
- 设置「启动时继续播放上次频道」**默认关**。开启后**冷启动**（完成引导后）直接在频道页之上打开播放页，返回键回到频道页；Activity 重建不再触发。记录的频道已不存在（列表被删除或重新导入导致 id 变化）时清除记录、不打开。

### 自动播放下一个
- 设置「自动播放下一个」**默认关**。开启后**点播内容播完**（`PlaybackState.ended`，仅非直播）时播放下一个频道：优先用户进入时的来源分组，否则频道自身分组（`ChannelRepository.nextChannel`，按列表顺序）；**最后一个之后停止，不绕回**。直播结束仍按 P2a 走重连。

### 设置页「播放」分组新增
- 后台播放（默认开）、画中画（默认开，仅支持的手机 / 平板）、播放手势（默认开，非 TV）、启动时继续播放上次频道（默认关）、自动播放下一个（默认关）。
- DataStore 新键：`playback_background_playback`、`playback_picture_in_picture`、`playback_gestures`、`playback_resume_last_channel`、`playback_auto_play_next`、`last_played_playlist_id`、`last_played_channel_id`。

### 其他
- `PlaybackController` 新增：`togglePlayPause`、`pause`、`setVolume`、`setInBackground`、`channelNavigator`；`PlaybackState.ended`；媒体项带 `MediaMetadata`（频道名、台标）。
- `PlaybackItem` 新增 `playlistId`、`groupName`（DAO `playbackRow` 查询补齐）。
- 新图标：画中画、播放、暂停、上一个、下一个。中英文字符串。

## 验证结果（沙箱）
`ktlintCheck detekt`、全项目 `test`、`:app:assembleDebug` 全部通过；测试与 lint 均以 `--rerun` 强制重跑，TEST-*.xml 与 APK 均为本轮产物。本轮无需任何 lint 修正。
- 单元测试 **348 项，0 失败、0 跳过**（P2a 结束时 255 项，+93）：app 9、core:common 12、core:data 24、core:datastore 11、core:domain 10、core:network 8、core:parser 60、core:player 94、feature:channels 6、feature:onboarding 3、feature:player 84、feature:playlist 14、feature:settings 13。
  - core:player 新增：ChannelNavigationPlayer（5：上一 / 下一映射切台、命令补齐、监听器改写、release 转交）、SleepTimer（12：倒计时、淡出、到点暂停并恢复音量、取消 / 重设、上下限截断、迟到 tick 不拉长）。
  - feature:player 新增：PlayerGestures（13：分区、方向锁定、进度 / 亮度 / 音量换算、双击）、PictureInPicture（10：小窗比例与 sourceRectHint）、PlayerViewModelSleepAndNext（15：睡眠定时、记录上次频道、自动下一个不绕回）、SleepTimerInput（3：自定义分钟仅接受 1–600、剩余时间格式、选中项跟随定时器）、PlayerViewModelTest 由 15 增至 37（后台 / PiP 生命周期、手势、PiP 按钮）。
  - app：MainActivityViewModel 启动续播（默认关、开启后打开、每次冷启动仅一次、无记录、频道不存在或已属其他列表时忽略并清除）；core:datastore、feature:settings：新设置键读写与默认值。
- ktlint、detekt 全部通过。
- APK：`work/StreamBox-P2-debug.apk`，21,404,227 字节，SHA-256 `9089a03c7ac0a9bdc4366f1b4cdfede6244bd549f3e8db3fa29de1c8b1bcd303`。

## 没做什么
- 系统"播放恢复"（Android 11+ 媒体控件中恢复上次播放）、Android Auto / 媒体浏览（`MediaLibraryService`）。
- 睡眠定时的默认时长设置、定时器持久化；"仅本频道"的画面比例（P2a 已知问题 3 仍在）。
- "自动继续播放"只做到**上次频道**，未做点播的**断点续播位置**（随 VOD 在 P6 做）。
- 仍按原计划后移：EPG 覆盖层信息（P4）、RTMP（R5）、UDP 组播 MulticastLock（R6）、FFmpeg 软解扩展（R5）。

## 已知问题
1. **从最近任务划掉 App 即停止播放**：`onTaskRemoved` 释放播放器并停止服务（切台依赖已随任务销毁的播放页）。
2. **后台回到前台时画面 1–2 秒后才出现**：后台期间视频轨被禁用，恢复时需要等下一个关键帧 / 重新缓冲视频。
3. **后台播放开启时，前台播放也会显示媒体通知**：服务在有播放器时即启动，通知不只在后台出现。
4. **前台时 TV 遥控器的播放 / 暂停键（MEDIA_PLAY_PAUSE 等）未处理**：播放页按键处理没有接这些键；后台时经 MediaSession 可用。
5. **系统播放恢复 / Android Auto 不支持**（见"没做什么"）。
6. **画中画中仍显示字幕**：小窗内字幕层未隐藏，小窗较小时可能遮挡画面。
7. **Fill / Stretch 模式下 `sourceRectHint` 只是近似**：按视频区域内居中矩形计算，进入动画可能有轻微跳变。
8. **带 DVR（时移窗口）的直播不能拖进度**：手势与双击快进退只对 `isSeekable`（非直播）生效。
9. **重新导入播放列表后"上次频道"记录失效**：导入会生成新的频道 id，启动时找不到即清除记录，不会打开播放页。
10. **TV 设置「播放」分组更长了**（P2a 已知问题 4 加重）：本次又加了后台播放、启动续播、自动下一个三项，遥控器需多次下移；后续改为二级页。
11. **全部未真机验证**：沙箱无模拟器，以上只经编译、单测和静态检查；MediaSession 通知、耳机 / 蓝牙按键、PiP、亮度 / 音量、淡出等需真机确认。
12. AAPT 间歇 I/O error（仅沙箱，与代码无关），P2a、P1 的已知问题仍然存在（见 `P2a.md`、`P1.md`）。

## P2b 验收清单（真机，需用户）
手机（realme GT6，Android 14 / 15）：
- [ ] 安装 `StreamBox-P2-debug.apk`（可覆盖安装 P2a 版本）。首次进入播放页弹出通知权限请求 → 允许。
- [ ] 设置 → 播放：后台播放、画中画、播放手势默认**开**；启动时继续播放上次频道、自动播放下一个默认**关**。
- **后台播放**（先在设置中关闭画中画，以免按 Home 进入小窗）：
  - [ ] 播放直播 → 按 Home：声音继续，通知栏出现媒体通知（频道名、台标、上一个 / 暂停 / 下一个）。
  - [ ] 通知中暂停 / 播放、上一个 / 下一个：切台后通知标题变为新频道。锁屏界面控件同样可用。
  - [ ] 有线耳机 / 蓝牙耳机的上一曲 / 下一曲 = 切台；耳机播放键 = 暂停 / 播放。
  - [ ] 播放中拔掉有线耳机 / 断开蓝牙 → 自动暂停。
  - [ ] 点通知回到播放页：1–2 秒后画面出现（已知问题 2），覆盖层显示。
  - [ ] 后台时从最近任务划掉 App → 声音停止、通知消失（已知问题 1）。
  - [ ] 关闭「后台播放」后按 Home → 立即停止，无通知；回到播放页重新起播。
  - [ ] 拒绝通知权限（系统设置中关闭）后按 Home：声音是否继续、有无崩溃，记录结果。
- **画中画**（重新开启画中画）：
  - [ ] 播放中按 Home / 上滑回桌面 → 自动变为小窗，比例与视频一致。
  - [ ] 小窗三个按钮：上一个 / 播放·暂停 / 下一个均有效。
  - [ ] 播放面板中点「画中画」→ 立即进入小窗。
  - [ ] 点小窗展开 → 回到全屏，覆盖层显示；关闭小窗（拖到底部 ×）→ 后台播放开启时声音继续、通知仍在；关闭后台播放再试 → 停止。
  - [ ] 播放面板打开时按 Home、或频道报错时按 Home → 不进入小窗。
  - [ ] 回到频道页再按 Home → 不进入小窗。
  - [ ] 依次在 16:9、4:3、填充、拉伸模式下进入小窗，观察比例与进入动画（已知问题 7）；带字幕源确认小窗内字幕显示情况（已知问题 6）。
  - [ ] 设置中关闭画中画 → 按 Home 不再进入小窗，面板中无「画中画」按钮。
- **手势**：
  - [ ] 左 1/3 上下滑：亮度变化并显示百分比；退出播放页后亮度恢复。
  - [ ] 右 1/3 上下滑：媒体音量变化并显示百分比。
  - [ ] 中 1/3 上下滑：切台（上滑下一台）；斜向滑动不会同时触发调节和切台。
  - [ ] 点播（MP4 等）左右滑：显示目标时间 / 总时长，松手跳转；直播上左右滑无反应。
  - [ ] 双击左侧 −10 秒、右侧 +10 秒（点播）；双击中间暂停 / 播放（直播、点播都可）；单击仍切换覆盖层。
  - [ ] 设置中关闭播放手势：只剩单击与任意位置上下滑切台。
- **睡眠定时**：
  - [ ] 面板 → 睡眠定时选 15 分钟：显示剩余时间；改选"关"后消失。
  - [ ] 自定义输入 1 分钟：约 50 秒后音量逐渐变小，1 分钟时暂停；之后按播放音量正常。输入 0 或 601 不被接受。
  - [ ] 定时运行中按 Home（后台播放）或进入小窗：到点仍会淡出并暂停。
- **启动续播**：
  - [ ] 开启「启动时继续播放上次频道」→ 播放某频道 → 从最近任务划掉 App → 重新打开：直接进入该频道，返回键回到频道页。
  - [ ] 关闭该设置后重新打开：停在频道页。
  - [ ] 刷新 / 重新导入该频道所在列表后重新打开：停在频道页，不报错（已知问题 9）。
- **自动播放下一个**：
  - [ ] 准备一个含多个短 MP4 的分组，开启「自动播放下一个」：一个播完自动播放同分组下一个；最后一个播完停止、不回到第一个。
  - [ ] 关闭后：播完停在结束状态。直播频道不受影响。
- [ ] P2a 回归：重连、播放面板、字幕、画面比例正常。

TV（遥控器）：
- [ ] 设置 → 播放：**不显示**画中画、播放手势两项；显示后台播放、启动续播、自动下一个（记录分组长度，已知问题 10）。
- [ ] 播放面板顶部有睡眠定时，遥控器可选择预设与自定义（数字输入）；无「画中画」按钮。
- [ ] 播放中按 Home：后台播放开启时声音继续（TV 上是否显示媒体控件视系统而定）；回到 App 画面恢复。
- [ ] 遥控器播放 / 暂停键在前台播放页无效（已知问题 4），记录实际表现。
- [ ] 启动续播、自动播放下一个同手机步骤。
- [ ] P2a 回归：菜单键面板、左右键音量 / 快进退、数字台号。
