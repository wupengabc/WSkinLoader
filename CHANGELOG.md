# 更新日志 / Changelog

## 1.6.0 — 2026-09-16

本次更新聚焦于「玩家皮肤映射」、更聪明的加载策略，以及皮肤缓存的即时刷新。

### 新功能 / New Features

- **玩家皮肤/披风映射**：可以把某位玩家的皮肤或披风替换为另一位玩家的。配置界面中新增「玩家映射」区块，分别填写「皮肤来源」和「披风来源」即可；留空表示使用该玩家自己的皮肤。
  - Player skin/cape mapping: override a player's skin or cape with another player's. A new "Player Mapping" section in the config screen lets you set a skin source and a cape source; leave them empty to use the player's own textures.
- **客户端快捷命令**：`/wskin skin <玩家>`、`/wskin cape <玩家>` 快速为自己套用他人皮肤/披风，`/wskin reset skin|cape` 恢复使用自己的皮肤。同时注册了 `/skin` 别名，输入 `/wskin` 可查看用法。
  - Client commands: `/wskin skin <player>`, `/wskin cape <player>` apply another player's skin/cape to yourself, and `/wskin reset skin|cape` restores your own. `/skin` is provided as an alias, and a bare `/wskin` prints usage.
- **配置界面全面重做**：改为原版风格的选项面板布局（左侧分类列表 + 右侧逐项设置），全部使用原版组件（Button、CycleButton、EditBox、列表等），因此可被资源包和界面模组一同美化。
  - Config screen rebuilt: a vanilla-style options panel (category list on the left, one option per row on the right) using only vanilla widgets, so resource packs and UI add-ons can restyle it.
- **皮肤预览界面重做**：同样改用原版控件，保留 3D 模型预览、旋转拖拽和模型类型切换。
  - Skin preview screen rebuilt with vanilla widgets, keeping the 3D preview, drag-to-rotate and model type toggle.
- **玩家列表头像跟随自定义皮肤**：Tab 列表中显示的头像现在与玩家模型上显示的皮肤保持一致。
  - Tab-list heads now match the custom skin shown on the player model.

### 改进 / Improvements

- **加载策略更清晰**：默认优先使用 Mojang 正版皮肤/披风，只有在该账号没有对应纹理时才回退到自定义 API；配置项 `useCustomApi` 默认改为关闭（开启后才强制走自定义 API）。
  - Clearer loading strategy: Mojang premium textures are used by default, and the custom APIs are only used as a fallback when that account has no such texture. `useCustomApi` now defaults to off.
- **支持跟随来源玩家的模型类型**：使用玩家映射时，粗细手臂模型会自动跟随来源玩家的皮肤。
  - Model type now follows the source player's skin when mapping is used.
- **配置界面新增「设置 / 清除」按钮**，映射编辑、模型类型（自动 / 纤细 / 宽体）和 API 索引选择更直观。
  - New "Set / Clear" buttons in the config screen, with clearer model type (Auto / Slim / Wide) and API index selection.
- **界面新增更多语言文本**，补齐全部 10 种语言（含简体中文、繁体中文、英语等）的新增条目。
  - More localization strings added across all 10 languages.

### 修复 / Bug Fixes

- **修复缓存删除后皮肤不刷新的问题**：清除缓存或修改映射后会立即重新加载，无需重进游戏或重新登录。
  - Fixed skins not refreshing after clearing the cache: mappings and cache resets now re-load immediately, no relog required.
- **修复正版服务不可用时的回退行为**：Mojang 服务暂时不可用时，不再用第三方皮肤覆盖正版皮肤。
  - Fixed fallback behavior when Mojang services are unavailable: third-party skins no longer override premium skins in that case.
- **网络请求更稳健**：Mojang 查询失败后进入 30 秒冷却，避免反复请求；请求连接会被正确关闭；玩家名与 API 地址会做 URL 编码。
  - More robust networking: Mojang lookups enter a 30-second failure cooldown, connections are properly closed, and player names / API URLs are URL-encoded.
- **配置保存更安全**：改为「先写临时文件再原子替换」，避免写入中断导致配置损坏；同时修复了配置读取的多线程竞争问题。
  - Safer config saving: writes to a temp file then atomically replaces it, preventing corruption on interrupted writes, plus a fix for a config-loading race condition.
- **修复头颅渲染空指针**：当皮肤尚未解析完成时，玩家头颅不再崩溃。
  - Fixed a null-pointer crash on player head rendering when the skin is not resolved yet.

### 兼容性 / Compatibility

- 升级到 **Minecraft 26.3**、Fabric Loader 0.19.5、Fabric API 0.160.5。
  - Updated to Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.160.5.
- Mixin 兼容级别提升至 **Java 25**。
  - Mixin compatibility level raised to Java 25.
- 移除了 Access Widener 依赖，改用公开 API 获取渲染后端信息。
  - Removed the Access Widener dependency; render backend info now uses public APIs.
