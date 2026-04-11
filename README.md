# WSkinLoader

一个 Minecraft Fabric 模组，支持为离线玩家加载自定义皮肤和披风。

## 功能特性

- ✅ 正版玩家使用官方皮肤系统
- ✅ 离线玩家从自定义 API 加载皮肤和披风
- ✅ 支持多个 API 源，按顺序尝试加载
- ✅ 通过 Cloth Config API 提供图形化配置界面
- ✅ 支持 ModMenu 集成
- ✅ 玩家覆盖配置：为特定玩家自定义规则
- ✅ 混合模式：正版皮肤 + 第三方披风（或反之）
- ✅ Tab 列表显示玩家头像（多人游戏中也显示）
- ✅ 玩家头颅显示自定义皮肤

## 默认配置

默认使用 LittleSkin 作为皮肤源：

- 皮肤: `https://littleskin.cn/skin/%name%.png`
- 披风: `https://littleskin.cn/cape/%name%.png`

其中 `%name%` 会被替换为玩家名。

## 配置方法

### 通过游戏内界面配置

1. 安装 ModMenu（推荐）
2. 进入游戏主菜单 -> Mods -> WSkinLoader -> 配置
3. 在配置界面中添加、删除或修改 API 地址
4. 在"玩家覆盖"标签中添加需要特殊处理的玩家名

### 通过配置文件配置

配置文件位于：`.minecraft/config/wskinloader.json`

#### 基础配置

```json
{
  "skinUrls": [
    "https://littleskin.cn/skin/%name%.png",
    "https://example.com/skins/%name%.png"
  ],
  "capeUrls": [
    "https://littleskin.cn/cape/%name%.png"
  ],
  "enableTabListHeads": true
}
```

**配置说明：**
- `enableTabListHeads`: 是否在 Tab 列表中显示玩家头像（默认：true）

#### 高级配置：玩家覆盖

为特定玩家配置自定义规则：

```json
{
  "skinUrls": ["https://littleskin.cn/skin/%name%.png"],
  "capeUrls": ["https://littleskin.cn/cape/%name%.png"],
  "playerOverrides": {
    "PlayerName": {
      "skipPremiumCheck": true,
      "skin": {
        "useCustomApi": true,
        "apiIndex": 0
      },
      "cape": {
        "useCustomApi": true,
        "apiIndex": -1
      }
    },
    "AnotherPlayer": {
      "skipPremiumCheck": false,
      "skin": {
        "useCustomApi": true,
        "apiIndex": 0
      },
      "cape": {
        "useCustomApi": false
      }
    }
  }
}
```

**配置说明：**

- `skipPremiumCheck`: 是否跳过正版检测（true = 直接使用自定义 API）
- `skin.useCustomApi`: 皮肤是否使用自定义 API（false = 使用正版皮肤）
- `skin.apiIndex`: 使用哪个 API（0 = 第一个，1 = 第二个，-1 = 按顺序尝试所有）
- `cape.useCustomApi`: 披风是否使用自定义 API（false = 使用正版披风）
- `cape.apiIndex`: 使用哪个 API

**使用场景：**

1. **跳过正版检测**：正版玩家但想使用第三方皮肤站
   ```json
   "PlayerName": {
     "skipPremiumCheck": true,
     "skin": { "useCustomApi": true, "apiIndex": 0 },
     "cape": { "useCustomApi": true, "apiIndex": 0 }
   }
   ```

2. **混合使用**：正版皮肤 + 第三方披风
   ```json
   "PlayerName": {
     "skipPremiumCheck": false,
     "skin": { "useCustomApi": false },
     "cape": { "useCustomApi": true, "apiIndex": 0 }
   }
   ```

3. **指定 API**：只使用特定的皮肤站
   ```json
   "PlayerName": {
     "skipPremiumCheck": true,
     "skin": { "useCustomApi": true, "apiIndex": 1 },
     "cape": { "useCustomApi": true, "apiIndex": 1 }
   }
   ```

## 工作原理

### 皮肤加载流程

1. 当游戏尝试加载玩家皮肤时，模组会通过 Mojang API (`https://api.mojang.com/users/profiles/minecraft/{name}`) 检查玩家是否为正版
2. **正版玩家**：通过 Mojang Session Server API (`https://sessionserver.mojang.com/session/minecraft/profile/{UUID}`) 主动获取皮肤和披风 URL，然后下载并缓存
3. **离线玩家**：按配置的 URL 列表顺序尝试从自定义 API 加载
4. 下载的皮肤材质会被缓存到 `SkinCache` 中
5. 通过 Mixin 拦截以下方法来应用自定义皮肤：
   - `AbstractClientPlayerEntity.getSkin()` - 游戏世界中的玩家模型
   - `PlayerListEntry.getSkinTextures()` - Tab 列表中的玩家头像
6. 如果所有 URL 都加载失败，使用 Minecraft 默认皮肤

### Tab 列表头像显示

1. 游戏渲染 Tab 列表
2. 调用 `MinecraftClient.isInSingleplayer()` 检查是否显示头像
3. `PlayerListHudMixin` 拦截，始终返回 `true`
4. 游戏认为在单人模式，启用头像显示
5. 游戏调用 `PlayerListEntry.getSkinTextures()` 获取玩家皮肤
6. `PlayerListEntryMixin` 拦截，从 `SkinCache` 获取自定义皮肤
7. 游戏使用加载的皮肤纹理渲染头像

**判断依据**：通过 Mojang 官方 API 查询玩家名，HTTP 404 = 离线玩家，HTTP 200 = 正版玩家。

**正版皮肤获取**：不等待原版逻辑，直接通过 Session Server API 获取皮肤和披风的真实 URL 并下载。

## 依赖

- Fabric Loader
- Fabric API
- Cloth Config API (>=21.0.0)
- ModMenu (可选，用于配置界面)

## 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录。

## 许可证

All Rights Reserved
