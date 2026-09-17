# gym-log

> 健身训练记录与身体数据追踪应用。把训练计划落到「每一组」，把长期变化画成「看得懂的图」。

---

## 这是什么

一个面向个人健身者的训练追踪工具，三个核心场景：

| 场景 | 回答的问题 |
|---|---|
| **练之前** | 今天练什么？ |
| **练的时候** | 现在做哪组？歇多久？ |
| **长期** | 三个月了我变强了吗？身体有变化吗？ |

**核心功能**：

- **跟练模式** —— 按计划逐动作、逐组引导，管理组间休息计时，支持**超级组**；全程单手可操作、屏幕常亮、断点续训、完全离线可用；**提示音不打断背景音乐**
- **计划管理** —— 内置 6 个成熟模板（力量 / 分化 / **徒手居家**），自定义计划支持任意周数、逐组处方、超级组编排、周期化
- **身体数据** —— 体重、围度、体成分、生理指标、主观状态、体态照片对比
- **数据可视化** —— 体重移动平均、估算 1RM 力量曲线、肌群周组数、训练容量、训练频率、计划执行度
- **管理后台** —— RBAC 权限模型、动作库与模板维护、用户管理、审计日志、数据看板
- **饮食与 AI** —— 热量与蛋白质轻量记录（含 TDEE 反推）；AI 只做训练总结与解释，不做计划生成

---

## 技术栈

| 层 | 选型 |
|---|---|
| **后端** | Java 17 · Spring Boot 3 · MySQL 8 · MyBatis-Plus · Spring Security + JWT · **RBAC** · Flyway · springdoc-openapi · WebClient（LLM） |
| **移动端** | Flutter 3 · Dart 3 · Riverpod · drift (SQLite) · fl_chart · wakelock_plus · audioplayers · flutter_tts |
| **Web 端** | Vue 3 · Vite · TypeScript · Element Plus · ECharts · Pinia |
| **开发工具** | IntelliJ IDEA · VS Code · Android Studio |

**架构约束**：Flutter App 与 Vue Web **共用同一套 REST API**，不存在端专属接口。

---

## 项目结构

```
gym-log/
├── docs/                     文档
│   ├── REQUIREMENTS.md       功能需求文档（主文档）
│   ├── METRICS.md            指标口径与图表规格
│   ├── TIMER-SPEC.md         跟练计时状态机与平台降级
│   └── DEVELOPMENT-PLAN.md   开发计划书（施工图）
├── server/                   Spring Boot 后端
├── app/                      Flutter 移动端
├── web/                      Vue 3 Web 端
└── README.md
```

---

## 文档导航

**建议阅读顺序**：

1. **[docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)** —— 从这里开始。定义了项目定位、术语表、范围边界、9 个功能模块、非功能需求、验收标准与迭代路线图。
2. **[docs/METRICS.md](docs/METRICS.md)** —— 每个指标怎么算、每张图画什么、什么情况下不画。
3. **[docs/TIMER-SPEC.md](docs/TIMER-SPEC.md)** —— 跟练功能的实现规格。计时、后台运行、跨平台差异的约束都在这里。
4. **[docs/DEVELOPMENT-PLAN.md](docs/DEVELOPMENT-PLAN.md)** —— **施工图**。按什么顺序、分几步、每步谁写、怎么验收。每次开工前看这份。

> **术语以 [REQUIREMENTS.md 第 3 节](docs/REQUIREMENTS.md#3-术语表) 为唯一来源。** 其他文档出现的领域词都以那里的定义为准。

---

## 开发环境

### 已验证的环境

| 组件 | 版本 | 位置 |
|---|---|---|
| JDK | 17.0.19 LTS | `D:\jdk17` |
| MySQL | 8.0.42 | `D:\MySQL\MySQL Server 8.0`（服务 `MYSQL80`） |
| Maven | 3.9.5 | IDEA 自带（不在 PATH） |
| Node.js | v24.20.0 | `C:\Program Files\nodejs` |
| Flutter | 3.47.4 stable | `E:\flutter` |
| Android SDK | API 36 / build-tools 36.0.0 | `E:\Android\Sdk` |
| Android Studio | AI-261.26222 | `F:\androidsrudio` |

**环境变量**（用户级）：

```
ANDROID_HOME             = E:\Android\Sdk
ANDROID_SDK_ROOT         = E:\Android\Sdk
PUB_HOSTED_URL           = https://pub.flutter-io.cn
FLUTTER_STORAGE_BASE_URL = https://storage.flutter-io.cn
CHROME_EXECUTABLE        = C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe
PATH                    += E:\flutter\bin
                           E:\Android\Sdk\platform-tools
                           E:\Android\Sdk\cmdline-tools\latest\bin
```

> 上述环境变量的原始值备份在 `C:\Users\strict\gym-log-env-backup-20260917.txt`。

### 环境自检

```bash
flutter doctor          # Android toolchain 应为 ✓
adb devices             # 应列出已连接的真机
```

### 为什么用 Flutter 镜像

`storage.flutter-io.cn` 实测 4.59 MB/s，官方源 3.07 MB/s。国内环境用镜像明显更快。

**如需改回官方源**：

```powershell
[Environment]::SetEnvironmentVariable('PUB_HOSTED_URL', $null, 'User')
[Environment]::SetEnvironmentVariable('FLUTTER_STORAGE_BASE_URL', $null, 'User')
```

---

## 快速开始

> 各模块尚未实现。以下是目标形态。

### 后端

```bash
cd server
# 首次运行前配置数据库连接
# 编辑 src/main/resources/application-dev.yml（该文件不纳入版本控制）
mvn spring-boot:run
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### 移动端

```bash
cd app
flutter pub get
flutter run                 # 需连接真机或启动模拟器
```

### Web 端

```bash
cd web
npm install
npm run dev
```

---

## 开发约定

| 项 | 约定 |
|---|---|
| **数据库变更** | 全部通过 Flyway 迁移脚本，禁止手工改表 |
| **接口版本** | URL 前缀 `/api/v1/` |
| **重量单位** | 数据库一律存 kg，`lb` 仅在显示层换算 |
| **容量口径** | 仅统计正式组（排除热身组） |
| **越权防护** | 所有资源查询强制带 `user_id` 条件 |
| **敏感配置** | 数据库密码写入 `application-dev.yml`，该文件加入 `.gitignore` |

---

## 已知限制

| 限制 | 说明 |
|---|---|
| **无法构建 iOS** | 构建 iOS 需要 macOS + Xcode。当前开发机为 Windows，只能出 Android 包 |
| **iOS 无震动提醒** | iOS 不向第三方 App 开放任意时长震动，仅支持 Haptic Feedback |
| 无 Docker | 本机未安装，测试使用本地 MySQL 实例 |
| 无 Redis | V1 不引入。JWT 无状态 + MySQL 存 refresh token 已足够 |

---

## 协议

个人项目，暂未指定开源协议。
