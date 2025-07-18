# ByteFlow - Android VPN Client

一个基于 Leaf 代理库的现代化 Android VPN 客户端应用，支持 V2Board API 自动订阅和节点管理。

## 功能特性

### 🔐 用户认证
- 基于 V2Board API 的完整认证系统
- 自动登录状态管理
- 用户信息和订阅数据同步
- 支持邮箱验证码注册

### 🌐 代理功能
- 支持 Shadowsocks (SS) 协议
- 自动获取订阅链接，无需手动配置
- 智能节点选择和延迟测试
- 实时连接状态监控

### 📊 数据管理
- 实时流量统计和使用情况
- 套餐信息和到期时间显示
- 多节点延迟测试和排序
- 订阅数据自动更新

### 🎨 用户界面
- 现代化 iOS 风格设计
- Material Design 组件
- 深色/浅色主题支持
- 流畅的动画效果

## 技术架构

### 核心技术
- **语言**: Kotlin
- **最小 SDK**: Android 5.0 (API 21)
- **目标 SDK**: Android 15 (API 35)
- **代理库**: Leaf Proxy Framework

### 主要组件
- **API 客户端**: 基于 HttpURLConnection 的网络请求
- **订阅管理**: 支持 Clash 配置解析和转换
- **VPN 服务**: 前台服务实现的 VPN 连接
- **延迟测试**: 并发Socket连接测试

### 项目结构
```
app/
├── src/main/java/com/byteflow/www/
│   ├── ui/
│   │   ├── fragments/          # UI 片段
│   │   └── adapters/           # 列表适配器
│   ├── utils/                  # 工具类
│   ├── models/                 # 数据模型
│   ├── service/                # VPN 服务
│   ├── ApiClient.kt            # API 客户端
│   ├── AuthManager.kt          # 认证管理
│   └── MainActivity.kt         # 主活动
├── src/main/res/               # 资源文件
└── build.gradle.kts            # 构建配置
```

## 开发环境搭建

### 前置要求
- Android Studio Arctic Fox 或更高版本
- JDK 11 或更高版本
- Android SDK 35
- Kotlin 1.8+

### 构建步骤
1. 克隆项目
```bash
git clone https://github.com/MagicNop/Xboard_Client.git
cd Xboard_Client
```

2. 打开项目
```bash
# 在 Android Studio 中打开项目
# 或使用命令行构建
./gradlew build
```

3. 运行应用
```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 配置说明

### API 配置
应用会自动从 V2Board API 获取订阅链接，无需手动配置。确保服务器端 API 符合以下端点：

- `POST /api/v1/passport/auth/login` - 用户登录
- `POST /api/v1/passport/auth/register` - 用户注册
- `GET /api/v1/user/info` - 获取用户信息
- `GET /api/v1/user/getSubscribe` - 获取订阅信息
- `GET /api/v1/user/plan/fetch` - 获取套餐列表

### 权限要求
- `INTERNET` - 网络访问
- `FOREGROUND_SERVICE` - 前台服务
- `BIND_VPN_SERVICE` - VPN 服务绑定
- `RECEIVE_BOOT_COMPLETED` - 开机启动

## 使用说明

### 首次使用
1. 启动应用后进入登录界面
2. 使用邮箱和密码注册新账户
3. 登录成功后自动获取订阅数据
4. 在节点列表中选择合适的服务器
5. 点击连接按钮开始使用

### 节点管理
- 应用会自动测试所有节点延迟
- 支持按延迟、名称排序
- 支持节点搜索和筛选
- 可切换到不同地区的节点

### 流量监控
- 实时显示上传/下载流量
- 显示套餐总流量和剩余流量
- 流量使用进度条和百分比

## 贡献指南

### 开发规范
- 遵循 Kotlin 编码规范
- 使用 Material Design 设计准则
- 保持代码简洁和注释清晰
- 确保新功能有适当的错误处理

### 提交流程
1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 发起 Pull Request

### 问题反馈
- 使用 GitHub Issues 报告 Bug
- 提供详细的复现步骤
- 包含设备信息和日志

## 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。

## 致谢

- [Leaf](https://github.com/eycorsican/leaf) - 高性能代理库
- [SnakeYAML](https://bitbucket.org/asomov/snakeyaml) - YAML 解析库
- [Material Components](https://github.com/material-components/material-components-android) - UI 组件库

## 联系方式

- 项目地址: https://github.com/MagicNop/Xboard_Client
- 问题反馈: https://github.com/MagicNop/Xboard_Client/issues

---

**注意**: 本应用仅供学习和研究使用，请遵守当地法律法规。