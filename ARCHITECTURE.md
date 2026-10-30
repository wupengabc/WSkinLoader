# WSkinLoader 架构设计

本文档描述 WSkinLoader 的架构设计和关键组件。

## 总体架构

```
┌─────────────────────────────────────────────────────────┐
│                    Minecraft 客户端                      │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   WSkinLoader 模组                       │
│                                                          │
│  ┌────────────┐  ┌────────────┐  ┌─────────────────┐  │
│  │   Config   │  │   Mixin    │  │    Commands     │  │
│  │  Management│  │  Injectors │  │   (/wskin)      │  │
│  └────────────┘  └────────────┘  └─────────────────┘  │
│         │              │                   │            │
│         └──────────────┴───────────────────┘            │
│                        │                                │
│         ┌──────────────┴────────────────┐               │
│         ▼                               ▼               │
│  ┌─────────────┐              ┌──────────────────┐     │
│  │ SkinLoader  │◄─────────────┤  Cache System    │     │
│  └─────────────┘              └──────────────────┘     │
│         │                                               │
│         ▼                                               │
│  ┌─────────────────────────────────────────────┐       │
│  │          Network Layer                      │       │
│  │  ┌─────────────┐    ┌──────────────────┐   │       │
│  │  │ Mojang API  │    │  Custom APIs     │   │       │
│  │  │  Checker    │    │   (LittleSkin)   │   │       │
│  │  └─────────────┘    └──────────────────┘   │       │
│  └─────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────┘
```

## 核心组件

### 1. 配置管理 (ModConfig)

**职责**：
- 加载、保存和验证配置文件
- 管理皮肤/披风 API 列表
- 管理玩家级覆盖规则
- 配置迁移和兼容性

**关键类**：
- `ModConfig` - 主配置类
- `ModConfig.ApiConfig` - API 配置
- `ModConfig.PlayerOverride` - 玩家覆盖配置

**设计决策**：
- 使用单例模式，全局访问
- 使用 AtomicBoolean 防止并发保存
- 临时文件 + 原子移动确保配置安全
- 自动迁移旧配置格式

### 2. 皮肤加载器 (SkinLoader)

**职责**：
- 统一的皮肤/披风加载入口
- 实现加载策略（正版优先/自定义优先）
- 管理加载状态和失败冷却
- 缓存失效和重载

**关键特性**：
- 异步加载，不阻塞渲染
- 失败冷却机制（30秒）
- 支持玩家纹理映射
- 皮肤模型类型检测（slim/wide）

**线程模型**：
- 4 线程固定大小线程池
- 守护线程，不阻止 JVM 退出
- 优雅关闭机制

### 3. Mixin 注入系统

**职责**：
- 拦截 Minecraft 的皮肤解析逻辑
- 替换纹理 ID
- 修改渲染行为

**关键 Mixin**：
- `AbstractClientPlayerEntityMixin` - 玩家实体皮肤
- `PlayerListHudMixin` - Tab 列表头像
- `LivingEntityRendererMixin` - 实体渲染
- `SkullBlockEntityRendererMixin` - 头颅方块

### 4. 网络层

#### Mojang API Checker

**职责**：
- 查询玩家是否为正版
- 获取玩家 UUID

**端点**：`https://api.mojang.com/users/profiles/minecraft/{name}`

**缓存策略**：
- 成功查询：永久缓存（直到清除）
- 失败查询：30秒冷却

#### Mojang Session API

**职责**：
- 获取正版玩家的纹理 URL
- 解析皮肤模型类型

**端点**：`https://sessionserver.mojang.com/session/minecraft/profile/{uuid}`

**缓存策略**：
- 成功查询：60秒缓存
- 失败查询：30秒冷却

#### Custom Skin Loader

**职责**：
- 从自定义 API 下载皮肤/披风
- 按顺序回退尝试多个 API
- 记录皮肤来源

### 5. 缓存系统

**多级缓存**：

1. **内存缓存** (SkinCache)
   - 已加载的纹理
   - 皮肤模型类型
   - 使用 ConcurrentHashMap

2. **磁盘缓存**
   - 位置：`.minecraft/cache/wskinloader/`
   - 按纹理类型分目录
   - 使用 SHA-1 哈希命名

3. **有界缓存** (BoundedCache)
   - LRU 驱逐策略
   - 防止内存无限增长
   - 用于 API 查询结果

## 数据流

### 加载正版皮肤

```
Player Join
    │
    ▼
SkinLoader.loadSkinForProfile()
    │
    ├──► 检查缓存 ──► 命中 ──► 返回
    │
    ├──► 检查正在加载 ──► 是 ──► 等待
    │
    ├──► 检查失败冷却 ──► 是 ──► 跳过
    │
    ▼
MojangApiChecker.getPlayerProfile()
    │
    ├──► 离线玩家 ──► 自定义 API
    │
    ▼
MojangSessionApi.getProfileTextures()
    │
    ├──► 无纹理 ──► 自定义 API
    │
    ▼
下载并注册纹理
    │
    ▼
缓存到内存和磁盘
```

### 加载自定义皮肤

```
Player Join
    │
    ▼
SkinLoader.loadSkinForProfile()
    │
    ▼
CustomSkinLoader.loadCustomSkin()
    │
    ▼
按顺序尝试 API
    │
    ├──► API 1 ──► 失败
    │
    ├──► API 2 ──► 成功 ──► 下载
    │
    ▼
缓存到内存和磁盘
```

## 配置策略

### 全局策略

1. **premiumFirst** (默认：true)
   - true: 优先加载正版纹理
   - false: 直接使用自定义 API

2. **keepPremiumWhenUnavailable** (默认：true)
   - true: Mojang 不可用时不回退到自定义 API
   - false: 允许回退

### 玩家级覆盖

- `skipPremiumCheck` - 跳过正版检测
- `useCustomApi` - 强制使用自定义 API
- `apiIndex` - 指定使用哪个 API
- `skinSourcePlayer` / `capeSourcePlayer` - 纹理映射

### 优先级

```
玩家映射
    │
    ▼
强制自定义 API
    │
    ▼
跳过正版检测
    │
    ▼
全局 premiumFirst
    │
    ▼
默认策略
```

## 安全性

### URL 验证

- 只允许 HTTP/HTTPS
- 阻止本地地址（localhost, 127.0.0.1）
- 阻止私有 IP 范围（防止 SSRF）
- 长度限制（512 字符）

### 输入验证

- 玩家名 URL 编码
- API 配置格式验证
- 配置数据完整性检查

## 性能优化

### 缓存策略

- 多级缓存减少网络请求
- LRU 驱逐防止内存泄漏
- 磁盘缓存支持离线使用

### 并发控制

- 异步加载不阻塞主线程
- 专用线程池处理网络请求
- 原子操作防止竞态条件

### 失败处理

- 冷却机制防止请求风暴
- 快速失败，不阻塞其他加载
- 降级策略（自定义 API → 默认皮肤）

## 扩展性

### 添加新的皮肤源

1. 实现 API 客户端类
2. 在 `SkinLoader` 中添加加载逻辑
3. 更新配置验证
4. 添加测试

### 添加新的 Mixin

1. 创建 Mixin 类
2. 添加到 `wskinloader.client.mixins.json`
3. 确保注入点兼容性
4. 测试多版本支持

## 已知限制

1. **版本兼容性**
   - 依赖 Minecraft 内部 API
   - Mixin 可能在版本间变化

2. **性能**
   - 首次加载需要网络请求
   - 大量玩家可能影响性能

3. **功能限制**
   - 仅支持客户端
   - 不支持自定义模型格式（如 OptiFine）

## 未来改进

1. **性能**
   - 预加载机制
   - 更智能的缓存策略
   - 批量加载优化

2. **功能**
   - 支持更多皮肤源
   - 插件系统
   - 统计和监控

3. **架构**
   - 依赖注入减少单例
   - 事件驱动架构
   - 更好的错误恢复

## 参考资料

- [Fabric Wiki](https://fabricmc.net/wiki/)
- [Mixin Documentation](https://github.com/SpongePowered/Mixin/wiki)
- [Mojang API Documentation](https://wiki.vg/Mojang_API)
