# 贡献指南

感谢你对 WSkinLoader 项目的关注！我们欢迎各种形式的贡献。

## 如何贡献

### 报告 Bug

如果你发现了 bug，请：

1. 检查 [Issues](https://github.com/wupengabc/wskinloader/issues) 中是否已有相关报告
2. 如果没有，创建新的 issue 并提供：
   - 清晰的问题描述
   - 复现步骤
   - 预期行为 vs 实际行为
   - Minecraft 版本、模组版本、日志文件
   - 截图（如果适用）

### 提交功能建议

我们欢迎新功能建议！请：

1. 在 issue 中描述你的想法
2. 说明为什么需要这个功能
3. 如果可能，提供实现思路

### 贡献代码

1. **Fork 仓库**
   ```bash
   git clone https://github.com/你的用户名/wskinloader.git
   cd wskinloader
   ```

2. **创建分支**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **进行修改**
   - 遵循现有代码风格
   - 为新功能添加测试
   - 更新相关文档

4. **测试**
   ```bash
   ./gradlew test
   ./gradlew build
   ```

5. **提交更改**
   ```bash
   git add .
   git commit -m "feat: 添加新功能的简短描述"
   ```

6. **推送并创建 Pull Request**
   ```bash
   git push origin feature/your-feature-name
   ```

## 代码规范

### Java 代码风格

- 使用 4 个空格缩进
- 变量和方法使用 camelCase
- 类名使用 PascalCase
- 常量使用 UPPER_SNAKE_CASE
- 每个类、公共方法添加 JavaDoc 注释
- 代码行宽建议不超过 120 字符

### 提交信息规范

使用语义化提交信息：

- `feat:` 新功能
- `fix:` Bug 修复
- `docs:` 文档更新
- `style:` 代码格式（不影响代码逻辑）
- `refactor:` 重构
- `test:` 测试相关
- `chore:` 构建过程或辅助工具的变动

示例：
```
feat: 添加皮肤预加载功能
fix: 修复配置文件保存时的并发问题
docs: 更新 README 中的安装说明
```

### 测试要求

- 新功能必须包含单元测试
- Bug 修复应添加回归测试
- 测试覆盖率应保持或提高
- 所有测试必须通过才能合并

## Pull Request 流程

1. 确保代码能够编译和运行
2. 确保所有测试通过
3. 更新相关文档
4. 在 PR 描述中清楚说明：
   - 解决的问题或添加的功能
   - 测试方法
   - 可能的影响范围

## 开发环境设置

### 必需工具

- JDK 25 或更高版本
- Gradle（包装器已包含）
- Git

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/wupengabc/wskinloader.git
cd wskinloader

# 构建
./gradlew build

# 运行测试
./gradlew test

# 运行 Minecraft 客户端（开发测试）
./gradlew runClient
```

### IDE 配置

推荐使用 IntelliJ IDEA：

1. 打开项目文件夹
2. IDEA 会自动识别 Gradle 项目
3. 等待依赖下载完成
4. 运行 `runClient` Gradle 任务进行测试

## 许可证

通过贡献代码，你同意你的贡献将根据项目的 [LICENSE](LICENSE.txt) 进行许可。

## 问题？

如有任何疑问，请：
- 在 issue 中提问
- 查看现有文档和代码
- 参考 [README.md](README.md)

感谢你的贡献！
