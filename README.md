# WSkinLoader

**English** | [中文](#中文)

A **client-side Minecraft Fabric mod** that loads skins and capes from **custom APIs (LittleSkin, Blessing Skin, etc.)** for offline players, while **premium players keep their official Mojang textures**. Includes player-to-player texture mapping, a vanilla-style in-game config screen, and a 3D skin preview.

---

## Features

- **Premium-first loading strategy** (configurable): Mojang textures are loaded first; custom APIs are only used when that account has no skin/cape. Can be turned off to skip Mojang entirely.
- **Protection when Mojang is unavailable** (configurable): if the Mojang session server is temporarily unreachable, your premium skin is never silently replaced by a third-party one.
- **Custom skin/cape APIs**: add any number of HTTP(S) APIs, each with a display alias and a `%name%` placeholder. APIs are tried in order.
- **Per-player overrides**: force custom APIs, skip the premium check, choose a fixed API index, or pin a model type (auto / slim / wide).
- **Player texture mapping**: wear another player's skin or cape with a single command, effective immediately without relogging.
- **Vanilla-style config screen**: General / Skin API / Cape API / Player Override / Cache tabs, built entirely from vanilla widgets so resource packs and UI mods can restyle it.
- **3D skin preview**: drag to rotate, scroll to zoom, model type toggle.
- **Tab list heads** follow the custom skin shown on the player model.
- **Skin source label** next to player name tags (e.g. "LittleSkin" / "Premium"), toggleable.
- **Cache management**: view cached players and clear the cache in-game; changes reload instantly, no relog required.
- **Robust networking**: 30-second cooldown after failed Mojang lookups, URL encoding for player names, proper connection cleanup.
- **Safe config saving**: writes to a temp file then atomically replaces the config; older config formats are migrated automatically.
- **10 languages**: English, 简体中文, 繁體中文, Deutsch, Español, Français, 日本語, 한국어, Português (Brasil), Русский.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and put it in `mods/`.
3. Download the WSkinLoader jar from [Modrinth](https://modrinth.com/mod/wskinloader) and put it in `mods/`.
4. (Recommended) Install [ModMenu](https://modrinth.com/mod/modmenu) to open the config screen.

---

## Commands

All commands are client-side; type `/wskin` to print usage.

| Command | Effect |
| --- | --- |
| `/wskin skin <player>` | Use `<player>`'s skin as your own |
| `/wskin cape <player>` | Use `<player>`'s cape as your own |
| `/wskin reset skin` | Remove the skin mapping |
| `/wskin reset cape` | Remove the cape mapping |

- Mapping onto yourself is the same as resetting.
- Mappings are saved to your per-player override config and take effect immediately (no relog).
- When mapping is used, the arm model (slim/wide) automatically follows the source player's skin.

---

## Configuration

### In-game (recommended)

1. Install **ModMenu**.
2. Open **Mods → WSkinLoader → Configure**.
3. Edit settings under the five tabs:
   - **General**: tab-list heads, name-tag labels, premium-first strategy, fallback behavior.
   - **Skin API** / **Cape API**: add, edit, reorder, or remove API sources.
   - **Player Override**: per-player rules and skin/cape mapping, with a 3D preview button.
   - **Cache**: cache statistics and "clear all".

### Via file

Config file: `.minecraft/config/wskinloader.json`

```json
{
  "enableTabListHeads": true,
  "enableNameTagLabel": true,
  "premiumFirst": true,
  "keepPremiumWhenUnavailable": true,
  "skinApis": [
    { "url": "https://littleskin.cn/skin/%name%.png", "alias": "LittleSkin" }
  ],
  "capeApis": [
    { "url": "https://littleskin.cn/cape/%name%.png", "alias": "LittleSkin" }
  ],
  "playerOverrides": {
    "PlayerName": {
      "skipPremiumCheck": false,
      "modelType": "auto",
      "skinSourcePlayer": "",
      "capeSourcePlayer": "",
      "skin":   { "useCustomApi": false, "apiIndex": -1 },
      "cape":   { "useCustomApi": false, "apiIndex": -1 }
    }
  }
}
```

### Fields

**Global**

| Field | Default | Description |
| --- | --- | --- |
| `enableTabListHeads` | `true` | Show player heads in the Tab list, following custom skins. |
| `enableNameTagLabel` | `true` | Show the skin source (API alias or "Premium") next to player name tags. |
| `premiumFirst` | `true` | Try Mojang premium textures first; use custom APIs only as fallback. `false` = never query Mojang, always use custom APIs. |
| `keepPremiumWhenUnavailable` | `true` | When Mojang is temporarily unreachable, do **not** fall back to third-party skins. `false` = allow custom API fallback. |
| `skinApis` / `capeApis` | LittleSkin | List of `{ "url", "alias" }`. The URL must be HTTP/HTTPS and may contain `%name%`, which is replaced with the player name. |
| `playerOverrides` | — | Per-player rules, keyed by player name. |

**Per player (`playerOverrides.<name>`)**

| Field | Default | Description |
| --- | --- | --- |
| `skipPremiumCheck` | `false` | Skip the Mojang check and load this player from the custom APIs. |
| `modelType` | `"auto"` | `"auto"` / `"slim"` / `"wide"` for the arm model. |
| `skinSourcePlayer` | `""` | If non-empty, use this player's skin (same as `/wskin skin`). |
| `capeSourcePlayer` | `""` | If non-empty, use this player's cape (same as `/wskin cape`). |
| `skin.useCustomApi` / `cape.useCustomApi` | `false` | Force the custom APIs for this texture type. |
| `skin.apiIndex` / `cape.apiIndex` | `-1` | `0` = first API, `1` = second, … `-1` = try all in order. |

> Upgrading from an old version? The legacy `skinUrls` / `capeUrls` lists and missing new fields are migrated automatically on first load.

---

## Loading strategy

For each texture (skin / cape), WSkinLoader resolves sources in this order:

1. **Forced custom API** — the player's `useCustomApi` is on, or `skipPremiumCheck` is set → load from the custom APIs directly.
2. **Global premium-first off** (`premiumFirst = false`) → same as above, for everyone.
3. **Premium-first on** (default):
   - Look up the player on `api.mojang.com`.
   - Not premium / not found → fall back to the custom APIs.
   - Premium → fetch textures from the Mojang session server and cache them.
     - Account has no skin/cape of that type → fall back to the custom APIs.
     - Mojang temporarily unreachable → keep the premium skin untouched if `keepPremiumWhenUnavailable` is on (default), otherwise fall back.
4. If every source fails, the vanilla default skin is used.

Downloaded textures are cached locally (per player + source), so reloads are instant and offline-friendly. Failed Mojang lookups enter a 30-second cooldown to avoid request spam.

---

## Example setups

**Use LittleSkin for everything (offline servers)**

```json
"premiumFirst": false
```

**Premium skin + third-party cape for one player**

```json
"PlayerName": {
  "skipPremiumCheck": false,
  "skin":   { "useCustomApi": false, "apiIndex": -1 },
  "cape":   { "useCustomApi": true,  "apiIndex": 0 }
}
```

**Wear another player's skin**

```
/wskin skin Notch
```

or in the config file:

```json
"YourName": {
  "skinSourcePlayer": "Notch"
}
```

---

# 中文

**[English](#wskinloader)** | 中文

一个 **Minecraft Fabric 客户端模组**：为离线玩家从**自定义 API（LittleSkin、Blessing Skin 等）**加载皮肤和披风，同时**正版玩家继续使用 Mojang 官方纹理**。支持玩家间皮肤/披风映射、原版风格的游戏内配置界面和 3D 皮肤预览。

---

## 功能特性

- **正版优先加载策略**（可配置）：默认优先加载 Mojang 正版皮肤/披风，只有该账号没有对应纹理时才使用自定义 API；关闭后完全跳过 Mojang。
- **Mojang 不可用保护**（可配置）：Mojang 服务暂时不可用时，不会用第三方皮肤覆盖正版皮肤。
- **自定义皮肤/披风 API**：可添加任意多个 HTTP(S) API，支持显示别名和 `%name%` 占位符，按顺序回退加载。
- **玩家级覆盖**：强制使用自定义 API、跳过正版检测、指定 API 索引、固定模型类型（自动 / 纤细 / 宽体）。
- **玩家纹理映射**：一条命令穿上其他玩家的皮肤或披风，立即生效、无需重登。
- **原版风格配置界面**：通用 / 皮肤 API / 披风 API / 玩家覆盖 / 缓存 五个标签页，全部使用原版控件，可被资源包和界面模组一同美化。
- **3D 皮肤预览**：拖拽旋转、滚轮缩放、模型类型切换。
- **Tab 列表头像**：与玩家模型上显示的自定义皮肤保持一致。
- **皮肤来源标签**：在玩家名称标签旁显示皮肤来源（如 "LittleSkin" / "正版"），可关闭。
- **缓存管理**：游戏内查看缓存玩家并一键清除；修改后立即刷新，无需重进游戏。
- **稳健的网络请求**：Mojang 查询失败后 30 秒冷却、玩家名 URL 编码、连接正确关闭。
- **安全的配置保存**：先写临时文件再原子替换；旧版配置自动迁移。
- **10 种语言**：简体中文、繁體中文、英语、德语、西班牙语、法语、日语、韩语、巴西葡萄牙语、俄语。

---

## 安装

1. 为 Minecraft 26.3 安装 [Fabric Loader](https://fabricmc.net/use/)。
2. 将 [Fabric API](https://modrinth.com/mod/fabric-api) 放入 `mods/`。
3. 从 [Modrinth](https://modrinth.com/mod/wskinloader) 下载 WSkinLoader 并放入 `mods/`。
4. （推荐）安装 [ModMenu](https://modrinth.com/mod/modmenu) 以打开配置界面。

---

## 命令

所有命令均为客户端命令；输入 `/wskin` 可查看用法。

| 命令 | 效果 |
| --- | --- |
| `/wskin skin <玩家>` | 使用该玩家的皮肤 |
| `/wskin cape <玩家>` | 使用该玩家的披风 |
| `/wskin reset skin` | 清除皮肤映射 |
| `/wskin reset cape` | 清除披风映射 |

- 映射到自己等同于重置。
- 映射会保存到玩家覆盖配置中，立即生效（无需重登）。
- 使用映射时，粗细手臂模型会自动跟随来源玩家的皮肤。

---

## 配置

### 游戏内配置（推荐）

1. 安装 **ModMenu**。
2. 打开 **模组 → WSkinLoader → 配置**。
3. 在五个标签页中修改设置：
   - **通用**：Tab 头像、名称标签、正版优先策略、不可用回退行为。
   - **皮肤 API** / **披风 API**：添加、编辑、排序或删除 API 源。
   - **玩家覆盖**：按玩家配置规则和皮肤/披风映射，附 3D 预览按钮。
   - **缓存**：缓存统计与"全部清除"。

### 通过配置文件

配置文件：`.minecraft/config/wskinloader.json`

```json
{
  "enableTabListHeads": true,
  "enableNameTagLabel": true,
  "premiumFirst": true,
  "keepPremiumWhenUnavailable": true,
  "skinApis": [
    { "url": "https://littleskin.cn/skin/%name%.png", "alias": "LittleSkin" }
  ],
  "capeApis": [
    { "url": "https://littleskin.cn/cape/%name%.png", "alias": "LittleSkin" }
  ],
  "playerOverrides": {
    "玩家名": {
      "skipPremiumCheck": false,
      "modelType": "auto",
      "skinSourcePlayer": "",
      "capeSourcePlayer": "",
      "skin":   { "useCustomApi": false, "apiIndex": -1 },
      "cape":   { "useCustomApi": false, "apiIndex": -1 }
    }
  }
}
```

### 字段说明

**全局**

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `enableTabListHeads` | `true` | 在 Tab 列表显示玩家头像，并跟随自定义皮肤。 |
| `enableNameTagLabel` | `true` | 在玩家名称标签旁显示皮肤来源（API 别名或"正版"）。 |
| `premiumFirst` | `true` | 优先使用 Mojang 正版纹理，自定义 API 仅作回退；`false` = 不查询 Mojang，直接使用自定义 API。 |
| `keepPremiumWhenUnavailable` | `true` | Mojang 暂时不可用时**不**回退到第三方皮肤；`false` = 允许回退到自定义 API。 |
| `skinApis` / `capeApis` | LittleSkin | `{ "url", "alias" }` 列表。URL 必须为 HTTP/HTTPS，可包含 `%name%`（替换为玩家名）。 |
| `playerOverrides` | — | 按玩家名配置覆盖规则。 |

**玩家级（`playerOverrides.<玩家名>`）**

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `skipPremiumCheck` | `false` | 跳过正版检测，直接从自定义 API 加载该玩家。 |
| `modelType` | `"auto"` | 手臂模型：`"auto"` / `"slim"`（纤细）/ `"wide"`（宽体）。 |
| `skinSourcePlayer` | `""` | 非空时使用该玩家的皮肤（同 `/wskin skin`）。 |
| `capeSourcePlayer` | `""` | 非空时使用该玩家的披风（同 `/wskin cape`）。 |
| `skin.useCustomApi` / `cape.useCustomApi` | `false` | 对应纹理类型强制使用自定义 API。 |
| `skin.apiIndex` / `cape.apiIndex` | `-1` | `0` = 第一个 API，`1` = 第二个，…… `-1` = 按顺序尝试全部。 |

> 从旧版本升级？旧的 `skinUrls` / `capeUrls` 列表和缺失的新字段会在首次加载时自动迁移。

---

## 工作原理

对每个纹理（皮肤 / 披风），WSkinLoader 按以下顺序解析来源：

1. **强制自定义 API** — 玩家的 `useCustomApi` 开启，或设置了 `skipPremiumCheck` → 直接从自定义 API 加载。
2. **全局关闭正版优先**（`premiumFirst = false`）→ 对所有玩家同上。
3. **正版优先开启**（默认）：
   - 在 `api.mojang.com` 查询玩家。
   - 非正版 / 未找到 → 回退到自定义 API。
   - 正版 → 从 Mojang Session Server 获取纹理并缓存。
     - 该账号没有对应类型的皮肤/披风 → 回退到自定义 API。
     - Mojang 暂时不可用 → `keepPremiumWhenUnavailable` 开启（默认）时保持正版皮肤不变，否则回退。
4. 所有来源都失败时，使用原版默认皮肤。

下载的纹理会按「玩家 + 来源」缓存到本地，刷新即时、支持离线。失败的 Mojang 查询会进入 30 秒冷却，避免反复请求。

---

## 使用场景示例

**离线服务器全局使用 LittleSkin**

```json
"premiumFirst": false
```

**单个玩家：正版皮肤 + 第三方披风**

```json
"PlayerName": {
  "skipPremiumCheck": false,
  "skin":   { "useCustomApi": false, "apiIndex": -1 },
  "cape":   { "useCustomApi": true,  "apiIndex": 0 }
}
```

**穿上其他玩家的皮肤**

```
/wskin skin Notch
```

或在配置文件中：

```json
"你的名字": {
  "skinSourcePlayer": "Notch"
}
```

---

## 许可证

本项目代码采用 **All-Rights-Reserved（保留所有权利）** 许可发布。详情见 [LICENSE.txt](LICENSE.txt)。

问题反馈：[GitHub Issues](https://github.com/wupengabc/wskinloader/issues)
