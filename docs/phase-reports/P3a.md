# P3a 阶段报告：数据层（收藏、观看历史、用户覆盖、稳定键续播、台号 2 秒）

日期：2026-11-18　应用：StreamBox Player（debug 包 `com.streambox.player.debug`）

P3 经用户确认拆为 **P3a 数据层**（本次）、**P3b 界面**（搜索、收藏页、最近观看、虚拟分组、三种视图、TV 双栏）、**P3c 列表编辑器**。P3a 提交：
- `460ac22` P3a part 1：Room v2（收藏 / 观看历史 / 用户覆盖）、上次频道改为 uniqueKey、Robolectric DAO / 迁移测试
- `3a57202` P3a part 2：TV 台号输入 2 秒确认（说明书 4.2）
- 文档提交见 `docs/DEVELOPMENT-STATUS.md`

本阶段**没有任何新界面**（也没有新增用户可见字符串）；用户能直接感受到的只有：台号停顿 2 秒才跳台、刷新播放列表后"启动续播"仍有效、播放过的频道开始进入观看历史（P3b 才有页面显示）。

## 做了什么

### Room schema v1 → v2（`core:database`）
- `StreamBoxDatabase` version 2，`Migration1To2`（`migration/Migration1To2.kt`），导出 `schemas/.../2.json` 已提交（`1.json` 保持 P1 原样）。`DatabaseModule` 注册 `StreamBoxDatabase.MIGRATIONS`。
- 新表，**全部以 `(playlistId, uniqueKey)` 或 `(playlistId, 分组名)` 为主键，从不存频道 id**，外键随播放列表级联删除：
  - `favorites(playlistId, uniqueKey, addedAt UTC 毫秒, customOrder 可空)`，索引 `addedAt`。
  - `watch_history(playlistId, uniqueKey, watchedAt UTC 毫秒)`，索引 `watchedAt`；一个频道一行，再看一次移到最前；写入时同一事务裁剪到最近 **50** 条。
  - `channel_overrides(playlistId, uniqueKey, hidden, customName, customSortOrder)`。
  - `group_overrides(playlistId, groupName, hidden, customName, customSortOrder)`（分组名为播放列表里的原名，空串 = 未分组）。
- `channels`、`channel_groups` 各加两列 `customName`、`customSortOrder`（ALTER TABLE ADD COLUMN，旧数据保留）。

### 覆盖表的落地方式：物化（设计决定）
- 覆盖表是唯一真相；其值**物化**到频道表：`channels.hidden / customName / customSortOrder`、`channel_groups.hidden / customName / customSortOrder`。
  - **导入提交时**：`RoomPlaylistImportStore.verifyAndCommit` 在同一事务、切换 `activeGeneration` 之前调用 `OverrideDao.applyToGeneration(playlistId, generation)`（只更新有覆盖的行），然后才统计分组频道数并原子切换。新一代在可见的那一刻就已带上用户的隐藏 / 重命名 / 排序。
  - **用户修改时**：`OverrideDao.updateChannel / updateGroup / update*Positions`（`@Transaction`）写覆盖表（回到默认值时删除该行），再重写受影响的各代行并刷新当前代的分组计数。
- 为什么不在每条查询里 join：分页、切台（上一个 / 下一个 / 首 / 尾）、台号、自动下一个、FTS 等查询已有 10 余条，全部再 join 两张覆盖表既慢又容易漏；物化后它们仍是对频道表的简单查询，只需把"显示名 / 有效顺序"表达式换上。
- `channels.hidden` 现在表示**实际隐藏**：频道自身被隐藏，或其分组被隐藏。分组的 `channelCount` 只计可见频道（全隐藏的分组从分组列表消失）。
- 读查询约定（`ChannelDao`）：显示名 `COALESCE(customName, name)`；有效顺序 `COALESCE(customSortOrder, sortOrder)`，导入顺序作次级排序（切台的位置比较也用这对值）；分组按显示名合并、筛选、返回（`PlaybackItem.groupName` 也是显示名）；无 tvg-chno 时台号仍是导入位置 `sortOrder + 1`（排序不改变台号）。
- 重排序（`ChannelOverridesRepository.reorderChannels / reorderGroups`）：把所给条目**在它们当前占据的位置内置换**，未列出的条目位置不变；不在当前数据里的 key 忽略。

### 收藏 / 历史的读取
- `FavoriteDao.observeFavoriteChannels()`、`WatchHistoryDao.observe(limit)` 用 uniqueKey 关联到**各播放列表当前代**的频道，并过滤停用列表与隐藏频道。频道在某次刷新中消失时记录**保留不显示**，之后的刷新里频道回来就重新出现。
- 收藏顺序（用户决定）：**设置了自定义（拖动）顺序的在前、按该顺序；其余按加入时间从早到晚**（新收藏排在末尾）。`reorder(keys)` 给所列条目 0..n-1，未列出的清除自定义位置；`clearCustomOrder()` 恢复按时间。跨播放列表统一排序。
- 删除播放列表会同时删除它的收藏、历史与覆盖（外键级联）。

### 领域层与数据层
- `core:model`：`ChannelKey(playlistId, uniqueKey)`；`Channel.key`、`PlaybackItem.uniqueKey / key`；`LastPlayedChannel(playlistId, uniqueKey)`（原为 channelId）；`WatchHistoryEntry`、`ChannelOverride`、`GroupOverride`。
- `core:domain` 新接口：`FavoritesRepository`、`WatchHistoryRepository`（`MAX_ENTRIES = 50`）、`ChannelOverridesRepository`；`ChannelRepository.channelIdByKey(key)`。新用例：`ToggleFavoriteUseCase`、`RecordPlaybackStartUseCase`（记上次频道 + 写历史）、`ResolveLastChannelUseCase`（key → 当前 id，找不到则清除记录）。
- `core:data`：`DefaultFavoritesRepository`、`DefaultWatchHistoryRepository`、`DefaultChannelOverridesRepository`，在 `DataModule` 绑定；`TimeModule` 提供 `java.time.Clock.systemUTC()`（测试可注入固定时钟）。名称重命名会 trim，空白名 = 恢复原名。
- `core:testing`：新增 `FakeFavoritesRepository`、`FakeWatchHistoryRepository`、`FakeChannelOverridesRepository`；`FakeChannelRepository` 实现 `channelIdByKey`，其 `PlaybackItem` 带 uniqueKey。

### 上次频道改为稳定键（修 K18，并修掉 K20 在续播中的表现）
- DataStore：`last_played_playlist_id` + 新键 **`last_played_channel_key`**（String）。P2b 的 `last_played_channel_id` 不再读取（频道 id 刷新即失效），下次保存或清除时删除。**影响**：升级后第一次冷启动不会续播（旧记录无法可靠解析），播放任一频道后恢复正常。
- `PlayerViewModel.rememberPlayingChannel`（频道首次 `isPlaying`，每个频道一次）改为调用 `RecordPlaybackStartUseCase`：同时记上次频道和观看历史。播放失败的频道不记。
- `MainActivityViewModel.channelToResume()` 改用 `ResolveLastChannelUseCase`：刷新后频道换了新 id 也能续播；频道真的不在了才清除记录。

### 台号输入 2 秒（用户决定，说明书 4.2）
- `ChannelNumberInput.DEFAULT_TIMEOUT_MS` 1.5 s → **2 s**。先写了失败测试（`the default timeout is 2 seconds (requirements 4_2)`，在旧代码上本地确认失败），再改常量。满 4 位 / OK 立即提交、返回键删位不变。

### 测试基础设施：Robolectric（用户同意的新依赖）
- `org.robolectric:robolectric` **4.18.1**（2026-11-18 查 Maven Central 最新稳定版），写入 `gradle/libs.versions.toml` 与 `docs/implementation-plan.md` 版本表。
- 新约定插件 **`streambox.android.robolectric`**（`build-logic/.../AndroidRobolectricConventionPlugin.kt`）：该模块单测改用 **JUnit 4**（`useJUnit()`），加入 junit4、robolectric、coroutines-test。原因：Robolectric 只有 JUnit 4 runner，而 JUnit 6 已**弃用** Vintage 引擎（JUnit 6.0 发布说明），所以不走 `junit-vintage-engine`，也不在同一模块混跑。其他模块仍是 JUnit 5（Jupiter）。目前只用于 `core:database`；core:data 的仓库测试用 MockK 在 JVM 上跑。
- Robolectric SDK 固定为 35（`core/database/src/test/resources/robolectric.properties`）。

## 验证结果
- 本地（aarch64 沙箱，临时装了 JDK 17 + Android SDK 37 + 社区 aarch64 aapt2）：`ktlintCheck detekt`，以及 core:database / core:data / core:domain / core:datastore / feature:player / feature:channels / app 的单测全部通过；整仓 `test` 一次跑完会因沙箱内存不足被杀，以 CI 为准。
- 先写失败测试：K18（`resume survives a refresh that re-imported the channel under a new id (K18)`）与 2 秒超时两条测试先在旧代码（`19dafb4` 的工作副本）上运行并确认失败，再实现修复。
- 新增测试（+49 项，348 → **397**）：core:database 27（`FavoriteDaoTest` 8、`WatchHistoryDaoTest` 4、`OverrideDaoTest` 8、`ChannelDaoKeyTest` 3、`MigrationTest` 4，均 Robolectric）、core:data +10、core:domain +6、core:datastore +1、feature:player +4、app +1。
- CI：run **44248545012**（`3a57202`）**全绿**：ktlint / detekt、`test`、`:app:lintDebug`（0 error、4 warning，与 D10 相同）、`:app:assembleDebug` + `:app:assembleRelease`。单测 **397 项、0 失败**（从该 run 的 `reports` artifact 统计 debug 单测结果）：app 10、core:common 12、core:data 34、core:database 27、core:datastore 12、core:domain 16、core:network 8、core:parser 60、core:player 94、feature:channels 6、feature:onboarding 3、feature:player 88、feature:playlist 14、feature:settings 13。
- debug APK（CI artifact `streambox-debug-apk`，交付名 `StreamBox-P3a-debug.apk`）：21,469,767 字节，SHA-256 `a20db4ec9e72e9b3a52b6347af5bf25172fc0986eda9c50306ee1b9ba20cd71a`。文档提交后 CI 会重新构建，APK 内容与此相同（文档不进 APK），但字节可能不同。

## 没做什么
- 任何界面：搜索、收藏页、最近观看、虚拟分组「全部 / 收藏 / 最近观看」、收藏按钮、三种视图、TV 双栏、列表编辑器（P3b / P3c）。
- 虚拟分组接入切台 / 台号 / 自动下一个 / 通知上下曲（P3b）。
- 搜索：未改 FTS。`channels_fts` 只索引原始 `name` / `tvgName`，**重命名后的名字不在 FTS 里**（P3b 决定：搜索时对 `customName` 另做 LIKE，或升 v3 把 customName 加进 FTS）。
- 播放列表的编辑 / 排序 / 启用停用（说明书 4.1，P3c）；自定义分组（把频道放进用户自建分组，P3c 需要新表 / 新迁移）。
- 播放页路由仍按频道 id（`PlayerRoute(channelId, groupName?)`）：从频道列表进入播放页的瞬间若恰好刷新完成，仍可能显示"频道不存在"（K20 的这部分留到 P3b，可给路由加 uniqueKey）。
- 设置「播放」拆二级页（K8）、K1、K14：按计划放在 P3b。
- 频道页数字直输：用户决定**不做**（只在播放页）。

## 已知问题
1. 升级到 P3a 后第一次冷启动不会续播（旧的按 id 记录被丢弃）；播放任一频道后恢复。
2. 隐藏分组会让其频道在「全部」里也不可见；某频道被单独隐藏后，即使分组再显示它仍隐藏（符合预期，但 P3c 界面需要能分别看到两种隐藏）。
3. 收藏 / 历史中频道已不在当前数据里时，记录保留但不显示，没有"失效条目"提示；历史的 50 条上限按存储行数计，失效条目也占名额。
4. 自定义顺序与导入顺序混合：只对部分频道设置了顺序时，有效顺序在两套数值间交错（重排序采用"在原位置内置换"以尽量避免跳动）。P3c 编辑器若需要"整组重排"可一次传入整组。
5. 观看历史目前只在数据层，没有界面查看 / 清空入口（P3b）。
6. 全部改动未经真机验证；Room 迁移只在 Robolectric（SQLite 由 Robolectric 提供）上测过，真机升级安装需验收（见下）。

## 真机验收步骤（需用户）
设备：手机 realme GT6 + Android TV。**请用覆盖安装**（不要先卸载），这样才能验证 v1 → v2 迁移。

### 手机
- [ ] 在已装 P2 版本（有播放列表、播放过频道）的手机上**覆盖安装** P3a debug APK，启动不崩溃，播放列表和频道都还在（数据库迁移成功）。
- [ ] 频道页分组、频道列表、频道号显示与升级前一致。
- [ ] 打开设置「启动时继续播放上次频道」，播放某个频道几秒，彻底退出 App 再打开：直接进入该频道。
- [ ] 在播放列表管理里**刷新**该播放列表，等导入完成后彻底退出 App 再打开：仍续播同一个频道（P2 版本这里会失效，K18）。
- [ ] 升级后第一次冷启动不续播属于预期（已知问题 1）。
- [ ] 刷新 / 删除播放列表、切台、自动下一个等 P1–P2 功能无回归。

### TV
- [ ] 覆盖安装后启动正常，频道列表与升级前一致。
- [ ] 播放页按数字键输入台号：停顿约 **2 秒**后跳台（原 1.5 秒）；输满 4 位或按 OK 立即跳；返回键删一位；不存在的台号提示"没有 N 号频道"。
- [ ] 启动续播 + 刷新播放列表后续播（同手机步骤）。
