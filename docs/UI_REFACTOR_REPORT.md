# UI 重构报告（按 6 张设计图，手机 / TV 双界面 + 明暗双主题）

> **状态：未编译、未运行、未跑测试。** 我的环境里没有 Android SDK / Gradle，只能用脚本做静态一致性检查，见第 6 节。

## 1. 运行时选择手机或 TV 界面
- `core:designsystem/Device.kt` 里的 `isTv()`：先看 `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION`，不是电视模式再看 `FEATURE_LEANBACK`。另外提供 `rememberIsTv()` 给 Compose 页面用。
- `app/ui/AppNav.kt` 中手机和 TV 共用一套路由，按设备选择外壳和首页：
  - **手机**：首页 `channels`，底部是悬浮圆角导航（直播 / 节目单 / 点播 / 我的）。
  - **TV**：首页 `tv_home`，左侧是窄图标栏（搜索 / 直播 / 节目单 / 点播 / 收藏，设置固定在底部）。

## 2. 设计系统（core:designsystem）
- **`Theme.kt`**：
  - `SdPalette` 语义 token：background / card / cardHigh / stroke / text×3 / accent / onAccent / live / onLive / star / track / focusFill / onFocusFill / glow / scrim / 设置图标色 hue×5。
  - 深色、浅色、AMOLED 三套取值一一对应。
  - Material 3 `ColorScheme`、Typography、Shapes 都由这套 token 生成。
  - `OnVideo {}`：播放器覆盖层内强制使用深色 token。
- **`DesignKit.kt`**：
  - 交互与焦点：`FocusSurface`（手机/TV 通用；TV 焦点为主色描边 + 浅色填充 + 轻微放大，内容可拿到焦点状态）、`onFocusGained`。
  - 基础组件：`SdChip`（胶囊 + 数量）、`LiveBadge`、`OutlineTag`、`SdProgress`、`ChannelLogo`（Logo 加载前或失败时显示彩色块 + 首字母，并按显示尺寸降采样）、`SectionHeader`、`SdSearchField`、`KeyHint`。
  - 设置页：`SettingsGroup` / `SettingsRow` / `IconTile` / `SdSwitch`。
  - 背景与时间：`backdropGlow`（无图时的柔光背景）、`rememberNow`、`formatClock`、`ProgramTime`。
- **默认主题**：`AppSettings.theme` 默认 **跟随系统**，设置里可选 浅色 / 深色 / 跟随系统 / AMOLED。主色默认青绿 `#2EE6C5`，可选 5 种。

## 3. 各页面
| 设计图 | 实现 | 文件 |
|---|---|---|
| 手机 直播首页 | Stream**Deck** 标题 + 播放列表胶囊（名称·频道数，下拉可切换/刷新/管理/添加）、搜索框、带数量的分组 Chip（全部/★收藏/最近/各分组）、“继续观看”横向卡片（LIVE、当前节目、进度）、“全部频道”列表（编号、Logo、名称+收藏星、当前·下一档、进度条、剩余分钟）、“按编号 ▾”菜单（排序 + 视图模式）；网格/紧凑视图保留 | `feature/channels/ChannelsScreen.kt`、`ChannelItems.kt` |
| 手机 我的 | 大标题“我的”、播放列表渐变卡片（名称·频道数·多久前更新）、分组设置（播放 / 节目指南 / 安全与数据 / 外观 / 关于），彩色图标块、右侧值 + 箭头、开关；原 §4.9 的设置项全部保留，滑块改为选择对话框 | `feature/settings/SettingsScreen.kt` |
| 手机 播放页（竖屏） | 16:9 视频区（返回、投屏、设置、全屏、LIVE/回看标记）、频道信息行 + 收藏按钮、“正在播出”卡片（进度、还剩 X 分钟、接下来）、圆形按钮（暂停/回看=从头看/字幕/音轨/画质）、分段“节目单 / 同组频道”、时间轴节目表（已播可回看标签、直播中高亮） | `feature/player-ui/PhonePlayerPortrait.kt` |
| 手机 横屏 | 旋转或点全屏按钮进入原有沉浸式播放和手势；全屏按钮会强制横屏，按返回先回到竖屏 | `PlayerScreen.kt` |
| TV 首页 | Hero 跟随焦点卡片（频道号+名称+LIVE、大标题、分组、进度、时间段、剩余、立即观看/收藏/节目单），右上角“播放列表 · N 频道 + 时钟”，下方横排：收藏频道、最近观看、前 6 个分组 | `feature/channels/TvHomeScreen.kt`、`TvHomeViewModel.kt` |
| TV 网格 EPG | 顶部预览区（正在播出/即将/已播 + 时间、标题、频道·分类、简介）、分组 Chip + 日期下拉（昨天 ~ 保留天数）、左侧频道列、时间轴、红色当前时间线 + 时间标签、焦点节目浅色高亮，底部按键提示。OK = 直播中观看 / 已播回看 / 未来节目详情；长按 OK 或 MENU = 详情（预约 / 回看） | `feature/epg/EpgGridScreen.kt`（手机共用，尺寸不同、无预览区） |
| TV 播放器覆盖层 | 左侧同组频道列表（分组名 · N 个频道 · ▲▼ 切台，正在播放带主色条，焦点白底）、底部信息条（Logo、节目标题、频道号名称 LIVE 清晰度·分类、接下来、进度条、暂停/从头看或返回直播/字幕/音轨/画面比例/收藏） | `feature/player-ui/TvPlayerOverlay.kt` |
| TV 搜索 / 收藏 | 左栏搜索框 + 分组（带数量），右栏频道列表 / 跨列表收藏 | `TvBrowseScreen.kt`、`FavoritesScreen.kt` |

TV 遥控器行为：
- **覆盖层隐藏时**：按 P2 的规则（上下切台、左右音量/快进、OK 呼出、MENU 打开设置、数字键直输）。
- **覆盖层显示时**：方向键在按钮和频道列表之间移动焦点；BACK 先关闭覆盖层；任何按键都会重置 5 秒自动隐藏。

## 4. 为新界面加的数据（尽量少改）
- **频道查询**：`ChannelListItem` 新增 `nowStart / nowEnd / nextTitle / nextStart`（只改 SQL 投影，**不改表结构**，数据库版本不变）。`ChannelRepository` 统一把节目时间从 EPG 源时间换成真实时间（加上偏移）。
- **新 DAO 流**：分组计数、收藏数、可见频道数、最近观看 / 收藏 / 同组频道（带当前·下一节目，每分钟重新查询）。
- **ViewModel**：
  - `ChannelsViewModel`：`counts`、`recent`、`activePlaylist`
  - `TvHomeViewModel`：新增
  - `EpgGridViewModel`：`dayOffset`、`keepDays`
  - `PlayerViewModel`：`schedule`、`groupItems`、`canCatchup`、`playProgram`、`startOver`、`playChannel`
  - `SettingsViewModel`：`playlistSummary`
- **播放设置面板**：`PlayerSettingsDialog` 新增 `sections` 参数，所以“字幕 / 音轨 / 画质 / 画面比例”按钮只打开面板中对应的部分。

## 5. 浅色模式的设计取舍
- **背景层次**：深色模式用“近黑背景 + 深蓝灰卡片”，浅色模式改为“浅灰白背景 `#F3F5F9` + 白色卡片 + 浅灰描边”，靠描边和底色差区分层次，不用阴影。
- **主色加深**：青绿 `#2EE6C5` 在白底上对比度不够，浅色模式自动乘 0.62 加深（默认得到约 `#1C8E7A`）。主色文字和图标在白底上约为 4.5:1，Chip 选中态用加深后的主色 + 白字。
- **LIVE 红**：浅色加深为 `#DC2F3C`，白字对比度足够；收藏星改用更深的琥珀色。
- **TV 焦点**：深色模式是“白底深字”。浅色背景上白底不明显，所以浅色模式反转为“深墨色底 `#111821` + 白字”，主色描边不变，保证 10 英尺距离也看得清。
- **视频覆盖层固定深色**：覆盖层叠在画面上，无论明暗模式都用深色 token（`OnVideo`），否则浅色字和白卡片会盖住视频。视频区底色固定为黑色，属于播放器本身，不算主题色。
- **装饰色调整**：设置图标的 5 种色调在浅色下加深；Logo 占位块的着色透明度从 0.28 降到 0.16；柔光背景的冷/暖色降低饱和度，避免浅色界面显得脏。

## 6. 静态自查（替代编译）
- **字符串资源**：用脚本检查每个模块的 `R.string.*` 都在本模块 strings.xml 中（`nonTransitiveRClass=true`），`DR.string.*` 都在 designsystem 中且已导入别名；中英文 key 一一对应。结果无问题。
- **import**：所有 `com.streamdeck.*` import 都能找到对应声明，设计系统组件的 import 齐全。结果无问题。
- **硬编码颜色**：新页面不再写死颜色。仅保留视频黑底这一处，属于画面本身。
- **括号配对**：所有改动过的文件都已检查。

## 7. 与设计图的差异和已知问题
1. **没有真实画面**：设计图里的“模糊画面”背景是示意，App 没有截图来源，改用按频道着色的柔光块 + Logo。
2. **示意数据不内置**：设计图中的频道和节目只是示意，App 内没有任何内置频道，空状态按规范显示引导。
3. **其他页面只换了配色**（v0.9 时）：点播页、添加播放列表、播放列表管理/编辑器、首启引导、单频道节目表当时还是原来的布局。**v0.10 已全部重做，见第 9 节。**
4. **平板**：横屏平板会直接进入全屏播放器，没有并排信息面板。
5. **没用 tv-material 组件**：TV 页面用标准 Compose + 自研 `FocusSurface`，没有使用 `androidx.tv:tv-material` 的 Card/Surface。依赖还在，后续可以替换。
6. **长按 OK**：依赖 Compose 对按键长按的支持（`combinedClickable`）。个别遥控器如果触发不了，可以改用 MENU 键打开详情，这条路径已经实现。
7. **从头看**：只在频道支持回看时出现；回看中会改为“返回直播”。
8. **列表查询开销**：频道列表 SQL 多了 4 个按行子查询（带 `(playlistId, epgChannelId, start)` 索引）。1 万频道下的滚动性能需要真机验证。
9. **首次编译**：建议先跑 `./gradlew ktlintFormat`，detekt 规则可能需要更新 baseline（文件变长、参数变多）。

## 8. 手动验收清单
- [ ] 手机竖屏：首页 Chip 数量正确，继续观看、排序/视图菜单正常，长按频道可收藏
- [ ] 手机播放页：竖屏布局正常，全屏按钮可横屏、返回可回到竖屏，字幕/音轨/画质按钮只打开对应部分，同组频道可切台，已播节目可回看
- [ ] 我的：所有开关和选择项能保存；家长锁开关可设置/移除 PIN
- [ ] TV：图标栏焦点可见且没有焦点陷阱；首页 Hero 跟随焦点；EPG 方向键滚动、当前时间线正确、OK / 长按 OK / MENU 正常
- [ ] TV 播放：OK 呼出覆盖层，焦点在暂停按钮上；方向键可以进入左侧频道列表；BACK 先关闭覆盖层；5 秒自动隐藏
- [ ] 浅色 / 深色 / 跟随系统 / AMOLED 切换后，手机和 TV 各页面可读、焦点清晰、LIVE 标签清楚


---

# v0.10.0：剩余页面按新设计重做

> **状态：同样未编译、未运行、未跑测试。** 只做了脚本静态检查（见 9.6）。

## 9.1 设计系统新增（core:designsystem）
- **`PageKit.kt`**（新文件）：
  - `SdTopBar`：二级页面标题栏，含圆形返回键、副标题和操作区。
  - 按钮：`SdButton`（Primary / Secondary / Danger，TV 聚焦时变高对比填充）、`SdIconButton`。
  - 卡片与输入：`SdCard`（分组表单卡片）、`SdTextField`（卡片风格输入框，聚焦时显示主色描边，`large` 为 TV 大号版）。
  - 行与选项：`SwitchRow`（整行可切换，TV 上只占一个焦点）、`OptionCard`（添加方式卡片）、`PageDots`（分页指示器）。
  - `ActionPanel` + `PanelAction`：手机上是 Material 底部弹层，TV 上是右侧侧边面板。面板打开后焦点落在第一项，BACK 关闭；先关闭面板再执行动作，所以一个动作可以接着打开另一个面板（例如“移动到分组”）。
  - 对话框：`SdDialog` / `InputDialog` / `ConfirmDialog` / `DialogButtons`，焦点请求放在对话框窗口内部。
- **`Components.kt`**：统一状态组件，原有签名不变，只在末尾增加可选参数。
  - `EmptyState`：彩色圆形图标、可选标题和主/次按钮。
  - `ErrorState`：“出错了”标题，加一个“原因”框原样显示具体原因（HTTP 状态码、超时等），有重试按钮和可选的次按钮。
  - `LoadingState` 换成新的样式。
  - `ProgressState`（新增）：大圆环，中间显示数值，带取消按钮。
  - TV 上的状态页会自动把焦点放到主按钮上。
- 新增 `ds_error_title / ds_reason / ds_page_of / ds_more / ds_next / ds_close`（中英各一套）。

## 9.2 各页面
| 页面 | 手机 | TV | 文件 |
|---|---|---|---|
| 点播首页 | 大标题 + 播放列表胶囊（多个 Xtream 列表时显示）、搜索框、电影/剧集 Chip、分类 Chip 横滑、海报网格（2:3，评分角标，续播进度条），顶部“继续观看”横向卡片 | 左栏：标题、播放列表选择、电影/剧集、分类列表（选中项有主色条，聚焦时高对比填充）；右栏：搜索 + 海报网格，顶部“继续观看”行 | `feature/vod/VodPhone.kt`、`VodTv.kt`、`VodCommon.kt` |
| 点播详情 | 360dp 背景海报渐隐到页面底色，小海报 + 标题 + 评分·年份·时长·类型；播放 / 继续播放（显示时间点）+ 已看按钮、进度条、简介；剧集有分季 Chip 和集列表（16:9 缩略图、进度、已看勾选） | 全屏背景 + 左侧渐隐，海报 + 大标题 + 元信息 + 简介，主按钮自动获得焦点；剧集有“继续/播放 第 X 季 第 Y 集”、分季 Chip、横排集卡片（长按 OK 切换已看）、按键提示 | 同上 |
| 添加播放列表 | 标题栏；三张方式卡片（M3U 网址 / 本地文件 / Xtream）；“基本信息”卡片；“连接安全”卡片（明文 HTTP 开关，填了 http:// 但没打开开关时给出提示）；可折叠的“高级选项”（User-Agent、自定义请求头、EPG 地址、更新策略 + 间隔）；导入按钮 | 左栏大标题 + 方式卡片（首个获得焦点），右栏大号表单，D-pad 可以走完全部输入和按钮 | `feature/playlist/AddPlaylistScreen.kt` |
| 导入进度 / 失败 | `ProgressState`：圆环中间显示已解析条数，“已解析 N 条”，取消按钮；失败用 `ErrorState`，显示具体原因，带“重试”和“返回修改” | 同一组件的 TV 尺寸，取消 / 重试按钮自动获得焦点 | 同上 |
| 播放列表管理 | 卡片：类型图标块、名称、类型·频道数、更新时间、刷新状态、启用开关；左侧拖动手柄（上下拖动排序）；底部操作：更新 / 频道 / 编辑 / ⋯（上移、下移、编辑、删除） | 左侧列表（聚焦即选中），右侧面板显示所选列表的详情和大按钮（启用开关、立即更新、编辑分组和频道、编辑、上移 / 下移、删除） | `PlaylistManagerScreen.kt` |
| 列表编辑器 | 两级：分组 → 频道。行内显示“已隐藏 / 已锁定”标签；点击分组进入频道，⋯ 或长按打开底部弹层（重命名、隐藏/显示、锁定/解锁、上移、下移；频道多一项“移动到分组”，会打开分组选择弹层）；BACK 先回到分组层 | 左栏分组（OK 查看频道，长按 OK 更多操作），右栏是所选分组的频道（OK 打开侧边面板），带按键提示 | `PlaylistEditorScreen.kt` |
| 首启引导 | 3 页加分页指示器：欢迎（Logo、Stream**Deck** 字标、3 条特性）→ 合规声明卡片 + 隐私说明 → 添加第一个播放列表（三张方式卡片，点哪张就带着该方式进入添加页）；底部返回 / 继续按钮 | 左右分栏：左侧品牌面板（大 Logo、字标、标语、分页点），右侧大字内容和按钮，每页主按钮自动获得焦点 | `OnboardingScreen.kt` |
| 单频道节目表 | 标题栏（频道名、编号·分组、指定 EPG、观看直播），分段“今天 / 明天 / 一周”，时间轴列表样式和竖屏播放页节目单一致；一周模式按天分组并带日期标题；自动滚到正在播出的节目 | 左侧频道面板（大 Logo、名称、正在播出卡片、观看直播 / 指定 EPG），右侧日期 Chip + 时间轴（聚焦高对比）+ 按键提示 | `feature/epg/ChannelProgramsScreen.kt` |
| 节目详情 / 指定 EPG | 改用 `SdDialog`：状态·日期·时间、分类标签、海报、简介、“开始时自动切换”开关；按状态显示 观看直播 / 回看 / 预约（取消预约），主按钮自动获得焦点 | 同一组件（TV 尺寸） | `ProgramDetailDialog.kt` |

单频道节目表的操作：
- **点击或按 OK**：直播中的节目直接观看；已播且频道支持回看的进入回看；未开始的节目切换预约。
- **长按**：打开详情。
- **行尾标签**：LIVE / 回看 / 已预约（铃铛）/ 预约。

## 9.3 为新界面做的少量逻辑改动
- **`VodHomeViewModel.continueWatching`**（新增）：从已有的进度表里取未看完的电影，按最近观看排序，最多 12 部，再查电影条目。只读，不改表。
- **`AddPlaylistViewModel`**：`submit / submitXtream` 增加可选参数 `PlaylistExtras`（UA、请求头 JSON、EPG 地址、更新策略、间隔）。创建播放列表后用 `PlaylistRepository.update` 写入，这一步也会按新策略排好 WorkManager 任务。旧调用方式不受影响。
- **`HeaderLines`**（新增，纯 Kotlin）：把“每行一个 `Name: Value`”的文本转成 `headersJson`（`StreamResolver` 读取的就是这个 JSON 对象），也能反向转换；同时报告格式不对的行号。附 `HeaderLinesTest`，6 个用例。
- **`PlaylistManagerViewModel.counts`**（新增）：每个列表的可见频道数，用已有的 `ChannelRepository.visibleCount`。
- **导航**：添加页路由改为 `add?type={type}`，原来的 `add` 仍然能匹配。首启引导点击方式卡片后，添加页会预选该方式；`AddPlaylistScreen` 新增参数 `initialType`。
- **表单状态**：添加表单的输入提升到页面级并可保存，导入失败后“返回修改”时内容不会丢。密码不写入保存状态。
- **数据库**：没有改表结构，数据库版本不变；也没有改任何 Repository 接口签名。

## 9.4 与要求 / 设计的差异
1. **点播没有“收藏”按钮**：现有数据层没有点播收藏表，加表要改数据库结构。这次按“尽量不改 data”处理，详情页提供“播放 / 继续播放”和“标记已看 / 未看”。需要的话下一轮可以加 `vod_favorite` 表（需要数据库迁移）。
2. **“继续观看”只含电影**：剧集的进度 key 只有 episodeId，找不到所属剧集，所以剧集续播从剧集详情页的“继续 第 X 季 第 Y 集”进入。
3. **TV 点播首页是海报网格**：没有按分类各放一行，因为每行一个分页查询会明显增加开销。左侧分类加右侧网格，可以完整用遥控器浏览。
4. **拖动排序只在手机上**：靠左侧手柄上下拖动，每拖过约 72dp 交换一次位置，每次交换写一次数据库。TV 用上移 / 下移按钮。拖动时没有跟手的位移动画。
5. **编辑播放列表对话框**：仍由 `feature:settings` 的 `PlaylistEditDialogWithPolicy` 提供（Material 对话框，配色跟随主题）。`feature:playlist` 自带的备用对话框已改用 `SdDialog`。
6. **频道编辑器里的分组没有显示频道数**：数据层没有现成的查询。

## 9.5 已知问题与风险
- **TV 文件选择**：很多电视没有系统文件选择器。点“选择文件”时会捕获 `ActivityNotFoundException` 并提示改用网址。
- **TV 文本输入**：依赖系统输入法。方向键可以在输入框之间上下移动，但个别输入法可能会吞掉方向键。
- **TV 侧边面板**：用 `Dialog` 实现。焦点请求放在对话框内部，但个别设备上首次打开时焦点可能要按一次方向键才出现。
- **长按 OK**：与 v0.9 相同，依赖 `combinedClickable`。编辑器、点播集卡片、节目表在 TV 上都有替代路径（OK 打开面板 / 详情）。
- **行长度**：超过 160 字符的行已自动在参数逗号处折行。缩进风格可能不符合 ktlint，首次编译前请运行 `./gradlew ktlintFormat`；detekt 的 `LongMethod` / `LongParameterList` 可能需要更新 baseline。

## 9.6 静态自查（替代编译）
- **字符串**：每个模块的 `R.string.*` 都在本模块 strings.xml 中，`DR.string.*` 都在 designsystem 中且已导入别名；所有模块中英文 key 一一对应（app 模块的 `app_name` 是 `translatable="false"`）；没有发现未转义的 `'` 或 `&`。
- **import**：所有 `com.streamdeck.*` 的 import 都能找到对应声明；设计系统符号在其他模块使用时都已导入；Material 图标的包路径（filled / outlined / automirrored）和用法一致，没有未使用的图标 import；顺手删掉了 AppNav 里一个本来就没用的 import。
- **括号**：去掉字符串和注释后，`{} () []` 在所有改动文件中都配对。
- **行长度**：所有改动文件都不超过 160 字符。
- **可点击元素**：都通过 `FocusSurface` / `SdButton` / `SdIconButton` 提供了 contentDescription；TV 上每个页面都有初始焦点或可聚焦的主按钮。
- **其他**：没有 TODO / FIXME，也没有内置任何频道或示例数据。

## 9.7 手动验收清单（v0.10）
- [ ] 点播首页：手机和 TV 都能切换电影/剧集和分类，搜索有效，看过一半的电影出现在“继续观看”
- [ ] 点播详情：播放 / 继续播放 / 已看切换正常；剧集分季切换、集进度、已看标记正常；TV 打开时主按钮已获得焦点
- [ ] 添加播放列表：三种方式都能导入；http:// 地址有提示；高级选项里的 UA、请求头、EPG、更新策略已写入（可在编辑对话框中核对）；请求头格式错误会标出行号
- [ ] 导入中：圆环和计数会更新，可以取消；失败时显示具体原因，“返回修改”后表单内容还在
- [ ] 播放列表管理：频道数正确；手机拖动排序、TV 上移 / 下移有效；启用开关、更新、删除确认正常
- [ ] 编辑器：两级导航和 BACK 行为正确；面板里每项操作都生效；“移动到分组”的面板可以选择
- [ ] 首启引导：三页切换和分页点正确；选中某个方式后添加页已预选；TV 每页焦点都在主按钮上
- [ ] 单频道节目表：今天 / 明天 / 一周切换；一周模式有日期标题；OK 和长按行为符合 9.2；预约后显示“已预约”
- [ ] 浅色 / 深色 / AMOLED 下以上页面都可读，TV 焦点清晰
