# 开发日志（DEV-LOG）

> 按时间倒序记录每一步开发：做了什么、为什么、提交、CI 结果、遗留问题。**接手的 AI 先读 `AGENTS.md`，再读本文件最上面几条，就知道停在哪里、下一步做什么。**
> 阶段总览见 `docs/DEVELOPMENT-STATUS.md`，阶段报告见 `docs/phase-reports/`。时间为北京时间（UTC+8）。

## 记录格式
每条：日期时间 · 阶段 / 步骤 · 提交 · CI · 内容 · 下一步 / 遗留。

---

## 2026-11-21 16:10 · P4 计划提交
- **用户**：16:08「继续开发，记得做好文档记录交接」——视为放行 P3c（真机验收仍待做）。
- **内容**：P4 拆为 P4a（XMLTV 解析 + Room v4 + 匹配 + 偏移 / 保留）、P4b（当前 / 下一节目、覆盖层、节目列表 / 详情、按节目名搜索、手动匹配、EPG 设置）、P4c（网格 EPG + 提醒 + WorkManager）。完整计划与 6 个待确认问题写在 `docs/DEVELOPMENT-STATUS.md` 的 P4 节。
- **下一步**：等用户回答 → 记入用户决定 → 开工 P4a。尚无代码改动。

## 2026-11-21 · P3c part 2 收尾：CI 修复与阶段报告
- **提交**：`8bf3e37`（detekt：`EditorRowActions` 移到独立文件）、`c810d98`（编辑器上移 / 下移按名称 / 频道键定位行；原先 `indexOf` 按对象相等，已隐藏或已重命名的行无法移动，由 `EditorViewModelsTest` 发现）。**CI run 44875405245 全绿**。
- **内容**：写 `docs/phase-reports/P3c.md`，更新 DEVELOPMENT-STATUS / README / AGENTS / implementation-plan。
- **下一步**：交付 debug APK（CI artifact）与源码 zip，等用户确认 P3c 后提 P4 EPG 计划。

## 2026-11-18 · P3c part 1：数据层（Room v3 自定义分组、播放列表启用 / 排序、编辑器查询）
- **状态**：已推送，等 CI。
- **用户决定**：P3c 7 项决定见 `docs/DEVELOPMENT-STATUS.md` 第 0 节；用户在真机上确认 K26 已解决后要求「按开发文档继续开发，并记录开发记录以便交接」。
- **内容**：
  - `core:database` schema **v3**：新表 `custom_groups`（id、name、sortOrder、createdAt）与 `custom_group_channels`（groupId + playlistId + uniqueKey 主键，position、addedAt；随分组或播放列表级联删除）；`Migration2To3`；`CustomGroupDao`（成员按稳定键解析到当前代，隐藏 / 停用播放列表的频道不列出但保留成员资格；重排序：列出的在前，其余保持原顺序）。
  - `ChannelDao` 新增编辑器查询 `observeEditorGroups` / `observeEditorChannels`（含隐藏项；`selfHidden` 区分「自身隐藏」与「因分组隐藏」）。`PlaylistDao` 新增 `setEnabled` / `setSortOrder`。
  - `core:model`：`CustomGroup`、`EditorGroup`、`EditorChannel`、`ChannelSource.Custom(groupId)`。
  - `core:domain`：新接口 `CustomGroupsRepository`；`ChannelOverridesRepository` 加 `editorGroups` / `editorChannels`；`PlaylistRepository` 加 `setEnabled` / `reorderPlaylists`。
  - `core:data`：`DefaultCustomGroupsRepository`（名字 trim，空名拒绝，同名复用）；自定义分组切台 / 台号与收藏同规则（`DefaultChannelRepository`）。
  - `core:testing`：新 `FakeCustomGroupsRepository`；`FakeChannelOverridesRepository` 支持编辑器列表；`FakePlaylistRepository`、`FakeChannelRepository`（`customGroupIds`）同步。
  - `feature:channels`：自定义分组作为虚拟分组显示在「最近观看」之后（手机 chips / TV 左栏），可选中、空分组提示；`feature:player`：`PlayerSource.CUSTOM`（id 放在 `groupName`）。
  - CI：新增步骤打印未提交的 Room schema（沙箱没有 JDK，`3.json` 需从 CI 日志取回后提交）。
- **测试**：`CustomGroupDaoTest`（6）、`EditorQueriesTest`（5）、`MigrationTest` +1、`DefaultCustomGroupsRepositoryTest`（4）、`DefaultChannelRepositoryTest` +1、`DefaultPlaylistRepositoryTest` +2、`ChannelsViewModelTest` +1、`PlayerViewModelSourcesTest` +2。
- **下一步**：取回 `3.json` 提交；P3c part 2 界面（播放列表管理：启用开关、上移 / 下移、进入编辑器；编辑器：分组 / 频道的隐藏、重命名、上移 / 下移，重排后浏览排序切「自定义」；频道长按：隐藏 / 重命名 / 加入分组；自定义分组管理）。

## 2026-11-18 · K26 修复（P3c Part 0）
- **提交**：`4b462f3`（修复）、`1936849`（ktlint 格式）、`3610bf4`（文档）。CI run 44299312383 全绿。
- **内容**：接入 NextLib `nextlib-media3ext:1.11.1-0.18.0`（GPL-3.0）FFmpeg 音频解码；MediaCodec 优先、FFmpeg 兜底；音轨回退；关闭 tunneling / offload；不支持时提示；播放面板「流信息」。注意：该 FFmpeg 构建**不含 MP2**。
- **结果**：用户 2026-11-18 真机确认已解决。
- **教训**：沙箱不能跑 Gradle，ktlint 只能靠 CI；类头放得下一行就写一行，表达式函数体首行与签名同行。
