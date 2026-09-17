# gym-log 开发计划书

> 本文档是**施工图**：[REQUIREMENTS.md](./REQUIREMENTS.md) 说「要什么」，本文档说「按什么顺序、分几步、每步谁写、怎么验收」。

---

## 0. 这份文档怎么用

| 文档 | 回答的问题 | 什么时候看 |
|---|---|---|
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 要做什么？为什么这么做？ | 想搞清楚需求时 |
| [METRICS.md](./METRICS.md) | 这个指标怎么算？ | 写统计逻辑时 |
| [TIMER-SPEC.md](./TIMER-SPEC.md) | 跟练状态机怎么设计？ | 写 Phase 3 时 |
| **本文档** | **现在做哪一步？怎么验收？** | **每次开工前** |
| [DEV-LOG.md](./DEV-LOG.md) | **实际做了什么？验证到什么？踩了什么坑？** | **想回顾时 / 排查同类问题时** |

> **分工**：本文档是**前瞻的**（计划、验收标准、谁写什么），
> [DEV-LOG.md](./DEV-LOG.md) 是**回顾的**（实际结果、验证证据、踩坑记录）。
>
> 步骤的**详细完成记录以 DEV-LOG.md 为准**。本文档的步骤条目只保留状态与验收结论。

**每完成一个步骤，本文档对应条目的复选框要打勾，并补上实际用时与踩坑记录。**

---

## 1. 协作方式

### 1.1 分工原则

> **你写「面试会考的、只有一遍机会的」；我写「模板性的、写十遍都一样的」。**

具体判断标准：

| 归你写 | 归我写 |
|---|---|
| 面试官会追问「为什么这么设计」的 | 有标准写法的样板代码 |
| 有多个合理方案、需要取舍的 | 增删改查的骨架 |
| 写错了很难发现、但影响很大的 | 配置、依赖、DTO |
| 项目的**独有难点** | 任何项目都差不多的部分 |

### 1.2 为什么认证部分这次建议我写

你在 **mini_mall 已经亲手写过一遍** `JwtUtil`、`JwtAuthenticationFilter`、双 `SecurityFilterChain`。再写一遍的边际学习收益很低，但 gym-log 有几个 **mini_mall 没有的难点**：

| gym-log 独有难点 | 难度 | 面试价值 |
|---|---|---|
| 周期化展开算法 | 高 | 高（有算法设计可讲） |
| 会话快照的深拷贝与版本化 | 高 | 高（数据一致性设计） |
| 超级组轮次推进状态机 | 高 | 高（状态机设计） |
| 指标聚合 SQL（移动平均、e1RM） | 中高 | 高（SQL 能力） |
| RBAC 权限模型 + 方法级鉴权 | 中 | 高（权限设计是高频题） |
| 离线优先与幂等同步 | 中高 | 高（分布式思维） |
| JWT + Spring Security 配置 | 中 | 中（**你已写过，且是标准套路**） |

**已定的分配**（2026-09-17 确认）：

> **认证（步骤 1.6–1.8）我写、你审查。** 你把写代码的时间投给上表前六项 gym-log 独有的难点。

**审查不是走过场。** 我写完会逐行讲清楚设计意图，并且你要能回答出第 6 节「Phase 1 结束后」的检查点问题。**答不出来就说明该补，而不是该继续。**

> ⚠️ **任何一步你都可以中途改主意**说「这个我来写」，我就只给规格和提示，不直接给答案。

### 1.3 每一步的节奏

每个步骤都按这六拍走，**不会一口气写一大堆再给你看**：

```
1. 我说   ── 这一步做什么、涉及什么原理、为什么这么设计
2. 写码   ── 我写 或 你写（你写的话我只给规格和提示）
3. 审查   ── 你读代码，有疑问就问；我讲清楚关键行
4. 验证   ── 跑起来，用明确的命令验证（不是"看起来没问题"）
5. 讲解   ── 我总结这一步的知识点，以及面试可能怎么问
6. 提交   ── 一个步骤一个 commit，你确认后我再提交
```

**节奏由你控制**：可以让我继续下一步，也可以说「停，这步我没懂」。

### 1.4 提交规范

- **一个步骤一个 commit**，不把多个步骤混在一起
- commit message 用英文，格式：`<type>: <简短描述>`
  - `feat` 新功能 / `fix` 修 bug / `refactor` 重构 / `docs` 文档 / `chore` 杂项
- **提交前我展示变更摘要**，你确认后我再提交
- **推送前单独问你**

---

## 2. 当前状态

### ✅ 已完成

| 项 | 状态 |
|---|---|
| 环境搭建（Phase 0） | Flutter 3.47.4 / Android SDK API 36 / Android Studio / 环境变量，均已验证 |
| `flutter build apk` | 通过（143.5MB，compileSdk 36，三架构） |
| 需求文档 | 4 份，12.2 万字，已推送 GitHub |
| 仓库 | `d:\aicoding\gym-log` → `brostrict/gym-log`，main 分支 |

### ⏳ 待办

| 项 | 说明 |
|---|---|
| **真机连接** | 插上安卓手机、开 USB 调试，`adb devices` 能列出设备。**Phase 3 之前必须完成，不阻塞 Phase 1–2** |
| **MySQL 凭据** | 见第 8 节。**步骤 1.2 之前必须配好** |

### 📊 进度追踪

**Phase 1 —— 后端地基**

| 步骤 | 内容 | 状态 |
|---|---|---|
| 1.1 | Spring Boot 项目骨架 | ✅ 完成 |
| 1.2 | 数据库与 Flyway 迁移 | ✅ 完成 |
| 1.3 | 统一响应与全局异常处理 | ✅ 完成 |
| 1.4 | 用户实体与 Mapper | ✅ 完成 |
| 1.5 | 注册接口 | ✅ 完成 |
| 1.6 | 登录接口与 JWT 签发 | ✅ 完成 |
| 1.7 | JwtAuthenticationFilter | ✅ 完成 |
| 1.8 | SecurityFilterChain 配置 | ✅ 完成 |
| 1.9 | Refresh Token 与登出 | ✅ 完成 |
| 1.10 | RBAC 三张表 | ✅ 完成 |
| 1.11 | Swagger / OpenAPI | ✅ 完成 |
| 1.12 | Phase 1 验收 | ✅ 完成 |

> 🎉 **Phase 1 完成（2026-09-17）**。8 项验收全通过，另补验了 token 真实过期路径。
> 交付：7 张表、6 个接口、33 个 Java 文件。详见 [DEV-LOG.md Phase 1 完成总结](./DEV-LOG.md#phase-1-完成总结)。

**Phase 2 —— 动作库与计划管理**

| 步骤 | 内容 | 状态 |
|---|---|---|
| 2.1 | 动作库表设计 | ✅ 完成 |
| 2.2 | 动作实体与 Mapper | ✅ 完成（与 2.1 合并） |
| 2.3 | 内置动作库种子数据（90 个） | ✅ 完成 |
| 2.4 | 动作查询接口 | ✅ 完成 |
| 2.5 | 自定义动作接口 | ✅ 完成 |
| 2.6 | 计划相关表设计（5 张表） | ✅ 完成 |
| 2.7 | 计划实体与 Mapper | ✅ 完成（与 2.6 合并） |
| 2.8 | 计划 CRUD 接口 | ✅ 完成 |
| 2.9 | 内置计划模板种子数据（6 个） | ✅ 完成 |
| 2.10 | 从模板创建计划 | ⬜ |
| 2.11 | 逐组处方 | ⬜ |
| 2.12 | 超级组编排 | ⬜ |
| 2.13 | **周期化展开算法** ← 你写 | ⬜ |
| 2.14 | **计划版本化** ← 你写 | ⬜ |
| 2.15 | 「今天练什么」查询 | ⬜ |
| 2.16 | Phase 2 验收 | ⬜ |

---

## 3. 阶段总览

| Phase | 内容 | 预计步数 | 产出 |
|---|---|---|---|
| ✅ 0 | 环境搭建 | — | 完成 |
| **1** | **后端地基** | **12** | 能注册登录，拿到 token 访问受保护接口 |
| 2 | 动作库与计划管理 | ~14 | 能创建计划（含超级组与徒手）并查询「今天练什么」 |
| 3 | 跟练（核心） | ~16 | 真机上完整跟练一场并落库，音乐不被打断 |
| 4 | 身体数据与可视化 | ~12 | 能回答「三个月有没有变强」 |
| 5 | Web 端（用户端） | ~8 | PC 上能看完整长期趋势 |
| 6 | 管理后台 | ~10 | RBAC + 审计日志 + 数据看板 |
| 7 | 饮食与 AI | ~6 | 可选，时间紧可砍 |
| 8 | 打磨 | ~6 | 导出、部署、演示材料 |

> **总计约 84 步。** 按每天 2–3 步算，约 6–8 周。这是**全职投入**的估算；业余时间做请自行乘以 2–3。

**阶段依赖是强顺序的**：1 → 2 → 3 → 4。5、6、7 可以在 4 之后并行或调序。

---

## 4. Phase 1 详细步骤 —— 后端地基

> **本阶段目标**：跑通「注册 → 登录 → 拿 token → 访问受保护接口 → token 过期 → 刷新」的完整闭环。
>
> **不做什么**：不碰业务逻辑（动作库、计划、训练），只搭地基。

### ✅ 步骤 1.1 —— Spring Boot 项目骨架

| 项 | 内容 |
|---|---|
| **目的** | 建出能启动的空项目 |
| **产出** | `server/pom.xml`、`GymLogApplication.java`、`application.yml` |
| **知识点** | Spring Boot 3 的 `pom.xml` 结构、`@SpringBootApplication` 组合注解、父子 POM 的依赖管理 |
| **谁写** | 我 |
| **验收** | 启动成功，8080 监听，请求能进到 Spring |

**完成记录（2026-09-17）**

- 实际用时：编译 4 分钟（首次下载依赖），启动约 20 秒
- 验收结果：
  - `mvn compile` → **BUILD SUCCESS**
  - 8080 → **LISTENING**
  - `GET /` → **HTTP 404** + Spring 标准 JSON 错误体（证明请求进到了 DispatcherServlet）
  - 时间格式 `2026-09-17 13:56:35` → 证明 Jackson 配置生效

**本步确定的版本**（踩坑记录）：

| 依赖 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.5.3 | 支持 JDK 17 的稳定线 |
| springdoc-openapi | **2.9.1** | ⚠️ **必须用 2.x**。3.x 是给 Spring Boot 4 的 |
| MyBatis-Plus | 3.5.17 | `spring-boot3-starter` |
| jjwt | 0.12.7 | |
| mysql-connector-j / flyway | **不写版本** | 由 Spring Boot BOM 管理 |

> ⚠️ **坑 1**：Maven Central 上 `mysql-connector-j` 最新是 `26.7.0`、`flyway-mysql` 是 `13.7.0`、`springdoc` 是 `3.1.1`——**这三个都是 Spring Boot 4 时代的东西，跟 JDK 17 不匹配**。选版本不能只看「最新」，要看与 Boot 版本配套的那条线。
>
> ⚠️ **坑 2**：Windows 中文环境的 `platform encoding` 是 **GBK**。pom 里必须显式设 `project.build.sourceEncoding=UTF-8`，否则**带中文注释的源码会编译乱码**。

**为什么这步只加了 4 个依赖**：
`web` / `validation` / `lombok` / `test`。数据库、Security、JWT、Swagger 都在后续步骤逐个加——**每加一个你能看清它是干什么的、不加会怎样**。一次性堆十几个依赖，出问题根本不知道是谁的锅。

> 特别是 **Security 不能现在加**：一旦加了，Spring Security 默认会锁住所有接口并生成随机密码，步骤 1.3–1.5 的接口就没法直接测。

---

### ✅ 步骤 1.2 —— 数据库与 Flyway 迁移

| 项 | 内容 |
|---|---|
| **目的** | 建库 + 第一张表，并让迁移可重复执行 |
| **产出** | `V1__init_user.sql`、`db/init.sql`、`application-dev.yml`(+example) |
| **知识点** | Flyway 命名与版本机制、`flyway_schema_history`、**profile 加载规则**、连接池自动装配 |
| **谁写** | 我 |
| **验收** | `flyway_schema_history` 有 `success=1` 记录；`user` 表结构正确 |

**完成记录（2026-09-17）**

验收结果：

| 检查项 | 结果 |
|---|---|
| Flyway 记录 | `version=1, success=1, execution_time=20ms` ✓ |
| `gym_log` 表 | `flyway_schema_history`、`user` ✓ |
| `user` 字段 | **18 个**，类型/可空/默认值全部符合设计 ✓ |
| `gym_log` 字符集 | `utf8mb4 / utf8mb4_0900_ai_ci` ✓ |
| 启动耗时 | 1.826 秒 |
| HikariCP | 连接池自动装配并启动 ✓ |

**踩坑记录（重要）**

> 🔴 **坑 3：`application-dev.yml` 建了但没生效**
>
> 报错：`Failed to configure a DataSource: 'url' attribute is not specified ... (no profiles are currently active)`
>
> **原因**：Spring Boot 的加载规则是——`application.yml` **总是**加载；`application-{profile}.yml` **只在对应 profile 激活时**才加载。我建了 `application-dev.yml` 却没激活 `dev`，等于文件白建。
>
> **修法用 `default` 而不是 `active`**：
>
> | 写法 | 行为 | 风险 |
> |---|---|---|
> | `profiles.active: dev` | **强制**用 dev，生产环境设了 `SPRING_PROFILES_ACTIVE=prod` 也会被覆盖 | ⚠️ 典型的「把开发配置带上生产」事故源 |
> | `profiles.default: dev` | **兜底**——没人指定时用 dev；生产设了 prod 就用 prod，这里自动失效 | ✅ 正确做法 |
>
> 已在 [application.yml](../server/src/main/resources/application.yml) 用 `default` 并附注释说明。

> ⚠️ **坑 4：密码写进了会被提交的文件**
>
> 修改密码时同时改了 `application-dev.yml`（已 gitignore，正确 ✓）和 `application-dev.yml.example`（**会被提交到公开仓库** ✗）。已恢复为占位符。
>
> 另发现 example 里写成了 `password:<值>` 的**冒号后缺空格**形式。YAML 要求映射的冒号后必须有空格，否则整行会被当成一个键名，配置读不到。已在模板中加注释警告。
>
> **教训**：改配置前先 `git check-ignore <文件>` 确认它是否会被提交。

**BOM 自动选定的版本**（再次印证不写版本号的正确性）：

| 依赖 | BOM 选定 | Maven Central 上的"最新版" |
|---|---|---|
| mysql-connector-j | **9.2.0** | 26.7.0 ← Boot 4 时代，不能用 |
| flyway-core / -mysql | **11.7.2** | 13.7.0 ← 同上 |

**两个未声明但自动引入的依赖**：`spring-boot-starter-jdbc` 和 `HikariCP 6.3.0`（由 mybatis-plus 传递带入）。所以**一行连接池配置都没写，连接池已经在了**。

---

### ✅ 步骤 1.3 —— 统一响应与全局异常处理

| 项 | 内容 |
|---|---|
| **目的** | 所有接口返回统一结构，异常不裸奔 |
| **产出** | `common/` 下 4 个类 + `system/SystemController`（健康检查） |
| **知识点** | `@RestControllerAdvice`、`@ExceptionHandler`、异常分层、日志级别选择 |
| **谁写** | 我 |
| **验收** | 六种场景的 HTTP 状态与响应体均正确 |

**完成记录（2026-09-17）**

六种场景实测结果：

| 场景 | HTTP | 响应体 |
|---|---|---|
| 正常 | 200 | `{"code":0,"message":"成功","data":{...}}` |
| 业务异常 | **409** | `{"code":20001,"message":"该邮箱已被注册"}` |
| 业务异常 + 自定义文案 | **400** | `{"code":60002,"message":"体重 500kg 超出合理范围（20–300kg）"}` |
| 未预期异常 | **500** | `{"code":10005,"message":"系统繁忙，请稍后重试"}`（无堆栈） |
| 路径不存在 | 404 | `{"code":10003,...}` |
| 方法不支持 | 405 | `{"code":10004,...}` |

日志级别实测：业务异常打 **WARN 且堆栈帧数 = 0**；未预期异常打 **ERROR 且堆栈直接定位到 `SystemController.java:65`**。

**三个设计决策**

1. **错误码分段编号**：`0` 成功，`1xxxx` 通用，`2xxxx` 用户，`3xxxx` 动作库，`4xxxx` 计划，`5xxxx` 训练，`6xxxx` 身体数据，`7xxxx` 饮食，`8xxxx` 管理后台，`9xxxx` 外部服务。留好空位，加模块不用重排。
2. **错误码同时携带 HTTP 状态**：不能一律返回 200——那样网关、监控告警、缓存全部失效。
3. **业务异常当预期事件、系统异常当 bug**：前者 WARN 不打栈（量大无价值），后者 ERROR 打全栈（要排查）。

**踩坑记录**

> 🔴 **坑 5：中文日志乱码**
>
> 现象：日志里 `业务异常` 显示成 `ҵ���쳣`。
>
> 根因：**Java 18 之前 `file.encoding` 跟随操作系统**，中文 Windows 上是 `GBK`；而现代终端（Windows Terminal / VS Code / IDEA）按 UTF-8 解码。Java 写 GBK 字节、终端按 UTF-8 读 → 乱码。
>
> 修法（两处都要改）：
> - `application.yml` → `logging.charset.console/file: UTF-8`
> - `pom.xml` → `spring-boot-maven-plugin` 加 `<jvmArguments>-Dfile.encoding=UTF-8</jvmArguments>`
>
> 只设 console 不够——如果日志同时输出到文件，文件也需要 UTF-8，否则用编辑器打开同样是乱码。

> ℹ️ **注意**：响应体里 `data` 为 null 时会被省略（`default-property-inclusion: non_null` 的效果）。前端拿到的 `res.data` 是 `undefined` 而非 `null`，两者都是 falsy，判断逻辑不受影响。

**验证接口已删除**：验证用的 `/demo/**` 四个接口已从 `SystemController` 移除，只保留 `/api/v1/system/ping` 健康检查。

---

### ✅ 步骤 1.4 —— 用户实体与 Mapper

| 项 | 内容 |
|---|---|
| **目的** | 打通「Java 对象 ↔ 数据库表」 |
| **产出** | `user/User.java`、`user/UserMapper.java`、`config/MybatisPlusConfig.java`、`UserMapperTest` |
| **知识点** | `BaseMapper` 白拿的方法、`@TableName`/`@TableId`、驼峰↔下划线自动映射、逻辑删除、分页插件 |
| **谁写** | 我 |
| **验收** | 集成测试通过，字段映射与数据库默认值均正确 |

**完成记录（2026-09-17）**

测试通过（`Tests run: 1, Failures: 0`），SQL 日志同时验证了三个机制：

```sql
-- ① 插入时只包含非 null 字段，其余交给数据库 DEFAULT
INSERT INTO user ( email, password_hash, nickname, gender, birth_year, height_cm, goal, experience )
VALUES ( ?,?,?,?,?,?,?,? )

-- ② 逻辑删除自动生效：查询自动附加 AND deleted=0
SELECT id,email,...,deleted FROM user WHERE id=? AND deleted=0

-- ③ 数据库默认值经 ORM 往返正确
Row: 1, ..., 1, 1, 1995, 175.5, MUSCLE_GAIN, INTERMEDIATE, kg, local, null, 0, ...
                                        ↑ status=1  ↑ unit_pref=kg  ↑ provider=local
```

**踩坑记录**

> 🔴 **坑 6：MyBatis-Plus 3.5.9 起分页插件被拆到独立模块**
>
> 只引 `mybatis-plus-spring-boot3-starter` 时，`PaginationInnerInterceptor`
> **编译期就报 cannot be resolved**——因为 `mybatis-plus-extension` 里已经没有这个类了。
>
> 原因：分页插件依赖 **JSqlParser**（一个不小的第三方 SQL 解析库），
> 而多数项目只用基础 CRUD。3.5.9 把它拆成独立模块，不用的项目可以少引一个依赖。
>
> 修法：额外引入 `com.baomidou:mybatis-plus-jsqlparser`（版本与 starter 对齐）。

> ⚠️ **坑 7：SQL 日志中文乱码（未完全解决）**
>
> 现象：MyBatis 打印的 SQL 参数里中文显示为乱码。
>
> **根因链**：
> 1. 原配置用 `StdOutImpl`，它**直接写 `System.out`，绕过 Logback** ——
>    所以 `logging.charset.console: UTF-8` 对它完全无效
> 2. 而 Windows 上 `System.out` 走**平台编码 GBK**，
>    `-Dfile.encoding` 管不到它，`-Dsun.stdout.encoding` 也没压住
>
> **已做的改进**：改用 `Slf4jImpl`，SQL 日志走 Logback ——
> 附带好处是有了级别控制、时间戳、Mapper 方法名，生产环境调成 info 就自动不打。
>
> **残余问题**：命令行输出仍是 GBK 字节。已用 `iconv` 严格验证——**按 GBK 解码完全正确**，
> 说明**数据本身没有问题**。
>
> ✅ **2026-09-17 用户实测确认：IDEA 控制台中中文显示正常。** 结案。
>
> **教训**：这类问题的排查成本极高，且容易陷入「以为解决了其实没有」。
> 判定方法应该是**直接看字节**（`xxd`）而不是看终端显示。

**两个安全设计**（写在 `User` 实体里）

| 字段 | 防护 | 防的是什么 |
|---|---|---|
| `passwordHash` | `@JsonIgnore` | 接口直接返回实体时，密码哈希泄露给前端 |
| `passwordHash` | `@ToString.Exclude` | `log.info("user={}", user)` 把哈希写进日志 |

第二条容易被忽略——**日志往往比数据库更容易被看到**（运维、日志平台、误提交的日志文件）。
BCrypt 哈希可以离线暴力破解，不受登录接口限流约束，拿到哈希 ≈ 拿到一份可以慢慢猜的密码。

---

### ✅ 步骤 1.5 —— 注册接口

| 项 | 内容 |
|---|---|
| **目的** | 能创建用户 |
| **产出** | `dto/RegisterRequest`、`config/PasswordConfig`、`UserService`、`AuthController` |
| **知识点** | `@Valid` + JSR-303、BCrypt 原理、**并发下的唯一性保证**、DTO 与实体的分离 |
| **谁写** | 我 |
| **验收** | 正常注册成功；重复/大写邮箱被拒；参数校验生效；库中密码是哈希 |

**完成记录（2026-09-17）**

| 场景 | HTTP | 响应 |
|---|---|---|
| 正常注册 | 200 | `{"code":0,"data":6}` |
| 重复邮箱 | **409** | `{"code":20001,"message":"该邮箱已被注册"}` |
| **大写邮箱 `ALICE@Example.COM`** | **409** | 同样判为重复 —— 归一化生效 |
| 邮箱格式错误 | 400 | `"邮箱格式不正确"` |
| 密码太短（3 位） | 400 | `"密码长度需在 8-32 位之间"` |
| 昵称为空 | 400 | `"昵称不能为空"` |

数据库侧验证：

```
email = alice@example.com      ← 已转小写
password_hash = $2a$10$...     ← BCrypt cost 10，长度 60
库中无明文密码 ✓
```

**核心设计：邮箱唯一性的两层防护**

```java
// 第一层：应用层查重 —— 为了友好提示，覆盖 99% 的情况
if (existsByEmail(email)) {
    throw new BizException(EMAIL_ALREADY_EXISTS);
}

try {
    userMapper.insert(user);
} catch (DuplicateKeyException e) {
    // 第二层：数据库唯一索引 —— 真正的保证
    throw new BizException(EMAIL_ALREADY_EXISTS);
}
```

> **为什么两层都要**：「查」和「插」之间存在时间窗——两个并发请求可能都查到「不存在」，然后都插入成功。
> **应用层的「先查后插」永远无法保证唯一性，真正的保证只能来自数据库唯一索引。**
> 这不是代码写得不严谨，是并发场景的固有性质。

**为什么只引 `spring-security-crypto` 不引完整 starter**

密码加密只需要 `BCryptPasswordEncoder`（在 crypto 这个零依赖小包里）。完整 starter 一旦引入，Spring Security **默认锁住所有接口**并生成随机密码，1.5/1.6 的接口就没法直接测了。完整的 Security 在 1.7/1.8 引入。

**为什么 Service 不写接口**

国内常见 `UserService` 接口 + `UserServiceImpl` 实现。本项目不这么做——只有一个实现时，接口是纯负担（改签名动两处、IDE 多跳一次），且没有实际收益。Spring 官方团队近年也明确建议不要写没有必要的接口。**这个取舍面试可能被问。**

**已知限制**

参数校验失败时**只返回第一条错误**。实测「全空」请求返回的是「密码长度需在 8-32 位之间」而非「邮箱不能为空」——因为字段错误的顺序不保证。

这是有意取舍（移动端用 Toast 展示，一次给一条更清楚）。若将来需要表单逐字段高亮，给 `Result` 加 `errors` 字段返回 `Map<字段名, 错误信息>` 即可。

---

### ✅ 步骤 1.6 —— 登录接口与 JWT 签发 ★

| 项 | 内容 |
|---|---|
| **目的** | 校验密码，签发 access token |
| **产出** | `common/JwtProperties`、`common/JwtService`、`dto/LoginRequest`、`dto/LoginResponse`、`UserService.login`、`/auth/login` |
| **知识点** | JWT 三段结构、密钥管理与启动期校验、**时序攻击**、账号枚举防护 |
| **谁写** | 我写，你审查 |
| **验收** | 登录返回 token；payload 解出正确 claims；错误密码与不存在邮箱返回**完全一致** |

**完成记录（2026-09-17）**

| 场景 | HTTP | 响应 |
|---|---|---|
| 正常登录 | 200 | 含 `accessToken` / `tokenType: Bearer` / `expiresIn: 3600` / user |
| 密码错误 | 401 | `{"code":20003,"message":"邮箱或密码不正确"}` |
| **邮箱不存在** | 401 | **与上面完全一致** —— 无账号枚举漏洞 |
| 禁用账号 + 正确密码 | **403** | `{"code":20004,"message":"账号已被禁用，请联系管理员"}` |
| 禁用账号 + 错误密码 | 401 | `20003` —— **不泄露禁用状态** |
| 参数校验 | 400 | 邮箱格式 / 密码为空 |

JWT 实际内容（`base64 -d` 解出）：

```
Header : {"alg":"HS512"}          ← jjwt 按密钥长度（512 位）自动选的，比预期的 HS256 更强
Payload: {"sub":"6","email":"alice@example.com","iat":1789628429,"exp":1789632029}
         exp - iat = 3600 秒 = 配置的 1h ✓
```

**三个安全设计（本步的核心）**

**① 账号枚举防护**：密码错误和邮箱不存在返回**完全相同**的错误码与文案。
若分成「用户不存在」和「密码错误」，等于直接告诉攻击者哪些邮箱有效——
这是撞库攻击的第一步。

**② 时序攻击防护**：用户不存在时也跑一次 BCrypt（对固定假哈希），使两种情况的耗时接近。

| | 未防护 | 已防护（实测） |
|---|---|---|
| 邮箱存在 + 密码错 | ~80ms（跑了 BCrypt） | **60 ms** |
| 邮箱不存在 | ~1ms（直接返回） | **59 ms** |

攻击者不需要看报错内容，**只看响应时间**就能批量判断邮箱是否注册过。实测差值 **1ms**，信号已消除。

**③ 检查顺序**：密码校验**在**账号状态检查**之前**。
如果先检查状态，攻击者能通过「返回『禁用』还是『密码错误』」来枚举账号。
**只有密码正确的人，才配知道这个账号被禁用了。**

**密钥管理**

| 项 | 做法 |
|---|---|
| 存放 | `application-dev.yml`（已 gitignore），生产用环境变量 `JWT_SECRET` 覆盖 |
| 生成 | `head -c 64 /dev/urandom \| base64` → 64 字节（512 位） |
| 校验 | 构造器里检查非空 + 长度 ≥ 32 字节，**不合法则启动直接失败** |
| 存储格式 | Base64——避免密钥含换行/控制字符导致 YAML 解析异常 |

> **为什么在构造器里校验而不是用时再检查**：配置错误应该**启动时立刻失败**，
> 而不是等第一个用户登录才炸。这是「快速失败」原则——暴露得越早，排查成本越低。

**踩坑记录**

> ⚠️ **坑 8：Windows 命令行传中文参数会损坏**
>
> 现象：`curl -d '{"nickname":"待禁用"}'` 返回 `{"code":10006,"message":"请求格式有误"}`，
> 换成 `-d @file.json`（从文件读）就成功。
>
> 原因：**Windows 命令行参数走 ANSI 代码页（GBK）**，
> Git Bash 传出的 UTF-8 字节在传给 curl 时被转换坏，导致 JSON 解析失败。
>
> **这不是接口 bug**——库里的昵称十六进制是 `E5BE85E7A681`，中文完全正确。
>
> **教训**：测试含中文的接口时，用 `-d @文件` 而不是内联 `-d '...'`。
> 排查这类问题要**看字节**（`HEX()`、`xxd`），不要被终端的显示误导。

---

### ✅ 步骤 1.7 —— JwtAuthenticationFilter ★

| 项 | 内容 |
|---|---|
| **目的** | 每个请求进来时，从 Header 解析 token 并放入 SecurityContext |
| **产出** | `security/JwtAuthenticationFilter`、`config/SecurityConfig`（过渡配置） |
| **知识点** | `OncePerRequestFilter`、`SecurityContextHolder` 的 ThreadLocal 本质、认证 vs 授权 |
| **谁写** | 我写，你审查 |
| **验收** | 合法 token 能识别出用户；篡改/伪造的 token 被拒绝 |

**验收结论**：六种场景全部符合预期。最关键的一条——**篡改 payload 冒充他人被拒**（验签失败）。
临时验证接口 `/system/whoami` 已删除。

> 📖 **详细记录（含全部验证数据与三个设计决策）见 [DEV-LOG.md 步骤 1.7](./DEV-LOG.md#步骤-17--jwtauthenticationfilter-)。**

**审查时你要能回答**：
- `OncePerRequestFilter` 比普通 `Filter` 多做了什么？
- 为什么这个过滤器**不**在 token 无效时抛 401？
- 为什么它**不**标 `@Component`？标了会怎样？
- `SecurityContextHolder` 为什么能保证「无状态」？
- 为什么 `.csrf().disable()` 在这个项目是安全的？什么情况下**不能**关？

---

### ✅ 步骤 1.8 —— SecurityFilterChain 配置 ★

| 项 | 内容 |
|---|---|
| **目的** | 声明哪些路径公开、哪些需要认证，以及被拒绝时返回什么 |
| **产出** | `SecurityConfig`（收紧）、`RestAuthenticationEntryPoint`（401）、`RestAccessDeniedHandler`（403）、`UserController` + `/users/me` |
| **知识点** | 401 vs 403 的区别、授权规则匹配顺序、CORS 预检、结构性越权免疫 |
| **谁写** | 我写，你审查 |
| **验收** | 公开路径免 token；受保护路径无 token 返回 401；非管理员访问管理端返回 403 |

**验收结论**：六种组合全部符合预期。**项目至此有了第一个真正受保护的接口**（`GET /api/v1/users/me`）。

> 📖 **详细记录（含 401/403 的辨析、`/error` 放行的必要性）见 [DEV-LOG.md 步骤 1.8](./DEV-LOG.md#步骤-18--securityfilterchain-配置-)。**

**审查时你要能回答**：
- 401 和 403 的区别是什么？客户端拿到这两个码分别该做什么？
- 为什么**匿名用户**访问 `/admin/**` 返回的是 401，而**已登录的非管理员**返回 403？
- 为什么这个项目可以 `csrf().disable()`？什么情况下**不能**关？
- `SessionCreationPolicy.STATELESS` 意味着什么？对水平扩展有什么影响？
- 为什么 `/error` 必须加到公开路径？不加会怎样？
- 为什么 CORS 预检（OPTIONS）必须放行？

---

### ✅ 步骤 1.9 —— Refresh Token 与登出

| 项 | 内容 |
|---|---|
| **目的** | access token 短期、refresh token 长期；登出能立即失效 |
| **产出** | `V2__init_refresh_token.sql`、`RefreshToken(+Mapper/Service)`、`/auth/refresh`、`/auth/logout` |
| **知识点** | **refresh token 存库而非 JWT 的原因**、令牌轮换、SHA-256 vs BCrypt 的选择依据 |
| **谁写** | 我写，你审查 |
| **验收** | 刷新能换到新令牌对；旧 refresh token 立即失效；登出后无法再刷新 |

**验收结论**：全部通过。库里存的是 SHA-256 哈希（原文不在库中）；轮换后旧 token 立即失效；
登出后刷新返回 401。

> 📖 **详细记录（含「为什么 token 用 SHA-256 而密码用 BCrypt」的完整论证）见 [DEV-LOG.md 步骤 1.9](./DEV-LOG.md#步骤-19--refresh-token-与登出-)。**

**审查时你要能回答**：
- refresh token 为什么不用 JWT？用了会怎样？
- 为什么 token 用 SHA-256 就够，而密码必须用 BCrypt？
- 什么是令牌轮换？不轮换有什么风险？
- 登出后 access token 还有效吗？为什么？这个取舍可以接受吗？
- 登出接口为什么要设为公开？

---

### ✅ 步骤 1.10 —— RBAC 三张表

| 项 | 内容 |
|---|---|
| **目的** | 把权限模型的地基打好（**权限校验留到 Phase 6**） |
| **产出** | `V3__init_rbac.sql`（4 表 + 种子数据）、`Role`、`UserRole` 及其 Mapper |
| **知识点** | RBAC 三层模型、**为什么不直接在 user 表加 `is_admin`**、权限命名约定 |
| **谁写** | 我写，你审查 |
| **验收** | 注册自动绑定 `USER` 角色；种子数据正确 |

**验收结论**：7 张表就位；14 个权限，ADMIN 全有、USER 有 0 个；新注册用户自动获得 `USER` 角色。

> 📖 **详细记录（含「为什么 USER 角色不授予任何权限」的论证）见 [DEV-LOG.md 步骤 1.10](./DEV-LOG.md#步骤-110--rbac-三张表-)。**

**审查时你要能回答**：
- 为什么是「用户-角色-权限」三层？去掉角色这一层会怎样？
- 为什么不直接在 `user` 表加 `is_admin` 字段？
- 为什么 `USER` 角色一个权限都不授？
- 权限的 `code` 为什么不直接用 `name`？

---

### 步骤 1.11 —— Swagger / OpenAPI 文档

| 项 | 内容 |
|---|---|
| **目的** | 接口可视化，面试可演示 |
| **产出** | `OpenApiConfig`（含 JWT 认证按钮） |
| **知识点** | springdoc 与 springfox 的区别、OpenAPI 3 规范、`@Operation` / `@Schema` 注解 |
| **谁写** | 我 |
| **验收** | 访问 `/swagger-ui.html`，能看到已实现的接口，且能用 Bearer token 授权后调通受保护接口 |

---

### 步骤 1.12 —— Phase 1 验收

| 项 | 内容 |
|---|---|
| **目的** | 端到端验证整个闭环 |
| **产出** | 无新代码，跑一遍验收清单 |
| **验收项** | ① 注册 → ② 登录拿 token → ③ 带 token 访问受保护接口成功 → ④ 不带 token 返回 401 → ⑤ 篡改 token 返回 401 → ⑥ access 过期后用 refresh 换新 → ⑦ 登出后 refresh 失效 → ⑧ 用用户 A 的 token 无法访问用户 B 的数据（此时用占位接口验证） |
| **对应验收标准** | AC-1-1 ~ AC-1-4 |

---

## 5. Phase 2–8 概要

> 详细步骤在进入该 Phase 前再展开，避免过早细化导致返工。

### Phase 2 —— 动作库与计划管理（~14 步）
内置动作库种子数据 → 自定义动作 → 计划 CRUD → 6 个模板 → **逐组处方** → **超级组编排** → **周期化展开算法**★ → 计划版本化★ → 「今天练什么」查询
**你写**：周期化展开算法、超级组校验、计划版本化的快照逻辑

### Phase 3 —— 跟练（~16 步，**工作量最大**）
会话创建与快照★ → 组记录 CRUD → 跟练状态机★ → 本地 SQLite → 断点续训 → **超级组轮次推进**★ → **音频隔离** → 离线队列 → 训练总结
**你写**：状态机、快照深拷贝、超级组推进逻辑
**顺序建议**：**先做不带超级组的顺序执行跑通，再叠加超级组**——一次性写完很难定位 bug

### Phase 4 —— 身体数据与可视化（~12 步）
身体测量 / 体成分 / 生理指标 / 主观状态 / 体态照片 → 指标聚合接口★ → 手机端图表（fl_chart，按 M7-B 适配）
**你写**：指标聚合 SQL（移动平均、e1RM、容量与组数的分组统计）

### Phase 5 —— Web 端（~8 步）
Vue 3 骨架 → 登录 → 计划管理 → 图表页（ECharts，含 PC 专属多序列图）

### Phase 6 —— 管理后台（~10 步）
RBAC 权限校验★（`@PreAuthorize`）→ 审计日志切面★ → 动作库维护 → 模板维护 → 用户管理 → 数据看板
**你写**：权限校验、审计日志切面

### Phase 7 —— 饮食与 AI（~6 步，可砍）
饮食轻量记录 → TDEE 反推★ → `LlmClient` 抽象 → 训练总结生成 → AI 开关与审计

### Phase 8 —— 打磨（~6 步）
数据导出 → 同步优化 → 性能优化 → 部署 → README 与演示材料

---

## 6. 学习检查点

> 每个 Phase 结束时，你应该能不查资料回答出下面的问题。**答不出来说明该补，而不是该继续往下做。**

### Phase 1 结束后

- [ ] JWT 的 payload 是加密的吗？为什么不能往里放密码？
- [ ] 为什么 access token 要短期、refresh token 要长期？
- [ ] refresh token 为什么存数据库而不是也做成 JWT？
- [ ] 为什么用 BCrypt 而不是 MD5 存密码？
- [ ] `OncePerRequestFilter` 和普通 Filter 有什么区别？
- [ ] 认证（Authentication）和授权（Authorization）的区别？
- [ ] 为什么需要 RBAC 三张表，直接在 user 表加 `is_admin` 有什么问题？

### Phase 3 结束后

- [ ] 为什么计时必须存绝对时间戳而不能存「剩余秒数」？
- [ ] 会话为什么必须深拷贝计划快照？不拷贝会出什么问题？
- [ ] 超级组为什么要用「组内位置 + 轮次」两个维度推进？
- [ ] 离线数据同步如何保证不重复？（幂等键的作用）
- [ ] 怎么让提示音不打断用户的音乐？

### Phase 4 结束后

- [ ] 体重为什么必须用移动平均？
- [ ] 估算 1RM 为什么要取最佳组而不是平均值？
- [ ] 容量和组数为什么要分成两个指标？
- [ ] 为什么贵图的 Y 轴从 0 开始，体重图的 Y 轴不从 0 开始？

---

## 7. 风险与应对

| 风险 | 表现 | 应对 |
|---|---|---|
| **Phase 3 超期** | 超级组让状态机复杂度翻倍 | 先做顺序执行版本跑通再叠加；必要时超级组降级为 Phase 3.5 |
| **卡在环境问题** | Android/Gradle 报错 | 已踩过的坑记在 [README](../README.md) 与本机 memory 中，先查那里 |
| **范围蔓延** | 想加需求文档里没有的功能 | 记进 `REQUIREMENTS.md` 第 11 节「待定」，**不在当前 Phase 里做** |
| **只写不测** | 攒了一堆代码没验证 | 每个步骤必须过验收才能进下一步，不允许「先写着，回头一起测」 |
| **文档与代码脱节** | 改了实现没改文档 | 实现偏离文档时**当场改文档**，不攒着 |

---

## 8. 数据库凭据怎么管

> **原则：数据库密码不进入对话记录，不进入 git，不进入日志。**

### 8.1 三处需要凭据，分别处理

| 用途 | 存放位置 | 谁写 |
|---|---|---|
| **Spring Boot 连库** | `server/src/main/resources/application-dev.yml` | **你自己写**。该文件已在 `.gitignore` 中，不会被提交 |
| **Claude 跑验证查询**（如确认 Flyway 建表成功） | `mysql_config_editor` 存储的 `--login-path` | 你运行一次交互命令，之后我用 `--login-path=gymlog` 调用，**全程看不到密码** |
| **生产环境** | 环境变量或密钥管理服务 | 部署阶段再说 |

### 8.2 配置 `--login-path`（推荐）

在你自己的终端里运行下面这条（**会提示输入密码，输入的内容不会显示，也不会进入对话**）：

```powershell
& "D:\MySQL\MySQL Server 8.0\bin\mysql_config_editor.exe" set `
    --login-path=gymlog --host=localhost --port=3306 --user=root --password
```

之后我可以用这条命令跑验证查询：

```powershell
& "D:\MySQL\MySQL Server 8.0\bin\mysql.exe" --login-path=gymlog -e "SHOW DATABASES;"
```

**安全说明**：`mysql_config_editor` 把密码存在 `%APPDATA%\MySQL\.mylogin.cnf`，文件内容是**混淆**而非强加密（这是 MySQL 官方工具的已知特性）。对本机开发环境足够，但**不要把该文件同步到云盘或提交到任何仓库**。

### 8.3 如果要撤销

```powershell
& "D:\MySQL\MySQL Server 8.0\bin\mysql_config_editor.exe" remove --login-path=gymlog
```

---

## 9. 下一步

**Phase 1 步骤 1.1 —— 建 Spring Boot 项目骨架。**

已确认的事项：

- ✅ 认证部分（1.6–1.8）由我写、你审查
- ✅ MySQL 用 `--login-path` 方式管理凭据，密码不进对话

**开工。**
