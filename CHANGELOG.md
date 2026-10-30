# 更新日志

所有重要的项目变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增
- 添加 CI/CD 配置（GitHub Actions）
- 添加基础单元测试（ModConfigTest, BoundedCacheTest）
- 添加 BoundedCache 工具类，防止缓存无限增长
- 添加 SkinLoader.shutdown() 方法，正确释放资源
- 添加贡献指南 (CONTRIBUTING.md)
- 添加更新日志 (CHANGELOG.md)

### 修复
- 修复 ModConfig 中的并发安全问题（使用 AtomicBoolean）
- 修复 ExecutorService 资源泄漏问题
- 修复 CustomSkinLoader 中的空指针检查缺失
- 改进 URL 验证安全性，防止 SSRF 攻击
- 改进错误处理和日志级别
- 清理 mixin 配置中的死代码引用
- 添加缓存大小限制（最多 1000 个玩家档案）

### 优化
- 使用 LRU 策略的有界缓存替代无界 ConcurrentHashMap
- 改进日志记录，区分网络错误和意外错误
- 优化缓存过期策略

## [1.6.2] - 2024-10-30

### 新增
- 目标 Minecraft 1.21.1 (Fabric)

### 修复
- 移除死代码和资源
- 清理构建配置

## [1.6.1] - 之前版本

### 新增
- 正版优先加载策略
- 聊天/UI 中的皮肤头像支持
- Tab 列表头像去重功能

### 修复
- 修复玩家纹理映射
- 修复 /wskin 命令
- 改进原版风格配置界面

## [1.6.0] - 之前版本

### 新增
- 玩家纹理映射功能
- /wskin 命令系列
- 原版风格配置界面

## [1.0.0] - 项目初始化

### 新增
- 基础皮肤加载功能
- 自定义 API 支持
- 配置文件系统

---

## 类型说明

- `新增` - 新功能
- `修复` - Bug 修复
- `变更` - 现有功能的变更
- `移除` - 移除的功能
- `优化` - 性能或代码质量改进
- `安全` - 安全性修复
- `弃用` - 即将移除的功能
