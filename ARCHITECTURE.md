# 架构

## 分层与模块
```
app ──► feature:* ──► core:ui ──► core:designsystem
 │          │            └──► core:common
 │          └──► core:domain ──► core:model
 └──► core:data ──► core:datastore ──► core:model
                └──► core:domain (实现其接口)
core:testing（仅测试）：Fake 仓库、Main 调度器扩展
```
- **core:model**：纯 Kotlin 数据类（AppSettings、ThemeMode、OnboardingState）。
- **core:common**：AppResult / AppError、协程调度器注入、URL 脱敏（日志中隐藏账号密码与 token）。
- **core:domain**：仓库接口与用例（如 CompleteOnboardingUseCase），不依赖 Android。
- **core:datastore**：DataStore Preferences 读写；读失败回退默认值。
- **core:data**：仓库实现，Hilt 绑定。
- **core:designsystem**：Material 3 主题（浅色 / 深色 / AMOLED 纯黑 / 动态取色）、TV 主题、图标、焦点环、空/加载/错误状态组件。
- **core:ui**：设备形态判断（手机 / 平板 / TV）、AppError 的本地化文案。
- **feature:onboarding / channels / epg / settings**：各自的界面、ViewModel、导航入口。

## 约束（由 build-logic 强制）
- feature 模块之间**禁止互相依赖**，只能通过 core:domain 或导航路由协作；违反时 Gradle 配置阶段直接报错。
- 所有模块的 Android / Kotlin / Compose / 测试配置集中在 `build-logic` 约定插件中。

## UI 状态
ViewModel 对外暴露 `StateFlow<UiState>`，界面用 `collectAsStateWithLifecycle` 收集；界面 Composable 无状态，便于预览和测试。

## 导航
类型安全路由（`@Serializable` 对象）。首次启动进入 onboarding 图，完成后跳到频道页并清除引导页回退栈。
- 手机：底部导航栏；平板 / 大窗口：侧边导航轨（NavigationSuiteScaffold 自动切换）。
- TV：Compose for TV 侧边抽屉，D-pad 左键展开；所有可交互元素有清晰焦点环；进入页面自动聚焦主按钮。
- 返回键：非频道页的一级页面先回到频道页，再退出。

## 单 APK 多形态
同一 APK 同时声明 `LAUNCHER` 和 `LEANBACK_LAUNCHER`，触摸屏与 leanback 均为非必需特性，并提供 TV banner。TV 判断依据系统 UI 模式 / leanback 特性，而非屏幕尺寸。
