# P5 阶段报告：回看（Catch-up）/ 时移（Timeshift）

> **未经编译验证**：代码没有构建或运行过，单元测试也没有实际执行。

## 做了什么
- **回看 URL 生成器**（`feature:catchup`，纯 Kotlin）：
  - 类型：`default`、`append`、`shift`（也认 `timeshift`）、`flussonic`（也认 `fs`、`flussonic-hls`、`flussonic-ts`）、`xc`、`vod`；不认识的值按 `default` 处理；没写类型时，有 `catchup-source` 就按 `default`，没有就按 `shift`。
  - 占位符：`{utc}` `${start}` `{utcend}` `${end}` `{lutc}` `${now}` `${timestamp}` `{duration}` `${duration}` `{offset}` `${offset}`，支持带除数的写法（如 `{duration:60}`，结果以分钟计），以及 `{Y}{m}{d}{H}{M}{S}`、`{utc:Y-m-d H:M:S}`（strftime 风格）、`${start:yyyyMMddHHmmss}`（java.time 格式）。
  - 可配置的格式规则：人类可读占位符使用的时区（默认设备时区）、Xtream 的服务器时区（默认 UTC）。
  - Flussonic：HLS 地址改写为 `index-{start}-{duration}.m3u8`（保留查询参数），mpegts 改写为 `timeshift_abs-{start}.ts`。
  - Xtream：生成 `/timeshift/{user}/{pass}/{分钟}/{YYYY-MM-DD:HH-MM}/{id}.ts`，凭据可以显式传入，也可以从直播地址解析（P6 起直接用保存的凭据）。
- **回看入口**：在网格 EPG 或频道节目页点一个已播完的节目 →“回看”。当频道声明了 `catchup` / `catchup-days`，且节目还在回看天数内时，才显示这个按钮。
- **回看播放**：信息条显示“回看 · 开始时间”和“返回直播”按钮；回看流可以拖进度、快进快退。
- **时移**：
  - 手机：直播中左右滑动，或双击左侧区域后退 10 秒。
  - TV：遥控器快退键后退 60 秒、快进键前进 60 秒，停止键或下一曲键返回直播。
  - 退到的位置受回看天数限制；向前超过“当前时间 − 15 秒”会自动回到直播。
- **时间处理**：始终区分三套时间——真实 UTC、EPG 源时间（`EpgClock` 加上偏移）、显示用的本地时区。
- **测试**：`CatchupUrlBuilderTest` 共 39 个用例（30 个普通测试，外加一个展开为 9 组的参数化测试），覆盖所有回看类型、占位符和格式化、跨日、伦敦春季夏令时、纽约秋季夏令时（本地小时重复）、−90 分钟负偏移、+30 分钟跨午夜偏移，以及非法输入。

## 没做 / 限制
- 时移是重新打开一条回看流，不是在本地缓存直播流后再拖动，所以每次时移都要重新缓冲。
- 不同服务商对 `${start}` 用 UTC 还是本地时间并不统一。目前人类可读占位符按设备时区格式化；还没有提供“每个播放列表单独指定时区”的设置界面，代码层面已经支持（`CatchupRequest.zone` / `xcZone`）。

## 已知问题
- 回看地址生成失败时（例如 Flussonic 地址格式不对），播放页显示失败原因，但不会自动退回直播。

## 手动验收清单（P5）
- [ ] 对已播完的节目点“回看”，能播放对应时段
- [ ] 回看中“返回直播”有效
- [ ] 手机直播中左右滑动可以时移；TV 上快退/快进键可以时移
- [ ] 设置 EPG 时间偏移后，回看的时段跟着变化
