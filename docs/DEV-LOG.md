# gym-log 开发日志

> **本文档是回顾性的**：按时间顺序记录每一步实际做了什么、验证到了什么、踩了什么坑。
>
> | 文档 | 定位 |
> |---|---|
> | [REQUIREMENTS.md](./REQUIREMENTS.md) | 要做什么，为什么 |
> | [DEVELOPMENT-PLAN.md](./DEVELOPMENT-PLAN.md) | 按什么顺序做，怎么验收（前瞻） |
> | **本文档** | **实际做了什么，验证到什么（回顾）** |
>
> **每完成一个步骤，在这里追加一条记录。**

---

## 目录

### Phase 1 —— 后端地基

| 步骤 | 名称 | 日期 | 状态 |
|---|---|---|---|
| 1.1 | Spring Boot 项目骨架 | 2026-09-17 | ✅ |
| 1.2 | 数据库与 Flyway 迁移 | 2026-09-17 | ✅ |
| 1.3 | 统一响应与全局异常处理 | 2026-09-17 | ✅ |
| 1.4 | 用户实体与 Mapper | 2026-09-17 | ✅ |
| 1.5 | 注册接口 | 2026-09-17 | ✅ |
| 1.6 | 登录接口与 JWT 签发 | 2026-09-17 | ✅ |
| 1.7 | JwtAuthenticationFilter | 2026-09-17 | ✅ |
| 1.8 | SecurityFilterChain 配置 | 2026-09-17 | ✅ |
| 1.9 | Refresh Token 与登出 | 2026-09-17 | ✅ |
| 1.10 | RBAC 三张表 | 2026-09-17 | ✅ |
| 1.11 | Swagger / OpenAPI | 2026-09-17 | ✅ |
| 1.12 | Phase 1 总验收 | 2026-09-17 | ✅ |

### Phase 2 —— 动作库与计划管理

| 步骤 | 名称 | 日期 | 状态 |
|---|---|---|---|
| 2.1 | 动作库表设计 | 2026-09-17 | ✅ |
| 2.3 | 内置动作库种子数据（90 个） | 2026-09-17 | ✅ |
| 2.4 | 动作查询接口 | 2026-09-17 | ✅ |
| 2.4b | 修正别名与分类（V6） | 2026-09-17 | ✅ |
| 2.5 | 自定义动作接口 | 2026-09-17 | ✅ |
| 2.6 | 计划相关表设计（5 张表） | 2026-09-17 | ✅ |
| 2.8 | 计划 CRUD 接口 | 2026-09-17 | ✅ |
| 2.9 | 内置计划模板（6 个） | 2026-09-17 | ✅ |
| 2.10 | 从模板创建计划 | 2026-09-17 | ✅ |

---

## 步骤 1.1 —— Spring Boot 项目骨架 ✅

**日期**：2026-09-17

### 做了什么

建出能启动的空项目。**只加 4 个依赖**：`web` / `validation` / `lombok` / `test`。

```
server/
├── pom.xml
└── src/main/
    ├── java/com/gymlog/GymLogApplication.java
    └── resources/application.yml
```

### 验证结果

| 检查项 | 结果 |
|---|---|
| `mvn compile` | BUILD SUCCESS（首次 4 分钟，主要是下载依赖） |
| 8080 端口 | LISTENING |
| `GET /` | HTTP 404 + Spring 标准 JSON 错误体 |
| Jackson 配置 | 生效（时间格式 `2026-09-17 13:56:35`） |

那个 404 是关键证据：**连接没被拒，请求真的进到了 DispatcherServlet**，只是还没有 Controller。

### 为什么依赖是逐步加的

**每加一个依赖，你能清楚看到它是干什么用的、不加会怎样。** 一次性堆十几个，出问题根本不知道是谁的锅。

特别是 **Security 不能现在加**——一旦引入，Spring Security 默认锁住所有接口并生成随机密码，1.3–1.5 的接口就没法直接测了。

### 踩坑

> ⚠️ **坑 1：不能只看「最新版本」**
>
> Maven Central 上 `mysql-connector-j` 最新是 `26.7.0`、`flyway-mysql` 是 `13.7.0`、`springdoc` 是 `3.1.1`——**这三个都是 Spring Boot 4 时代的东西**，跟本机 JDK 17 不匹配。
>
> 选版本要看**与 Boot 版本配套的那条线**，不是看谁数字大。
>
> | 依赖 | 选用 | Maven Central「最新」 |
> |---|---|---|
> | Spring Boot | 3.5.3 | — |
> | springdoc-openapi | **2.9.1**（2.x 才对应 Boot 3） | 3.1.1 ✗ |
> | mysql-connector-j | 不写版本，BOM 管 | 26.7.0 ✗ |
> | flyway | 不写版本，BOM 管 | 13.7.0 ✗ |

> ⚠️ **坑 2：Windows 中文环境的编码**
>
> Maven 报 `platform encoding: GBK`。pom 里必须显式设 `project.build.sourceEncoding=UTF-8`，否则**带中文注释的源码会编译成乱码**。

---

## 步骤 1.2 —— 数据库与 Flyway 迁移 ✅

**日期**：2026-09-17

### 做了什么

```
server/
├── db/init.sql                                    建库（只跑一次）
└── src/main/resources/
    ├── application-dev.yml                        数据库配置（gitignored）
    ├── application-dev.yml.example               模板（会提交）
    └── db/migration/V1__init_user.sql             user 表
```

### 验证结果

| 检查项 | 结果 |
|---|---|
| Flyway 记录 | `version=1, success=1, execution_time=20ms` |
| 表 | `flyway_schema_history` + `user` |
| `user` 字段 | **18 个**，类型/可空/默认值全部符合设计 |
| 库字符集 | `utf8mb4 / utf8mb4_0900_ai_ci` |
| 启动耗时 | 1.826 秒 |
| HikariCP | 自动装配并启动 |

完整启动日志：

```
profile    : "dev"
Flyway     : Migrating schema `gym_log` to version "1 - init user"
             Successfully applied 1 migration, now at version v1
HikariPool-1: Start completed
Tomcat     : started on port 8080
Started GymLogApplication in 1.826 seconds
```

### 关键设计

**为什么用 Flyway**：手动改表的致命问题是**不可追溯**——你本地加了个字段，同事拉下代码跑不起来，因为他不知道要加。Flyway 把结构变更变成**有版本号的代码**，跟着 git 走。

命名规则 `V<版本号>__<描述>.sql`——**两个下划线**。⚠️ 已执行过的脚本**不能再改**，Flyway 会校验 checksum 并拒绝启动。

**`utf8mb4` 而不是 `utf8`**：MySQL 的 `utf8` 是**假的 UTF-8**，只支持 3 字节，**存不了 emoji**。用户昵称打个 😀 就直接报错。

**不用外键约束**：国内互联网公司的普遍做法（阿里 Java 规范明确写了）。外键在高并发下因锁竞争影响性能，且分库分表后无法维护。

### 踩坑

> 🔴 **坑 3：`application-dev.yml` 建了但没生效**
>
> 报错：`Failed to configure a DataSource: 'url' attribute is not specified ... (no profiles are currently active)`
>
> **原因**：Spring Boot 的加载规则是——`application.yml` **总是**加载；`application-{profile}.yml` **只在对应 profile 激活时**才加载。
>
> **修法用 `default` 而不是 `active`**：
>
> | 写法 | 行为 | 风险 |
> |---|---|---|
> | `profiles.active: dev` | **强制**用 dev，生产设了 `SPRING_PROFILES_ACTIVE=prod` 也会被覆盖 | ⚠️ 「把开发配置带上生产」的事故源 |
> | `profiles.default: dev` | **兜底**——没人指定时用 dev；生产设了 prod 就用 prod | ✅ 正确做法 |

> ⚠️ **坑 4：密码写进了会被提交的文件**
>
> 修改密码时同时改了 `application-dev.yml`（已 gitignore，正确 ✓）和 `application-dev.yml.example`（**会被提交到公开仓库** ✗）。已恢复为占位符。
>
> 另发现 example 里写成 `password:<值>` 的**冒号后缺空格**形式。YAML 要求映射的冒号后必须有空格，否则整行被当成一个键名，配置读不到。
>
> **教训**：改配置前先 `git check-ignore <文件>` 确认它是否会被提交。

### 两个文件的分工

| | `application-dev.yml` | `application-dev.yml.example` |
|---|---|---|
| **谁读它** | Spring Boot 启动时加载 | 只有人会打开看 |
| **内容** | 真实密码 | 占位符 |
| **进 git 吗** | ❌ 被忽略 | ✅ **会提交** |
| **用途** | 本机跑起来 | 告诉别人「该建哪些配置项」 |

别人克隆项目后：`cp application-dev.yml.example application-dev.yml`，然后填自己的密码。

---

## 步骤 1.3 —— 统一响应与全局异常处理 ✅

**日期**：2026-09-17

### 做了什么

```
common/
├── Result.java                  统一响应体（不可变，静态工厂创建）
├── ErrorCode.java               错误码枚举，10 个模块段位
├── BizException.java            业务异常
└── GlobalExceptionHandler.java  全局异常处理器
system/
└── SystemController.java        健康检查
```

### 验证结果

| 场景 | HTTP | 响应体 |
|---|---|---|
| 正常 | 200 | `{"code":0,"message":"成功","data":{...}}` |
| 业务异常 | **409** | `{"code":20001,"message":"该邮箱已被注册"}` |
| 业务异常 + 自定义文案 | **400** | `{"code":60002,"message":"体重 500kg 超出合理范围（20–300kg）"}` |
| 未预期异常 | **500** | `{"code":10005,"message":"系统繁忙，请稍后重试"}`（无堆栈） |
| 路径不存在 | 404 | `{"code":10003,...}` |
| 方法不支持 | 405 | `{"code":10004,...}` |

日志级别实测：

```
WARN  业务异常 | code=20001 | message=该邮箱已被注册
      → 后续堆栈帧数 = 0

ERROR 未预期的异常 | GET /api/v1/system/demo/system-error
      java.lang.IllegalStateException: ...
        at com.gymlog.system.SystemController.demoSystemError(SystemController.java:65)
      → 堆栈直接定位到出问题的行
```

### 三个设计决策

**① 错误码分段编号**，留好空位：

```
0        成功
1xxxx    通用      10001 未登录 · 10002 无权限 · 10005 系统错误
2xxxx    用户      20001 邮箱已注册 · 20003 密码错误
3xxxx    动作库    30001 动作不存在
4xxxx    计划      40002 计划已开始不能改
5xxxx    训练会话
6xxxx    身体数据  60002 数值超范围
7xxxx    饮食
8xxxx    管理后台
9xxxx    外部服务  90001 AI 不可用
```

**为什么不用 HTTP 状态码当业务码**：HTTP 状态码只有几十个且语义固定。业务错误有几十上百种——「邮箱已注册」「计划已过期」「组数超上限」都塞进 400，前端就没法区分该给用户看哪句提示。

**② 错误码同时携带 HTTP 状态**：不能一律返回 200。从运维角度，一个「HTTP 200 但 body 里写着系统错误」的响应是灾难——**网关、监控告警、CDN 缓存全部失效**。

**③ 业务异常 WARN 不打栈，系统异常 ERROR 打全栈**：业务失败是设计内的分支（「邮箱已注册」在用户手滑时天天发生），打全栈会刷爆日志且无排查价值。

### 踩坑

> 🔴 **坑 5：中文日志乱码**
>
> 现象：日志里 `业务异常` 显示成 `ҵ���т쳣`。
>
> 根因：**Java 18 之前 `file.encoding` 跟随操作系统**，中文 Windows 上是 `GBK`；而现代终端按 UTF-8 解码。
>
> 修法：
> - `application.yml` → `logging.charset.console/file: UTF-8`
> - `pom.xml` → `maven-surefire-plugin` 加 `<argLine>-Dfile.encoding=UTF-8</argLine>`
>
> 只设 console 不够——日志同时写文件时，文件也需要 UTF-8，否则用编辑器打开同样乱码。

---

## 步骤 1.4 —— 用户实体与 Mapper ✅

**日期**：2026-09-17

### 做了什么

```
user/
├── User.java                    实体，映射 user 表
└── UserMapper.java              继承 BaseMapper
config/
└── MybatisPlusConfig.java       分页插件
test/
└── UserMapperTest.java          集成测试
```

### 验证结果

测试通过（`Tests run: 1, Failures: 0`），SQL 日志同时验证了三个机制：

```sql
-- ① 插入只包含非 null 字段，其余交给数据库 DEFAULT
INSERT INTO user ( email, password_hash, nickname, gender, birth_year, height_cm, goal, experience )
VALUES ( ?,?,?,?,?,?,?,? )

-- ② 逻辑删除自动生效 —— 没写任何相关代码，AND deleted=0 是自动加的
SELECT id,email,...,deleted FROM user WHERE id=? AND deleted=0

-- ③ 数据库默认值经 ORM 往返正确
Row: 1, ..., 1, 1, 1995, 175.5, MUSCLE_GAIN, INTERMEDIATE, kg, local, null, 0, ...
                ↑ status=1      ↑ unit_pref=kg      ↑ provider=local
```

### 关键设计

**逻辑删除是「配置一次、全局生效」的**。在 `application.yml` 里声明 `logic-delete-field: deleted` 之后，所有 `selectById` / `selectList` 都会自动带上 `deleted=0`，不需要每处手写。

**两个安全防护**（写在 `User` 实体上）：

| 字段 | 防护 | 防的是什么 |
|---|---|---|
| `passwordHash` | `@JsonIgnore` | 接口直接返回实体时，密码哈希泄露给前端 |
| `passwordHash` | `@ToString.Exclude` | `log.info("user={}", user)` 把哈希写进日志 |

第二条容易被忽略——**日志往往比数据库更容易被看到**（运维、日志平台、误提交的日志文件）。BCrypt 哈希可以离线暴力破解，不受登录接口限流约束。

### 踩坑

> 🔴 **坑 6：MyBatis-Plus 3.5.9 起分页插件被拆到独立模块**
>
> 只引 `mybatis-plus-spring-boot3-starter` 时，`PaginationInnerInterceptor`
> **编译期就报 cannot be resolved**——`mybatis-plus-extension` 里已经没有这个类了。
>
> 原因：分页插件依赖 **JSqlParser**（不小的第三方 SQL 解析库），而多数项目只用基础 CRUD。3.5.9 把它拆成独立模块。
>
> 修法：额外引入 `com.baomidou:mybatis-plus-jsqlparser`（版本与 starter 对齐）。

---

## 步骤 1.5 —— 注册接口 ✅

**日期**：2026-09-17

### 做了什么

```
user/
├── AuthController.java          POST /api/v1/auth/register
├── UserService.java             业务逻辑
└── dto/RegisterRequest.java     请求参数 + 校验注解
config/
└── PasswordConfig.java          BCryptPasswordEncoder
```

### 验证结果

| 场景 | HTTP | 响应 |
|---|---|---|
| 正常注册 | 200 | `{"code":0,"data":6}` |
| 重复邮箱 | **409** | `{"code":20001,"message":"该邮箱已被注册"}` |
| **大写邮箱 `ALICE@Example.COM`** | **409** | 同样判为重复 —— 归一化生效 |
| 邮箱格式错误 | 400 | `"邮箱格式不正确"` |
| 密码太短（3 位） | 400 | `"密码长度需在 8-32 位之间"` |
| 昵称为空 | 400 | `"昵称不能为空"` |

数据库侧：

```
email = alice@example.com      ← 已转小写
password_hash = $2a$10$...     ← BCrypt cost 10，长度 60
库中无明文密码 ✓
```

### 核心设计：邮箱唯一性的两层防护

```java
// 第一层：应用层查重 —— 为了友好提示，覆盖 99% 的情况
if (existsByEmail(email)) throw new BizException(EMAIL_ALREADY_EXISTS);

try {
    userMapper.insert(user);
} catch (DuplicateKeyException e) {
    // 第二层：数据库唯一索引 —— 真正的保证
    throw new BizException(EMAIL_ALREADY_EXISTS);
}
```

```
T1: 请求A 查邮箱 → 不存在
T2: 请求B 查邮箱 → 不存在     ← 两个请求都通过了检查
T3: 请求A 插入 → 成功
T4: 请求B 插入 → 唯一索引冲突 ✗
```

**应用层的「先查后插」永远无法保证唯一性**——这不是代码写得不严谨，是并发场景的固有性质。**真正的保证只能来自数据库唯一索引。**

### 另外两个决策

**只引 `spring-security-crypto`，不引完整 starter**：密码加密只需要 `BCryptPasswordEncoder`（零依赖小包）。完整 starter 一旦引入，Spring Security **默认锁住所有接口**并生成随机密码。

**Service 不写接口**：国内常见 `UserService` + `UserServiceImpl`。本项目不这么做——只有一个实现时接口是纯负担。Spring 官方团队近年也明确建议不要写没有必要的接口。

### BCrypt 原理

| | MD5 / SHA-256 | BCrypt |
|---|---|---|
| 速度 | 极快（GPU 每秒数十亿次） | **故意设计得慢** |
| 盐 | 需手动管理 | **内置**，存在哈希串里 |
| 抗暴力破解 | 弱 | 强 |

哈希串形如：

```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 │  │  └── 22 字符盐 + 31 字符哈希
 │  └───── cost = 10（迭代 2^10 = 1024 次）
 └──────── 算法版本
```

**cost 存在哈希串自身里**，所以调高 cost 不影响已有密码——老密码用老 cost 验证，新密码用新 cost 生成，可平滑升级。

### 已知限制

参数校验失败时**只返回第一条错误**。实测「全空」请求返回「密码长度需在 8-32 位之间」而非「邮箱不能为空」——字段错误顺序不保证。

这是有意取舍（移动端 Toast 一次给一条更清晰）。

---

## 步骤 1.6 —— 登录接口与 JWT 签发 ✅

**日期**：2026-09-17

### 做了什么

```
common/
├── JwtProperties.java      配置绑定（含密钥）
└── JwtService.java         签发与解析
user/
├── dto/LoginRequest.java
├── dto/LoginResponse.java  record + 嵌套 record
├── UserService.java        +login()
└── AuthController.java     +POST /auth/login
```

### 验证结果

| 场景 | HTTP | 响应 |
|---|---|---|
| 正常登录 | 200 | 含 `accessToken` / `tokenType: Bearer` / `expiresIn: 3600` / user |
| 密码错误 | 401 | `{"code":20003,"message":"邮箱或密码不正确"}` |
| **邮箱不存在** | 401 | **与上面完全一致** —— 无账号枚举漏洞 |
| 禁用账号 + 正确密码 | **403** | `{"code":20004,"message":"账号已被禁用，请联系管理员"}` |
| 禁用账号 + 错误密码 | 401 | `20003` —— **不泄露禁用状态** |
| 参数校验 | 400 | 邮箱格式 / 密码为空 |

### JWT 实际内容

```
Header : {"alg":"HS512"}     ← jjwt 按密钥长度（512 位）自动选的，比预期的 HS256 更强
Payload: {"sub":"6","email":"alice@example.com","iat":1789628429,"exp":1789632029}
         exp − iat = 3600 秒，与配置的 1h 一致 ✓
Signature: zW7OzBAOXXhMiLZQanDv... (86 字符)
```

**⚠️ Payload 只是 Base64 编码，不是加密。** 上面那行 payload 用 `base64 -d` 就能解出来，任何人都能做。它的安全性来自「**签名不可伪造**」，不是「内容不可读」。

所以**只能放「泄露了也不致命」的信息**——绝不能放密码、手机号、身份证号。

### 三个安全设计（本步最值钱的部分）

**① 账号枚举防护**

密码错误和邮箱不存在返回**完全一样**的响应。如果分成两种提示，等于**直接告诉攻击者哪些邮箱注册过**——这是撞库的第一步。

**② 时序攻击防护（实测有效）**

| | 未防护 | 已防护（实测） |
|---|---|---|
| 邮箱存在 + 密码错 | ~80ms（跑了 BCrypt） | **60 ms** |
| 邮箱不存在 | ~1ms（直接返回） | **59 ms** |

攻击者不需要看报错内容，**只看响应时间**就能批量判断邮箱是否注册过。修法是用户不存在时也拿假哈希跑一次 BCrypt。实测差值 **1ms**，信号已消除。

**③ 检查顺序：密码校验在账号状态检查之前**

```
禁用账号 + 正确密码 → 403 告诉你账号被禁用
禁用账号 + 错误密码 → 401 只说邮箱或密码不正确
```

如果反过来先查状态，攻击者就能靠「返回禁用还是密码错」来枚举账号。**只有密码正确的人，才配知道这个账号被禁用了。**

### 密钥管理

| 项 | 做法 |
|---|---|
| 存放 | `application-dev.yml`（已 gitignore），生产用环境变量 `JWT_SECRET` 覆盖 |
| 生成 | `head -c 64 /dev/urandom \| base64` → 64 字节（512 位） |
| 校验 | 构造器里检查非空 + 长度 ≥ 32 字节，**不合法则启动直接失败** |
| 存储格式 | Base64——避免密钥含换行/控制字符导致 YAML 解析异常 |

**为什么在构造器里校验而不是用时再检查**：配置错误应该**启动时立刻失败**，而不是等第一个用户登录才炸。这是「**快速失败**」原则——暴露得越早，排查成本越低。

### 踩坑

> ⚠️ **坑 8：Windows 命令行传中文参数会损坏**
>
> 现象：`curl -d '{"nickname":"待禁用"}'` 返回 `{"code":10006,"message":"请求格式有误"}`，换成 `-d @body.json`（从文件读）就成功。
>
> 原因：**Windows 命令行参数走 ANSI 代码页（GBK）**，Git Bash 传出的 UTF-8 字节在传给 curl 时被转换坏，导致 JSON 解析失败。
>
> **这不是接口 bug**——库里的昵称十六进制是 `E5BE85E7A681`，中文完全正确。
>
> **教训**：测试含中文的接口时用 `-d @文件`；排查编码问题**看字节**（`HEX()` / `xxd`），不要被终端的显示误导。

---

## 步骤 1.7 —— JwtAuthenticationFilter ✅

**日期**：2026-09-17

### 做了什么

```
security/
└── JwtAuthenticationFilter.java   从 Header 取 token，识别当前用户
config/
└── SecurityConfig.java            安全过滤器链（本步为过渡配置）
pom.xml                             +spring-boot-starter-security
```

### 验证结果

用临时接口 `/api/v1/system/whoami` 读取 `SecurityContextHolder` 的内容（验证后已删除）：

| 用例 | principal | loggedIn | principalType |
|---|---|---|---|
| 不带 token | `anonymousUser` | false | String |
| **带合法 token** | **`6`** | **true** | **Long** |
| 篡改签名（中间字符） | `anonymousUser` | false | String |
| **篡改 payload 冒充用户1** | `anonymousUser` | false | String |
| 格式非法 `not-a-jwt` | `anonymousUser` | false | String |
| 缺 `Bearer ` 前缀 | `anonymousUser` | false | String |

同时确认注册/登录接口**没有被 Security 锁住**（仍是 200）。

### 最关键的验证：篡改 payload 冒充他人

```
原 payload : {"sub":"6","email":"alice@example.com","iat":...,"exp":...}
改后       : {"sub":"1","email":"alice@example.com","iat":...,"exp":...}
             ↑ 把 sub 从 6 改成 1，想冒充用户 1；签名保持原样

结果：验签失败 → anonymousUser
```

**这就是 JWT 签名防的东西。** Payload 谁都能解开看（`base64 -d`），
但**改了 payload 就算不出对应的签名**——攻击者没有密钥。

一句话总结：**Payload 可读但不可改。**

### 三个设计决策

**① 过滤器不抛异常，只「不认证」**

常见错误写法是「token 无效就返回 401」。但这会导致：

```
用户 token 过期 → 访问一个【公开接口】（如健康检查）
→ 过滤器抛 401 → 公开接口也访问不了
```

正确做法：**token 无效就什么都不做**，继续放行，由授权层决定
「未认证的请求能不能访问这个路径」。

**② 过滤器不用 `@Component`**

Spring Boot 会把容器里**所有 `Filter` 类型的 Bean** 自动注册到 Servlet 过滤器链上。
如果标了 `@Component`：

```
① Spring Boot 自动注册 → 在 Security 链【外】执行一次
② addFilterBefore 加进 Security 链 → 在链【内】再执行一次
→ 每个请求解析两遍 JWT，日志打两遍，调试时极度困惑
```

本项目的做法：**不标 `@Component`**，在 `SecurityConfig` 里手动 `new`。

**③ 关闭 CSRF 的前提**

CSRF 攻击依赖「浏览器自动带上 Cookie」。本项目凭据是 `Authorization` 头里的 token，
**不是 Cookie**，浏览器不会自动附带，跨域请求也会被拦截。

> ⚠️ **但如果将来改成用 Cookie 存 token，必须把 CSRF 打开**，否则就真的暴露了。

### 关键概念

**`SecurityContextHolder` 底层是 `ThreadLocal`**

所以放进去的认证信息**只在当前请求线程内可见**，请求结束会被清理。
这也是「无状态」安全的原因：下一个请求是**另一个线程**，拿不到上一个请求的认证信息，
**必须重新带 token**。

**`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` 的位置讲究**

认证类过滤器必须在授权判断之前执行，否则授权时拿不到认证信息，
所有请求都会被判为「未认证」。

### 踩坑

> ⚠️ **坑 9：`isAuthenticated()` 无法判断「是否已登录」**
>
> 用 `auth.isAuthenticated()` 判断登录状态，**匿名用户也会返回 `true`**。
>
> 原因：Spring Security 会给未认证的请求塞一个 `AnonymousAuthenticationToken`，
> 它的 `isAuthenticated()` 语义是「已被识别为匿名用户」，**不是「已登录」**。
>
> 正确判断：
> ```java
> boolean isAnonymous = auth == null || auth instanceof AnonymousAuthenticationToken;
> ```
>
> 好在授权配置里的 `.authenticated()` 已经正确处理了匿名情况，
> 但**自己写判断时很容易踩**。

> ℹ️ **测试方法本身的坑：Base64 填充位**
>
> 第一次测「篡改签名」时只改了签名的**最后一个字符**，结果**验签通过了**，
> 一度以为是过滤器有 bug。
>
> 原因：签名是 86 个 Base64 字符 = 516 位，而 HMAC-SHA512 只有 512 位——
> **最后一个字符有 4 位是填充位，改它不影响解码后的字节**。
>
> 改中间的字符才是有效篡改。
>
> **教训**：验证「篡改检测」时，要确保篡改真的改变了数据。

---

## 步骤 1.8 —— SecurityFilterChain 配置 ✅

**日期**：2026-09-17

### 做了什么

```
security/
├── RestAuthenticationEntryPoint.java   401 响应处理器
└── RestAccessDeniedHandler.java        403 响应处理器
config/
└── SecurityConfig.java                 收紧授权规则（原为 permitAll）
user/
├── UserController.java                 GET /api/v1/users/me（第一个受保护接口）
└── dto/UserProfileResponse.java
```

### 验证结果

| 路径 | 条件 | HTTP | 响应 |
|---|---|---|---|
| `/system/ping` | 无 token | 200 | — |
| `/users/me` | 无 token | **401** | `{"code":10001,"message":"未登录或登录已过期"}` |
| `/users/me` | 篡改 token | **401** | 同上 |
| `/users/me` | 合法 token | **200** | 用户资料 |
| `/admin/users` | **无 token** | **401** | 同上 |
| `/admin/users` | **已登录但非管理员** | **403** | `{"code":10002,"message":"没有权限执行此操作"}` |

**敏感字段泄漏检查**：`passwordHash` / `deleted` / `providerUserId` 均未出现在响应中 ✓

### 最容易搞错的一点：匿名用户访问管理端返回 401 而不是 403

```
未登录   + 访问 /admin/**  →  401   「你是谁？」
已登录   + 访问 /admin/**  →  403   「知道你是谁，但你不够格」
```

**为什么匿名时应返回 401 而不是 403**：401 意味着「去认证可能就能通过」——
客户端应该跳登录页。如果返回 403，客户端会提示「无权限」，
但用户其实只是**没登录**，跳一下登录页就好了。

这是 Spring Security 的 `ExceptionTranslationFilter` 内置的正确行为：
- 匿名用户权限不足 → 交给 `AuthenticationEntryPoint`（401）
- 已认证用户权限不足 → 交给 `AccessDeniedHandler`（403）

### 关键设计

**公开路径列表越短越安全**

每加一条都是一次有意识的决定。加之前先问：**未登录的人访问它，会造成什么后果？**

```java
private static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/system/ping",     // 负载均衡器探活，要求认证会被误判为不可用
    "/error",                  // ⚠️ 见下方踩坑
};
```

**`anyRequest().authenticated()` 是安全默认值**

新增接口时如果忘了配规则，默认是「需要登录」而不是「公开」。
**宁可多拦，不可漏放。**

**授权规则从上到下匹配，先匹配到的生效**

```java
.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // CORS 预检必须放行
.requestMatchers(PUBLIC_PATHS).permitAll()
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")     // Phase 6 才真正生效
.anyRequest().authenticated()                              // 兜底
```

**CORS 预检为什么必须放行**：浏览器发预检（OPTIONS）时**不带 Authorization 头**——
这是 CORS 规范规定的。如果预检也要求认证，跨域请求永远无法成功。

**`/users/me` 的结构性越权免疫**

接口不接受 `id` 参数，`userId` **完全来自 token**。所以结构上不存在
「用 A 的 token 拿 B 的数据」的可能。

> 对比：如果接口设计成 `/users/{id}`，就必须显式校验
> `id` 是否等于当前登录用户——**这是越权漏洞最常见的形式**。

### 踩坑

> ⚠️ **坑 10：`/error` 不放行会导致错误响应被 401 覆盖**
>
> Spring Boot 处理异常时会把请求**转发到 `/error`**。如果这个路径不在公开列表里，
> 它会被 Security 拦截，客户端拿到的是 401 而不是真实的错误码。
>
> **症状**：某个接口本该返回 400 参数错误，实际却返回 401「未登录」——
> 排查时会被严重误导（以为是认证问题，实际是参数问题）。
>
> **排查这类问题的思路**：如果一个接口的响应码「不对劲」，
> 先确认它到底有没有进到你的 Controller——看日志里有没有对应的业务日志。

---

## 步骤 1.9 —— Refresh Token 与登出 ✅

**日期**：2026-09-17

### 做了什么

```
db/migration/
└── V2__init_refresh_token.sql          refresh_token 表
user/
├── RefreshToken.java                   实体
├── RefreshTokenMapper.java
├── RefreshTokenService.java            签发 / 校验 / 撤销
└── dto/
    ├── RefreshRequest.java
    └── TokenResponse.java
```

### 验证结果

| 步骤 | 结果 |
|---|---|
| 登录 | 返回 accessToken（JWT）+ refreshToken（43 字符 Base64URL） |
| **库里存的是哈希** | `token_hash` 64 字符 SHA-256；**原文不在数据库中** ✓ |
| 刷新 | 新的 accessToken 与 refreshToken **都与旧的不同**（轮换生效） |
| **用旧的 refresh token 再刷** | **401** `{"code":20007,"message":"刷新凭证无效，请重新登录"}` ✓ |
| 登出 | 200 |
| **登出后再刷** | **401** `20007` ✓ |
| 库中状态 | 两条记录均 `revoked=1`、`revoked_at` 已填 ✓ |

### 核心问题：为什么 refresh token 不用 JWT

JWT 的特点是**无状态**——服务端不存任何东西。这既是优点也是致命缺点：

```
用户手机丢了，想远程登出
→ JWT 是无状态的，服务端根本没有「这个 token 还有效」的记录
→ 无法让它提前失效
→ 只能干等它自然过期（30 天）
```

**refresh token 必须可主动撤销，所以不能是纯 JWT，必须有服务端记录。**

### 双 token 的分工

| | access token | refresh token |
|---|---|---|
| 形式 | JWT（自包含） | **随机字符串（存库）** |
| 有效期 | 1 小时 | 30 天 |
| 传输频率 | **每个请求** | 只在刷新时 |
| 能否撤销 | 不能（等过期） | **能** |
| 校验成本 | 验签，无 IO | 查一次库 |

### 为什么用 SHA-256 而不是 BCrypt 存 token

这是个容易被问到的点——密码用了 BCrypt，token 为什么不用？

| | 密码 | refresh token |
|---|---|---|
| 来源 | 用户自己选 | **SecureRandom 生成** |
| 熵 | 低（`password123` 约 30 位） | **256 位** |
| 是否可猜 | 可以（字典攻击） | **不可能** |
| 需要的哈希 | **慢哈希**（BCrypt） | 快哈希即可（SHA-256） |

**核心逻辑**：BCrypt「故意慢」是为了对抗**暴力枚举**。但 256 位熵的空间根本枚举不了——
慢哈希没有收益，只会让每次刷新白白多花 80ms。

哈希的目的在这里不是「防猜测」，而是「**防数据库泄露后直接可用**」。

### 令牌轮换（Token Rotation）

每次刷新时，旧 refresh token 立即作废，签发全新的。

**不轮换的问题**：一个 refresh token 在 30 天里可以被反复使用，一旦泄露就是 30 天的持续访问权。
**轮换后**：泄露的 token 最多只能用一次。

轮换还带来**重放检测**的能力：

```
正常：客户端持 RT-1 → 刷新 → 拿到 RT-2，RT-1 作废
攻击：攻击者偷到 RT-1 并使用 → 拿到 RT-3
      真用户下次用 RT-1 刷新 → 发现它已作废
      → 说明 RT-1 被泄露过！→ 应撤销该用户全部令牌
```

> V1 只做基础轮换，「检测到已撤销 token 被使用时撤销全部」留到 Phase 6 安全加固。

### 已知取舍：登出后 access token 仍有残留有效期

登出撤销的是 **refresh token**，不是 access token。
access token 是无状态 JWT，服务端**无法**让它提前失效。

**登出后它仍会有效直到自然过期（最长 1 小时）。**

要彻底解决只能引入黑名单（存已撤销的 access token 直到过期），
但那等于放弃无状态优势——每个请求都要查黑名单，还不如直接用有状态 session。

1 小时的窗口是业界普遍接受的做法。要更短就调小 `jwt.access-token-ttl`，代价是刷新更频繁。

### 三个设计决策

**① 登出接口必须公开**

如果要求带 access token，那么 **access token 过期的用户就无法登出**了——
而对用户来说，「登出」在任何时候都应该能做。安全性由请求体里的 refresh token 本身保证。

**② 刷新时要重新查用户状态**

不能想当然认为「token 有效 = 用户可用」。用户可能在持有有效 refresh token 期间
被管理员禁用了。检测到禁用时**撤销其全部 token**。

**③ 登出是幂等的**

token 已经无效时不报错。用户点两次登出、或 token 刚好过期，都不该看到错误提示。

### 踩坑

> ℹ️ **`X-Forwarded-For` 是客户端可伪造的**
>
> 服务部署在 Nginx 后面时，`getRemoteAddr()` 拿到的是**代理服务器**的 IP。
> 真实 IP 在 `X-Forwarded-For` 头里，格式 `客户端IP, 代理1IP, 代理2IP`——**第一个才是真实客户端**。
>
> **但它可以被客户端随意伪造**。所以：
> - 只能用于**记录和审计**，绝不能用于权限判断或限流
> - 生产环境应在 Nginx 层用 `proxy_set_header` 覆盖，而不是信任客户端传来的值

---

## 步骤 1.10 —— RBAC 三张表 ✅

**日期**：2026-09-17

### 做了什么

```
db/migration/V3__init_rbac.sql    4 张表 + 种子数据
user/
├── Role.java / RoleMapper.java
└── UserRole.java / UserRoleMapper.java
```

**只建表结构，不做权限校验**——完整的 `@PreAuthorize` 方法级鉴权在 Phase 6。

### 验证结果

| 检查项 | 结果 |
|---|---|
| V3 迁移 | `version=3, description=init rbac, success=1` |
| 表 | 7 张（新增 `role` / `permission` / `user_role` / `role_permission`） |
| 角色 | `USER` / `ADMIN` 两个 |
| 权限 | **14 个**，覆盖 5 个资源 |
| ADMIN 权限数 | **14**（全部） |
| USER 权限数 | **0**（设计如此，见下） |
| 注册时绑定角色 | 新用户自动获得 `USER` ✓ |
| 中文入库 | `HEX(name)` = `E699AEE9809AE794A8E688B7` = 「普通用户」✓ |

### 为什么不用 `user` 表加 `is_admin` 布尔字段

| | `is_admin` | RBAC 三表 |
|---|---|---|
| 加角色 | **改表结构** | 插一行数据 |
| 权限粒度 | 只有「是/否管理员」 | 到「资源+操作」 |
| 审计 | 说不清「谁能删用户」 | `role_permission` 表就是答案 |
| 面试 | 「加了个字段」 | 能展开讲角色继承、权限缓存、越权防护 |

**实现成本差不多，面试价值差很多。**

而且 `is_admin` 有个硬伤：**角色与行为耦合**。将来加「教练」角色——
能看学员数据但不能删用户——布尔字段表达不了，只能再加字段，越加越乱。

### 为什么 USER 角色不授予任何权限

普通用户的所有操作都是「操作自己的数据」。这类权限用
**「是否登录」+「数据归属校验（`WHERE user_id = ?`）」**就够了。

把「自己的数据」也做成权限项会导致：
1. 每个新用户注册都要插几十条授权记录
2. 权限表膨胀到 **用户数 × 权限数**

**`permission` 表只为管理端的跨用户操作服务。**

### 权限命名约定：`资源:操作`

```
exercise:read / create / update / delete
template:read / create / update / delete
user:read / update / disable / delete
audit:read
dashboard:read
```

**注意 read 和 delete 是分开的**——将来可能有人需要「能看但不能删」，
权限拆细了才能这么配。

### 为什么现在就建表（而不是等 Phase 6）

Phase 1 注册用户时就要**绑定默认角色**。如果等到 Phase 6 才建，
存量用户全都没有角色，还得写数据修补脚本。

**本项目实际就遇到了这个问题**：`alice@example.com` 是在 V3 之前注册的，
建表后没有角色，需要手动补一条：

```sql
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM user u CROSS JOIN role r
WHERE r.code = 'USER'
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = u.id);
```

> **这就是「表结构要早建」的实证**——早建表的代价是多想一步，
> 晚建表的代价是数据修补脚本 + 停机窗口。

### 踩坑

> ℹ️ **MySQL 的 `DELETE ... JOIN` 需要先选库**
>
> ```sql
> -- ✗ 报 ERROR 1046 (3D000): No database selected
> DELETE ur FROM gym_log.user_role ur JOIN gym_log.user u ON ...
>
> -- ✓ 加 -D 指定默认库，或先 USE
> mysql --login-path=gymlog -D gym_log -e "DELETE ur FROM user_role ur JOIN user u ON ..."
> ```
>
> 即使表名已经写了库前缀，多表 DELETE 的别名解析仍需要默认库。
> 单表 DELETE 不受影响。

---

## 步骤 1.11 —— Swagger / OpenAPI ✅

**日期**：2026-09-17

### 做了什么

```
config/OpenApiConfig.java     文档元信息 + JWT 认证方案
config/SecurityConfig.java    放行 Swagger 路径
pom.xml                       +springdoc-openapi-starter-webmvc-ui 2.9.1
```

### 验证结果

| 检查项 | 结果 |
|---|---|
| `/swagger-ui.html` | 302 重定向到 `/swagger-ui/index.html` |
| `/swagger-ui/index.html` | 200 |
| `/v3/api-docs` | 5202 字节 JSON |
| 自动发现的接口 | **6 个**（4 个 auth + ping + users/me） |
| 安全方案 | `bearerAuth` → `type=http scheme=bearer format=JWT` |
| 全局安全要求 | 已应用 |

### 为什么不选 springfox

| | springfox | springdoc |
|---|---|---|
| 维护状态 | **2020 年后基本停更** | 活跃维护 |
| Spring Boot 3 | **不支持** | 支持 |
| 规范 | Swagger 2 | **OpenAPI 3** |

**springfox 在 Spring Boot 3 上根本跑不起来**——它依赖的 `javax.*` 包已经换成了
`jakarta.*`。这是选型时必须先查的：**库的维护状态和框架版本兼容性，
比它的功能列表重要得多。**

### 版本陷阱（第三次遇到）

Maven Central 上 springdoc 最新是 **3.1.1**，但那是给 **Spring Boot 4** 的。
本项目 Boot 3.5.3 对应 **2.x** 线。

用错版本的典型症状：启动时一堆 `NoClassDefFoundError`，
或者 Swagger 页面 404 但没有任何报错。

### 一个必要的安全提醒

Swagger 路径**只在开发环境应该放行**。生产环境把接口文档暴露给公网，
等于给攻击者一份**完整的攻击面清单**——有哪些接口、什么参数、什么返回结构。

V1 暂时放行，Phase 8 部署时会改成「仅开发环境生效」。

---

## 步骤 1.12 —— Phase 1 总验收 ✅

**日期**：2026-09-17

### 验收结果

| # | 验收项 | 结果 |
|---|---|---|
| ① | 注册 | 200 |
| ② | 登录拿 token | 200，返回双 token |
| ③ | 带 token 访问受保护接口 | 200，返回资料 |
| ④ | 不带 token | **401** `{"code":10001}` |
| ⑤ | 篡改 token | **401** `{"code":10001}` |
| ⑥ | 刷新令牌 | 200，新 token 可用 |
| ⑦ | 登出后 refresh 失效 | **401** `{"code":20007}` |
| ⑧ | `?id=1` 越权尝试 | **参数被忽略**，仍返回自己的数据 |

### 额外补验：token 真实过期路径

1 小时的有效期没法等，所以用 `--jwt.access-token-ttl=5s` 重启验证：

```
TTL 设为 5 秒         → expiresIn = 5
立即访问              → HTTP 200
等 8 秒后访问         → HTTP 401 {"code":10001,"message":"未登录或登录已过期"}
用 refresh 换新 token → HTTP 200
```

**这一次证明的是 jjwt 真的在校验 `exp` 字段**，而不只是把它写进了 payload——
两者的区别，正是「配置看起来对」和「行为确实对」的区别。

---

## Phase 1 完成总结

### 交付物

| 类别 | 内容 |
|---|---|
| **数据库** | 7 张表（`user` / `refresh_token` / `role` / `permission` / `user_role` / `role_permission` / `flyway_schema_history`） |
| **迁移脚本** | V1（用户）、V2（刷新令牌）、V3（RBAC） |
| **接口** | 6 个：注册、登录、刷新、登出、健康检查、当前用户资料 |
| **安全机制** | JWT 认证、refresh token 轮换与撤销、RBAC 表结构、统一 401/403 响应 |
| **文档** | Swagger UI 自动生成 |

### 代码规模

```
33 个 Java 文件（main）
+ 1 个测试类
```

### 学到的东西（按面试价值排序）

1. **并发下的唯一性保证**——应用层「先查后插」永远不可靠，必须靠数据库唯一索引兜底
2. **账号枚举与时序攻击防护**——错误信息要一致，耗时要接近
3. **双 token 设计**——为什么 refresh token 不能是 JWT（要能撤销）
4. **SHA-256 vs BCrypt 的选择依据**——取决于输入的熵，不是「越慢越好」
5. **RBAC 三层模型**——为什么不在 user 表加 `is_admin`
6. **401 vs 403 的语义区别**——以及匿名用户该返回哪个
7. **JWT 签名的价值**——payload 可读但不可改

### 踩过的坑（11 个）

前 8 个见前面的记录，本轮新增：

| # | 坑 | 教训 |
|---|---|---|
| 9 | `isAuthenticated()` 对匿名用户也返回 true | 判断登录要看是不是 `AnonymousAuthenticationToken` |
| 10 | `/error` 不放行会让错误响应被 401 覆盖 | 响应码「不对劲」时先确认请求有没有进 Controller |
| 11 | MySQL 多表 `DELETE ... JOIN` 需要先选库 | 加 `-D 库名` 或先 `USE` |

> **Windows 中文环境的编码问题占了 4 个坑**（源码、日志、命令行参数、SQL 参数），
> 且每次表现形式都不同。这一条经验本身就值回票价。

---

## 步骤 2.1 —— 动作库表设计 ✅

**日期**：2026-09-17

### 做了什么

```
db/migration/V4__init_exercise.sql     exercise 表（18 字段 + 6 索引）
exercise/
├── MetricType.java            计量类型枚举
├── MuscleGroup.java           肌群枚举
├── Equipment.java             器械枚举
├── MovementPattern.java       动作模式枚举
├── Exercise.java              实体
└── ExerciseMapper.java
test/ExerciseMapperTest.java   3 个测试
```

### 验证结果

| 检查项 | 结果 |
|---|---|
| V4 迁移 | `success=1`，44ms |
| 表总数 | 8 张 |
| `exercise` 字段 | 18 个，类型/默认值正确 |
| 索引 | PRIMARY + `uk_exercise_user_name`（唯一）+ 4 个查询索引 |
| **枚举往返** | `MuscleGroup` / `Equipment` / `MovementPattern` / `MetricType` **全部正确** |
| BigDecimal 往返 | `bw_factor` 的 `1.00` 正确读回 |
| 中文往返 | 「杠铃卧推」无损 |

### 核心设计一：内置 vs 自定义动作的区分（有个陷阱）

自然的想法是 `user_id` 为 NULL 表示内置。**但这样唯一约束会失效**：

```sql
UNIQUE KEY (user_id, name)
-- 内置动作 user_id 都是 NULL
-- SQL 标准规定：NULL 不等于 NULL，唯一索引里多个 NULL 视为互不相同
-- → 可以插入两个都叫「卧推」的内置动作，约束形同虚设
```

**解法：内置动作用 `user_id = 0`**（哨兵值，真实自增 id 从 1 开始）。

> **这个坑的危险之处在于：看代码觉得约束在，实际上没起作用。**
> 它不会报错、不会警告，只会在某天发现数据库里有重复数据。

### 核心设计二：三个正交的分类维度

| 维度 | 回答的问题 |
|---|---|
| `primary_muscle` | 今天练胸有哪些动作？ |
| `equipment` | 酒店只有哑铃能练什么？ |
| `movement_pattern` | 深蹲架被占了，有什么替代动作？ |

一个「分类」字段表达不了这三个问题。特别是 **`movement_pattern`
是找替代动作的关键**——按肌群找会推荐腿举（固定器械），
按动作模式找会推荐高脚杯深蹲（同样的运动模式，训练效果更接近）。

### 核心设计三：计量类型决定「怎么记」

```
WEIGHT_REPS       重量 × 次数     杠铃卧推
REPS_ONLY         仅次数          引体向上
DURATION          仅时长          平板支撑
DISTANCE_DURATION 距离 + 时长     跑步
```

**为什么用「一个字段 + 多个可空列」，而不是每种类型建一张表**：

| 方案 | 问题 |
|---|---|
| 每种类型一张表 | 查「某次训练的所有组」要 UNION 四张表，代码里到处 if-else |
| EAV（键值对表） | 灵活但查询灾难——「取最近 10 次最佳组」变多层自连接 |
| **宽表 + 判别器** | 代价是「有可空列」，换来所有统计 SQL 都是单表操作 |

### 核心设计四：`bw_factor` —— 让徒手训练计入容量

自重动作重量是 0，`Σ(重量×次数)` 会得到 0，导致徒手训练完全不计入容量。

`bw_factor`（体重系数）表示实际负荷相当于体重的多少倍：

| 动作 | 系数 | 理由 |
|---|---|---|
| 引体向上 / 双杠臂屈伸 | 1.00 | 撑起全部体重 |
| 倒立撑 | 0.90 | 大部分体重 |
| 俯卧撑 | 0.65 | 脚撑地分担了一部分 |
| 反向划船 | 0.55 | |
| 臀桥 / 平板支撑 | 0.50 | |

容量公式：`体重 × bw_factor × 次数`

### 核心设计五：`status` 和 `deleted` 是两回事

| 字段 | 语义 | 场景 |
|---|---|---|
| `status` | **业务停用**（下架） | 管理员下架一个动作。历史记录正常显示，新计划里选不到 |
| `deleted` | **逻辑删除** | 真的不要了 |

> ⚠️ **停用动作绝不能物理删除或逻辑删除**——历史训练记录引用它，
> 删了之后「我三个月前练的是什么」就查不出来了。

### 为什么用 VARCHAR 而不是 MySQL 的 ENUM 类型

| | MySQL ENUM | VARCHAR |
|---|---|---|
| 增删值 | **要 ALTER TABLE** | 直接插 |
| 顺序敏感 | **是**——底层存序号，调顺序会让存量数据含义错乱 | 否 |
| 校验 | 数据库层 | Java 枚举（编译期） |

### 踩坑

> ℹ️ **BigDecimal 比较要用 `isEqualByComparingTo` 而不是 `isEqualTo`**
>
> ```java
> new BigDecimal("1.0").equals(new BigDecimal("1.00"))  // false！精度不同
> new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0  // true，数值相同
> ```
>
> 数据库 `DECIMAL(4,2)` 读回来是 `1.00`，而 `new BigDecimal("1.0")` 是 `1.0`——
> 用 `isEqualTo` 断言会失败。
>
> 而 **`equals()` 还有一个更隐蔽的坑**：`BigDecimal` 的 `equals` 连精度一起比，
> 所以 `HashSet<BigDecimal>` 会把 `1.0` 和 `1.00` 当成两个不同的元素。
> **涉及 BigDecimal 的比较，一律用 `compareTo`。**

---

## 步骤 2.3 —— 内置动作库种子数据 ✅

**日期**：2026-09-17

### 做了什么

```
db/migration/V5__seed_exercise.sql    90 个动作
exercise/MovementPattern.java         +3 个模式（肘屈/肘伸/提踵）
```

### 验证结果

| 检查项 | 结果 |
|---|---|
| V5 迁移 | `success=1`，17ms |
| 动作总数 | **90 个** |
| 肌群分布 | 腿 22 · 背 17 · 手臂 14 · 胸 14 · 肩 12 · 核心 11 |
| 计量类型 | 重量×次数 60 · 仅次数 23 · 距离时长 4 · 仅时长 3 |
| 器械分布 | **自重 27** · 哑铃 20 · 杠铃 18 · 器械 13 · 绳索 12 |

**两项数据一致性检查**：

```sql
-- 自重动作必须都有 bw_factor，否则容量算不出来
SELECT COUNT(*) FROM exercise WHERE metric_type='REPS_ONLY';              -- 23
SELECT COUNT(*) FROM exercise WHERE metric_type='REPS_ONLY' AND bw_factor IS NOT NULL;  -- 23 ✓

-- 负重动作不该有 bw_factor，有说明填错了
SELECT COUNT(*) FROM exercise WHERE metric_type='WEIGHT_REPS' AND bw_factor IS NOT NULL;  -- 0 ✓
```

> **为什么要做反向检查**：只查「该填的填了」不够——
> 还要查「不该填的没填」。正向检查通过但反向有数据，
> 说明赋值逻辑写反了，而这种错误在正向检查里看不出来。

### 关于「自重动作比杠铃还多」

自重 27 个 vs 杠铃 18 个，这不是巧合——**是为了让徒手计划模板能落地**。

`REQUIREMENTS.md` 里把「徒手 / 居家计划」列为 Must，理由是：
器械可及性是健身最大的现实约束（出差住酒店、居家、健身房器械被占）。
如果动作库里自重动作不够，「徒手兜底」就是空话。

### 踩坑

> ℹ️ **分类维度是从「典型场景」想出来的，真实数据总有例外**
>
> 最初定义 `MovementPattern` 时只想了大肌群复合动作（推/拉/蹲/铰链），
> 写到手臂和腿部时发现**弯举、臂屈伸、提踵无处归类**。
>
> 补了三个：`ELBOW_FLEXION` / `ELBOW_EXTENSION` / `CALF_RAISE`。
>
> **补的成本很低**——枚举加常量即可，数据库不用动
> （VARCHAR 存的是枚举名，不是序号）。这正是当初不用 MySQL `ENUM` 类型的好处：
> 如果用 MySQL ENUM，加值要 `ALTER TABLE`，而且顺序敏感。

> ⚠️ **名称相似的动作要注意区分**
>
> 库里有「面拉」（BACK，练背）和「绳索面拉（肩）」（SHOULDERS，练后束）——
> 虽然动作几乎一样，但**训练目的不同，归到不同肌群**。
>
> 唯一约束 `uk_exercise_user_name` 是按 `(user_id, name)` 判重的，
> 名称不同就不冲突。但命名相似会让人困惑——
> 更好的做法是加在 `alias` 里而不是造两个名字。这里保留是为了说明这个取舍。

---

## 步骤 2.4 —— 动作查询接口 ✅

**日期**：2026-09-17

### 做了什么

```
exercise/
├── ExerciseService.java              查询逻辑（含可见性规则）
├── ExerciseController.java           GET /exercises, GET /exercises/{id}
└── dto/
    ├── ExerciseQuery.java            查询条件
    └── ExerciseResponse.java         响应（编码 + 中文标签）
common/PageResponse.java              通用分页响应
```

### 验证结果

| 场景 | 结果 |
|---|---|
| alice 全部 | **91**（90 内置 + 1 自定义） |
| bob 全部 | **90**（只有内置） |
| `muscle=CHEST` | 15 | <!-- 步骤 2.16 已改名为 primaryMuscle，见下方注记 -->
| `equipment=DUMBBELL` | 20 |
| `metricType=REPS_ONLY` | 24 |
| `onlyCustom=true` | 1 |
| 分页 `page=1&size=5` | 5 条记录，total=91 |
| 中文搜索 `卧推` | **6**（窄距/杠铃/上斜/下斜/哑铃/上斜哑铃） |
| 中文搜索 `深蹲` | 5 |
| 组合筛选 `CHEST + DUMBBELL` | 4 |
| 模式筛选 `SQUAT` | 9 |

> **⚠️ 参数名后来改过**（步骤 2.16）：`muscle` → `primaryMuscle`，
> `pattern` → `movementPattern`，与响应字段名对齐。
> 上表是当时的原始记录，照着敲请用新名字。
> 改名原因见步骤 2.16 的验收记录：旧名字会导致
> **参数被静默忽略、返回全量数据**。

### ★ 核心安全验证：可见性规则

```
bob   GET /exercises/96 → 404 {"code":30001,"动作不存在"}
alice GET /exercises/96 → 200 拿到自己的自定义动作
```

**列表和单条两条路径都做了校验**——这是关键。

> **为什么单条查询最容易漏**：列表和单条是两套代码路径。
> 列表加了过滤条件，单条如果直接 `selectById(id)` 就绕过了所有过滤。
> 攻击方式很直接：拿一个别人的资源 id 直接访问。
>
> **这类漏洞在真实项目里极其常见**，因为开发者写完列表接口后，
> 单条接口往往是从别处复制过来的。

### 越权时返回 404 而不是 403

```
✗ 403「无权限」 → 攻击者确认了「这个 id 存在」，可据此枚举
✓ 404「不存在」 → 无法区分「不存在」和「别人的」，拿不到额外信息
```

**这个原则对所有按 id 访问的资源都适用**（训练记录、计划、照片……）。

### SQL 优先级的坑（写查询条件时极易踩）

```java
// ✗ 错误写法
wrapper.eq(Exercise::getUserId, 0)
       .or()
       .eq(Exercise::getUserId, userId);
wrapper.eq(Exercise::getStatus, ENABLED);

// 生成：WHERE user_id = 0 OR user_id = ? AND status = 1
// AND 优先级高于 OR，实际等价于：
//       WHERE user_id = 0 OR (user_id = ? AND status = 1)
// → 内置动作的 status 条件失效了！
```

```java
// ✓ 正确写法：用 and(...) 把 OR 条件包起来
wrapper.and(w -> w.eq(Exercise::getUserId, 0)
                  .or()
                  .eq(Exercise::getUserId, userId));
wrapper.eq(Exercise::getStatus, ENABLED);
// 生成：WHERE (user_id = 0 OR user_id = ?) AND status = 1
```

**这类 bug 极难发现**——单测时数据往往很干净，看不出差异。

### 分页的两个必要防护

**① `size` 必须有上限**

```java
return Math.min(size, 100);
```

不限制的话，客户端传 `size=1000000` 就能一次拉走整张表——
既是性能问题，也是数据泄露风险（爬虫可轻松全量抓取）。

**② 排序最后一定要有唯一字段**

```java
wrapper.orderByAsc(Exercise::getPrimaryMuscle)
       .orderByAsc(Exercise::getSortOrder)
       .orderByAsc(Exercise::getId);   // ← 这个不能省
```

只按前两个排序时，相同 `sort_order` 的记录在**不同页之间顺序可能变化**，
导致翻页时看到重复或遗漏的记录。用 id 兜底保证顺序稳定。

---

## 步骤 2.4b —— 修正别名与分类（V6）✅

**日期**：2026-09-17

### 发现的两个问题

**这两个都是实际调接口测出来的，不是设计时能想到的。**

| 问题 | 现象 | 根因 |
|---|---|---|
| ① 术语不一致 | 搜「徒手」得 **0 条** | 数据里写的是「自重」，用户说的是「徒手」 |
| ② 分类错误 | 动感单车被归到 `SQUAT` 模式 | 写种子数据时批量赋值导致 |

### 修复

```
db/migration/V6__fix_exercise_aliases.sql
```

| 检查项 | 修复前 | 修复后 |
|---|---|---|
| 搜「徒手」 | 0 | **27** |
| 搜「无器械」 | 0 | **27** |
| 徒手别名覆盖 | — | **27 / 27** |
| 动感单车 pattern | `SQUAT` | **NULL** |

### 为什么新建 V6 而不是改 V5

**已执行的迁移脚本是不可变的**——Flyway 会校验 checksum，改了会导致启动失败。

这不是限制，而是**版本化迁移可靠性的来源**：每个脚本代表一次已发生的历史事实，
历史不能改写，只能追加新的修正。

> 这个约束在实际开发中会经常遇到：写完迁移、跑过了、发现写错了。
> **正确做法永远是新建一个脚本，而不是回去改。**

### 踩坑

> ⚠️ **测试时 URL 里的中文要手动编码，且极易编错**
>
> ```bash
> # 「器」= E5 99 A8 → %E5%99%A8
> # 「机」= E6 9C BA → %E6%9C%BA
> ```
>
> 我第一次把「无器械」编成了 `%E6%97%A0%E6%9C%BA%E6%A2%B0`（无机械），
> 结果返回 0 条，差点误判为「V6 没生效」。
>
> **教训**：中文搜索测试返回 0 条时，**先核对 URL 编码，再怀疑服务端**。
> 用 `printf '字符' | xxd` 确认字节，比肉眼数十六进制可靠。

---

## 步骤 2.5 —— 自定义动作接口 ✅

**日期**：2026-09-17

### 做了什么

```
exercise/
├── ExerciseService.java          +create / update / delete / loadEditable
├── ExerciseController.java       +POST / PUT / DELETE
└── dto/ExerciseSaveRequest.java  创建与编辑共用的请求体
```

### 验证结果

| # | 场景 | HTTP | 响应 |
|---|---|---|---|
| 1 | 创建自定义动作 | 200 | id=97 |
| 2 | 重名创建 | **409** | `{"code":30003,"同名动作已存在"}` |
| 3 | **字段规范化** | — | WEIGHT_REPS→`bw_factor=NULL`；REPS_ONLY→`1.00` |
| 5 | **编辑内置动作** | **403** | `{"code":10002,"内置动作不能修改"}` |
| 6 | **bob 编辑 alice 的动作** | **404** | `{"code":30001,"动作不存在"}` |
| 7 | **bob 删除 alice 的动作** | **404** | 同上 |
| 8 | alice 编辑自己的 | 200 | 改名 + 换器械均生效 |
| 9 | alice 删除自己的 | 200 | 之后查不到（逻辑删除生效） |

### ★ 核心：三条归属校验路径全部挡住

```java
private Exercise loadEditable(Long currentUserId, Long id) {
    Exercise e = mapper.selectById(id);
    if (e == null || !e.isAvailable())     throw NOT_FOUND;

    if (e.isBuiltIn())                     throw FORBIDDEN("内置动作不能修改");
    if (!e.isOwnedBy(currentUserId))       throw NOT_FOUND;      // ← 不泄露存在性
    return e;
}
```

**把校验收在一个方法里，是为了避免散落各处导致遗漏。**
编辑和删除都要做同样的判断，写两遍就容易只改一处。

**注意两种错误码的差异**：
- 内置动作 → **403**，因为客户端确实有权限知道这个动作存在（它是公开数据），
  只是没权限改
- 别人的自定义动作 → **404**，因为**不能泄露「这个 id 存在」**

这个区分不是随意定的：对**公开可见的资源**用 403，对**私有资源**用 404。

### 字段规范化：派生字段该由服务端维护

`bwFactor` 和 `metricType` 是联动的——负重动作不该有体重系数，自重动作必须有。

两种处理方式：

| 方式 | 体验 |
|---|---|
| 校验后拒绝 | 用户改计量类型时必须手动清空 `bwFactor`，多一步操作 |
| **自动规范化** | 服务端按规则处理，用户不用操心 |

选了自动规范化：

```java
if (metricType == REPS_ONLY) {
    setBwFactor(request.getBwFactor() == null ? BigDecimal.ONE : request.getBwFactor());
} else {
    setBwFactor(null);   // 强制清空
}
```

**为什么「强制清空」而不是「报错」**：留着值会让容量计算多算一遍体重——
这是个**静默的数据错误**，界面上看不出来，只在统计图表里表现为「容量莫名偏高」。
不如服务端直接抹掉。

自重动作**没填就给默认值 1.0**（按整体重计算），而不是报错——
用户建一个「负重引体」时未必知道该填多少，1.0 是合理起点，之后可改。

### 请求 DTO 里刻意没有的字段

```java
// ExerciseSaveRequest 里**没有**这些：
private Long userId;      // ← 归属必须来自 token
private Integer status;   // ← 服务端控制
private Integer deleted;  // ← 服务端控制
```

**如果 `userId` 由客户端传入**，用户可以伪造 `userId: 0` 去创建「内置动作」，
或把动作挂到别人名下。**这是最典型的一类越权漏洞。**

### 踩坑

> ⚠️ **中文 JSON 必须用 `-d @文件`，不能内联**
>
> 这次又踩了同一个坑——所有内联 JSON 的请求都返回
> `{"code":10006,"message":"请求格式有误"}`，看起来像接口 bug，
> 实际是 **Windows 命令行把 UTF-8 中文转坏了**（走 ANSI 代码页 GBK）。
>
> ```bash
> # ✗ 内联：中文被损坏
> curl -d '{"name":"我的卧推变式",...}'
>
> # ✓ 用 heredoc 写文件再传：字节原样保留
> cat > /tmp/req.json <<'EOF'
> {"name":"我的卧推变式",...}
> EOF
> curl -d @/tmp/req.json
> ```
>
> **这是本项目第 3 次遇到同一类问题**（前两次：API 请求体、SQL 参数）。
> 已列入「踩坑汇总」并标注为高频问题。

> ℹ️ **`default-property-inclusion: non_null` 会让 null 字段从响应里消失**
>
> 验证「`bwFactor` 被清空」时，grep `"bwFactor"` **什么都没匹配到**——
> 一度以为字段丢了。
>
> 实际上是 `application.yml` 里配了 `default-property-inclusion: non_null`，
> null 字段不序列化。**「字段消失」和「字段为 null」在前端看来是同一件事**
> （都是 `undefined`），但排查时要意识到这个配置的存在。
>
> 这种场景下**直接查数据库**比看响应可靠。

---

## 步骤 2.6 —— 计划相关表设计 ✅

**日期**：2026-09-17

### 做了什么

```
db/migration/V7__init_program.sql
program/
├── Program.java / ProgramMapper.java
├── WeekTemplate.java / WeekTemplateMapper.java
├── DayTemplate.java / DayTemplateMapper.java
├── PrescribedExercise.java / PrescribedExerciseMapper.java
├── PrescribedSet.java / PrescribedSetMapper.java
├── ProgramStatus.java
└── TargetWeightType.java
training/SetType.java
```

### 验证结果

| 检查项 | 结果 |
|---|---|
| V7 迁移 | `success=1`，96ms |
| 表总数 | **13 张** |
| 唯一约束 | 3 个（周序号 / 训练日编号 / 组序号均防重） |
| 集成测试 | 搭建完整计划结构并原样读回，通过 |

### 五张表的层级关系

```
program                    计划（含版本号、root_id）
  └─ week_template         周结构（含强度修饰）
       └─ day_template           训练日模板
            └─ prescribed_exercise    处方动作（含超级组标记）
                 └─ prescribed_set       逐组处方
```

### 设计一：为什么「周」和「训练日」要分开

**8 周 × 每周 3 练 = 24 个训练日。** 手写 24 个 `day_template` 不可维护——
改一个动作要改 8 遍。

拆开后：

| | 记录数 |
|---|---|
| 不拆（每周单独建训练日） | 24 |
| **拆开（3 个结构 × 8 个强度修饰）** | **3 + 8 = 11** |

处方值在**创建训练会话时**展开计算。

### 设计二：周期化用「修饰」而非「重写」

```java
// 不存「第 5 周卧推 65kg」，而是存「第 5 周 +5%」
最终重量 = 动作级基准重量 × (1 + weightAdjustPct / 100)
最终组数 = 动作级基准组数 + setAdjust
```

**这个设计的意义**：用户如果第 3 周发现基准重量设低了，
改一次动作级基准值，后面所有周的处方**自动跟着调整**。
存绝对值的话要改 8 处。

### 设计三：逐组处方（计划灵活度的关键）

只支持「动作级统一目标」会挡掉大量真实计划：

```
5×5 递增： 60 / 65 / 70 / 70 / 70 kg
5/3/1：    75% / 85% / 95%
金字塔：   12 / 10 / 8 / 6 次
递减组：   最后一组做完立即减重
```

**两层结构，只为需要特殊目标的组建记录**：

```
prescribed_exercise（动作级默认）  ← 3组×10次 这种只写这一层
  └─ prescribed_set（逐组覆盖）    ← 只有递增/递减才建记录
```

展开规则：有逐组记录用它，否则回落动作级默认值。
避免「3组×10次」建三条一模一样的冗余记录。

### 设计四：超级组不是独立实体

只是动作上的**分组标记**：

```java
supersetGroup  = NULL  → 普通动作
supersetGroup  = 1     → 属于第 1 个超级组
orderInGroup   = 1,2,3 → 组内执行顺序
```

> ⚠️ **超级组只改变执行顺序，不改变计量。**
> A1 做 3 组、A2 做 3 组，就是各记 3 组。
> **绝不能存成一条合并记录**——那会让容量计算、PR 判定、
> 单动作历史全部失效。（REQUIREMENTS 不变量 C）

### 设计五：目标重量支持三种写法

| 方式 | 适用 | 例子 |
|---|---|---|
| `ABSOLUTE` 绝对重量 | 重量固定 | 卧推 60kg |
| `PERCENT_1RM` 1RM 百分比 | 力量周期化 | 本周 75% |
| `RPE` 主观强度 | 状态波动大 | RPE 8 |

**为什么不能只存公斤数**：用户 1RM 进步后，只支持绝对重量的计划要手动改
每一个动作的重量。而 `%1RM` 让计划**自动跟随能力变化**——
这正是周期化训练的核心需求。

### 设计六：版本化（不变量 B 的载体）

```java
version   // 修改已开始的计划时递增
rootId    // 同一逻辑计划的所有版本共享它；第一个版本等于自身 id
status    // ACTIVE / PAUSED / ARCHIVED / FINISHED
```

修改进行中的计划时不改原记录，而是：
1. 原计划标记为 `ARCHIVED`
2. 新建一条 `program`，`version + 1`

训练会话记录它用的是哪个 `programId`，所以**历史永远指向旧版本**。

### 设计七：deload 标记会被统计用到

```java
isDeload  // 是否减量周
```

**计算计划完成率时 deload 周要排除**——否则用户按计划减量，
反而被算成「没完成」。这个字段看着不起眼，但漏了会导致统计结果违背直觉。

### 验证方式：搭一套完整结构而不是只看表

光验证「表建出来了」不够——这是五层嵌套结构，
每张表字段都对，不代表能组合出一个真实计划。

测试搭了一套「8 周推拉腿」骨架：

```
计划（8周，version=1，rootId=自己）
├─ 第 1 周（基准，+0%）
├─ 第 8 周（deload，-40%，setAdjust=-1）   ← 验证 isDeloadWeek()
└─ 推日 A
   ├─ 动作1（普通，4组×8-10次）              ← 验证 isRepRange()
   ├─ 动作2（超级组1，组内顺序1）             ← 验证 isInSuperset()
   ├─ 动作3（超级组1，组内顺序2）
   └─ 动作4（5×5 递增，第1组是热身）          ← 验证 countsTowardVolume()
      → 5 条逐组处方，重量 60/65/70/70/70
      → 容量统计应只算 4 组（排除热身）
```

**最后一行的断言最关键**：`countsTowardVolume()` 返回 4 而不是 5，
证明「热身组不计入容量」的口径在数据模型层面是成立的。

### 一个包结构上的决策

`SetType` 放在 `com.gymlog.training` 而不是 `com.gymlog.program`——

它同时被**计划**（`prescribed_set.set_type`）和
**实际执行**（`set_record.set_type`，Phase 3）使用。
放在任何一方都会造成概念上的归属混乱。

**判断依据**：一个类型如果被两个模块共用，它就不属于任何一个模块。

---

## 步骤 2.8 —— 计划 CRUD 接口 ✅

**日期**：2026-09-17

### 做了什么

```
program/
├── ProgramService.java              嵌套创建 / 一次查询组装 / 级联删除
├── ProgramController.java           6 个接口
└── dto/
    ├── ProgramCreateRequest.java    嵌套结构（4 层 record）
    ├── ProgramDetailResponse.java   完整嵌套响应
    ├── ProgramSummaryResponse.java  列表摘要
    └── ProgramUpdateRequest.java    仅元信息
```

### 验证结果

| # | 场景 | HTTP | 结果 |
|---|---|---|---|
| 1 | 创建完整计划 | 200 | id=2 |
| 2 | 超级组只有 1 个动作 | **400** | `40004「至少需要 2 个动作」` |
| 3 | 休息日带动作 | **400** | `「休息日不能包含动作」` |
| 4 | **两次失败后库中数据** | — | `1/2/2/4/5`（只有第一个计划的）✓ |
| 5 | 计划详情（完整嵌套） | 200 | 周/日/动作/逐组处方全部正确 |
| 6 | 计划列表 | 200 | 返回摘要，不含完整结构 |
| 7 | 更新信息 | 200 | |
| 8 | **bob 查/改/删 alice 的计划** | **404 ×3** | 三条路径全挡住 |
| 9 | 暂停 / 恢复 | 200 | |
| 9 | **直接归档** | **403** | `「归档由版本化流程触发，不能直接设置」` |
| 10 | **删除级联** | 200 | `1/2/2/4/5` → `1/0/0/0/0` |

### 核心设计：嵌套创建必须在一个事务里

创建计划不是一次 INSERT，而是**一次插入五层**：

```
1 条 program
  + N 条 week_template
  + M 条 day_template
      + K 条 prescribed_exercise
          + J 条 prescribed_set
```

**第 4 条验证专门检验了这一点**：连续发两个非法请求后，
库里只有第一个合法计划的数据 —— **没有留下半截计划**。

> 不这么做的话，中间失败会留下「有训练日但没动作」或「有动作但没组」的残缺数据。
> 这种数据在界面上表现为「计划打不开」，且极难定位是哪一层断的。

### 核心设计：一次查询组装，避免 N+1

详情接口**固定 6 次查询，与计划规模无关**：

```sql
1. program                WHERE id = ?
2. week_template          WHERE program_id = ?
3. day_template           WHERE program_id = ?
4. prescribed_exercise    WHERE day_template_id IN (...)
5. prescribed_set         WHERE prescribed_exercise_id IN (...)
6. exercise               WHERE id IN (...)          -- 取动作名称
```

**对比嵌套查询（N+1）**：8 周计划有 3 个训练日、约 20 个动作，
逐层查是 `1 + 3 + 20 + 60 = 84` 次数据库往返。

> ORM 让「对象.集合.再集合」写起来很自然，但**每次点号都可能是一次数据库查询**。
> 这是 N+1 问题最常见的来源。

### 🐛 测试中发现并修复的 bug：排序覆盖了 orderIndex

**现象**：详情返回的动作顺序和用户设置的 `orderIndex` 不一致。

```
用户设置：1.卧推(普通)  2.划船(超级组)  3.深蹲(超级组)  4.硬拉(普通)

错误排序：划船, 深蹲, 卧推, 硬拉        ← 超级组被提到了最前面
正确排序：卧推, 划船, 深蹲, 硬拉
```

**根因**：我把「分组」和「排序」混为一谈了。原来的比较器写的是
「有超级组的排在前面」——这直接覆盖了 `orderIndex`。

**正确语义**：超级组影响的是**执行节奏**（组内不休息），
不是**在训练日里的位置**。位置仍然由 `orderIndex` 决定。

**修法**：

```java
// 排序键 = 普通动作取自己的 orderIndex
//          超级组成员取「组内最小的 orderIndex」
Map<Integer, Integer> groupSortKey = ...;  // 先算出每个超级组的最小 orderIndex
```

> **这个 bug 光看代码发现不了**——比较器的逻辑看起来是自洽的，
> 只有把真实数据跑一遍、对着用户配置的顺序核对，才能看出问题。
> **再一次印证：验证不能只看「跑通了」，要对着预期结果逐项核对。**

### 一个刻意的限制：不支持改结构

`PUT /programs/{id}` **只能改名称和说明**，不能增删训练日或改动作。

结构变更要走**版本化**路径（步骤 2.14）：归档旧版本 + 新建新版本。
直接改结构会让已完成的训练记录「追溯性地改变含义」——
用户第 5 周改了训练日的动作，前 4 周的历史会显示成改后的样子。

### 校验的归属：跨记录约束必须手写

`ProgramCreateRequest` 上有大量 JSR-303 注解，但有四类校验**注解表达不了**：

| 约束 | 为什么注解做不到 |
|---|---|
| 周序号不能重复 | 作用在列表整体，不是单个字段 |
| 训练日序号不能重复 | 同上 |
| 超级组至少 2 个动作 | 需要先按组号分组再判断 |
| 超级组内顺序不能重复 | 同上 |

**判断依据**：注解只能看「单个字段的值」，
凡是需要「看到多个记录才能判断」的约束，都得手写。

---

## 步骤 2.9 —— 内置计划模板 ✅

**日期**：2026-09-17

### 做了什么

```
db/migration/
├── V8__init_program_template.sql     表结构
└── V9__seed_program_template.sql     6 个模板
program/
├── ProgramTemplate.java
└── ProgramTemplateMapper.java
```

### 6 个内置模板

| 编码 | 目标 | 水平 | 每周 | 训练日 | 周数 | 预计 |
|---|---|---|---|---|---|---|
| `STRONGLIFTS_5X5` | 力量 | 入门 | 3 | 2 | 12 | 45min |
| `PPL_3DAY` | 增肌 | 中级 | 3 | 3 | 8 | 60min |
| `UPPER_LOWER_4DAY` | 增肌 | 中级 | 4 | 4 | 8 | 55min |
| `PPL_6DAY` | 增肌 | 进阶 | 6 | 6 | 8 | 50min |
| `BODYWEIGHT_3DAY` | 综合 | 入门 | 3 | 3 | 8 | 35min |
| `BODYWEIGHT_BEGINNER` | 综合 | 入门 | 3 | 2 | 4 | 25min |

### 设计决策：结构存 JSON，不建镜像表

备选方案是建 `template_week` / `template_day` / … 五张表，与 program 那套一一对应。

**不选它的三条理由**：

1. **完全重复**——两套结构相同的表 = 两套实体 + 两套 Mapper + 两套增删改逻辑
2. **模板是静态数据**——只在「从模板创建计划」时被整体读取，之后不再被查询
3. **形状天然对应**——JSON 结构就是 `ProgramCreateRequest` 的结构，
   从模板创建计划 = 读 JSON → 反序列化 → 调用 `create()`，**零转换逻辑**

**代价**：无法用 SQL 直接查模板内部。实际只有「删除动作时的引用检查」会用到，
解析 JSON 即可，且是低频操作。

> **判断依据：当一个数据结构只被整体读写、从不被部分查询时，
> 存 JSON 比建关系表更合适。**

### 关于训练日的循环语义（一个模型细节）

5×5 是 A/B 交替的（周一 A、周三 B、周五 A，下周反过来）。
但模型里训练日只有 `dayNumber` 编号，怎么表达交替？

**解法：训练日在整个计划周期内连续循环**，不是每周重置。

```
2 个训练日模板（A、B）+ 每周 3 练
  第 1 周：第1次→A  第2次→B  第3次→A
  第 2 周：第4次→B  第5次→A  第6次→B     ✓ 自动交替
```

用「每周重置」的话，第 2 周又会从 A 开始，变成 A/B/A 无限重复。

PPL（3 个模板 × 3 练）和上下肢（4 × 4）正好整除，循环效果相同。
规则在步骤 2.15「今天练什么」里实现。

### JSON 里用动作名称而不是 id

动作 id 是**自增的**，各环境不一致（开发库的「杠铃卧推」是 id=6，
生产库可能是 id=1）。用 id 会导致迁移脚本在不同环境插入不同的引用。

用名称的代价是：**名称写错不会在插入时报错**，而是等到用户点
「使用这个模板」时才失败。而且这种错误肉眼极难发现——
少一个字、多一个空格，扫过去看不出来。

**所以必须有机器核对**：

```sql
-- 反连接：找出模板引用了但动作库里不存在的名称
SELECT DISTINCT jt.exercise_name
FROM program_template t,
     JSON_TABLE(t.structure, '$.days[*].exercises[*]'
       COLUMNS (exercise_name VARCHAR(64) PATH '$.exerciseName')) AS jt
WHERE jt.exercise_name NOT IN (SELECT name FROM exercise WHERE user_id = 0);
-- 空结果 = 全部匹配 ✓
```

同样的检查也写成了单元测试（`ProgramTemplateTest`），
因为**这个约束在数据库层面无法用外键表达**——JSON 里的值不是引用。

### 模板内容的取舍

**5×5 的硬拉只安排 1 组**，而不是 5 组——硬拉的恢复成本远高于其他动作，
5 组 5 次的硬拉会严重影响后续训练。这个细节在网络上的模板里常被忽略。

**徒手模板的动作数刻意给足**（自重动作库有 27 个），
确保「零器械计划」不是空话——出差住酒店也能照常训练，
这是 REQUIREMENTS 里把它列为 Must 的原因。

**每个模板都配了 8–12 周的周期化**，包括 deload 周。
很多免费模板只给「练什么」不给「练多久、什么时候减量」，
而后者才是能长期坚持的关键。

### 验证

3 个测试全部通过：

| 测试 | 内容 |
|---|---|
| 模板完整性 | 6 个模板都存在，JSON 可解析，`totalWeeks` 与 weeks 数组长度一致 |
| **动作名称引用** | 所有引用的动作名都存在于动作库（一次性列出所有缺失项，不遇错即停） |
| 超级组配置 | 要么没有超级组，要么至少 2 个成员 |

> 第二条的断言用了「收集所有缺失项再一次性断言」而不是「遇到第一个就失败」——
> 修的时候能一次改完，不用反复跑。

---

## 步骤 2.10 —— 从模板创建计划 ✅

**日期**：2026-09-17

### 做了什么

```
program/
├── ProgramService.java                 +createFromTemplate / 名称解析
├── ProgramController.java              +POST /programs/from-template
├── ProgramTemplateController.java      GET /program-templates（列表/详情）
└── dto/
    ├── ProgramFromTemplateRequest.java
    └── ProgramTemplateResponse.java
```

### 验证结果

| 模板 | 创建结果 | 周数 | 训练日 | 动作数 |
|---|---|---|---|---|
| `STRONGLIFTS_5X5` | 200, id=3 | 12 | 2 | 6 |
| `PPL_3DAY` | 200, id=4 | 8 | 3 | 15 |
| `UPPER_LOWER_4DAY` | 200, id=5 | 8 | 4 | 22 |
| `BODYWEIGHT_3DAY` | 200, id=6 | 8 | 3 | 15 |
| `BODYWEIGHT_BEGINNER` | 200, id=7 | 4 | 2 | 8 |
| `PPL_6DAY` | 200, id=8 | 8 | 6 | 25 |
| 非法模板编码 | **404** | — | — | — |

**动作数逐个核对过**，与模板定义完全一致（如 PPL_3DAY = 5+5+5 = 15）。
详情接口返回的每个动作都同时有 `exerciseName` 和 `exerciseId`——
**名称→id 解析成功**。

### 核心工作：名称 → id 的解析

模板里存的是动作**名称**（因为 id 各环境不一致），入库需要 **id**。
这层转换是这一步唯一的额外工作。

**两个实现要点**：

**① 一次 IN 查询，不逐个查**

```java
// 先从模板 JSON 里收集所有不重复的动作名称
Set<String> names = collectNames(root);
// 一次查出
List<Exercise> exercises = exerciseMapper.selectList(
    ... .in(Exercise::getName, names));
```

一个模板引用 10–20 个不重复的动作，逐个查就是 10–20 次数据库往返。

**② 缺失时列出全部，不遇到第一个就抛**

```java
List<String> missing = names.stream().filter(n -> !result.containsKey(n)).toList();
if (!missing.isEmpty()) {
    log.error("模板引用的动作不存在 | template={} | missing={}", templateCode, missing);
    throw new BizException(..., "模板数据不完整，缺少动作：" + String.join("、", missing));
}
```

**一次列出全部缺失项，运维一次能修完**，不用改一个跑一次。

### 复用 create() 而不是重写

创建逻辑走的是同一个 `create()`——校验、事务、级联插入完全一样。

**复制一份的话，将来改 `create` 就极可能忘了同步改这里**，
然后两个入口的行为慢慢分叉。

### 接口设计：为什么用 `/from-template` 而不是查询参数

```
POST /api/v1/programs/from-template   body: {templateCode, name, startDate}
POST /api/v1/programs                 body: {嵌套的完整结构}
```

两者的**请求体结构完全不同**（一个是三个字段，一个是五层嵌套）。
用同一路径靠参数区分会让接口语义模糊，Swagger 上也无法清晰展示两种 body。

### 模板列表不返回 structure

`ProgramTemplateResponse` **刻意不含 `structure` 字段**——
那是完整的嵌套结构，一个模板上百条记录。

列表页只需要「够用户判断要不要选它」的信息（目标、水平、频率、器械）。
结构在点「使用这个模板」时才需要，而那时客户端根本不需要拿到它。

**实测**：6 个模板的列表响应只有 **2715 字节**。

### 一个设计上的取舍：创建时先原样落库

用户想改结构（增删动作、调组数）是**创建之后**的事，创建时先原样拷贝模板。

这样有两个好处：
1. 用户能看到模板的原始样子，知道自己在改什么
2. 创建逻辑不必处理「部分覆盖模板」的复杂情况

---

## 步骤 2.13 —— 周期化展开算法 ✅

### 做了什么

把「第 5 周 +5%」这种**修饰**，展开成每一组的具体目标。

| 文件 | 作用 |
|---|---|
| `program/ProgramExpander.java` | **纯函数**。所有计算都在这里，无依赖无状态 |
| `program/ExpansionService.java` | 数据加载层。查库 → 组装参数 → 调纯函数 |
| `program/ProgramStructureSupport.java` | 从 `ProgramService` 抽出的共用逻辑（归属校验、排序、批量加载） |
| `program/dto/ExpandedWorkout.java` | 展开结果的形状 |
| `GET /api/v1/programs/{id}/workouts/{day}?week=N` | 出口 |

### 三个设计决定

#### ① `weightAdjustPct = -40` 表示「减掉 40%」，不是「降到 40%」

```
-40  →  基准 × 0.60   ← 采用这个
-40  →  基准 × 0.40   ← 不采用
```

两种理解在 60kg 上差 12kg（36 vs 24），**而且不会报任何错**，
只会让用户莫名其妙地练轻或练重。所以语义必须写死、写进文档、写进测试。

选「减量」的理由：字段名叫 `adjust`（调整**量**），
而且健身圈说 deload 就是「减量 40%」。

📌 测试 `appliesDeloadAsReduction` 里那条 `36` 的断言就是防这个语义被改坏的。

#### ② 返回「目标强度四元组」，不返回裸重量

因为 **RPE 处方根本算不出具体重量**。

RPE 描述的是「练到什么程度」（RPE 8 = 还能再做 2 次），不是「用多重」。
同一个 RPE 8，状态好那天是 80kg，状态差那天只有 70kg——
**这正是 RPE 存在的意义**。

如果返回类型是 `BigDecimal weight`，遇到 RPE 只能：返回 null（丢掉信息）或**编一个数字**（最糟，用户会照着错的重量练）。

所以用 `{type, weight, pct, rpe}`，与 `prescribed_exercise` 的存储结构一致。

#### ③ V1 只实现 `ABSOLUTE`，另外两种**明确报错**

```
{"code":40006,"message":"动作「杠铃卧推」使用了 RPE 作为目标重量，
  当前版本暂不支持。请改为填写具体的公斤数"}
```

**报错而不是静默忽略**：静默忽略的话，用户看到一份没有重量的处方，
可能以为「这个动作不用负重」——照着练是危险的。

错误码单独给 `PROGRAM_TARGET_UNSUPPORTED`（40006）而不是复用 `BAD_REQUEST`：
前端要据此**引导用户去设置 1RM**，而不是笼统提示「参数有误」。

### 核心设计：优先级链为什么不写成嵌套 if

朴素写法（两个层级 × 三种类型）：

```java
if (ps != null) {
    if (ps.getTargetWeight() != null) { ... }
    else if (ps.getTargetWeightPct() != null) { ... }
    else if (ps.getTargetRpe() != null) { ... }
    else { /* 回落到动作级 —— 这份逻辑要写第二遍 */ }
} else {
    /* 回落到动作级 —— 再写一遍 */
}
```

问题：嵌套 4 层；**回落到动作级的逻辑要写两遍**；加第四种类型要改四处。

改成「每层各自解析成 Target（解析不出就是 null），再用 `firstNonNull` 串起来」：

```java
ExpandedWorkout.Target target = firstNonNull(
        targetFromSet(ps),          // 第 1 优先级
        targetFromExercise(pe)      // 第 2 优先级
);
```

优先级链变成一行，加一层只加一个参数。

### clamp 放在哪一层

**组数**：`clamp(baseSets + setAdjust, 1, 20)` —— 在**应用完周调整之后**。
顺序反了就白夹：先生效 `setAdjust = -3` 把 2 变成 -1，再夹到 1，结果对；
但如果先夹 2，再加 -3，就是 -1，漏出去了。

**重量**：`adjusted.max(0.5)` —— 0.5kg 是最小现实配重（哑铃通常 1kg/2.5kg 递增）。
极端 deload 可能算出 0.1kg 甚至负数，界面上显示「-3.2kg」会让用户以为程序坏了。

### 为什么展开函数是纯函数，怎么保证它一直是纯的

三个实际好处（不是为了「看起来优雅」）：

1. **能直接单测，不用起 Spring**。这次 17 个测试的展开部分只花 **0.13 秒**，
   而带 `@SpringBootTest` 的类每个要 5-8 秒。
   速度不是唯一原因——**一半的边界情况（周修饰算出负数、组数被减到 0、
   逐组处方缺失）在数据库里很难造出来，在这里就是构造一个对象的事。**
2. **能被两处复用**：Phase 3 创建会话快照也要用它。
3. **行为可预测**：同样的输入永远同样的输出。

**怎么保证一直是纯的**：不是靠「把类放在不同的包」，而是
**构造器私有 + 没有字段 + 不注入任何 Bean**。
想在方法里查数据库，得先加一个字段——而加字段这个动作本身就很显眼。

### 顺手修掉的两个问题

**① `ProgramService` 里三个私有方法被复制到了 `ExpansionService`（很差的做法）**

归属校验、排序规则、批量加载这三件事，两个 Service 都需要，
但都**不拥有**它。复制一份的后果特别隐蔽：
排序规则一旦分叉，「计划详情里的动作顺序」和「跟练时的动作顺序」就不一致，
而**两边单独看都是对的**，极难发现。

抽成 `ProgramStructureSupport`，两个 Service 共用一份。

**② `ExerciseMapperTest` 有两个测试一直是红的（早于本步骤）**

```
Duplicate entry '0-引体向上' for key 'exercise.uk_exercise_user_name'
```

时间线：测试写在 `4d2e0e4`，`V5__seed_exercise.sql` 在 `2fc1c2a` 才加进来。
种子数据里有「杠铃卧推」和「引体向上」，唯一索引 `(user_id, name)` 一撞就炸。

**这个红得特别隐蔽**：报错是 `DuplicateKeyException`，看起来像「插入逻辑坏了」，
实际上插入逻辑完全正常，是**测试数据选得不合适**。
它红了很久没被发现，因为平时只跑单个测试类。

修复：测试数据统一加 `[测试]` 前缀，让测试**自洽**——
不必知道种子数据里有什么。

> **教训：测试数据不要用生产数据里可能出现的名字。**

### 验证结果

**单元测试 17 个全通过**（`ProgramExpanderTest`，无 Spring 容器）：

| 分组 | 覆盖 |
|---|---|
| 基准展开 | 无周修饰、次数归一化、休息时间 |
| 周修饰（6） | +5% / -40% 语义 / 小数百分比 / 组数增 / 组数夹到 1 / 重量夹到 0.5 |
| 优先级链（4） | 逐组覆盖 / 逐字段回落 / 休息三级回落 / 默认 90 秒 |
| 不支持的目标（3） | RPE 报错带动作名 / %1RM 报错 / 无目标合法 |
| 超级组 | 透传且不改变数值 |
| 元信息与容错 | 动作缺失时不抛异常 |

**端到端（真实 HTTP，计划含明确重量）**：

| 周 | 期望 | 实际 |
|---|---|---|
| 无 week | 基准 | 卧推 60/60/**70**kg，侧平举 10kg，下压 25kg，引体无重量 |
| week=1 (+0%) | 不变 | ✅ |
| week=2 (+5%) | ×1.05 | 63 / 63 / **73.5** kg，10.5kg，**26.25**kg |
| week=3 (-40%, -1组) | ×0.6，少一组 | 36kg × 2 组，6kg，15kg，`deloadWeek=true` |

三个亮点：
- **set3 的 70kg×5 在 week=2 变成 73.5kg** —— 逐组处方的覆盖在调整后依然保留
- **week=3 时第三组消失** —— 组数减到 2，那条处方没有槽位了；回到非减量周又会回来。
  这正是「修饰」而非「改写」的语义
- **26.25kg** —— 小数百分比没有丢精度

**错误路径**：

| 场景 | 返回 |
|---|---|
| 不存在的训练日 | `40005 该计划没有第 99 个训练日` |
| 访问别人的计划 | `40001 计划不存在`（不泄露存在性） |
| RPE 处方 | `40006 动作「杠铃卧推」使用了 RPE …请改为填写具体的公斤数` |

**全量测试 25 个全绿**（含修复后的 `ExerciseMapperTest`）。

### 一个观察：模板创建的计划没有重量

用 `PPL_3DAY` 模板创建的计划展开后 `target` 全是空的——
因为**模板不可能知道你的力量水平**，只给组数×次数×休息。

这是合理行为，但意味着：**用户从模板创建计划后，需要自己填一遍重量。**
Phase 3 的跟练界面要考虑「没有目标重量时怎么显示」——
不能显示「0kg」，应显示「上次用了多少」或留空让用户现场决定。

---

## 步骤 2.14 —— 计划结构编辑 ✅

> **这一步从「版本化」降级而来。** 原计划是「改结构 = 归档旧版 + 建新版本」，
> 后来发现**会话快照已经覆盖了主要风险**——训练记录冻结的是处方副本，
> 不是结构 id，所以改结构不会改写历史。版本化只剩「回滚旧版」这点价值，
> V1 不做。论证见 REQUIREMENTS 6.3 不变量 2。

### 做了什么

`PUT /api/v1/programs/{id}/structure` —— 提交**完整的**周与训练日，服务端全量替换。

| 文件 | 改动 |
|---|---|
| `program/dto/ProgramStructureRequest.java` | 新增。复用 `ProgramCreateRequest` 的嵌套 record |
| `program/ProgramService.java` | 抽出 `insertStructure`；新增 `updateStructure`；`validateStructure` 改签名 |
| `program/ProgramController.java` | 新增端点 |
| `common/ErrorCode.java` | `PROGRAM_ALREADY_STARTED` → `PROGRAM_NOT_EDITABLE`（码值不变） |
| `program/Program.java` | `version` / `rootId` 的注释完全重写（用途变了） |

### 核心决策：为什么是全量替换

| 理由 | 说明 |
|---|---|
| **客户端本来就这样编辑** | 用户对着表单改，点保存时手里就是完整结构。细粒度接口逼客户端自己算 diff，而这份逻辑要在 Flutter 和 Vue 里各写一遍 |
| **跨记录校验才做得成** | 「超级组至少两个动作」「组内顺序不重复」——细粒度接口每次只看到一个动作，根本没法定这些规则 |
| **没有中间状态** | 细粒度接口存在「删了旧动作、还没加新动作」的瞬间，此时数据**不合法**（超级组只剩一个成员） |

**代价**：所有子记录的 id 每次编辑都会变。

### 这个代价为什么可以接受（也是这一步最重要的约束）

因为**没有任何东西外键引用这些子记录**。

会话快照存的是处方**值**（重量、次数、休息），不是 `prescribed_exercise_id`。
所以 id 洗牌对历史数据没有任何影响。

> **⚠️ 这条前提必须一直成立。**
> 如果哪天 Phase 3 把会话改成引用结构表，这个方法就得推倒重来，
> 改成基于 id 的增量更新——**而且失败方式极其隐蔽**：
> 不会报错，只会让用户的历史会话显示成空白。

为此写了一个测试 `childIdsChurnOnEveryEdit`，它**断言 id 会变**。
看起来像在确认一个 bug，实际是在固定一个设计契约：
哪天有人改成增量更新，这个测试会红，逼他重新确认那条前提。

同时把这条约束写进了 REQUIREMENTS 6.3 不变量 2。

### 乐观锁：不是可选项

全量替换下，两个设备同时编辑会**静默丢数据**：

```
手机加了深蹲 → 保存
电脑加了卧推 → 保存      ← 深蹲被整份覆盖，两边都没任何提示
```

所以 `expectedVersion` **必填**。理由：可选的并发检查等于默认关闭的并发检查，
而它要防的正是这种没有任何报错的数据丢失。

选「必填」还带来一个额外好处：客户端**必须先读过计划**才能提交，
不可能提交一份自己都没见过的结构。

用的是现成的 `Program.version` 字段（之前一直是死字段）——
乐观锁只需要一个单调递增的计数器，不必新建锁表。

**版本号只在结构变化时递增**，改名/改说明/暂停恢复都不动它。
否则另一台设备只是在改名字，就会让正在编辑结构的设备莫名冲突。
（测试 `renamingDoesNotBumpVersion` 固定了这个行为。）

### 校验必须在删除之前

`updateStructure` 的顺序是：

```
归属校验 → 状态守卫 → 乐观锁 → 结构校验 → 删除 → 插入 → 版本+1
```

**校验放在删除之前**，否则用户提交一份非法结构，他的整个计划就被清空了——
而且他会收到一个「超级组配置不合法」的错误，以为只是没保存成功。

测试 `validationFailureLeavesDataIntact` 专门盯这个顺序。

### 把校验和插入抽出来共用

`validateStructure` 和 `insertStructure` 现在被创建和编辑两条路径共用。

不抽的话，将来加一个字段就得记住改两个地方，而**漏改一边不会有任何报错**，
只会让「创建的计划」和「编辑过的计划」行为不一致——
这种 bug 只有在用户「先建后改」时才会出现，测试很容易漏掉。

### 顺手清理的三处过期注释

降级版本化之后，有三处注释还在描述已经废弃的设计，会**主动误导**后来的人：

| 位置 | 原文 | 问题 |
|---|---|---|
| `Program.version` | 「修改已开始的计划时递增，并新建一条记录」 | 完全反了——现在的语义是乐观锁计数器 |
| `ProgramService.updateMeta` | 「结构变更要走版本化路径」 | 结构编辑已经实现了，而且不走版本化 |
| `ErrorCode.PROGRAM_ALREADY_STARTED` | 「计划已开始，不能直接修改」 | **已开始的计划现在恰恰可以改** |

第三条最要紧：错误文案是会**给用户看**的。留着它，用户会以为
「计划一旦开始就改不了了」——而事实正相反。

已改名为 `PROGRAM_NOT_EDITABLE`，文案改为「计划已归档或已完成，不能修改」。
数字码 40002 保持不变，客户端不受影响。

### 验证结果

**单元/集成测试 10 个全通过**，全量 **35 个全绿**：

| 分组 | 覆盖 |
|---|---|
| 替换（3） | 动作被换掉 / 提交变少时旧训练日被删 / **id 会变（契约）** |
| 乐观锁（3） | 版本递增 / 过期版本被拒且旧数据完好 / 改名不动版本号 |
| 守卫（4） | 已归档不可编辑 / 校验失败旧数据完好 / 别人的计划 404 / 不能引用他人私有动作 |

**端到端（真实 HTTP）**：

编辑前的计划 12：`version=1`，1 个训练日（推日，4 个动作含超级组）

提交的编辑：加一个「腿日」（杠铃深蹲 5×5 @100kg）、卧推 3 组→4 组、
第 2 周调整 +5%→+7.5%、**移除原来的超级组**

| 检查项 | 结果 |
|---|---|
| 版本号 | 1 → 2 ✅ |
| 训练日 | 1 → 2 ✅ |
| week2 调整 | 5.0 → 7.5 ✅ |
| 卧推组数 | 3 → 4 ✅ |
| 超级组 | 已被移除 ✅ |
| 子记录 id | day 29→63，pe 123→161 ✅（如设计） |
| 过期版本提交 | `40003 计划已被其他设备修改（当前版本 2，你提交的是 1）` ✅ |
| 冲突后数据 | 完好，版本仍是 2 ✅ |
| 用正确版本重提 | 成功，版本 → 3 ✅ |

**与 2.13 组合验证**（展开编辑后的结构）：

| 场景 | 结果 |
|---|---|
| 推日 week2 (+7.5%) | 卧推 4 组：64.5 / 64.5 / **75.25** / 64.5 kg |
| 腿日 week2 (+7.5%) | 深蹲 5×5 @ 107.5kg |
| 腿日 week3 (deload) | **4 组**（5-1），60kg |
| 不存在的训练日 | `40005` ✅ |

第 3 组的 70kg 逐组处方**同时扛过了结构编辑和周调整**（75.25 = 70 × 1.075），
说明 2.13 和 2.14 两条链路正确组合。

---

## 步骤 2.15 —— 「今天练什么」查询 ✅

### 做了什么

`GET /api/v1/workouts/today?programId=&date=&day=`

App 首页加载时调的第一个接口（M4-A-1 的今日训练卡片）。

| 文件 | 作用 |
|---|---|
| `training/TrainingSchedule.java` | **纯函数**：由日期与已完成次数算出「第几周、该练第几天」 |
| `training/TodayWorkoutService.java` | 编排：选计划 → 算位置 → 展开内容 |
| `training/SessionCounter.java` | **Phase 3 的临时接缝**（见下） |
| `training/dto/TodayWorkoutResponse.java` | 响应形状 |

### 核心规则：训练日按「练了几次」推进，不按日期推进

这条规则**不是这一步定的**——写 V9 种子文件时就已经写在文件头注释里了，
这一步是去实现它：

```
2 个训练日模板（A、B）+ 每周 3 练
  第 1 周：第1次→A  第2次→B  第3次→A
  第 2 周：第4次→B  第5次→A  第6次→B      ← 自动交替了
```

**为什么不能按日期算**，两条路都试过：

| 方案 | 问题 |
|---|---|
| 每周重置 | 退化成 A/B/A 无限重复，5×5 的 A/B 交替直接废掉 |
| 按星期几固定 | 出差、生病就崩——周三没练，周五该练的还是「周三那个」，而不是顺延 |

按次数推进的好处是**自愈**：漏练一次不打乱顺序，整体往后挪。
用户不需要「补课」，也不会莫名跳过某个训练日。

**但周次仍然由日期决定**（`startDate + 7n`）。因为周期化的强度调整本来就按自然周走——
第 5 周该加重量，不会因为你少练两次就延后。

> 这两条一个按次数、一个按日期，看着矛盾，其实回答的是不同问题：
> **「练哪个」看历史，「练多重」看日历。**

### ⚠️ 这一步有一个已知缺口，而且是显式的

训练日轮转需要「已完成的训练次数」，而 `workout_session` 表要到 Phase 3 才建。

所以 `SessionCounter` **现在恒返回 0** —— 后果是轮转永远停在第 1 个训练日。

**为什么值得单开一个类装这个 0**：这样 Phase 3 要改的地方**只有那一个文件的一个方法**。
写在 Service 里的话，轮转的调用点、参数拼装、注释混在一起，
将来很容易改漏或改错位置。而且类名本身就在说明「这个数字应该从会话表来」——
一个孤零零的 `0` 常量做不到这一点。

类的 javadoc 里写清了三件事：现在是假的、为什么、Phase 3 要怎么改
（包括「只数 COMPLETED，练到一半放弃的不该推进轮转」这个细节）。

### 计划选择：两条规则，不做「手动指定当前计划」

| 顺序 | 规则 |
|---|---|
| 1 | 已开始的（`startDate <= today`）里面，**开始得最晚**的那个 |
| 2 | 没有已开始的，取**最近创建**的那个 |

不做「用户手动指定当前计划」的理由：那要多一个字段、多一个切换入口、
多一个「忘了切换」的坑，而且可能出现「没有任何计划被选中」的死状态。
按日期推断足够准，永远不会选出空。

用户真想练另一个计划时，传 `programId` 即可。

只考虑 `ACTIVE`：暂停的计划是用户主动停的，归档的更是明确不要了，
都不该自动冒到首页。

### 没有计划时返回 200，不是 404

「我没有计划」是**正常状态**，不是错误。用 404 会让客户端把正常空状态
和真正的请求失败混在一起处理。客户端判断 `programId == null` 就显示空状态。

### 复用而不是重写展开逻辑

训练日的内容直接复用 `ExpansionService.expandDay`，
返回类型也复用 `ExpandedWorkout.ExerciseItem`。

代价是多一次 `program` 查询（它内部也会 `loadOwned`）。
换来的是**展开逻辑只有一份**——跟练界面、计划预览、今天练什么
三处显示的重量必须完全一致，不一致是查不出来的 bug。

重新定义一套展开结果的形状，还会让跟练界面要写两套渲染逻辑。

### 一个刻意的字段不一致

`weekNumber` 和 `weightAdjustPct` 的取值时机**不同**：

| 字段 | 未开始 / 已结束时 |
|---|---|
| `weekNumber` | **仍有值**（日历事实） |
| `weightAdjustPct` / `deloadWeek` | **为 null / false**（训练内容） |

因为这两个字段描述的是**推荐的那个训练日**，没有推荐训练日时就该是空的。
计划还没开始时告诉用户「本周 +0%」是噪声。

### 验证结果

**单元测试 14 个**（`TrainingScheduleTest`，无 Spring，0.1 秒跑完）+ **集成测试 14 个**，
全量 **63 个全绿**。

纯函数的测试全压在边界上：

| 边界 | 断言 |
|---|---|
| 第 6 天 / 第 7 天 | 差一天就跨周。写成 `ceil(days/7.0)` 会整体偏移一周 |
| 第 56 天 / 第 57 天 | 8 周计划的最后一天仍在计划内，第 57 天才结束 |
| 超期三年 | 周次**夹在 8**，不涨到 156（涨了会去查不存在的强度修饰，静默按基准展开） |
| 不限期计划 | 三年后仍是 ONGOING，且周次是真的 157 |
| 2 天计划 × 6 次 | 逐次断言 A/B/A → B/A/B，直接对应种子文件那条规则 |
| 没有训练日 | 返回 null，而不是假装有第 0 个 |

**端到端（真实 HTTP）**，计划 12（3 周，09-21 开始）：

| 场景 | 结果 |
|---|---|
| 09-18（计划未开始） | `NOT_STARTED`，还有 3 天，不推荐训练日，但仍给出可选列表 ✅ |
| 09-21（第 1 周） | 推荐「推日」，卧推 60/60/**70**/60 kg ✅ |
| 10-05（第 3 周减量） | `deload=true`，卧推 **3 组**（4-1），36/36/**42** kg ✅ |
| 10-12（超期） | `FINISHED`，周次**夹在 3/3**，不推荐训练日 ✅ |
| `day=2` 切换 | 腿日，深蹲 5×5 @100kg，切换列表标出当前 ✅ |
| `day=99` | `40005 该计划没有第 99 个训练日` ✅ |
| 不带 `programId` | 自动挑到唯一的 ACTIVE 计划 ✅ |

第 3 周那条 `42kg` 值得看：**逐组处方的 70kg 在减量周也跟着缩放了**（70 × 0.6），
说明 2.13 / 2.14 / 2.15 三条链路串起来是对的。

### 顺带踩的坑

**access token 只有 1 小时**，e2e 验证中途过期了，收到 `10001 未登录或登录已过期`。
这不是 bug，是设计（无状态 JWT），但**验证脚本要能处理重新登录**，
否则跑到一半会误以为功能坏了。

---

## 步骤 2.16 —— Phase 2 验收 ✅

**日期**：2026-09-18

对着真实 HTTP 接口跑了一遍 M2/M3 的 Must 条目（脚本见 `docs/` 外的临时目录，
逻辑写在下面的表里）。**43 项检查：42 通过，1 项是已知内容缺口。**

> 验收的价值不在于「全部通过」，而在于**它抓出了 3 个真问题**——
> 其中 2 个是我自己写的 API 缺陷，1 个是我自己踩的坑。
> 如果没有这一步，它们会一直潜伏到写 Flutter 客户端时才爆出来。

### 验收结果

| 分组 | 项数 | 结果 |
|---|---|---|
| A 数据层 | 1 | ✅ |
| B 动作库（M2） | 12 | 11 ✅ / 1 ⚠️（M2-4 内容缺口） |
| C 计划模板（M3-A） | 5 | ✅ |
| D 自定义计划（M3-B） | 12 | ✅ |
| E 展开 / 今天练什么 | 6 | ✅ |
| F 权限（AC-1-1） | 7 | ✅ |

数据层现状：**14 张表、9 个迁移、90 个内置动作、6 个内置模板**。

### 🐛 验收抓出的三个问题

#### ① 筛选参数被静默忽略，返回全量数据（严重）

```
?movementPattern=HORIZONTAL_PUSH    ← 字段当时叫 pattern
→ 参数被忽略
→ 返回全部 90 条，HTTP 200，code=0
```

**没有任何报错。** 客户端以为筛选生效了，拿到的是全量数据。
服务端日志里一切正常，这种 bug 在客户端要排查很久。

**这是我自己的验收脚本踩到的**——我照着响应字段名 `movementPattern` 写了参数，
拿到 90 条还以为「筛选可能没配好」。

更糟的是**我的断言写得太松**：只查了 `total > 0`，所以第一次跑的时候这项是 PASS。
直到我盯着 `HORIZONTAL_PUSH=90` 这个数字觉得不对才发现的。

> **教训一：断言要检查「结果确实被过滤了」，不能只检查「有结果」。**
> **教训二：参数名和响应字段名不一致，是在给使用者挖坑。**

修了两处：
1. **改名对齐**：`muscle` → `primaryMuscle`，`pattern` → `movementPattern`
2. **加参数白名单校验**：不认识的参数直接 400，并列出所有可用参数

#### ② `WebDataBinder.setIgnoreUnknownFields(false)` 会让每个请求 500（框架陷阱）

修 ① 的时候我先试了框架自带的严格绑定：

```java
@InitBinder
public void initBinder(WebDataBinder binder) {
    binder.setIgnoreUnknownFields(false);   // ← 这一行让所有请求挂掉
}
```

结果**连不带任何参数的 `GET /exercises` 都 500**：

```
NotWritablePropertyException: Invalid property 'acceptencoding'
  of bean class [com.gymlog.exercise.dto.ExerciseQuery]
```

原因：**Spring MVC 绑定 `@ModelAttribute` 时会把 HTTP 请求头也当成待绑定属性**。
关掉「忽略未知字段」后，`Accept-Encoding` → `acceptencoding`、
`User-Agent` → `useragent` 全变成了非法属性。

框架层面**没有**「对未知查询参数报错、但忽略请求头」的开关。
最后手写了 `QueryParamGuard`——二十行，而且错误信息比框架做得清楚：

```
不认识的查询参数：muscle。可用参数：equipment、keyword、metricType、
movementPattern、onlyCustom、page、primaryMuscle、size
```

白名单**从 DTO 字段反射推导**，不手写常量列表——
手写的迟早会和 DTO 漂移，而漂移的表现是「加了新筛选条件但参数被拒」，很难排查。

#### ③ 错误信息泄露内部类名

枚举值传错时的返回：

```
Failed to convert property value of type 'java.lang.String' to required type
'com.gymlog.exercise.MovementPattern' for property 'movementPattern'
```

`com.gymlog.exercise.MovementPattern` —— **包名、类名直接给了客户端**。

根因：`handleBindException` 直接用了 `FieldError.getDefaultMessage()`。
对 `@Min` / `@NotBlank` 这类注解，`defaultMessage` 是我们自己写的文案，没问题；
**但类型转换失败（`typeMismatch`）没有自定义消息**，塞进去的是 Spring 的原始异常文本。

> 有意思的是 `handleNotReadable` 早就写了「不要把 `e.getMessage()` 直接返回，
> 它可能包含类名和字段路径」。**同一个原则，另一条路径漏了。**

修完后：

```
参数 movementPattern 的值不正确：BOGUS。可选值：HORIZONTAL_PUSH / HORIZONTAL_PULL /
VERTICAL_PUSH / VERTICAL_PULL / SQUAT / HINGE / LUNGE / CORE / CARRY /
ELBOW_FLEXION / ELBOW_EXTENSION / CALF_RAISE
```

不泄露类名、中文、而且**直接列出所有合法值**——不用去翻 Swagger。

### ⚠️ 唯一未通过项：M2-4 动作要领与常见错误（已补，见步骤 2.17）

**现状**：`exercise` 表的 `instructions` / `common_mistakes` 两列
**90 条全为空**。接口层是好的（字段有、能返回），是**种子数据没写**。

**定级**：M2-4 是 **Should**，不是 Must。Phase 2 的目标是
「能创建计划（含超级组与徒手）并查询今天练什么」，不依赖这两个字段。

**但它的影响不小**：动作详情页会有一块明显的空白，
对一个求职作品集来说，这是**看得见的未完成**。

**处理**：另起一步补内容，见下面的「步骤 2.17」。补完后**验收 43 项全通过**。

### 新增的测试（8 个）

| 测试类 | 守什么 |
|---|---|
| `QueryParamGuardTest` | 未知参数被拒；白名单**不会与 DTO 漂移** |
| `GlobalExceptionHandlerTest` | 不泄露类名；列出合法值；自定义校验文案不丢；超长值截断 |

第二个类里有两条断言值得单说：

- `plainValidationKeepsCustomMessage` —— 改异常文案处理时最容易顺手把
  `@NotBlank(message="邮箱不能为空")` 这类自定义文案也吃掉
- `truncatesLongRejectedValue` —— 回显客户端传的值是安全的，
  但**长度要有上限**，否则一个 10MB 的参数会被原样塞进错误响应

**全量测试 71 个全绿。**

### Phase 2 完成总结

#### 交付物

| 类别 | 内容 |
|---|---|
| **数据库** | 7 张新表（`exercise` / `program` / `week_template` / `day_template` / `prescribed_exercise` / `prescribed_set` / `program_template`） |
| **迁移** | V4–V9（动作库表、种子 90 个动作、别名修正、计划 5 表、模板表、种子 6 个模板） |
| **接口** | 动作库查询/详情/自定义增删改；计划 CRUD、从模板创建、结构编辑、状态切换；周期化展开；「今天练什么」 |
| **算法** | 周期化展开（纯函数）、训练日轮转（纯函数）、全量替换 + 乐观锁 |
| **测试** | 71 个（含 17 + 14 个纯函数单测，无 Spring 容器） |

#### 三个值得记住的设计

1. **`-40%` 是「减掉 40%」不是「降到 40%」** ——
   两种理解差 12kg 且不报错。语义必须写死、写进文档、写进测试。
2. **训练日按「练了几次」推进，不按日期** ——
   按周重置会退化成 A/B/A 无限重复；按星期几固定则漏练一次就乱。
   按次数推进是自愈的。
3. **全量替换的安全性依赖「会话快照存值不存外键」** ——
   这条前提写进了 REQUIREMENTS 6.3，并且有一个测试断言 id 会变，
   哪天有人改成增量更新就会红。

#### 已知欠账（带进 Phase 3）

| # | 欠账 | 影响 | 处理时机 |
|---|---|---|---|
| 1 | `SessionCounter` 恒返回 0 | 轮转永远推荐第 1 个训练日 | **Phase 3 第一件事** |
| 2 | ~~动作要领 / 常见错误全为空~~ | — | ✅ 步骤 2.17 已补 |
| 3 | 删除计划时不检查是否有训练记录 | 删了计划，历史记录指向已删除的计划 | Phase 3（session 表建好之后） |
| 4 | AC-3-1 / 3-2 / 3-3 未验证 | 它们都依赖会话快照 | Phase 3 |

---

## 步骤 2.17 —— 补充动作要领与常见错误 ✅

**日期**：2026-09-18

验收发现 M2-4 的 90 条动作**要领和常见错误全为空**，这一步补上。

### 只补 41 个，不是全部 90 个

**选择依据是可验证的，不是拍脑袋**：先用脚本从 `V9__seed_program_template.sql`
里把 6 个模板引用的动作名全提取出来，去重后正好 41 个。

```
STRONGLIFTS_5X5       5 个
PPL_3DAY             15 个
UPPER_LOWER_4DAY     22 个
BODYWEIGHT_3DAY      14 个
BODYWEIGHT_BEGINNER   8 个
PPL_6DAY             24 个
                → 去重后 41 个
```

**为什么是这 41 个**：模板是「零配置开始训练」的入口，用户必定会看到。
其余 49 个是冷门变式（各种握距、角度、器械细分），
用户自己挑动作时几乎不会翻到，补它们的边际收益很低。

> 这个「先算出真正需要的那批」的做法本身值得记：
> 90 个凭感觉补，工作量大且一半是浪费；41 个精确覆盖，
> 而且**覆盖率是可验证的**——脚本一跑就知道漏没漏。

写完用脚本交叉核对了一遍：

```
模板引用动作      : 41
迁移覆盖动作      : 41
重复的 UPDATE     : 无
模板用到但没补的  : 无 ✅
补了但模板没用的  : 无 ✅
```

### ⚠️ `WHERE` 必须限定 `user_id = 0`

唯一索引是 `(user_id, name)`，所以**用户完全可以建一个叫「杠铃深蹲」的自定义动作**。

不加 `user_id = 0` 的话，这条 UPDATE 会把内容写到**他的**动作上——
而他的动作本来应该是空的。

这个错误不会报任何异常，只是某个用户某天发现自己的自定义动作
莫名其妙多了一段他没写过的文字。

**验证过了**：建一个同名自定义动作，迁移后它的 `instructions` 仍是 null。

### 内容格式约定

```
动作要领：起始姿势 → 动作过程 → 关键提示，2–4 句
常见错误：2–3 条，每条附「为什么错」或「怎么改」
```

第二条是刻意的：写成「别弓背」没有价值，写成
「弓背启动——腰椎承受剪切力，这是硬拉最危险的错误」才有。
**错误提示要让人知道后果，他才会真的改。**

### 验证结果

| 检查项 | 结果 |
|---|---|
| 迁移执行 | `Successfully applied 1 migration, now at version v10`（31ms） |
| 有要领的动作 | **41 / 90**（正是设计的数量） |
| 有常见错误的动作 | **41 / 90** |
| 中文往返 | 无损（杠铃深蹲要领 116 字） |
| 接口返回 | ✅ |
| **同名自定义动作未被污染** | ✅ `instructions = None` |
| 覆盖交叉核对 | 41 = 41，无遗漏无多余 |
| **验收** | **43 项全通过**（此前 42/43） |
| 全量测试 | 71 个全绿 |

---

## 步骤 3.1 —— 会话三张表与实体 ✅

**日期**：2026-09-18

Phase 3 的第一步。**这是整个项目最关键的一组表**——
用户练了什么、练了多少，最终都落在这里。

### 三张表

```
workout_session          一次训练
  └─ session_exercise      快照：这次练了哪些动作
       └─ session_set_target  快照：每一组的目标
```

| 文件 | 内容 |
|---|---|
| `V11__init_workout_session.sql` | 三张表 + 索引 + 幂等唯一键 |
| `WorkoutSession` / `SessionExercise` / `SessionSetTarget` | 实体 |
| `SessionStatus` / `SessionExerciseStatus` | 状态枚举 |
| 三个 Mapper | 数据访问 |

### 快照里绝不能出现 `day_template_id` / `prescribed_exercise_id`

这是 2.14 就定下的约束（写进了 REQUIREMENTS 6.3），这一步把它落到了表结构上。

**原因**：计划结构编辑走**全量替换**，那两张表的 id 每次编辑都会变。
快照如果引用它们，用户改一次计划，所有历史会话就指向了不存在的行——
而且**不会有任何报错**，只是历史显示成空白。

所以存的是**值**：`day_number` + `day_name` + 动作名 + 处方数值。
唯一保留的外键是 `program_id`（指向计划本身，而计划走逻辑删除，行不会消失）。

### 写了一个测试专门证明这条不变量

`snapshotSurvivesProgramStructureEdit` 的流程：

```
1. 建计划：卧推 3 组 × 8-10 次 @ 60kg
2. 建会话快照（60kg）
3. 用户大改计划结构 → 全量替换成 5 组 × 3 次 @ 120kg，训练日改名为「腿部日」
4. 断言快照**原封不动**：仍是 60kg / 8-10 次 / 150 秒休息 / 组数 3 / 训练日名「推日」
```

第 3 步之后特意加了一条断言，确认**计划确实变了**（version 2，新的处方 id）。
否则「快照没变」可能只是因为改计划根本没成功——那样的测试是假的。

### `exerciseId` 和 `exerciseName` 两列都要存

看着冗余，其实**缺一不可**：

| 列 | 用途 | 什么时候会失效 |
|---|---|---|
| `exercise_id` | **聚合**——「单动作历史 + sparkline」要按动作分组 | 用户删掉自定义动作后查不到了 |
| `exercise_name` | **显示用的真相**——动作被删了改名了，历史显示的仍是当时的名字 | 永不失效 |

- 只存 id：动作一删，历史变成「未知动作」
- 只存 name：没法按动作聚合，sparkline 做不出来

这和计划里的情况不同——那边删动作会被 `EXERCISE_IN_USE` 挡住。
但**历史会话不该受这个约束：练过的记录不能因为动作下架就丢失**。

### 为什么 `session_set_target` 是表而不是 JSON 列

计划的模板结构用了 JSON（`program_template.structure`），理由是「读整份、从不部分查询」。
**这条理由在这里不成立**：

- 符合率（`Σmin(实际,计划)/Σ计划`）要在 SQL 里按组聚合
- `set_record`（实际记录）与它按 `(session_exercise_id, set_number)` 一一对应，
  「计划 vs 实际」是一次自然的 join
- 一个会话约 20–60 组，规模很小

用 JSON 的话，上面每一条都要写 `JSON_EXTRACT`。**同样的技巧，换个场景就是错的。**

### 幂等键：离线优先的必然要求

`client_key` + 唯一索引 `(user_id, client_key)`。

健身房信号差，训练结束后重试上传是常态。没有幂等键，重试 3 次就产生 3 条一模一样的训练记录（AC-5-1）。

**`client_key` 允许为 NULL**：手动补录不需要幂等。而 SQL 标准里 `NULL ≠ NULL`，
所以唯一索引不会挡住多条 NULL——这个性质在 V4 的内置动作设计里已经用过一次
（那次是**反向**利用：不能靠 NULL 表达「内置」，因为唯一约束会失效）。

测试覆盖了三种情况：同 key 冲突、多条 NULL 共存、不同用户可以用同一个 key。

### ⚠️ 踩到一个新坑：已应用的迁移不能改

写 V11 时发现 **V7 里 `program.version` 的注释已经过期**——
它还在描述「修改已开始的计划时新建一条记录」的版本化设计，
而那个设计在 2.14 已经降级了。

第一反应是回头改 V7 的注释。**但那样会让启动直接失败**：
Flyway 会校验已应用迁移的 checksum，改一个字符就报 `Migration checksum mismatch`。

**迁移文件是只追加的，不是可编辑的。**

正确的做法是把说明写在**新**迁移的头部（V11 的文件头就写了这件事），
或者在实体类的 javadoc 里说明（`Program.version` 的注释在 2.14 已经重写过）。

> 这条规则值得记住：**数据库迁移是「历史记录」，不是「当前状态的描述」。**
> 想改历史的表现形式，只能在后面追加一条修正。

### 验证结果

| 检查项 | 结果 |
|---|---|
| V11 迁移 | `success=1` |
| 表总数 | **17**（新增 3 张） |
| 表注释 | 会话动作快照 / 每组目标快照 / 训练会话 |
| 三层结构读写 | ✅ 枚举往返无损 |
| **★ 全量替换计划后快照不变** | ✅ |
| 幂等键唯一约束 | ✅ 同 key 冲突、NULL 共存、跨用户独立 |
| 补充测试 | 6 个 |
| **全量测试** | **77 个全绿** |

---

## 步骤 3.2 —— 会话创建与快照 ✅

**日期**：2026-09-18

会话的生命周期：创建（含快照）→ 完成 / 放弃。
**Phase 2 留下的第一笔欠账（`SessionCounter` 恒返回 0）在这一步还清了。**

### 交付

| 接口 | 作用 |
|---|---|
| `POST /api/v1/sessions` | 开始训练（含快照）。幂等 |
| `GET /api/v1/sessions/active` | 当前进行中的会话（断点续训） |
| `GET /api/v1/sessions/{id}` | 详情（全部来自快照） |
| `PATCH /api/v1/sessions/{id}/finish` | 结束训练 |
| `PATCH /api/v1/sessions/{id}/abandon` | 放弃训练 |

### ★ 最关键的一条：创建会话不重算「第几周第几天」

`SessionService.create` **直接调用 `TodayWorkoutService.today()`**，把它的结果原样快照。

不重算的理由：**「首页显示今天练什么」和「点开始实际练什么」必须是同一份结果。**

如果这里自己再算一遍周次和训练日，就会出现这种 bug：
首页显示「推日 · 卧推 63kg」，点开始进去却是「拉日」——
而且两边的代码单独看都是对的，只有对着用才发现。

> **「唯一真相来源」不是靠约定维持的，是靠只写一遍。**

### 快照存的是展开后的值

```
计划里写              快照里存
─────────────        ──────────────────────────────
第 2 周 +10%     →   set1 66kg   set2 66kg   set3 77kg
第三组 70kg            （60×1.1 和 70×1.1 都算好了）
```

第三组的 77kg 值得看一眼：**逐组处方的 70kg 也跟着乘了 1.10**。
存修饰的话，用户几周后再看，还得拿当时的计划重新算一遍才能知道那天推多重——
而计划早就被改过了。

### 「只能有一个进行中会话」

一个人不可能同时练两场。已有 IN_PROGRESS 会话时，`POST /sessions` **返回那一场**
并把 `resumed` 置为 true，而不是新建一条。

**为什么返回它而不是报 409**：客户端点「开始训练」时如果已经有一场没结束，
返回它 + `resumed=true` 让客户端显示「继续上次训练」，是 happy path 的一部分；
报错则逼客户端把正常状态当异常处理。

配套的逃生口是 `abandon`——用户真想换一场，先放弃旧的。

### 幂等：返回已存在的那条，不报错

```
第 1 次 POST clientKey=k-1 → sessionId=96  resumed=false
第 2 次 POST clientKey=k-1 → sessionId=96  resumed=true
第 3 次 POST clientKey=k-1 → sessionId=96  resumed=true
```

离线重试本来就不该收到错误——客户端只是想确保数据到了。

### ★ 轮转闭环（Phase 2 欠账 1 还清）

`SessionCounter` 从「恒返回 0」换成真实实现：

```sql
SELECT COUNT(*) FROM workout_session
 WHERE user_id=? AND program_id=? AND status='COMPLETED'
```

**只数 COMPLETED**：练到一半放弃的不推进轮转。
**不加日期范围**：轮转是整个计划周期内连续的，不是每周重置
（加「本周」条件会退化成 A/B/A 无限重复，5x5 的 A/B 交替就废了）。

端到端验证了完整链条：

```
第 2 步：已完成 0 次  → 推荐「推日」
第 6 步：结束训练
第 7 步：已完成 1 次  → 推荐「腿日」   ← 轮转推进了
```

测试里还验证了绕回：两个训练日，练完两场回到第一个。

### 一个被测试逼出来的设计确认

写测试时我断言「计划还没开始时不能开始训练」，结果**测试失败了**——
因为显式指定 `dayNumber` 时会绕过日程检查。

第一反应是「代码有 bug」，但回头看 `resolveDay` 的原始注释写的是
「计划还没开始（**轮转**没有意义）」——**限制的是轮转，不是用户的选择**。

于是这条行为被确认为**刻意的**，并补了一个测试把它固定下来：

```
dayNumber 为空  → 「告诉我今天该练什么」→ 要看日程
dayNumber 有值  → 「我知道我要练第几天」→ 用户说了算
```

挡掉后者会连带挡掉两件合理的事：① 计划下周一开始但今天想先练一次；
② 8 周计划走完了想再从头来一轮。而且响应里**仍然带着 `scheduleState`**，
客户端想提示「计划已结束」随时可以提示——**信息没丢，只是不替用户做决定**。

> 教训：**测试失败时，先确认是实现错了还是期望错了。**
> 这次是期望错了，而且错得有价值——它逼我把一条模糊的行为写清楚。

### 验证结果

**测试 19 个**（`SessionServiceTest`），全量 **96 个全绿**。

端到端（真实 HTTP）：

| 步骤 | 结果 |
|---|---|
| 首页推荐 | 第 2 周 +10% → 推荐「推日」 |
| 开始训练 | 卧推 3 组：**66 / 66 / 77** kg（逐组处方的 70kg 也跟着 ×1.1） |
| 幂等重试 ×2 | 始终 sessionId=96，`resumed=true` |
| 断点续训 | `GET /active` 拿到 96，状态 IN_PROGRESS |
| 结束训练 | COMPLETED，时长 3720 秒 |
| **★ 轮转** | 已完成 1 次 → 推荐「**腿日**」 |
| **★ 改计划后查会话** | 计划已改成 腿部日/深蹲/120kg/5组，**快照仍是 推日/杠铃卧推/66kg/3组** |
| 结束后新开 | 新 sessionId，`resumed=false` |
| 进行中再开 | 返回进行中的那场 |
| 放弃后重开 | 新的一场临时训练，`programId=null` |
| 权限 | 读/结束别人的会话 → `50001 训练记录不存在` |

---

## 步骤 3.3 —— 组记录 CRUD ✅

**日期**：2026-09-18

用户**实际**做的那一组：推了多少、做了几次、歇了多久。

### 交付

| 接口 | 作用 |
|---|---|
| `PUT /sessions/{sid}/exercises/{seid}/sets/{n}` | 记录 / 覆盖第 n 组 |
| `DELETE` 同上路径 | 删除第 n 组 |
| `PATCH /sessions/{sid}/exercises/{seid}/status` | 跳过 / 完成动作 |

### 为什么必须是两张表

```
session_set_target   计划：这一组**应该**推多重
set_record           实际：这一组**实际**推了多重
```

按 `(session_exercise_id, set_number)` 对齐，但**不是一对一**：
用户可以临时加组、少做几组、或者干脆不按目标重量做（这是常态）。

**合并的话「计划」和「实际」就分不开了**，而这两者的差值
正是「符合率」这个指标的全部意义。

### ★ 幂等靠业务键，不靠额外的 UUID

唯一索引 `(session_exercise_id, set_number)` 同时承担两件事：
数据约束 + 离线同步的幂等键。所以接口是 **PUT 而不是 POST**——
路径已经唯一确定了「哪个动作的第几组」，语义天然幂等。

**为什么不另加 `client_key`**：组号本来就是这个动作内的唯一标识，
再加一列只是多一个要维护的东西，而且**重试时那个 UUID 可能被重新生成**，
幂等就失效了。业务键不会。

### 补了 V11 漏掉的两个快照字段

写这一步时才发现：**容量口径依赖两个会变的量，而它们不在快照里**。

| 字段 | 为什么必须快照 |
|---|---|
| `session_exercise.bw_factor` | 自重动作容量 = 体重 × bw_factor × 次数。而 **M10-B-3 说管理员可以编辑 bw_factor**——不快照的话，管理员把引体的系数从 1.00 改成 0.95，用户三个月前的历史容量会追溯性地变小 |
| `workout_session.body_weight_kg` | 体重每周都在变，不快照的话自重动作的历史容量永远算不准 |

第二个字段**现在恒为 NULL**（身体数据是 Phase 4），但先留好——
事后加列比重构数据便宜得多。

> 这两个字段是**用需求反推出来的**：AC-7-8 说容量要按 `体重 × bw_factor × 次数` 算，
> 而 M10-B-3 说这两个量里有一个可以被管理员改。
> 两条需求放在一起看，才看得出「不快照就违反不变量 1」。

### 自动推进与它的例外

记录完一组后按「记录了几组」重算动作状态：

```
0 组            → PENDING
>= 目标组数     → COMPLETED
否则            → IN_PROGRESS
```

**SKIPPED 的例外**：跳过的动作 0 条记录，直接按规则推导会变回 PENDING，
跳过就白跳了。所以「0 组且当前是 SKIPPED」时保持不变。
但一旦真记录了组，就按记录的来——**用户动手了，说明他改主意了**。

### 🐛 测试抓到的 bug：返回的 finished 用了旧状态

`progress()` 里我写的是 `exercise.isFinished()`，而传进来的实体是
**改之前**从库里读的。结果「跳过动作」返回 `status=SKIPPED` 但 `finished=false`——
客户端据此不会跳到下一个动作，**用户点了跳过却停在原地**。

修法：`finished` 由参数 `status` 推导，不用实体。
这也顺带消除了「调用方必须先刷新实体」这个隐性约定。

### ⚠️ 测试数全表 = 环境依赖的测试

跑完全量测试发现 `SessionServiceTest` 有 3 个失败，
但单独跑那个类是全绿的。原因是断言写成了：

```java
sessionMapper.selectCount(new LambdaQueryWrapper<>())   // 数整张表
```

而**我之前跑端到端 HTTP 验证时提交了 6 条真实会话留在库里**，
于是 `expected: 1L but was: 7L`——代码一行没改。

改成按 `userId + clientKey` 限定，断言才只关于自己造的数据。

> **数全表的测试是环境依赖的测试。** 它今天绿只是因为库恰好是空的。

### 一个顺手改掉的文案问题

原来 `requireInProgress(session, action)` 的文案是
`"该训练" + action + "了，无法重复操作"`，调用方传「已完成」「已结束」这类描述。
结果「记录一组」拼出了：

```
该训练已结束了，无法重复操作        ← 不通，而且「重复操作」也不对
```

用户是第一次记录，不是重复。改成按**实际状态 + 动作动词**拼接：

```
该训练已完成，无法重复结束
该训练已完成，无法记录
该训练已放弃，无法记录
```

### 验证结果

**测试 16 个**（`SetRecordServiceTest`），全量 **112 个全绿**。

端到端（真实 HTTP）：

| 步骤 | 结果 |
|---|---|
| 记录第 1 组 | `IN_PROGRESS 1/3 组` |
| 记录第 3 组 | **`COMPLETED 3/3 组 finished=true`**（自动完成） |
| 同组号再 PUT | `3/3`——覆盖，没有新增 |
| 临时加第 4 组 | `4/3 组`（允许超过计划） |
| 删除第 4 组 | `3/3` |
| 删除不存在的组 | `code=0`（幂等，离线乱序到达不报错） |
| 跳过动作 | `SKIPPED 3/3 finished=true` |
| 跳过后再记录 | 回到 `COMPLETED` |
| **⭐ 我的会话 + 别人的动作 id** | `50001 该训练中没有这个动作` |
| 会话详情 | 目标 `60kg×8-10 ×3` 与 实际 `62kg×8 / 60kg×9 / 55kg×12` 同时返回 |
| 结束训练后记组 | `50002 该训练已完成，无法记录` |

---

## 步骤 3.4 —— 训练总结与历史列表 ✅

**日期**：2026-09-18

| 接口 | 作用 |
|---|---|
| `GET /api/v1/sessions` | 训练历史（分页，带容量与组数） |
| `GET /api/v1/sessions/{id}/summary` | 训练总结（统计 + PR + 与上次对比） |

### 口径只有一份实现

所有数值都走 `TrainingMetrics`（纯函数），Service **不做任何口径判断**。

容量规则（METRICS 4.1）：

```
仅正式组（set_type ≠ WARMUP）
WEIGHT_REPS        Σ(重量 × 次数)
REPS_ONLY（自重）   Σ(体重 × bw_factor × 次数)
DURATION 类        不计入（折算不成重量×次数）
```

**为什么必须只有一个实现**：口径一旦分叉，两处算出的容量会不一样，
而且**都不会报错**。用户看到「训练总结说 3200kg，周报图说 3050kg」，
然后开始怀疑整个 App 的数据。

### e1RM 有两份实现，靠契约测试守住

`TrainingMetrics.e1rm`（Java 纯函数）是权威实现。
但 PR 判定要「扫描全部历史取最大 e1RM」——这在 SQL 里是一次聚合，
在 Java 里要把用户所有历史拉进内存（练一年上万行，而每次打开总结都要跑）。

所以 `SetRecordMapper.bestE1rmBefore` 里重复了一次公式，并**加了一个契约测试**：
同一批数据，SQL 算出的值和 Java 算出的值必须相等。改公式时那个测试会红。

> 这是**有意识的重复**，不是疏忽。区别在于：它被记录下来了，而且有测试盯着。

### PR 的时间范围用「早于本次」而不是「排除本次」

```sql
AND ws.started_at < #{before}
```

用「排除本次会话」的话，用户回看三个月前那场训练的总结，
后来练出的成绩会把当时的 PR 抹掉——他会看到「那天一个 PR 都没有」，
而当时他明明突破了。

端到端验证了这一点：**回看第一场时 PR 仍然存在**。

### 容量和组数都给，因为它们回答不同的问题

METRICS 4.0 说过这件事，这一步兑现：容量（kg）看「总负荷涨没涨」，
组数看「练得够不够」。只给一个会让用户做出错误的训练决策。

### 与上次对比只比「同计划同训练日」

不是「上一次随便什么训练」——推日和腿日的容量本来就不在一个量级，
拿腿日跟推日比会让用户以为自己退步了。

### 顺手修的一处口径不一致

`totalReps` 原本把热身组的次数也累加了。但容量和组数都排除了热身组，
于是会显示「正式组 2 组，共 30 次」——**2 组做不出 30 次**，两个数字对不上。

要么全排除热身，要么全包含，不能只在一处例外。改成全排除。

### 一次类型一致性修正

`SessionExercise` 的 `primaryMuscle` / `metricType` 原本是 `String`，
而项目其他地方都是枚举。改回枚举：

- 容量口径要按计量类型分支，**字符串比较写错一个字母不会报错，只会静默算漏**
- 展开函数手里本来就是枚举（`exercise.getPrimaryMuscle()`），
  转成字符串再转回来只是多两次转换和多一个写错的机会

改这个连带修了三处编译错误（`ProgramExpander`、`SessionDetailResponse`、
`WorkoutSessionMapperTest`），**其中两处是测试**——说明测试确实逮住了这次重构。

### ⚠️ 又一个「测试没传 startedAt」的坑

`SessionCreateRequest.startedAt` 为 null 时默认「现在」。
测试里不传的话，所有会话的开始时间都一样——

结果是 **PR 判定和「与上次对比」全错**：更早的那场不算更早，
「历史最好成绩」查出来是空的，第二次训练被当成第一次。

三个测试因此变红。修法是测试显式传 `startedAt`。

> 这暴露了一个真实的设计含义：**`startedAt` 是业务字段，不是审计字段**。
> 排序、PR 判定、对比全都依赖它。真实客户端必须传对。

### 验证结果

**测试 23 个**（13 个纯函数 + 10 个集成），全量 **135 个全绿**。

纯函数测试的锚点直接用了验收标准给的两个数字（AC-7-6）：

```
5kg × 12  → e1RM 7kg
20kg × 3  → e1RM 22kg
```

这两个数字同时说明了「为什么不能直接比重量」：看重量是 20 > 5，
看容量是 60 = 60 打平，**只有 e1RM 能分出高下**。

端到端（真实 HTTP）：

| 步骤 | 结果 |
|---|---|
| 第一场 60kg×8/6 + 热身 40kg×12 | 容量 **840**（热身的 480 被排除），正式组 2、热身组 1、正式次数 14 |
| 第一场的 PR | 76.00，`previousBest` 为 null（第一次做） |
| 第二场 70kg×8/6 | 容量 980 |
| 第二场的 PR | **88.67，此前 76.00，提升 12.67** |
| 与上次对比 | 容量 +140kg，时长 +600s，卧推 **+10kg** |
| 历史列表 | 倒序，每条带容量与组数 |
| **回看第一场** | **PR 仍在**（`早于本次` 的范围限定生效） |

---

## 步骤 3.5 —— Flutter 骨架、网络层与最小登录闭环 ✅

**日期**：2026-09-18

Phase 3 从这一步开始进入客户端。10 个 Dart 文件、约 1100 行。

### 交付

```
app/lib/
├── main.dart                         ProviderScope + 按登录态切页
├── core/
│   ├── config/app_config.dart        后端地址（可 --dart-define 覆盖）
│   ├── network/
│   │   ├── api_client.dart           Dio 封装：拆包 / 带 token / 自动续期
│   │   └── api_exception.dart        后端 {code,message} → 异常
│   ├── storage/token_storage.dart    flutter_secure_storage
│   └── providers.dart                依赖装配点
└── features/
    ├── auth/                         登录 / 注册 / 登录态
    └── today/today_screen.dart       「今天练什么」（冒烟页）
```

### 三件事在 `ApiClient` 里统一处理

| 职责 | 说明 |
|---|---|
| **拆包** | 后端返回 `{code, message, data}`，这里只把 `data` 交出去；`code != 0` 抛 `ApiException` |
| **带 token** | 自动附 `Authorization`，业务代码不碰 |
| **自动续期** | access token 过期时用 refresh token 换新的并**重放原请求**（AC-1-2） |

### ★ 自动续期里两个必须处理的坑

**① 并发的 401 只刷新一次**

多个请求同时收到 401 时，如果各自去刷新，会同时发 N 个刷新请求。
而后端的 refresh token 是**一次性轮换**的——第一个成功、旧的立即失效，
剩下 N-1 个全部失败，**用户被踢下线**。

用一个 `Future<bool>? _refreshing` 把并发请求收敛到同一次刷新上。

**② 刷新接口自己失败时不能再触发刷新**

否则会用 refresh token 去刷新 refresh token，无限循环。
`_isAuthEndpoint()` 把 `/auth/login`、`/auth/register`、`/auth/refresh`
排除在外；另外原请求上打一个 `retried` 标记，刷新成功但接口仍返回 10001 时不再重试。

### 真机联调：`adb reverse` 而不是局域网 IP

手机上的 `localhost` 指向手机自己。常见做法是填电脑的局域网 IP，
但那要求同一网段 + 放行 Windows 防火墙入站规则，**换个网络 IP 就变**。

```bash
adb reverse tcp:8080 tcp:8080
```

走 USB，不依赖网络。代价是**设备重连后会失效**，需要重新执行——
界面的错误提示里专门写了这一条，免得下次自己踩。

### ⚠️ 网络层的设计取舍：不用 go_router

加了又删掉了。现在只有两个页面（登录 / 首页），
`switch (authState)` 就够，引入路由表只是多一层要维护的间接。

**不用的依赖是坏味道**——留着它，下一个人会以为它在起作用。
等页面到三个以上（首页 / 计划 / 历史）再引入。

### 🐛 真机验证抓到的 bug：把「没有目标重量」显示成了「自重」

代码审查看不出来，跑起来一眼就看见：

```
#1 杠铃卧推
   第 1 组 · 自重 × 6-8 · 休息 150s      ← 杠铃卧推怎么会是自重？
```

根因是 `target` 为 null 有**两种完全不同的含义**：

| 情况 | metricType | 含义 | 该显示 |
|---|---|---|---|
| 动作本身不负重（引体、俯卧撑） | `REPS_ONLY` | 真的没有重量 | 自重 |
| 计划没填目标重量 | `WEIGHT_REPS` | 用户自己决定用多重 | **重量自定** |

混为一谈会让用户以为「杠铃卧推不用加片」——**照着练是危险的**。

> 这正是 2.13 就记录过的现象：模板不可能知道你的力量水平，
> 所以从模板创建的计划**所有动作都没有目标重量**。
> 当时说「跟练界面必须处理这种情况」，这一步真的撞上了。

### 验证结果

**真机全链路验证**（Redmi K60 Ultra / Android 15 / arm64）：

| 步骤 | 结果 |
|---|---|
| `flutter analyze` | **No issues found** |
| `flutter build apk --debug` | 成功（首次 Gradle 151s，增量 42s） |
| 安装 + 启动 | 成功 |
| 登录页渲染 | 正常，中文无乱码 |
| **输入注入真的登录** | 后端日志：`POST /api/v1/auth/login` → 通过 |
| **自动带 token 调受保护接口** | 后端日志：`JWT 认证成功 \| userId=50 \| GET /api/v1/workouts/today` |
| 空状态（没有计划） | 正确显示「还没有进行中的计划」 |
| **真实数据渲染** | 从 PPL_3DAY 模板建计划后刷新：计划名、第 1 周、推日、5 个动作、每组次数与休息全部正确 |
| **token 持久化** | 杀掉 App 重开**直接进首页**，没有回到登录页 |
| 重量显示 bug | 发现并修复，重新构建后验证通过 |

**用输入注入驱动真机做端到端验证**——`adb shell input tap / text`
配合 `screencap` 截图确认每一步，不依赖人工点屏幕。

### ⚠️ 输入注入本身的坑

**键盘弹出会把布局顶上去**，之前量好的坐标全部失效。

第一次操作时我在「无键盘」状态下量了密码框坐标，
输入完邮箱后键盘已经弹出、布局上移，那个坐标落到了「去注册」上——
于是密码被追加进了邮箱框。

**正确做法**：每一步之后**重新截图量坐标**，不要复用键盘出现前的坐标。

另外 `keyevent 4`（BACK）按两次会把 App 退出到桌面——
第一次收键盘，第二次就退出了。收键盘只按一次。

### 当前进度

Phase 3 已完成 5 步（后端 4 + 客户端骨架 1）。
按「到能真实上手练一场」还需 5 步估算：
首页完善 → 跟练页（顺序版）→ 记录组 → 总结页 → 真机联调。

---

## 步骤 3.6 —— 跟练页（顺序版）✅

**日期**：2026-09-18

**Phase 3 的核心。** 状态机实现在 `workout_controller.dart`，
按计划里的建议**先做不带超级组的顺序执行版**——一次性写完很难定位 bug。

### 交付

```
app/lib/features/workout/
├── workout_models.dart        会话快照的模型（手写解析）
├── workout_api.dart           会话相关接口
├── workout_controller.dart    ★ 状态机
└── workout_screen.dart        跟练界面
```

客户端从 1100 行涨到 **2825 行**。

### ★ 三条计时设计决策，逐条落到代码

**决策 1：存绝对时间戳，不存「剩余秒数」**

```dart
final DateTime? restDeadlineAt;   // 休息结束的绝对时刻
Duration get restRemaining =>     // 现算，不是字段
    restDeadlineAt!.difference(pausedAt ?? DateTime.now());
```

切后台、锁屏会让 Dart 定时器冻结。存剩余秒数必然漂移——
锁屏 5 分钟回来会显示「还剩 40 秒」，而其实早该结束了（AC-4-1）。

存 deadline 则 `deadline - now` 永远是对的，**无论中断多久**。

配套：界面回到前台时调 `syncRestTimer()` 重新对表。
如果后台期间 deadline 已过，立刻结束休息，而不是从「还剩 40 秒」继续倒数。

**决策 2：暂停通过平移 deadline 实现**

```dart
final pausedFor = DateTime.now().difference(pausedAt!);
restDeadlineAt = restDeadlineAt!.add(pausedFor);
```

不引入第二个 `remaining` 变量——**「还剩多久」永远只有一个来源**。
存在两个真相的地方，迟早会不一致。

**决策 3：暂停只允许在休息中**

训练中是正计时，暂停没有意义——用户不会中途暂停深蹲。

### 定时器不是「每秒轮询比对」

```dart
_restTimer = Timer(remaining, () { ... });   // 到点才触发
```

真正的判断永远是 `deadline - now`，定时器只负责「叫醒」。

界面上的倒计时数字由一个**独立的 `_Ticker` widget** 每秒重建——
不是把整个页面设为 StatefulWidget。**只让倒计时那部分重绘**，
录入控件不跟着每秒重建。

### 记录失败时不往下走

```dart
} on ApiException catch (e) {
  state = s.copyWith(saving: false, error: '这一组没记上：${e.message}');
  return;   // ← 不推进
}
```

失败还继续推进的话，用户会以为这组记上了而实际丢失——
训练结束后对不上账，且**无法补救**（他已经不记得那一组做了多少次）。
停在这里让他重试，代价小得多。

### 实际休息时长靠「再 PUT 一次」补写

「这一组之后歇了多久」在完成那一组的当下还不知道。
所以休息结束时用**同一个 `(动作, 组号)` 再 PUT 一次**，把 `restActualSec` 补上。

**这是 3.3 那个幂等唯一键的直接回报**——重复 PUT 是覆盖而不是新增。
没有它就得为「补写」单独设计一个接口。

### 🐛 真机抓到两个 bug

#### ① 完成一组后没有进入休息（严重）

现象：组号推进到「第 2 组 / 共 4 组」了，**但界面还停在录入态**。
用户以为没记上，会再点一次「完成本组」。

根因：`_enterRest(s)` 里只写了 `state = s`，忘了把 `phase` 设成 `resting`。
调用方传进来的 `s` 还是 `exercising` 的拷贝。

> **这个 bug 单元测试抓不到**：`restDeadlineAt` 是对的、`setNumber` 是对的、
> 定时器也安排上了——只有「当前该显示哪一屏」错了。
> 必须真的跑一遍点一次才看得见。

#### ② 下一组没有带出上一组的实际值

M4-C-3 要求「默认为上一组的实际值」。我写成了重置回计划值，
于是用户每组都要重新按一遍步进器。

按计划 60kg 做完第一组、发现状态好加到 65kg，
第二组默认还给他 60kg —— 这跟需求正好相反。

修法：从 `lastCompleted` 带出，但**只在同一个动作内延续**，
换动作了当然要用新动作的目标。

### ⚠️ 构建踩坑：Kotlin 增量编译缓存损坏

```
Could not close incremental caches in .../caches-jvm/jvm/kotlin:
  class-fq-name-to-source.tab, source-to-classes.tab, ...
```

`flutter clean` **解决不了**（缓存可能在 Gradle 共享目录里而不在 `build/` 下），
停 Gradle 守护进程也没用。

最终在 `android/gradle.properties` 里关掉增量编译：

```properties
kotlin.incremental=false
kotlin.incremental.useClasspathSnapshot=false
```

代价是 Kotlin 每次全量编译，换来**构建不再随机失败**。
文件里写了完整的排查过程和恢复方法。

### 验证结果

**真机全流程跑通**（Redmi K60 Ultra）：

| 步骤 | 结果 |
|---|---|
| `flutter analyze` | No issues found |
| 「开始训练」 | 后端 `创建训练会话 \| sessionId=674 \| 动作数=5` |
| 跟练页渲染 | 动作名 / 第 1-5 个动作 / 第 1 组共 4 组 / 目标 6-8 次 / 休息 150s |
| 步进器 | 重量 ±2.5kg 到 **60.0**，次数 ±1 到 **8** |
| **「完成本组」落库** | `set_number=1, WORKING, weight=60.00, reps=8` |
| **休息倒计时** | 150 秒的休息显示 **2:25**（减去已过的 5 秒，准确） |
| 下一组提示 | 「杠铃卧推 · 第 3 组 · 重量自定 × 6-8」 |
| ±15s / 暂停 / 跳过休息 | 正常 |
| **跳过休息后补写实际时长** | 第 2 组的 `rest_actual_sec = 21` |
| **断点续训** | 重开 App 再进跟练，后端 `已有进行中的会话，返回它`，界面恢复到「第 2 组」 |

### 已知缺口（下一步处理）

| # | 缺口 | 说明 |
|---|---|---|
| 1 | **超级组未实现** | 界面会显示「超级组 · 暂不支持」的角标——**明确提示而不是静默按顺序做**，否则用户会以为组间不休息的编排生效了 |
| 2 | 恢复后不带出上一组的实际值 | `_initialState` 没填 `lastCompleted`，续训后默认值退回计划值 |
| 3 | 训练总结页是占位 | 容量 / PR / 与上次对比来自 3.4 的接口，界面还没做 |
| 4 | `clientKey` 没落盘 | 崩溃重试会生成新 key，幂等失效。离线队列那一步（3.13）修 |
| 5 | 无本地持久化 | 杀进程后状态从**服务端记录**恢复（已验证可行），但离线时会丢 |

---

## 步骤 3.7 —— 训练总结页 ✅

**日期**：2026-09-18

练完之后那一屏（M4-B-7）。接的是 3.4 就备好的 `GET /sessions/{id}/summary`。

### 客户端不做任何口径计算

容量、PR、与上次对比**全部由后端算好**。

客户端自己算一遍的话，训练总结和周报图会给出不同的数字——
而**两个数字都不会报错**，用户只会开始怀疑整个 App 的数据。

### 容量和组数并排显示，不能只留一个

```
训练容量          正式组数
  480 kg            2 组
```

METRICS 4.0：两者回答的是不同问题——
容量看「总负荷涨没涨」，组数看「练得够不够」。

同样是 15 组，用 60kg 做和 80kg 做，训练刺激完全不同。
只看组数会漏掉「负荷翻倍」这个进步；只看容量会被大肌群主导，
手臂和肩被压成看不见的线。

### 🐛 又抓到一个 bug：训练时长少算

现象：一场跨了几分钟的训练，`duration_sec` 只有 **24 秒**。

根因：`restore()`（以及 `start()` 复用已有会话时）把 `_sessionStartedAt`
设成了 `DateTime.now()`，**丢掉了服务端记录的真实开始时间**。

**每次续训都会少算**——用户练了 40 分钟，接了个电话重开 App，
最后记录成「练了 2 分钟」。

修法：`WorkoutSession` 解析服务端的 `startedAt`，用它而不是本地时钟。

**验证方式特意设计成能区分修复前后**：

```
开始训练 → 杀掉 App → 等 40 秒 → 重开续训 → 结束
修复前：duration ≈ 15 秒（从重开算起）
修复后：duration = 64 秒（started_at 15:32:01 → finished_at 15:33:07，实际 66 秒）
```

### 顺手补的缺口

续训时不再把 `lastCompleted` 留空——从已有记录里推出最后一条填进去。
这样「下一组带出上一组实际值」（M4-C-3）在恢复之后同样生效。
不补的话，用户上次特意加到 65kg，重开 App 后又变回 60kg。

### 验证结果

**真机全流程跑通**（Redmi K60 Ultra）：

| 检查项 | 结果 |
|---|---|
| `flutter analyze` | No issues found |
| 结束确认弹窗 | 「结束训练？还没完成的动作会被标记为跳过。」 |
| 总结页渲染 | 训练日 + 时长 + 容量 + 组数 + 明细 + PR |
| **数字正确性** | 容量 **480 = 60×8 + 0×6**；正式组 **2**；总次数 **14 = 8+6** |
| **e1RM** | **76.0 = 60 × (1 + 8/30)**，与 3.4 的验收标准一致 |
| PR 卡片 | 「杠铃卧推 · 第一次记录 · 估算 1RM 76.0 kg」 |
| 无对比时的提示 | 「第一次练这个训练日，没有对比」 |
| 会话状态 | `COMPLETED` |
| **续训后时长正确** | `duration_sec = 64`（实际 66 秒） |

### 🔧 训练时长的算法改了（用户提问后修的）

原本是「**点开始 → 点结束**」的墙上时间。用户问「这个训练时长是怎么算的」，
一查发现它和 V11 注释里写的设计**对不上**——

> V11 注释：「训练时长由客户端记录后上报，**不在这里算 (finished_at - started_at)**。
> 因为用户可能中途暂停（接电话、等器械），墙上时间不等于实际训练时间，
> 而暂停时长只有客户端知道。」

**那句话描述的是一个当时并未实现的设计**：客户端确实上报了，
但上报的恰恰就是墙上时间，没有任何暂停扣除。

用真实数据看：

```
id    started_at            finished_at           墙上时间   记录时长
674   15:17:39              15:29:57              738 秒     24 秒   ← 双重错误
675   15:32:01              15:33:07               66 秒     64 秒
```

674 那场的组记录时刻是 **15:18:28** 和 **15:20:17**——用户实际在练的
大约就是这 2 分钟，墙上却跨了 12 分钟。

**新算法（服务端推算，不再让客户端上报）**：

```
时长 = 最后一组的完成时刻 − 会话的 started_at
```

|  | 旧：点结束的时刻 | 新：最后一组的时刻 |
|---|---|---|
| 组间休息 | ✅ 算 | ✅ 算 |
| 第一组前的准备 | ✅ 算 | ✅ 算 |
| **练完忘记点结束** | ❌ **算进去** | ✅ 不算 |
| 中途长时间走开 | ❌ 算进去 | ❌ 仍算进去（需会话级暂停） |

**关键好处是自我修正**——不依赖用户在正确的时间点按按钮。

而且数据本来就在库里，**没必要让客户端算一遍再传**
（674 那个 bug 就是客户端算错造成的）。

客户端上报的 `durationSec` 保留为兜底，只在**一条组记录都没有**时使用。

**实测验证**（故意练完等 60 秒再点结束）：

```
started_at      15:44:03
最后一组完成     15:44:48
记录的时长       45 秒    ← 正确
墙上时间         115 秒   ← 那 60 秒被排除了
```

**三个测试锁住这个行为**：正常推算、无组记录时兜底、时钟偏慢时不出现负数。

> V11 的列注释已经用 **V13 迁移**更新了——
> 已应用的迁移不能改（Flyway checksum），只能在新迁移里澄清。
> 这个处理方式在 V11 头部也用过一次（说明 V7 的 `version` 注释过期）。

### 📋 逐动作明细：紧凑记法 + 可展开

用户给了一个参考格式：

```
动作 + 组数×次数 + 重量/强度
  "深蹲 5×5 @80%1RM，周容量 25次"
  "卧推 4×8×80kg，容量 2560kg"
```

**但这个格式是「处方记法」，不是实际记录**——「深蹲 5×5 @80%1RM」
是「应该做多少」。训练总结要回答的是「**我今天实际练了什么**」。

而且我们的数据里**计划值和实际值都有**，这正好是「符合率」这个指标的
全部意义——不用起来可惜了。

（另外「胸 14组/周」是 METRICS 4.2 的肌群周组数，属于 Phase 4 的图表，
不是单次训练总结该放的东西。）

**最终形态**：

```
本次训练
点动作可以展开看计划与实际对照

杠铃卧推              60×6 ×3        ⌄
3/4 组               1080 kg
```

展开后：

```
  组    计划      实际
  1     6-8      60×6
  2     6-8      60×6
  3     6-8      60×6
  4     6-8      未做           ← 灰色
```

**紧凑记法的规则：连续的相同组折叠，不同的展开。**

```
20×10, 20×10, 20×10   →  20×10 ×3
60×8,  60×8,  65×6    →  60×8 ×2 · 65×6
60×8,  65×6,  60×8    →  60×8 · 65×6 · 60×8   （不连续，不折叠）
```

一套规则同时覆盖两种情况：不折叠的话 4 组要占满一行还写不下；
无条件折叠（只看第一组）又会把递减组写成假的。

**服务端不拼这个字符串**——「重复的组要不要合并」是展示逻辑，
改一次就要动服务端。结构化数据出，排版留在客户端。

### ✏️ 休息时也能改下一组的重量/次数

用户问：「在动作的间歇组间，可不可以修改实际的训练次数和重量，
不一定和训练计划完全一致」

**分成两半，答案不一样**：

| 问题 | 改前 |
|---|---|
| 实际值能不能和计划不一致？ | ✅ **本来就可以**。计划只是默认值，后端存的是用户填的值 |
| 能不能在**组间休息时**改？ | ❌ **不能**。步进器只在 `exercising` 时显示 |

第二点是真实的摩擦：做完一组坐下来，趁两分钟休息把下一组的重量调好，
是很自然的动作，而改前必须等点了「开始本组」才能调——
**人正坐着看倒计时的时候，恰恰是最想调的时候。**

**根因**：草稿值（`_weight`/`_reps`）存在界面局部状态 `_ActiveViewState` 里，
休息界面根本看不到。

**修法**：把草稿提到状态机（`WorkoutState.draftWeight` / `draftReps`），
两个界面共享同一份。顺带消除了一个隐患——以前在动作内导航离开再回来，
草稿会重置。

休息界面用的是**小一号的紧凑步进器**（48dp 而不是 64dp）：
休息界面的主角是倒计时，录入控件不该抢视觉重心，但功能完全一样。

**验证**（引体向上，计划 6-10 次）：

```
休息界面把次数从 26 调到 9（计划外）
  ↓ 跳过休息 → 开始本组
第 2 组界面显示「9」   ← 草稿带过来了
  ↓ 完成本组
数据库：set 1 reps=26, set 2 reps=9   ← 改的值真的入库
```

训练中界面也加了一行提示：「计划只是建议值，按实际状态改就行」——
用户不该觉得必须按计划做。

### 一个开放的设计问题

「结束训练」目前把会话标成 `COMPLETED`，**会推进训练日轮转**。

但用户只做了 2 组就点结束——这算「完成了一场」吗？

- 按 `SessionCounter` 的原则（只数 COMPLETED），它算了
- 但另一个原则是「练到一半不该推进轮转」

**两者的区别在于用户是否明确表示「这场结束了」**：
主动点结束 = 结束了（哪怕只练了 5 分钟），
被叫走/杀进程 = 没结束（会话留在 IN_PROGRESS，下次续训）。

所以当前行为是对的。但**「一组都没做就点结束」**这种情况应该算
`ABANDONED` 而不是 `COMPLETED`——留待后续判断。

### 当前进度

| | |
|---|---|
| Phase 3 后端（3.1–3.4） | ✅ 完成，135 个测试 |
| Phase 3 客户端（3.5–3.7） | ✅ 骨架 / 跟练页 / 总结页 |
| 客户端代码 | **16 个文件、3391 行** |

**现在这台手机可以完整走完一场训练**：开始 → 跟练 → 休息 → 记录 → 结束 → 总结。

剩下的：首页完善（断点续训入口 + 从模板建计划）、超级组、离线队列、音频隔离。

---

## 步骤 3.8 —— 首页完善：模板库 + 断点续训入口 ✅

**日期**：2026-09-18

首页之前是个「冒烟页」：没有计划时只说一句「还没有进行中的计划」，
练到一半退出后也没有任何回到训练的入口。这一步把两个缺口补上。

### ① 没有计划 → 直接给模板库

这是**新用户的第一屏**。没有它的话，用户注册完进来看到一句
「还没有进行中的计划」就不知道下一步该干什么。

六个内置模板带完整信息展示：目标标签（力量/增肌/综合）、描述、
每周频率与总周数、难度、预计时长、所需器械。

**创建前会问一次开始日期**，因为：
- 这个日期决定「今天算第几周」，默认今天会让想下周开始的人算错周期
- 而且**创建之后没有地方能改它**（计划结构编辑不涉及元信息），
  所以必须在这里问

### ② 有未完成的训练 → 顶部横幅

```
▶ 继续上次训练                    >
  A 日 · 已完成 0/3 个动作
```

放在首页**最上方**而不是做成一个普通按钮——它比「开始新训练」更优先：
上一场没结束，现在开始新的只会把旧的晾在那里。

⚠️ 「未完成」不等于「练了一半放弃」：锁屏、切后台、杀进程，
只要没点结束，会话就还是 IN_PROGRESS。**训练场上被打断是常态**，
这条横幅就是让他能接着练。

### 🐛 真机抓到的 bug：横幅永远不出现

数据库里会话明明是 `IN_PROGRESS`，界面却没有横幅。

根因：`activeSessionProvider` 是**带缓存的 FutureProvider**。
它在首页首次加载时就取过了（那时还没开始训练），
用户点「开始训练」创建会话、再用返回箭头退出时，**没人让它失效**。

修法：`await Navigator.push(...)` 之后 invalidate。

**验证时踩了一个自己的坑**：第一次验证时我 curl 放弃了会话、
又去点刷新，但截图拍到了放弃之前的残留渲染，
日志时间线（`GET /sessions/active` 在 16:16:03、`abandon` 在 16:16:32）
才把真相说清楚。

重做了一遍干净的三步验证：

```
① 重开 App（库里无 IN_PROGRESS）      → 无横幅  ✓
② 点「开始训练」                       → POST /sessions，建了 1042
③ 点返回箭头退出（不结束训练）
   → 日志出现 GET /sessions/active     ← 刷新发生了
   → 界面出现「继续上次训练」横幅        ✓
```

### 一个刻意的取舍

横幅的 provider **忽略 loading 和 error**：

```dart
final session = ref.watch(activeSessionProvider).asData?.value;
if (session == null) return const SizedBox.shrink();
```

横幅是锦上添花的东西，拉取失败时**安静地不显示就好**，
不该在首页上弹一个错误——用户此刻关心的是「今天练什么」，
不是「会话列表查不到」。

（顺带：`asData?.value` 而不是 `.value` / `.valueOrNull`——
后两者的名字在 Riverpod 2 和 3 之间变过，`asData` 是稳定的。）

---

## 步骤 3.11 —— 音频隔离：提示音不打断音乐 ✅

> 按计划里的建议**提前到这里做**（原排在超级组之后）。
> 理由：它是 Phase 3 唯一的硬性要求，而且一旦 `audioplayers`
> 默认就抢焦点，整个提示音方案要推倒重来——越早验越便宜。

> **这是 TIMER-SPEC 里唯一的「硬性要求」标注项（3.4.1 / AC-4-8 / AC-4-9）。**
> 先做它的理由：如果 `audioplayers` 默认会抢音频焦点，
> 整个提示音方案得换，留到后面发现的代价大得多。

### 为什么这是「能否使用」而不是「体验优化」

健身房里绝大多数人戴耳机听歌训练。**提示音每 90 秒打断一次音乐，
用户会直接卸载**——他不是觉得不好用，是根本用不下去。

三条要求缺一不可，而且第三条是根本原因：

| 要求 | 违反的后果 |
|---|---|
| 不暂停其他 App 的音乐 | 每 90 秒音乐停一次 |
| 不降低其他 App 的音量 | 音乐一直在 duck，忽大忽小 |
| **不夺取音频焦点** | 前面两条都是它的结果 |

### Android：要的就是这三行

```dart
contentType: AndroidContentType.sonification,          // 合成提示音，不是音乐
usageType:   AndroidUsageType.assistanceSonification,  // 辅助提示
audioFocus:  AndroidAudioFocus.none,                   // ★ 根本不申请焦点
```

`audioFocus: none` 不是「音量小一点」，是**连焦点请求都不发**。
核对 `audioplayers_android 5.3.0` 的 `FocusManager.kt` 确认过：
`AUDIOFOCUS_NONE` 时 `audioFocusRequest` 为 `null`，
`requestAudioFocus()` 里那句 `audioFocusRequest!!` 永远不会被执行到。

### ★ 三个「文档没说、读源码才发现」的坑

**坑 1：iOS 的 `ambient` 配 `mixWithOthers` 会直接断言失败。**

规格里写的是 `.ambient`，但 `AudioContextIOS` 的构造函数里有 assert：
`mixWithOthers` 只能配 `playback` / `playAndRecord` / `multiRoute`——
`ambient` 本身就隐含混音，不能显式声明。照抄规格会**在 debug 模式下直接崩**。

最后选了 `playback` + `mixWithOthers`（规格 3.4.1 允许的另一个选项）。
理由不只是断言：**`ambient` 会被静音拨片静音，而音乐 App 用的 `playback` 不受影响。**
用户开着静音听歌时，音乐正常播放而我们的提示音一声不响——
比打断音乐更糟，因为它是**静默失效**，没人会想到去查。

**坑 2：`flutter_tts` 真正决定「抢不抢焦点」的是 `speak()` 的一个 bool。**

```dart
Future<dynamic> speak(String text, {bool focus = false})   // ← Android 专有
```

原生侧 `if (focus) { requestAudioFocus() }`，而它请求的是
`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`——**音乐立刻被压低**。

也就是说 **TTS 默认不抢焦点**，但只要有人顺手写成 `speak(text, focus: true)`
就会破坏隔离，而且症状很隐蔽（只有语音播报时音乐变小，提示音正常）。
所以代码里**显式传 `focus: false`**，把默认值变成明文契约。

**坑 3：`setAudioAttributesForNavigation()` 不收任何参数。**

它内部写死 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + `CONTENT_TYPE_SPEECH`。
我一开始按「导航模式」猜了一堆参数名，编译器直接拦下了。

### 语音播报默认关闭（规格 3.4）

主通道是**提示音 + 震动**：不要权限、不要网络、不挑设备。
语音是用户主动打开的增强项。

顺带解开一个耦合：`flutter_tts` 在引擎就绪前会**挂起所有方法调用**，
所以 `await setLanguage(...)` 要等 TTS 引擎初始化完才返回——
设备没装中文引擎时这个 Future 可能**永远不完成**。
如果让提示音去 await 它，结果是**没装 TTS 的手机连提示音都不响**。
所以 TTS 改成「用户打开语音时才初始化」，默认路径完全不碰它。

### 真机验证：不看「听起来没问题」，看焦点栈

验收方式规格里写的是「用网易云音乐播放歌曲跑完整场跟练，音乐全程不得中断」。
但「我听着没问题」不是证据。真正可查的是**系统侧的焦点记录**：

| 查什么 | 命令 | 说明 |
|---|---|---|
| 当前焦点归属 | `dumpsys audio` → `Audio Focus stack` | 谁持有着焦点 |
| **持有者有没有被通知丢失** | 同上，看 `loss:` 字段 | `loss: none` = **从没被要求让路** |
| 其他 App 是否还在放 | `dumpsys media_session` | `state=PLAYING` |
| 我们有没有请求过焦点 | `dumpsys media.metrics` → `audio.focus` | `requestAudioFocus` 一条都不能有 |

**`loss: none` 是最关键的一条**：它是「持有焦点的那个 App 有没有被告知
有人来抢」的直接记录。`GAIN_TRANSIENT_MAY_DUCK` 会把它变成
`LOSS_TRANSIENT_MAY_DUCK`——**这一个字段就能证明「没有 duck」**，
不需要去测音量。

### 验证结果

真机（Redmi K60 Ultra / Android 15），网易云音乐播放中，
`完成一组 → 180 秒休息 → 全程自然走完`：

```
第一轮：61 次采样（每 3 秒一次，覆盖 200 秒）
第二轮：59 次采样（改写代码后重跑，冷启动 + 180 秒休息）
  netease:  gain: GAIN  ·  loss: none  ·  PLAYING      ← 120/120 次全一致
  本 App:   从未出现在焦点栈里                          ← 0 次
media.metrics 全量焦点事件:
  uid 10221（本 App）: 1 条，且是 abandon，**没有任何 requestAudioFocus**
  uid 10056（网易云）: 4 条 requestAudioFocus，0 条 abandon
  全库 duck / LOSS_TRANSIENT 事件: 0 条
```

同时用 logcat 确认提示音**真的播了**（不然「音乐没被打断」毫无意义）：

```
16:34:52.177  start(10823) / 16:34:52.450 stop   ← 休息过半，1 声（273ms）
16:36:18.701  start(10824) / ...stop             ← 剩 3 秒，第 1 声（192ms）
16:36:19.105  start(10824) / ...stop             ← 第 2 声（147ms），间隔 404ms
16:36:19.517  start(10824) / ...stop             ← 第 3 声（122ms），间隔 412ms
16:36:21.716  start(10825) / 16:36:22.345 stop   ← 归零，1 声长音（629ms）
```

**结果：三条要求全部满足。**

顺带验到一件事：**三声提示音和归零声的间隔实测 3.015 秒，设计值 3.000 秒，
误差 15ms。** 这是「存绝对时间戳、不存剩余秒数」那套设计在真机上的兑现。

### 🐛 抓到一个真问题：整个会话的第一次播放慢半拍

上面那组时间戳一算就露馅了：

```
归零声 − 过半声 = 89.539 秒   （设计值 90.000）→ 过半声晚了 461ms
归零声 − 第1声  =  3.015 秒   （设计值  3.000）→ 正常
```

**同一个会话里，第一次播放晚了 461ms，后续只差 15ms。**

原因在 `SoundPoolPlayer.kt`：音源是在 `setSource` 时才
`soundPool.load()` 的，而 `load()` **是异步的**，`play()` 要等
`prepared == true` 才真的出声；一个播放器还会在换音源时
`unload` 掉上一个。

对一个「还剩 3 秒」的提示音来说，461ms 是 15% 的误差——不能接受。

**修法两步：**

1. **三个音效各用一个播放器**，`init()` 时就把音源 `setSource` 好。
   一个播放器轮换三个音源会反复 unload/load，等于白预加载。
2. **预热输出通路**：`setVolume(0)` 静音跑 120ms 再停。
   播放器对象建好 ≠ 音频通路建好，第一条 `AudioTrack` 要等真正出声才创建。
   这一下发生在用户还在看屏幕的时候，不在倒计时里。

> ⚠️ 预热后**必须把音量恢复成 1**。忘了这一步，之后所有提示音都是静音的——
> 而「静音」和「正常」从代码上看起来一模一样，极难排查。

**修完再测一遍**（同样是冷启动 + 180 秒休息，同样的测法）：

| 指标 | 修之前 | 修之后 |
|---|---|---|
| 过半声相对归零声的偏差 | **+461 ms** | **−69 ms** |
| 第一声提示音创建的 AudioTrack | 新建 `10823`（含 HAL 配置） | **复用预热时的 `10826`** |
| 三声提示音间隔（代码写 400ms） | 404 / 412 ms | 381 / 406 ms |

logcat 里最直接的一条证据是 **AudioTrack 的 id**：

```
16:40:24.305 start(10826) ...   ← 预热，静音 106ms
16:42:08.993 start(10826) ...   ← 过半声，复用同一个 track，264ms 出声
```

修之前那次，过半声是 `start(10823)`——**整条音频通路是那一刻才建起来的**。

剩下的 −69ms 是两次播放各自的启动抖动（`stop()` + `resume()` 两个平台通道往返），
方向甚至反了过来，属于正常范围。

### 一个看着吓人、其实无害的事件

`media.metrics` 里本 App 有且只有一条焦点事件：

```
{callingPackage=com.gymlog.gym_log,
 clientName=...ModernFocusManager$$ExternalSyntheticLambda0,
 event#=abandonAudioFocus}
```

只 abandon、没有对应的 request。原因是 `AudioPlayer()` 构造函数用的是
**默认 context（`audioFocus: gain`）**，`FocusManager` 在 `init{}` 里
就已经按 gain 建好了 request 对象；随后 `setAudioContext(none)` 把它置空，
而 `setPlayerMode(lowLatency)` 换掉底层播放器时，旧对象走了释放路径。

**没调过 `requestAudioFocus` 就没有焦点可放弃**，所以它对网易云毫无影响——
上面 61 次采样里 `loss: none` 全程没变过就是证据。

但这条值得记下来：**它意味着「先 `setAudioContext` 再干别的」是必须的**，
顺序反了（比如先 `play()` 再设 context）就会真的抢到焦点。
代码里靠 `init()` 被 await + `_play()` 内部 `await _ensurePlayer()` 保证。

---

## 步骤 3.15 —— 时长类动作的组内计时（平板支撑倒计时）✅

> 编号用 3.15 而不是接着 3.11 —— 3.12 已经被「本地 SQLite」占了，
> 而这一步是插进来做的（用户提问触发），不是路线图里的原定顺序。

### 起因：时长类动作在跟练页基本是坏的

用户问「平板支撑这种能不能计时」。查下去发现不是一个新功能，是三处已有问题：

| 问题 | 表现 |
|---|---|
| 跟练页**什么都不显示** | `needsWeight` / `needsReps` 对 `DURATION` 都是 false，那屏只剩一句「计划只是建议值」 |
| `duration_sec` **从来没被采集过** | `completeSet` 有 `durationSec` 形参、API 也透传，但界面从来不传。全库 27 条 `set_record`，`duration_sec` **全是 NULL** |
| 秒数**塞在次数字段里** | 模板里写着 `"targetRepsMin":30,"targetRepsMax":60,"note":"目标是秒数"`——靠一句中文注释提示，后端没有一行代码知道这件事 |

`AC-7-9`（平板支撑不计入容量、单独统计总时长）因此一直拿不到数据。

### 设计：倒计时和超时是**同一个数的两种符号**

用户要的是「倒计时，撑过头自动转正计时」。有两种实现法，选了第三种：

```
已用 = now − phaseStartedAt
剩余 = targetDurationSec − 已用     （正数 = 倒计时中）
超时 = 已用 − targetDurationSec     （正数 = 已超目标）
```

**不新增 `holdDeadlineAt` 字段**，全部由已有的 `phaseStartedAt` 现算。理由是它一次性绕掉一整类 bug：

- `copyWith` 里 `restDeadlineAt ?? this.restDeadlineAt` **无法置 null**，新增字段就得再加一个 `clearXxx` 标志
- 离开 `exercising` 有**四个出口**（完成最后一组 / 跳过 / 结束 / reset），每个都要记得清
- 忘了清的话，下一个时长动作在 `preparing` 阶段会显示一个早过期的倒计时——变成「粘性标志」

改成现算之后，「到点」纯粹是一个**时间事实**而不是状态转移：归零那一刻不改 state，Timer 被系统吞掉的最坏后果只是**少响一声**，显示永远是对的。

### 三个提示音的排布

| 时刻 | 声音 |
|---|---|
| 每 `announceIntervalSec` | 一声短音（+ 语音「还剩 N 秒」） |
| 剩余 3 秒 | 三声短音 |
| 归零 | 一声长音，转入超时 |
| 超时期间每 `announceIntervalSec` | 一声短音（+「已超 N 秒」），**最多 60 秒** |

两个细节：

- **归零那一刻的间隔 tick 要跳过**（`left` 落在 `[0,3]` 区间就不响），否则长音和 tick 会挤在一起变成一团噪音
- **超时播报必须封顶**：平板支撑做到力竭直接趴地上、忘了点完成是常见场景，而跟练页开着 Wakelock，不封顶手机会一直在旁边响

### ⚠️ 迁移里的坑：MySQL 的 JSON 列会**重排 key**，REPLACE 静默失效

最直觉的写法是拿种子文件里的原文做子串替换：

```sql
REPLACE(structure, '"targetRepsMin":30,"targetRepsMax":60', ...)
```

**这个字符串在数据库里一次都不出现。** 实测 `LOCATE` 返回 **0**。两个原因叠加：

1. MySQL 的 JSON 列是**二进制格式**，读出时按「key 长度、再按字节序」**重排 key**。`targetRepsMax` 和 `targetRepsMin` 都是 13 字节，比到第 12 个字符 `'a' < 'i'`，所以 **Max 排在 Min 前面**
2. 渲染时 key 和值之间**带空格**：`"targetRepsMax": 60`

于是 `REPLACE` **匹配 0 行、静默成功**，Flyway 报告迁移通过，而模板 JSON 根本没改。后面「从模板建计划 → 没拿到 targetDurationSec → 跟练页没有倒计时」——整条链路没有任何一层会报错。

**改用 `JSON_SET` + 数组下标**，并且在 `WHERE` 里用**动作名做守卫**（下标指的不是平板支撑就一行都不改）。下标看似脆弱，但 V9 是冻结的（Flyway checksum 不允许改），种子数据在任何环境都完全一致。

迁移末尾还加了一道**断言**：两个模板必须都长出 `targetDurationSec`，否则用 `SELECT 1 UNION ALL SELECT 2` 造一个错误 1242 让**整个迁移失败**。

> 断言的写法本身也踩了一下：`JSON_EXTRACT(structure, '$.days[*].exercises[*].x') IS NOT NULL` 是**恒真**的——
> 带 `[*]` 通配时返回的是**数组**（没匹配到就是 `[]`），永远不为 NULL。
> 要用 `JSON_CONTAINS_PATH`。

### 🐛 顺带抓到三个已存在的静默 bug

**1. `restActualSec` 多算了整组时长**

`phaseStartedAt` 全项目只有两个用处：`beginSet` 里写、`_recordActualRest` 里读（算 `now − phaseStartedAt` 当「实际休息时长」）。但 `_enterRest` **从不重置它**，所以算出来是**组内用时 + 休息时长**。

真机数据坐实（对比 `session_set_target.rest_sec`）：

| set_record.id | 计划休息 | 实际记录 |
|---|---|---|
| 567 | 180s | 182s |
| 566 | 180s | **196s** |
| 108 | 150s | 155s |

差值恰好是那组的用时。修法：`_enterRest` 里一并写 `phaseStartedAt = now`，让它的语义变成「**当前阶段**的起点」——对休息计时和组内计时都成立。

**2. `duration_sec` 会被休息结束的那次 PUT 抹成 null**

记录组是 `PUT`，而服务端是**全量覆盖**语义（不是 PATCH）：没传的字段一律写 null。休息结束时会用同一组**再 PUT 一次**补写 `restActualSec`，那次没带 `durationSec` → 刚记好的时长被静默抹掉。

而且**只有非最后一组会被抹**（最后一组走 `exerciseDone`，不经过休息），现象是「同一个动作里前几组没时长、最后一组有」——极难排查。

顺带发现 `workout_api.dart` 里有一条**错误的注释**写着「后端对『没传』和『传了 null』的处理不同——前者保留原值」。已改正。

**3. 首页显示 `重量自定 × null-null`**

V14 把秒数从 `targetRepsMin/Max` 搬走之后，首页的时间线拼出来是字符串 `"null-null"`。

这是「**字段搬家**」最容易漏的一类地方：后端改了契约，**每一个曾经读过旧字段的界面都要跟着改**，而漏掉的地方不报错，只在界面上印一个 `null`。跟练页我改了两处（目标卡、休息页预览），首页那一处是真机上才看见的。

### 🐛 真机抓到的 UI bug：弹出菜单里的开关不动

声音开关放在 AppBar 的一个弹出菜单里。拨「语音播报」时，**日志显示 `setVoiceEnabled(true)` 执行了（TTS 都初始化了），但截图里开关还停在原位**。

原因：弹出菜单渲染在 **overlay 路由**里，是另一棵 widget 树。外层 `_SoundToggleButton` 确实 watch 了 provider、也确实重建了，但**已经打开的那个菜单不会跟着重建**。用户会以为没点上，再拨一次，又拨回去了。

修法：把菜单内容本身改成 `Consumer`，让它在菜单那棵树里 watch。

### 验证结果

**后端**

```
mvn clean test → 139 通过（新增 1 条模板守卫）
```

迁移验证（**先造数据再迁移**——直接跑的话数据搬迁是空操作，什么也证明不了）：

| 检查 | 结果 |
|---|---|
| 迁移前建的计划（program 1312） | `prescribed_exercise.id=2185`：`30/60` → `target_duration_sec=30`、`announce_interval_sec=10`、reps 置 NULL |
| 两个模板的 JSON | `BODYWEIGHT_3DAY` → 30/10；`BODYWEIGHT_BEGINNER` → 20/10；reps 字段已清 |
| 模板 → `GET /workouts/today` | 平板支撑 3 组均带 `targetDurationSec:30 / announceIntervalSec:10`，`targetReps` 为 null |
| 会话快照 `GET /sessions/{id}` | 同上 |
| **编辑计划往返** | `PUT /programs/{id}/structure` 原样回传后，`prescribed_exercise` 行 id 从 2535 变成 **2554**（确认发生了删重建），字段**仍在** |

新增的 `ProgramTemplateTest` 用例**验证过它真的会失败**：人为把模板数据改坏跑了一次，它精确定位到「BODYWEIGHT_3DAY / 平板支撑 : 缺少 targetDurationSec」。

**客户端（真机 Redmi K60 Ultra）**

测试计划：3 组 × 目标 20 秒，播报间隔 5 秒，组间休息 15 秒。

| 检查 | 结果 |
|---|---|
| 目标卡（时长类） | 「目标时长 20 秒 / 播报间隔 每 5 秒 / 组间休息 15s」✓ |
| 倒计时 | 大号 `0:08` + 进度条 + 「目标 20 秒」✓ |
| 超时 | 变红、`已超目标` + **`+0:24`** + 进度条满格 ✓ |
| 首页 | 「第 1 组 · **撑 20 秒** · 休息 15s」（修掉 `null-null` 之后）✓ |
| 总结页 | 容量 **0 kg**、**时长类合计 1 分 34 秒**、逐组「计划 20s / 实际 88s、6s、未做」✓ |

提示音时间线（logcat 的 `AudioTrack` 事件，本次会话 pid 28141）：

```
18:12:24.000 start/stop  274ms   ← 剩 15 秒
18:12:28.997 start/stop  270ms   ← 剩 10 秒
18:12:33.994 start/stop  271ms   ← 剩  5 秒      三声间隔 4.997s（代码写 5s）
18:12:36.023 start/stop  170ms   ┐
18:12:36.414 start/stop  124ms   ├ 剩 3 秒三声，间隔 391 / 406ms（代码写 400ms）
18:12:36.820 start/stop  106ms   ┘
18:12:39.031 start/stop  653ms   ← 归零长音（文件 600ms）
18:12:43.992 … 50.772 … 55.770 … 60.769   ← 超时期间继续每 5 秒一声
```

归零那一刻（t≈20s）**没有**间隔提示音——被正确跳过了，不然会和长音挤在一起。

`duration_sec` 落库（这一次终于不是 NULL 了）：

| set | 目标 | `duration_sec` | 说明 |
|---|---|---|---|
| 1 | 20s | **88** | 撑过头 → 超时部分计入 |
| 2 | 20s | **6** | 提前结束 → 如实记录 |

**两条都挺过了休息结束的补写 PUT**（旧的覆盖 bug 会把它们抹成 null）。
同时 `rest_actual_sec = 14`（计划 15 秒）——**小于计划值**，正是 `phaseStartedAt` 修复的证明（旧代码会记成 `88+15=103`）。

### 已知缺口

- **`program_template` 里平板支撑的 `note` 还写着「目标是秒数」**。秒数已经是正式字段了，这句注释成了误导。改它要再开一个迁移（V9 冻结），暂时留着
- **`DISTANCE_DURATION`（跑步/划船机）的 `distance_m` 仍然没采集**，另开一步
- **计划编辑界面没做**（推到 M3）：`targetDurationSec` 只能在模板里带，改值要走 API
- **语音播报的实际发声没验**（默认关闭，本次只验到「开关能存、TTS 能初始化」）

---

## 步骤 4.0 —— Phase 4 开工前的两笔环境欠账 ✅

> Phase 4 拆成 4A（训练数据统计与图表）/ 4B（身体数据）/ 4C（体态照片，另行立项）。
> 开工前先还两笔欠账——不还的话后面每一步都难受。

### ① 测试跑在**开发库**上

`server/db/init.sql` 里建了 `gym_log_test`，注释写着「避免测试数据污染开发数据」——
**但从来没有接线过**：`src/test` 下没有 `resources/`、没有 `application-test.yml`、
没有任何 `@ActiveProfiles`，`spring.profiles.default: dev` 让测试连的是 `gym_log`。

平时靠 `@Transactional` 回滚看不出问题。**但已提交的数据（比如造数据的种子）对测试是可见的**
——一旦种下几十场会话，断言「历史列表有 2 条」的测试就会红，
而红的原因看起来和它测的东西毫无关系。

**做法**：`application-test.yml` **只覆盖库名，凭据从 dev 继承**：

```java
@ActiveProfiles({"dev", "test"})   // dev 给账号密码，test 只覆盖 url
```

照抄 dev 的写法会让这个文件被迫二选一：带密码提交（泄漏）或也被 gitignore
（新克隆的人跑测试时一头雾水）。这样写**不含任何密钥，可以放心提交**。

**⚠️ 测试通过不能证明换了库。** 单独查了才敢下结论：

| 检查 | 结果 |
|---|---|
| `gym_log_test` 表数量 | 0 → **18** |
| Flyway 迁移 | 14 个全成功，最新 V14 |
| 种子 | 90 个动作、6 个模板 |
| `gym_log` | 未被污染 |

顺手发现两件事：**全库零外键**（所以测试能用不存在的 `user_id` 通过；
`AC-10-5` 的级联删除只能靠应用层），以及**六个测试类用的 `USER = 1L` 在库里不存在**。

### ② 组数口径散落**五处**，而有一处现成的没人用

`setType != WARMUP` 内联在 `SessionSummaryService` 的**四处**
（总结主循环、与上次对比、历史列表统计、`bestWorkingWeight` 的 filter），
外加 `TrainingMetrics.setVolume` 里一处。

而 `SetRecord.countsTowardVolume()` **写得好好地、注释也对、零调用者**。

> 这正是 `TrainingMetrics` 类注释警告的那种缓慢分叉：规则写了五遍，一处都没用上正确的那处。
> 某天有人给热身组开个例外，他只会改自己看到的那一处，然后五个数字互相对不上，
> **而且都不会报错**。

统一到 `TrainingMetrics.isWorkingSet(SetType)` 单一谓词，实体方法委托给它。
**139 个测试全绿，行为保持。**

---

## 步骤 4.1 —— 统计的纯函数层 ✅（4A 第一部分）

`com.gymlog.stats` 三个类，照 `TrainingMetrics` / `TrainingSchedule` 的模式
（`final class` + 私有构造 + 静态方法，无 Spring）。**34 个测试，整个包跑完不到 100ms。**

| 类 | 内容 |
|---|---|
| `WeekSeries` | 周起点（周一）、周枚举（零填充）、streak、当前周判定 |
| `MovingAverage` | 日历窗口滑动平均（O(n)）、`enoughDataForMa` |
| `Adherence` | 符合率（逐单元 clamp）、偏差标签 |

### 三条规则，各自只有一个实现

**1. 周起点是周一**，测试专门盯了「周日归到**本周**一，不是下周一」——
按西方习惯写会让整张图 X 轴错位一格，而且没人会发现。

**2. 日历窗口，不是「最近 N 个点」** —— 一周只称两次的人，按点数算窗口实际覆盖 3.5 周，
曲线会又平又滞后，而它要压的是**日内**噪声。

**3. clamp 是逐单元的** —— 先求和再 clamp 会把「A 多做 3 组、B 少做 3 组」
算成 100%，掩盖掉「有个动作完全没按计划做」。

### 🐛 一个测试红了，但错的是测试

写了条用例断言「上周没练 → streak 归零」，跑出来是 **1**。

回去看 `METRICS 5.2` 原话是「**从当前周往前**，连续满足…的周数」——
这周练了、上周没练，当前这一段就是 1 周。**实现是对的，期望写错了。**

顺带定死了一处歧义：`METRICS 5.5` 的「当前周不参与 streak 判定」，
按它**自己写的理由**（「避免周一就显示断连」）实现——周三已经练了两次的人
该看到 streak 涨，按字面读却和不练的人一样。

### ★ 三处「代码和文档打架」已当场修文档

`DEVELOPMENT-PLAN` 的规矩是「实现偏离文档时**当场**改文档」。这三处都是：

| 文档 | 原来写的 | 代码的实现 |
|---|---|---|
| `METRICS 1.2` | 「最近 7 天内**所有测量值**的算术平均」 | 先按日取均值，再对窗口内日值求平均 |
| `METRICS 5.2` | streak「≥ **计划频率**」 | 「≥ 1 次」（分母在当前 schema 下算不出来） |
| `METRICS 2.1` | 围度 **9** 个部位 | **12** 个（`REQUIREMENTS` M6-A-2 是 12） |

第一条尤其值得记：按字面读会让**同一天测 3 次的人权重变三倍**，
即「越勤快测量的人曲线越抖」——与 1.3 整节的目的直接矛盾。

---

## 步骤 4.2 —— 统计聚合层 ✅（4A 第二部分）

| 文件 | 内容 |
|---|---|
| `StatsMapper` | PR 的窗口函数查询（`ROW_NUMBER() OVER (PARTITION BY ...)`） |
| `StatsService` | `weekly` / `e1rm` / `prs` |
| `StatsController` + `StatsRangeQuery` | 三个端点 |

### 聚合走 Java 还是 SQL —— 按「有没有界」分

`SessionSummaryService` 早就论证过容量不走 SQL（口径依赖 `metric_type` + `bw_factor`
+ 快照体重三字段交叉判断，写 SQL 是 `CASE WHEN` 且会和 `TrainingMetrics` 分叉）。
**但那条论证只对有界区间成立**：

| 场景 | 走哪 | 理由 |
|---|---|---|
| 有界区间（周序列、单动作曲线） | **Java 批查** | 12 周约 900–1080 行；口径一份实现 |
| 无界全史（PR 看板） | **SQL 窗口函数** | 730 天约 **7800 行**，而这里只需每个动作一行 |

第四条理由：`set_record` 没有 `deleted` 列而 `workout_session` 有，
MyBatis-Plus 的逻辑删除**只对 `LambdaQueryWrapper` 生效**——走 Java 批查
等于让框架替你守这条不变量。

**白捡的便宜**：e1RM 图的「历史最高」参考线直接复用 `bestE1rmBefore` 传一个远未来
——零新 SQL、零第三份公式，而且它已经被契约测试守着。

### 一处 API 陷阱：用范围 DTO 当白名单会放行被忽略的参数

`/stats/prs` **不接受任何参数**（PR 是全时段语义）。最初写成
`QueryParamGuard.rejectUnknown(StatsRangeQuery.class, ...)`——那会**放行** `from`/`to`，
而接口根本不看它们：客户端以为自己筛了范围，实际拿到全时段数据，**两边都不报错**。

给 `QueryParamGuard` 加了 `rejectAll`。

### 调试过程：三次「红了」，两次错的是测试

`StatsServiceTest` 最初 15 个里 6 失败 3 错误，最后全部修好。**但真正错在被测代码上的只有一处。**

**① 会话没结束**（测试脚手架错）
`create()` 建的会话初始状态是 `IN_PROGRESS`（`SessionService.java:93`），
而 `completedSessions()` 按 `METRICS 5.1` 只统计 `COMPLETED`。测试里从来没调 `finish()`。

证据很干净：**依赖会话数据的断言全返回 0，而纯零填充的断言全过**——
后者不需要真有会话。修法是脚手架补 `finish()`，且**必须在记录完组之后**
（`recordSet` 要求会话处于 `IN_PROGRESS`）。

**② 脚本把 Python 字面量泄漏进了 Java**（工具错）
用 Python 批量改测试文件时，替换串里写了个 `finish(s1 if "s1" in tail else ...)`——
条件表达式原样进了 Java 源码。编译错误是 `Unresolved compilation problem`。
**教训：脚本化编辑生成的是代码，改完必须看一眼，不能只看「替换成功」的计数。**

**③ 参考线没四舍五入**（★ 这一处是真错在代码上）
`allTimeBest` 复用 `bestE1rmBefore`，而那条 SQL 的 `MAX(weight * (1 + reps / 30))`
**不做小数位处理**，Java 侧的 `TrainingMetrics.e1rm` 却有 `.setScale(2, HALF_UP)`。

不补这一步，同一张图上参考线写 `81.666667` 而曲线点是 `81.67`——用户会以为它们不是一回事。

> **契约测试为什么没抓到**：`sqlE1rmMatchesJavaE1rm` 用的是 `isEqualByComparingTo`
> （数值比较，不比精度），所以它一直是绿的。**它守的是「值对不对」，不是「显示一致不一致」。**

**④ PR 排序断言写反了**（测试错）
`METRICS 7.3` 要求「按达成日期**倒序**（最近的在前）」，服务端确实倒序了，
而我在测试里用了 `isSorted()`（查升序）。改用 `isSortedAccordingTo(reverseOrder())`。

> 加上之前 streak 那次（`WeeksSeriesTest`），**这一轮有三次是测试的期望写错**。
> 断言失败时先问「规格到底怎么写的」——规格里的原话往往就否掉了自己的期望。

### 端到端验证（HTTP，三个端点全覆盖）

`mvn clean test` → **188 全绿**（原 139 + 纯函数 34 + 聚合 15）。

| 端点 | 结果 |
|---|---|
| `GET /stats/weekly` | 6 周全部铺开（5 个空周都是 0）、周起点周一、当前周标记正确、符合率 66.7 |
| `GET /stats/exercises/{id}/e1rm` | 三个点 `70.00 / 75.83 / 76.50`，与 Epley 手算一致；历史最高 76.5 |
| `GET /stats/prs` | 最大重量 67.5 与最佳 e1RM 76.5 **分开两条**，`achievedOn` 正确 |
| `/stats/prs?from=...` | **400**「这个接口不接受查询参数，但收到了：from」（`rejectAll` 生效） |
| 时长类动作 | `supported=false` + `metricType=DURATION`，不是一张空图 |

顺带验到 **`AC-7-9`**：平板支撑的周桶是 `容量 0 / 组数 4 / 肌群 CORE 4`——
时长类不计入容量、但组数照算。

### 造数据时踩到的两个坑（HTTP 层）

**1. `startedAt` 不传就默认 `now()`**（`SessionService.java:94`）。
造了三场训练，日期全变成今天——`e1RM` 数值算得对（`60×5=70.00`），
但三个点的日期全挤在同一天，PR 的「达成日期」也全错，**而且不报错**。
这是 `SessionSummaryServiceTest` 那条血泪注释的 HTTP 版：**造数据必须显式传 `startedAt`**。

**2. `abandon` 只对 `IN_PROGRESS` 有效**（有 `requireInProgress` 守卫），
已完成的会话撤不掉。我循环里用 `-o /dev/null` 把错误吞了，
三场「已撤」其实一场都没撤成——**静默失败的又一种形态：把响应丢掉就等于关掉了错误通道**。

---

## 步骤 4.3 —— 造演示数据 ✅（4A 第三部分）

### 为什么必须有

图表要画「三个月趋势」，而库里只有十几场零散会话（跨度 8 天）。
**不造数据的话，既没法验证图表画得对不对，也没法演示。**

`DemoDataSeeder`，用 `--gymlog.seed-demo=true` 触发，可重复执行（先清 demo 用户的旧数据）。

### 走 Service 调用，不手写 INSERT

手写 SQL 会造出「看起来对、但不满足不变量」的数据，**而且不报错**：

- `create()` 按传入的 `date` **反算 `weekNumber` 和 `isDeload`**——把第 6 周设成 deload，
  倒填的第 6 周会话自动带标记。手写 INSERT 得不到
- 快照的 `bw_factor` / `primary_muscle` / `metric_type` 全由 `insertSnapshot` 拷贝，手写必漏
- 训练日轮转依赖「已完成会话总数」，顺序错了会让训练日名和动作对不上

### 要造的是「形状」，不是「数值」

| 形状 | 为什么 |
|---|---|
| 一个**空周**（W4） | `METRICS 4.6`：空周要显示 0 高度柱，不跳过 |
| 一个 **>14 天空档**（W7–W8） | `METRICS 1.5` 的断线规则 |
| **热身组**（深蹲第 1 组） | 否则「热身不计入容量和组数」在图上看不出来 |
| **REPS_ONLY + DURATION 动作** | 否则自重和时长类的整条路径在演示里是空白 |
| **一个 deload 周**（W6） | e1RM 曲线上的凹口一眼能看出「这图是对的」 |

### 🐛 失败一次：`IN` 查询里不存在的项**静默消失**

挑动作时用了 `WHERE name IN ('杠铃深蹲','杠铃卧推',...,'站姿推举',...)` 列了 10 个名字，
**结果只回来 9 个**——「站姿推举」其实叫「站姿杠铃推举」。扫一眼结果很容易以为全都在。

好在脚本是**启动即失败**（`动作库里没有「站姿推举」`），而不是少造一个动作继续跑——
后者会造出一份「看起来正常、但少练了一个部位」的数据，要过很久才会发现图不对。

> **教训：用 `IN` 查询核对存在性时，必须核对「回来了几条」**，
> 而不是「回来的都有哪些」。

### 验证结果

**数据分布**（9 周 × 3 场 = 27 场，W4 与 W7–W8 按设计缺席）：

```
周起点        容量       组数  场次  符合率  肌群组数
2026-06-29   8040.0     30    3    96.8   胸7 背10 腿4 肩3 核心6
2026-07-06   8200.8     30    3    96.8   ...
2026-07-13   8361.6     30    3    96.8   ...
2026-07-20   0.0         0    0     —      —          ← 空周（零填充）
2026-07-27   8683.2     30    3    96.8   ...
2026-08-03   3426.0     21    3    95.5   ...        ← deload 周
2026-08-10   0.0         0    0     —      —          ┐ >14 天
2026-08-17   0.0         0    0     —      —          ┘ 空档
2026-08-24   9326.4     30    3    96.8   ...
...
2026-09-14   9808.8     30    3    96.8   ...        ← 当前周（半透明）
```

**这张表同时验到了四件事**：

1. 空周零填充、连档不跳过 ✓
2. deload 周容量掉到约 40%（−40% 重量 + 减 1 组）✓
3. **腿只有 4 组而深蹲计划 5 组 → 热身组被正确排除** ✓
4. **核心 6 组但容量不含平板支撑** → `AC-7-9` 在数据里看得见 ✓

**e1RM 曲线**（杠铃深蹲，9 个点，deload 处有明显凹口）：

```
2026-07-03   81.67
2026-07-10   83.30
2026-07-17   84.93
2026-07-31   88.20
2026-08-07   49.00   ← deload
2026-08-28   94.73
2026-09-04   96.37
2026-09-11   98.00
2026-09-18   99.63   历史最高
```

**PR 看板 13 条**，两类分开、达成日期与「距今天数」都对。

---

## 步骤 4.4 —— 客户端图表页 ✅（4A 收尾）

`lib/features/stats/`：`stats_models.dart` / `stats_api.dart` / `stats_screen.dart` /
`exercise_history_screen.dart`。入口是首页 AppBar 的洞察图标（`Navigator.push`，
**没有加底部导航**——那需要一个保持状态的 shell，是另一个量级的工作）。

### 按 `M7-B` 的平台适配

| 约束 | 落点 |
|---|---|
| 单序列（`M7-B-1`） | 不做多序列叠加；肌群组数用**横条**而不是堆叠柱 |
| 默认 3 个月（`M7-B-2`） | 范围切换器，服务端默认值只当兜底 |
| 长按读数（`M7-B-3`） | 两个图都实现 |

### 肌群周组数为什么不用 fl_chart

6 个肌群 × 12 周的堆叠柱，在 360dp 宽的屏幕上每周只有约 30dp，
柱子细到看不出分层、X 轴标签只能放缩写。

而 **fl_chart 1.2 的 `BarChartData` 不支持横向**（没有 `rotated` 参数，已查源码确认）。
横向条形本质就是「一行一个分类的定宽矩形」，用普通 widget 反而更好控制标签宽度。

### ★ 真机才暴露的三个问题

#### ① fl_chart 会**无条件多补一个 `max` 刻度**

趋势页底部 X 轴渲染成了 `9-79-14`——最后两个标签叠在一起。

根因在 `AxisChartHelper.iterateThroughAxis` 的结尾（`axis_chart_helper.dart:56`）：

```dart
if (maxIncluded && !lastPositionOverlapsWithMax) {
  yield max;      // 不管 interval 是多少，max 永远被额外补一个刻度
}
```

`min=0, max=13, interval=3` → 网格给出 `0/3/6/9/12`，**再加一个 13**。
后两个只差一个数据单位，手机上直接叠成一坨。

**越界检查挡不住它**——13 是合法下标，只是离前一个太近。

修法：不靠 `interval` 决定画不画，自己算出该画的下标集合
（`lib/core/chart_axis.dart`，`axisLabelIndices`），并且
**从最后一个点往前步进**——从 0 开始的话 14 个点的标签会落在 `0/3/6/9/12`，
恰好把最该看的那个点（当前周）漏掉。

> 单动作历史页当时看着是好的（4 个标签没重叠），**那是运气**：
> 同样的多余刻度在那里恰好落得够开。按「看起来没问题」放过，
> 等数据点数一变就会重现。

#### ② release 包**一个权限都没申请**

换 release 包之后所有请求直接失败，客户端只报「网络连接失败」。

Flutter 模板只在 `src/debug/AndroidManifest.xml` 里声明 INTERNET
（debug 要靠它做 hot reload），**主 manifest 是空的**：

```
$ adb shell dumpsys package com.gymlog.gym_log | grep -A2 "requested permissions"
    requested permissions:            ← 空的
```

所以 debug 包一路正常、release 包全挂，**而失败长得像网络问题**。

这意味着之前所有「真机验证」用的都是 debug 包（`build/app/outputs/flutter-apk/`
里那份 228MB 的 `app-debug.apk` 就是证据）——「release 能联网」这条从没被验证过。

修法：主 manifest 加 `INTERNET`，另配 `network_security_config.xml`
只放行 `localhost` / `127.0.0.1` / `10.0.2.2` 的明文 HTTP。
**不用 `usesCleartextTraffic="true"` 全局放行**——那会连同以后的 HTTPS 后端一起开口子。

#### ③ 肌群卡片存的是**下标**，切范围后跳到了不相干的一周

在「3 个月」里翻到 8-3（下标 7），一切到「1 年」，下标 7 变成了 **11-3**——
用户没碰过切换器，卡片上那一周却换了，而且落在整段没数据的地方。

**下标只在某一个时间范围里有意义。** 改成存那一周的**日期**：
切范围时要么还显示同一周，要么它不在区间内了（退回最新一周），两种结果都能解释。

### `AC-7B-*` 真机验证（Redmi K60 Ultra / Android 15 / **release 包**）

| 项 | 结果 |
|---|---|
| `AC-7B-1` 肌群图是横向条 | ✅ 6 个肌群各占一行，中文名完整 |
| `AC-7B-2` 默认范围 3 个月 | ✅ |
| `AC-7B-3` 长按读数 | ✅ 容量图「7-6 那周 / 8,201 kg / 30 组 · 3 次」，且跟随手指移动 |

| 其它 | 结果 |
|---|---|
| 空周零填充 | ✅ 容量图 0 高度点、肌群卡片全 0 + 「这一周没有训练记录」、周条带 2px 空柱 |
| deload 凹口 | ✅ W6 容量 3,426（约为正常周 40%） |
| 范围切换 | ✅ 1 年 = 53 周，X 轴 5 个标签均匀、含最后一个点 |
| 周切换器 | ✅ 8-10 空周 / 8-3 deload 周数据都对 |
| PR 达成日期 | ✅ 罗马尼亚硬拉在腿日（今天）、硬拉在拉日（2 天前）——与 `set_record` 实际日期一致 |
| 单动作历史页 | ✅ 汇总行 113.9 / 97.6 / 26 组；长按「8-5 / 最佳 56 kg / 2 组」与列表 `48×5 · 48×5` 自洽（Epley 48×1.1667=56） |

### 客户端第一个测试

`app/test/chart_axis_test.dart`（11 个用例）。之前客户端**零测试**。

为一个纯函数开测试目录看起来小题大做，但它是刚出过真实 bug 的那一个，
而且 `axisLabelIndices` 有真边界（点数 < target、整除、从后往前）——
顺带钉死「最后一个点一定有标签」和「相邻标签至少隔 2」两条不变量。

`flutter_test` 是 SDK 自带的，只是之前没人建 `test/` 目录。

### 已知缺口

- `M7-B-5`（离线查看）依赖 3.12 的本地 SQLite，**仍欠着**
- 横屏（`M7-B-4`）未专门验证
- 单动作历史页 Y 轴最低刻度和上一格间距不匀（`minY = floor(min×0.9)` 所致），
  读数不受影响，暂不处理

---

## 步骤 4B —— 身体数据（`body_metric` + 录入 + 趋势图）✅

后端 `com.gymlog.body`（表 V15 + 2 个枚举 + 服务 + 4 个端点），
客户端 `lib/features/body/`（模型 / API / 趋势页 / 录入面板）。

### 窄表，而 `set_record` 是宽表

同一个项目里两种相反的建模，区别在两处、缺一不可：

| | `set_record`（宽） | `body_metric`（窄） |
|---|---|---|
| 判别器 | **封闭**：4 种计量类型 | **开放**：今天 14 个，明天可能 15 个 |
| 一行几个值 | 多个（重量+次数+时长+距离） | **一个** |

`SetRecord` 的注释里写着「用宽字段而不是 EAV」——那条论证针对的是
「一行多个**相关**量」的记录（查总容量要 `weight * reps`）。
身体数据一行就是一个标量，「今天 72.4kg」没有可透视的东西；
反过来用宽表的话，想加一个「握力」就要 `ALTER TABLE`。

### ★ 三个必须这么写的地方

#### ① `site` 是 `NOT NULL DEFAULT 'NONE'`，不是可空列

MySQL 的 UNIQUE 索引把 **NULL 当作互不相等**。而 `site` 只在围度（12 部位）
和酸痛度（6 肌群）上有值，其余 12 个指标全是「没有部位」——
如果 `site` 可空，幂等键

```sql
UNIQUE KEY uk_body_metric_natural (user_id, metric_type, site, measured_at)
```

**对这 12 个指标全部失效**：客户端重传一次体重，库里就多一条，而且不报任何错。

选 `NONE` 而不是空串，是因为本项目所有枚举都按 MyBatis 默认的 `name()` 存
（`SetType` 存的就是 `'WARMUP'`），`NONE` 自然落进这套约定，
不用为「空串 ↔ null」另写一个 TypeHandler。

> 有一条测试**绕过 Service 直接写库**验这个约束——因为 Java 层完全正确，
> 只有真插一次才知道索引认不认。

#### ② `value` 是 `DECIMAL(6,2)` 不是 `(5,2)`

`(5,2)` 上界 999.99，而 BMR 正常值 1200–2500 kcal 直接溢出。
14 个指标里只有它出界，取 `(6,2)` 一次覆盖。

#### ③ 列名是 `measure_condition` 不是 `condition`

`CONDITION` 是 MySQL 保留字。叫 `condition` 的话每处手写 SQL 都要加反引号，
漏一处就是语法错误；MyBatis-Plus 生成的 SQL 还得靠 `@TableField` 打反引号——
而那是**运行期**才暴露的错。换个名字成本为零。

### ★ 3 个真机/联调才暴露的问题

#### ① 变化量算出了**方向相反**的结论

第一版是「距上一次测量」。真数据上：体重从 74.45 降到 73.33，
接口却返回 **+1.01**——因为它拿 9-18 的**晨起空腹**值去减了 9-16 的**训练后**值，
差值主要来自测量时机。

这恰恰是 `METRICS 1.3` 整节在防的事（「日间波动 1–2kg，信噪比 1:2 到 1:4」）。
而且规格里本来就写了该给什么：

> `METRICS 1.4` 图表规格 · 交互：点击某点显示该次测量值、测量条件、**相对 7 日均值的偏差**

改成两级基准：**7 日均值**（优先，它已经把日内噪声和测量条件一起压掉了）
→ **上一次同条件的测量**（没有 MA 时退化，围度就是这种）。
响应里带 `referenceLabel` 说明基准是什么，客户端不自己判断。

#### ② 部位切换器死循环

客户端要画部位切换器，就得知道「哪些部位有数据」，而那个列表在
`/body/series` 的响应里——可 `series` 不传 `site` 就被 60005 拒。

```
GET /body/series?metricType=CIRCUMFERENCE   → 60005 围度必须指定部位
   ↑ 可 availableSites 只在这个响应里
```

**用户永远选不了部位，页面卡在转圈。**

改：GET 少传 `site` 不是错误，返回一个**空的、但带 `availableSites` 的**响应。
错误只告诉调用方做错了，列表告诉它该做什么。
60005 保留给 POST（录入时不写部位确实是错的）和显式的非法部位（60006）。

#### ③ 图表 Y 轴的 `min` 也会被多补一个刻度

同一个 fl_chart 行为，这次在 Y 轴：`iterateThroughAxis` 不止补 `max`，
`min` 也补（`if (minIncluded && !firstPositionOverlapsWithMin) yield min`）。

`minY = 73.9` 落在刻度网格（74/76/78）之外 → 额外补一个 73.9，
和 74 **叠在一起**，左下角糊成一团。

底轴可以用下标集合过滤，Y 轴不行（连续值，没有「第几个」）。
改法是让 `min`/`max` **本身落在刻度网格上**（`niceRange`，标准的 nice-number 算法）。
顺带把读数从「120/100/80/60/50」变成「120/100/80/60/40」——
之前最后两格间距只有前面的一半。

### 🐛 顺带抓到的既有 bug：自重动作的容量**一直是 0**

`AC-7-8` 说「自重动作容量 = 体重 × `bw_factor` × 次数，`bw_factor` 取自动作库」。
整条链路上：

```
Exercise.bwFactor              有值（动作库存了，管理端能改）
   ↓
ExpandedWorkout.ExerciseItem   ← ❌ 根本没有这个字段，值在这里丢了
   ↓
SessionExercise.bwFactor       列有（V12 加的，注释还写着 AC-7-8）
   ↓
TrainingMetrics.setVolume      一直在读它
```

**为什么几个月没被发现**：读不到时 `setVolume` 返回 **0**，
而 0 正是 `METRICS 4.1`「没记体重就不计容量」的**合法值**。
所以「引体向上容量 0」看起来完全正常——演示数据里也有意放了自重动作，
但那时没有体重记录，0 是对的解释。

直到 4B 真的记了体重，它还是 0，才露出来。

这和第 51 条（`countsTowardVolume()` 零调用者）是同一个形状：
**写了、注释也对、有人读，就是没人写**。

修复后演示数据验证：卷腹（`REPS_ONLY`, `bw_factor` 0.35）从 `0` 变成
`384.98` = 73.33 × 0.35 × 15。

> ⚠️ **修复只影响之后的会话，不追溯改写历史**（快照语义，`REQUIREMENTS 3.3` 不变量 1）。
> 库里还留着修复前创建的、别的用户的 5 条 `bw_factor = NULL` 的快照——
> 它们的容量仍然是 0，**这是对的**。

### 🐛 种子脚本「可重复执行」是假的

`DemoDataSeeder` 的注释写着可重复执行，清理走的是 Mapper 的 `delete`。
但 `workout_session` 和 `program` 都配了**逻辑删除**
（`logic-delete-field: deleted`）——那是 `UPDATE ... SET deleted = 1`，
**物理行还在**，而唯一索引 `uk_session_client_key` 不认识 `deleted` 这个标志。

于是第二次跑种子：

```
已清理演示账号的 27 场旧会话          ← 日志说成功了
Duplicate entry '63-demo-w1-d0' for key 'workout_session.uk_session_client_key'
```

第一次跑没事，只是因为库里没有旧数据、清理分支根本没进——
**「可重复执行」这句话当时是没验过的**。

改：清理走 `JdbcTemplate` 裸 SQL 物理删除（顺带把计划也清了，
之前每跑一次多一个计划）。用裸 SQL 而不是给 Mapper 加 `@Delete`：
这是演示数据清扫、不是业务操作，写在这里调用点就看得见「这里会硬删」。

> 修的过程中还发现 `day_template` 上**没有** `week_template_id`——
> 「周」和「天」是两个独立维度，都直接挂在 `program_id` 上。
> 直觉上「一天属于某一周」，但这里是叉乘出来的。

### 验证结果

**演示数据**：140 条身体数据（体重每日晨起 + 每周一次训练后 / 体成分五项每周 /
围度每月 5 个部位 / 静息心率 + 睡眠质量每周）。

| 项 | 结果 |
|---|---|
| 快照体重按**各自日期**取 | ✅ 27 场会话、26 个不同体重、0 空值，78.55 → 73.33 |
| 幂等 | ✅ 同一 (指标,时刻) POST 3 次 → 库里 1 行 |
| 日聚合（`METRICS 1.2` 第一步） | ✅ 同一天 73.33 + 72.8 → daily 一个点 73.07 |
| 自重容量（`AC-7-8`） | ✅ 卷腹 0 → 384.98 |
| 校验与错误码 | ✅ 60005 / 60006 / 60002 / 10006 逐条验过 |
| 归属（`AC-1-1`） | ✅ 删别人的记录返回 60001 且真没删掉 |
| 客户端 `AC-7B-4` | ✅ 部位切换器只列**有数据的 5 个**部位，自动选中第一个 |
| 客户端录入闭环 | ✅ 表单完全由 `/body/metric-types` 生成；录完曲线/头条数字/列表都刷新 |

### 显式不做

- **血压**（`M6-C-2`, Should）：一次测量拆两行、靠 `measured_at` 完全相同配对，
  窄表的「一个指标一条时间序列」前提不成立
- **体态照片**（`M6-E`, 4C）：存储方案 `Q2` 未定
- **测量提醒**（`M6-A-5`, Should）：需要推送，归 Phase 8

---

## 步骤 4B 补记 —— 指标精简与 Y 轴留白（用户反馈）

两处都是**用户看完真机之后提出来的**，都属于「自己盯着看不出来」的那类。

### ① 去掉体脂秤推算的五项

删掉 `BODY_FAT` / `MUSCLE_MASS` / `WATER_PCT` / `BMR` / `VISCERAL_FAT`，
连带删掉 `device` 列（体脂秤型号，`M6-B-6`——它的全部意义就是给这五项标注换秤断点）。

判据是用户给的：**用户能不能自己测**。

| 能自测 | 不能 |
|---|---|
| 体重（秤）、围度（软尺）、静息心率（数脉搏）、主观感受 | 体脂率、骨骼肌量、水分率、BMR、内脏脂肪 |

后五个不是「测」出来的，是体脂秤用生物电阻抗 + 公式**推算**的：
不同品牌算法差异极大（`M6-B-6` 自己的注释就写了差 3–5 个百分点），
用户无法独立验证，而且**趋势没有行动含义**——BMR 从 1625 掉到 1605，你能做什么？
而腰围从 86 掉到 81.6 是明确的行为反馈。

`REQUIREMENTS M6-B` 整节（含 2 个 Must）标记为不做，
其中真正有意义的部分改为**从自测数据推导**（公开公式，见下）。

**⚠️ V16 迁移会删库里的历史行，不只是从界面隐藏。**
理由：枚举值一旦从 `BodyMetricType` 去掉，那些行就再也映射不回来
（MyBatis 按 `name()` 反序列化，找不到枚举直接抛异常）——
它们会变成「读不出来的行」，任何 `selectList` 都会炸。
**留着读不出来的行比删掉危险得多**：删掉是显式且立刻可见的。

> 迁移末尾照例跟一条断言——「影响 0 行」在 Flyway 里是合法成功（踩坑 43）。

### ② Y 轴上下各留一整格

用户原话：「图表中的 y 轴数据，下面可以留一节，不然看上去变化都是从最低到最高。」

根因是我上一轮引入的**规格偏离**：`METRICS 1.4` 写的是「上下各留 10% 余量」，
4A 第一版也是那么做的；换成 `niceRange` 对齐刻度之后，10% 那条保证丢了——
对齐会把百分比余量整个吃掉，因为网格间距是 `step`，余量只能取 `step` 的整数倍，
中间值取不到。数据离网格只差一点点时（比如 `lo = 72.9`、`step = 2`），
对齐后余量只剩 11%，曲线看起来就是**从轴底冲到轴顶**。

改成离散规则：**上下各至少留一格**。实际余量必然 ≥ 一个 `step`。

| | 改前 | 改后 |
|---|---|---|
| 体重（跨度 6.5kg） | 11% | **20%** |
| 围度（跨度 2cm） | 17% | 17%（本来就在格上，旧逻辑也补了格） |

只补底部是不够的——**最高点贴着顶边同样会留下「涨到顶」的错觉**，
所以两侧都补。

> 顺带解释了一个观察：围度图看起来没变化，是因为它的数据恰好落在网格上
> （99.0 和 101.0 都是 0.5 的倍数），旧逻辑的「贴边补一格」已经生效了。
> 真正改善的是体重图。

---

## 步骤 4B 补记二 —— 从自测数据推导 ✅

用户提的方向，已实现：**BMI / 体脂率估算 / BMR / 腰高比**。

### 不需要建表 —— `user` 表早就有这三个字段

`height_cm` / `birth_year` / `gender` 在 V1 就加了（当时是给「个性化推荐」留的），
一直没用上。所以这一轮**零迁移**，只补了一个写接口
`PUT /users/me/body-profile`。

只传要改的字段，未传的保持不变——**「漏传 = 清空」是危险的默认语义**。

### 四个公式

| 指标 | 公式 | 需要 |
|---|---|---|
| BMI | `体重 / 身高(m)²` | 体重、身高 |
| 体脂率 | `1.20×BMI + 0.23×年龄 − 10.8×性别 − 5.4`（Deurenberg） | + 出生年、性别 |
| BMR | 男 `10×体重 + 6.25×身高 − 5×年龄 + 5`，女常数项 `−161`（Mifflin-St Jeor） | + 出生年、性别 |
| 腰高比 | `腰围 / 身高` | 腰围、身高 |

**为什么用 Mifflin-St Jeor 而不是 Harris-Benedict**：前者是 1990 年针对现代人群
重新拟合的，后者会系统性高估约 5%。

### ★ 三个关键决策

#### ① 实时算，不落库

派生值没有独立的测量时刻——体重一变它就该变。存下来就要处理
「体重改了，派生的怎么办」，那是个**没有正确答案**的问题：
重算会让历史值变化（违反快照语义），不重算又会让图上出现和体重对不上的数。

实时算的成本是零：体重和腰围本来就要各查一次。

#### ② 不出趋势曲线

BMI = 体重/身高²，而身高是常数——**BMI 曲线和体重曲线形状完全一样**，
只是纵轴刻度不同。体脂率估算在年龄不变的短期内同理，BMR 也由体重决定。
三条曲线提供的信息和体重曲线**完全等价**，属于 `METRICS 0.1` 说的「装饰」。

而「BMI 23.4，正常」是体重曲线给不了的东西：那是**一层判断**。

#### ③ 分级标准在服务端

BMI 用**中国标准**（WS/T 428-2013：24 超重、28 肥胖），不是 WHO 的 25/30 ——
混用会让一批中国人被告知「你正常」而实际已经超重。体脂率阈值男女不同。

阈值和公式放一起，因为它们是**同一份知识**：分开写就会出现
「算出来 23.5 却标成超重」这种自相矛盾。

### 🐛 联调时抓到的两个

#### ① 腰高比显示 `0.5`，却判「健康」

客户端为了好看用了 `toStringAsFixed(1)`，把服务端的 0.466 四舍五入成 **0.5** ——
而 0.5 正好是这个指标的**阈值**（服务端判 ≥0.5 是风险）。

于是界面印出「0.5 健康」，用户一眼就会怀疑这个判断。

改：客户端**不做任何四舍五入**，服务端已经按各指标的语义定好了小数位
（BMI 一位、腰高比三位、BMR 取整）。

> 这是「显示层自作主张」的典型：它以为自己只是在美化，实际改掉的是**语义**。

#### ② 性别是 1/0，不是 2/1

`User.GENDER_MALE = 1`、`GENDER_FEMALE = 2`，而 Deurenberg 要的是 `male ? 1 : 0`。
直接把枚举值代进去的话，女性会算出**比男性还低**的体脂率——
而那个数看起来完全正常，不会报错。

实现里显式映射，测试钉死「同样身高体重年龄，女性必须高 10.8 个百分点」。

### 验证

| 项 | 结果 |
|---|---|
| 资料未填 | ✅ 四项各自说缺什么（「身高」「身高、出生年、性别」），**不是整块藏起来**——那条提示本身就是补资料的入口 |
| 手算核对 | ✅ BMI 23.9、体脂率 19.4%、BMR 1682、腰高比 0.466，四个都和手算一致 |
| 改性别为女 | ✅ 体脂率 19.4 → **30.2**（+10.8）、BMR 1682 → **1516**（−166），BMI 和腰高比**不受影响**（它们本来就不该受影响） |
| 后端测试 | 273 全绿（+17，全是手算期望值，不是「跑一遍看看」） |

### 显式不做

- **TDEE（每日总消耗）**：BMR 乘活动系数就行，但活动系数需要知道训练频率和日常活动量，
  而那个输入本身很主观。作为独立一步。
- **FFMI（去脂体重指数）**：链条太长（要先有体脂率，而体脂率本身是估算），
  误差会叠乘。

---

## 竞品对照：训记（2026-09-18）

用户提议拿同功能的「训记」（`com.trainnote.rn`）对照。**只做记录，不改代码**。

方法：`adb` 拉起 → 逐屏看 + `uiautomator dump` 抓 UI 文字（比截图 OCR 快得多）。

### 一、动作库

两个**正交筛选轴** + 右侧字母索引：

| 轴 | 值 |
|---|---|
| 肌群（17 类，含二级） | 胸→上胸/中下胸、背→上背/下背、二头→内侧头/外侧头、三头、前臂、小腿、肩、前锯肌、斜方肌、颈部、臀部、腿、功能性、核心稳定、腹部、**热身动作**、**拉伸** |
| 器械（11 类） | 杠铃 / 哑铃 / 壶铃 / 绳索 / 悍马机 / 史密斯 / 器械 / T杠 / 自重 / 其他 / 置顶 |

卡片 = 解剖图（目标肌标红）+「讲解」角标 + 名称。

#### 我们缺的动作（筛过「常见 + 经典」）

| 缺口 | 具体动作 |
|---|---|
| **上背厚度** | 潘德雷划船、海豹划船、俯卧杠铃划船、地雷杆划船、反手划船、半程硬拉 |
| **后链 / 臀** | 早安式、地雷杆罗马尼亚硬拉、杠铃单腿硬拉、臀推变式、杠铃上凳 |
| **二头变式** | 蜘蛛弯举、集中弯举、拖式弯举、EZ杆弯举、俯卧上斜弯举 |
| **胸的变式** | 暂停卧推、宽距卧推、抬腿卧推、杠铃片夹胸 |
| **整类缺失** | 热身动作、拉伸、前锯肌、颈部 |

我们的 90 个（ARMS 14 / BACK 17 / CHEST 14 / CORE 11 / LEGS 22 / SHOULDERS 12）
比预想的厚——T杆划船、牧师凳弯举、臀冲、臀桥、罗马尼亚硬拉、面拉、山羊挺身
这些**已经有了**。

#### ⚠️ 有一处建议**不要抄**

他们的粒度到了「**器械型号 + 版本号**」：
悍马机推胸(版本2) / 悍马机推胸(版本3) / 塔式上斜卧推 / 直立器械推胸(版本2)……

那是给「同一个动作在不同健身房的机器上不一样」用的，靠 UGC + 内容团队撑起来。
一个人做作品集项目铺到那个量级，既没素材也没意义。
**该抄的是维度，不是粒度。**

#### ★ 更重要的一个判断：他们的 17 分类是「查找维度」，不是「统计维度」

我们的 `MuscleGroup` 注释里本来就写了：

> 为什么只有 6 个粗分类 —— 这个枚举用于**统计维度**。统计维度太细会导致
> 每个分类的数据量都很小，看不出趋势。细分部位属于**动作描述**，不进入统计。

所以正确做法**不是**把 6 改成 17——那会让「肌群周组数」图裂成 17 条、
每条都没数据。而是**两者并存**：动作加一个「细分部位」字段用于筛选，
`MuscleGroup` 保持 6 个用于统计。

这条对照反而验证了原来的设计是对的，只是**缺了查找那一维**。

### 二、其它启发

| 他们的 | 我们的 | 判断 |
|---|---|---|
| **人体肌肉热力图**（正反面按训练量着色） | 6 条横条 | 值得考虑。横条读数精确，热力图一眼看出「哪里没练」——但需要解剖图素材，且精确读数会丢 |
| **挑战 + 排行榜** | 无 | 需要多用户数据，Phase 7/8 |
| 首页**饮食记录**（蛋白/碳水/脂肪/饮水四环） | 无 | 就是 Phase 7 的计划 |
| 计划分 **官方 / 个人模版 / 个人计划(AI)** | 只有「模板创建 + 自定义」 | **「个人模版」值得加**——用户把调好的计划存成模版复用，成本低 |
| **有氧 vs 力量**分开统计 | 跑步/跳绳混在 `LEGS` 里 | 我们 `DURATION`/`DISTANCE_DURATION` 不进容量，但也没有独立的有氧统计 |
| 动作详情挂 `要点 / 历史 / 图表 / 置顶` | 有前三个，无收藏 | 高度一致——**说明这块我们没走偏** |
| 视频 / 3D / 肌肉图要 PRO 解锁 | — | 商业模式，与本项目无关 |

### 三、历史模块（用户指定细看）

四个入口：`历史`（日历）/ `统计`（8 个板块）/ `报表`（可分享月报）/ `设置`（板块排序）。

统计页的 8 个板块：

```
1. 训练概况      本周训练数 · 力量 N 天 · 有氧 N 天 · 过去 5 周迷你条
2. 部位概览      人体肌肉正反面热力图（重点 / 辅助 两级图例）
3. 运动时间      平均训练 小时/周
4. 平均力量训练  总量图 / 均值线 切换
5. 平均有氧训练  同上
6. 部位容量      部位下拉 + 容量均值
7. 组数对比 | 容量对比   每个部位一行，含 7 周迷你条
8. 最大重量与 PR  管理动作；每项「最大重量 + 1RM 预测」并列
```

#### ★ 值得参考的（按价值排）

**① 每个指标都带「环比上周」，而且「持平」是个明确状态**

他们**没有一个孤立的数字**：「0.0 小时/周」下面紧跟「−1.0 小时（持平）」+「⚖️环比上周」。

我们的趋势页给的是「值 + 曲线 + 较 7 日均值」，**没有和上周比**。
而且「持平」这个词值得学——「没变」本身是信息，不是「差 0」。

**② 部位之间的横向对比**

板块 7「组数对比 | 容量对比」是**每个部位一行**并排，一眼看出「腿练得比胸多」。

我们的图全是**时间序列**（容量折线、肌群横条），缺「同一时刻各部位横向比」这个视角。
⚠️ 但**不要把「组数/容量」做成切换**——`METRICS 4.0` / 4.7 论证过
两者不可互相替代、必须搭配显示。该学的是「横向对比」，不是「二选一」。

**③ 「管理部位 / 管理动作」用户自选关注对象**

只练上半身的人，不该每次都看到「腿 0 组」那一行。

**④ 有氧与力量分开统计**

「训练概况」里两者分开计数、用不同颜色；「运动时间」也拆成两个板块。

我们 `DURATION`/`DISTANCE_DURATION` 的动作（跑步、跳绳、单车、椭圆机）
**混在 `LEGS` 里**，不进容量、也没有独立统计。这是个真实缺口。

**⑤ 统计页板块可拖拽排序**

8 个板块太长，让用户自己决定看哪些、什么顺序。

**⑥ 月报：把 30 天压成 3 个数字**

总时长 / 训练次数 / 强度 + 与上月对比，导出图片。
对我们**没有直接价值**（没用户可分享），但「把一段时间压成几个数 + 一句对比」
这个形态有用——比让用户自己看 8 张图更容易得到结论。

#### ⚠️ 明确不值得参考的

**日历视图。** 他们的「历史」是按月排格子、有训练的日子标记。

而 `METRICS 5.3` 已经论证过不用日历热力图：

> 热力图编码的是「**每天**练没练」，而训练计划的单位是「**每周**练几次」。
> 3 次/周的计划，用户 100% 达标也只能填满 43%。**达标者的图看起来像失败。**

诚实地说，**他们的月视图和我论证的年视图不完全是一回事**：
月视图 30 格、能放数字，年视图 371 格、只能放色块。月视图下「被空白淹没」的问题弱得多。

但 5.3 的核心问题**依然存在**：把 4 天休息日渲染成「空」，等于把计划的一部分显示成缺失。
而且「坚持情况」的周条带已经回答了「我坚持得怎么样」——
日历多出来的信息是「哪几天练」，那个信息的**行动价值低**（没人会因为「我总在周三翘」改变什么）。

**不推荐做。**

### 结论

动作库要补，但**补的是内容广度（约 30 个动作）和两个新分类（热身/拉伸）**，
不是维度粒度。细分部位字段是另一件事，见
[DEVELOPMENT-PLAN 第 9 节](./DEVELOPMENT-PLAN.md) 的待办表。

历史/统计模块**整体上我们没走偏**——他们那 8 个板块里有 6 个我们已有对应物，
而且 `METRICS 4.0` / 4.7 的「容量与组数必须搭配显示」比他们的「二选一切换」更对。
真正的缺口是**环比上周**、**部位横向对比**、**有氧独立统计**这三样。

---

## 步骤 4C —— 动作库扩充与浏览（90 → 125）✅

竞品对照的直接产出。代码 + 数据 + 一个新页面。

### 一、动作库 90 → 125

按缺口补（`V17`），**补的是内容广度，不是维度粒度**：

| 缺口 | 补了什么 |
|---|---|
| **上背厚度**（最大的缺口） | 潘德雷划船、海豹划船、俯卧杠铃划船、地雷杆划船、反手杠铃划船、半程硬拉 |
| **后链 / 臀** | 早安式、地雷杆罗马尼亚硬拉、杠铃单腿硬拉、杠铃上凳 |
| **二头变式** | 蜘蛛弯举、集中弯举、拖式弯举、EZ杆弯举、俯卧上斜弯举 |
| **三头长头** | 绳索过顶臂屈伸（长头只在手臂过顶时被拉长，绳索下压给不了） |
| **胸的技巧变式** | 暂停卧推、宽距杠铃卧推、杠铃片夹胸 |
| **整类缺失** | 热身 6 个、拉伸 6 个 |

### 二、★ 两个新分类不是肌群 —— `isMuscle()` 挡在统计之外

`exercise.primary_muscle` 是 **NOT NULL**，而热身、拉伸也需要一个分类才能被查到，
所以借住在 `MuscleGroup` 里。但它们**不是肌群**：

> 肌群周组数图回答的是「我各肌群练得均衡吗」，旁边挂着「10–20 组/周」的
> 增肌参考区间。把拉伸混进去会变成「腿 8 组、拉伸 6 组」并列——
> 而 6 组拉伸和 6 组深蹲在训练意义上毫无可比性。

这和 `SetType.WARMUP` 被排除在容量之外是**同一个形状的问题**：
在一张用于横向比较的图上，混进不可比的量。

过滤只有一处实现（`MuscleGroup.isMuscle()`），聚合点只有一处调用
（`StatsService.WeekAcc.muscleSets()`）。

> **回归测试回退验证**：把 `muscles()` 改回 `values()`，2 条测试变红
> （`Expected size: 6 but was: 8`）。调用点确实被守住了。

### 三、★ 补上「查找维度」—— 动作库浏览页

对照竞品时想清楚的一件事：它们的肌群分类有 17 个，我们只有 6 个。
**但我们的 6 个是统计维度，它们那 17 个是查找维度。**

所以正确的做法不是把 6 改成 17（那会让肌群组数图裂成 17 条、每条都没数据），
而是**补上查找这一维**。

新增 `GET /exercises/filters` + 客户端 `ExerciseLibraryScreen`：
部位 / 器械 / 模式 三行筛选轴。`equipment` 和 `movement_pattern` **列本来就有**，
只是一直没做成筛选。

### 🐛 联调抓到的四个

#### ① 动作列表按肌群名的**字母序**排

`ORDER BY primary_muscle` 走字符串序 → `ARMS, BACK, CHEST, CORE, LEGS,
SHOULDERS, STRETCH, WARMUP`。于是动作库第一屏全是「手臂」，
「胸」排在「核心」后面——和任何人对身体部位的直觉都不一致。

但 MyBatis-Plus 表达不了「按自定义顺序排」，只能写 `FIELD()`。
**这必然是枚举顺序在 SQL 里的第二份**，所以配了一条契约测试
（`ExerciseOrderContractTest`）钉住两者一致——同类先例是
`sqlE1rmMatchesJavaE1rm` 守着 SQL 里那份 e1RM 公式。

> ⚠️ 顺带踩到：`last("ORDER BY ...").orderByAsc(x)` 会生成
> **两个 ORDER BY**，语法错误。`last()` 永远拼在 SQL 最末尾，
> 所以整条 ORDER BY 只能写在它里面——代价是列名要手写蛇形。

#### ② 分页上限 100，而动作库有 125 个

界面显示「60 个动作」（客户端默认值），而库里有 125 个。
**第 101 个之后的动作直接消失，列表看起来完全正常。**

上限提到 500，客户端显式请求。测试断言是
「**要上限条数时必须拿到全部**」（`records.size() == total`）而不是
「≥125」——等动作库超过上限的那天，这条会红，逼着做分页而不是让截断悄悄发生。

#### ③ `instructions` 里存了 Markdown

我在 `V17` 的要领里写了 `**短语**` 表示强调，客户端直接把星号打了出来。
查库发现 **`V10` 里已经有 15 条同样的问题**——只是一直没有界面展示要领
（动作详情页是这次才加的）。

根因不是「客户端没渲染 Markdown」，而是**格式不该存在数据里**：
`**` 存进 DB 后，每个消费者（Flutter、Phase 5 的 Vue、导出、直接查库的人）
都要各自实现一遍渲染。**强调靠措辞，不靠标记。**

修法分两种：`V17` 还没提交 → 改源码 + 本地重放；
`V10` 已应用 → `V18` 用 `REPLACE` 清理存量。

> ⚠️ **重放 V17 时漏了测试库**，导致 144 个测试报
> `FlywayValidateException: Migrations have failed validation`——
> 我改了 `gym_log` 的历史记录，`gym_log_test` 还留着旧 checksum。
> **两个库都要重放**。现象是「所有 Spring 测试一起挂」，
> 而报错信息里只有一个 `Cannot resolve reference to bean 'sqlSessionTemplate'`，
> 真正的根因在 `Caused by` 链的最底下。

#### ④ 客户端 `_ExerciseTile` 又写出了占位字段

写 widget 时先写了个 `final Exercise e = const Exercise(...)` 占位，
忘了删。这是第二次（4B 的 `_DerivedRow` 同样）。

### 验证

| 项 | 结果 |
|---|---|
| 动作总数 | ✅ 125（胸 17 / 背 23 / 腿 26 / 肩 13 / 手臂 20 / 核心 14 / 热身 6 / 拉伸 6） |
| 肌群图仍是 6 项 | ✅ 周统计返回 6 项，不含 STRETCH/WARMUP |
| 排序 | ✅ 按枚举顺序（胸 → 背 → 腿 → …），不再是字母序 |
| 筛选 | ✅ 背 → 23 个；拉伸 → 6 个；「清空筛选」出现 |
| 详情面板 | ✅ 标签 / 别名 / 要领 / 常见错误；文本无星号 |
| 后端测试 | 282 全绿（+9） |

### 顺带记录：AppBar 挤了

Today 页现在有 **4 个图标**（趋势 / 身体数据 / 动作库 / 刷新）。
这是「该做底部导航了」的信号——4.4 时推迟过（底部导航需要保持状态的 shell）。
再加页面就必须做，已记入待办。

---

## 踩坑汇总

| # | 坑 | 一句话教训 |
|---|---|---|
| 1 | Maven Central 上的「最新版」可能是给 Spring Boot 4 的 | 看与 Boot 配套的版本线，不是看谁数字大 |
| 2 | Windows 中文环境的 platform encoding 是 GBK | pom 必须显式设 `sourceEncoding=UTF-8` |
| 3 | `application-dev.yml` 建了但 profile 没激活 | 用 `profiles.default` 不用 `profiles.active` |
| 4 | 密码写进了会被提交的 example 文件 | 改配置前先 `git check-ignore` |
| 5 | 中文日志乱码 | Java 18 前 `file.encoding` 跟随操作系统 |
| 6 | MyBatis-Plus 3.5.9 拆出了分页插件 | 要额外引 `mybatis-plus-jsqlparser` |
| 7 | SQL 日志绕过 Logback 导致编码失控 | 用 `Slf4jImpl` 不用 `StdOutImpl` |
| 8 | Windows 命令行传中文参数损坏 | 用 `-d @文件`；看字节不看显示 |
| 9 | `isAuthenticated()` 对匿名用户也返回 true | 判断登录要看是不是 `AnonymousAuthenticationToken` |
| 10 | `/error` 不放行会让错误响应被 401 覆盖 | 接口响应码「不对劲」时，先确认请求有没有进到 Controller |
| 11 | MySQL 的多表 `DELETE ... JOIN` 需要先选库 | 加 `-D 库名` 或先 `USE`，即使表名写了库前缀 |
| 12 | 测试数据用了生产数据的名字，撞唯一索引后**红了很久没人发现** | 测试数据加前缀，让测试自洽 |
| 13 | 一个测试类红了不影响别的类，单跑测试时看不出来 | 提交前跑**全量**测试，不是只跑刚改的那个 |
| 14 | `TaskStop` 只杀 Maven 外壳，forked 的 java 进程还占着 8080 | 重启前先 `netstat -ano \| grep :8080` 确认端口真的释放了 |
| 15 | git-bash 的 curl 会转换 `/tmp/x` 路径，而 Python 把 `/tmp` 当 `C:\tmp` | 两边都用**显式 Windows 路径**，别用 `/tmp` |
| 16 | `@JsonInclude(NON_NULL)` 让 null 字段在 JSON 里消失 | 客户端一律按「key 可能不存在」处理，不要假设字段总在 |
| 17 | 查询参数名拼错会被 Spring **静默忽略**，返回全量数据还带 200 | 筛选接口必须校验参数名；断言要检查「确实被过滤了」而不只是「有结果」 |
| 18 | `setIgnoreUnknownFields(false)` 会把 **HTTP 请求头**也当成待绑定属性 | 框架没有「只严格校验查询参数」的开关，只能自己查 |
| 19 | `FieldError.getDefaultMessage()` 对 typeMismatch 返回的是 Spring 原始异常文本 | 里面含包名类名，不能直接返回给客户端 |
| 20 | `BindingResult.rejectValue(f, code, msg)` 的第三参是**错误消息**不是被拒绝的值 | 想指定 rejectedValue 要直接构造 `FieldError` |
| 21 | 已应用的 Flyway 迁移**不能改注释**，checksum 校验会让启动失败 | 迁移是只追加的历史记录；要修正只能在**新**迁移里说明 |
| 22 | 测试里 `selectCount` 数**整张表**，跑完端到端验证后立刻变红 | 数全表的测试是环境依赖的测试；断言必须限定到本测试造的数据 |
| 23 | 从库里读出的实体在写完之后**没有回写**，用它算派生字段就出错 | 派生值从「刚写入的值」推，别从「读出来的实体」推 |
| 24 | 测试里不传 `startedAt`，所有会话开始时间都一样，依赖先后的逻辑全错 | 时间戳是**业务字段**不是审计字段；测试必须显式给 |
| 25 | 一个指标的字段口径不一致（次数含热身、容量不含），两个数字对不上 | 要么全排除，要么全包含，不能只在一处例外 |
| 26 | 真机上 `localhost` 指向手机自己，不是开发机 | `adb reverse tcp:8080 tcp:8080`，比配局域网 IP + 防火墙简单；**设备重连会失效** |
| 27 | 输入注入时键盘弹出会把布局顶上移，旧坐标全部失效 | 每一步之后重新截图量坐标；`keyevent 4` 按两次会退出 App |
| 28 | `target` 为 null 被当成「自重」显示，而杠铃卧推显然不是 | **null 的两种含义要分开**：动作不负重 vs 计划没填重量 |
| 29 | 状态转移里只更新了数据、忘了更新 `phase`，界面停在旧屏 | 单元层面数据全对，**只有真机点一次才看得见**；状态机的每个转移都要问「该显示哪一屏」 |
| 30 | Kotlin 增量编译缓存损坏，`flutter clean` 和停守护进程都无效 | `kotlin.incremental=false` 是可靠解法；代价是编译慢一点 |
| 31 | 客户端用 `DateTime.now()` 当训练开始时间，续训后时长少算 | **服务端已有的时间戳要用服务端的**；本地时钟只知道「我这一刻在跑」 |
| 32 | 注释里写着一套设计、代码实现的是另一套，**两边长期不一致** | 注释是「意图」，代码是「事实」；发现对不上时先问哪个是对的，再改另一个 |
| 33 | 改了 record 的结构，用到它的测试类源码没变，Maven 不重新编译它们 | 报错是 `Unresolved compilation problem` + 「构造器未定义」；`mvn clean` 解决 |
| 34 | 带缓存的 provider 在状态变化后不会自己刷新，界面上看不到新数据 | 导航返回后要 `invalidate`；**「接口调过了」不等于「界面是对的」** |
| 35 | 截图拍到了操作生效前的残留渲染，得出错误结论 | 拿不准时**对日志时间线**——服务端收到的请求顺序不会骗人 |
| 36 | 照抄文档写的 iOS 音频类别，**构造函数里的 assert 会在 debug 下直接崩** | 涉及平台差异的 API，先用编译器/断言验一遍再谈行为 |
| 37 | `flutter_tts` 抢不抢焦点由一个 `focus` bool 决定，**默认 false 但没人知道** | 「默认是对的」不等于「不会被改错」；把隐式默认写成**显式传参** |
| 38 | `soundPool.load()` 是异步的，一个会话里**第一次播放慢了 461ms** | 音频要**预加载 + 预热输出通路**；「能用」和「准时」是两回事 |
| 39 | 预热时把音量设成 0，忘了恢复——之后所有提示音都是静音的 | 临时改状态一定要 `finally` 恢复；**静音和正常在代码上长得一模一样** |
| 40 | 看到自己 App 的 `abandonAudioFocus` 事件，一度以为抢了焦点 | 有 abandon 不等于有 request；**「没请求过」才是判据**，别被日志吓到 |
| 41 | 用 `dumpsys audio` 的 `state:started` 抓不到 180ms 的短音 | 采样频率追不上事件时换数据源：**logcat 的 `AudioTrack` 事件是完整流水** |
| 42 | **MySQL 的 JSON 列会重排 key 并加空格**，`REPLACE` 拿源文件原文匹配 **0 行、静默成功** | 改 JSON 列之前先 `LOCATE` 验一下字符串真的存在；写操作要用 `JSON_SET` 而不是文本替换 |
| 43 | 迁移里「影响 0 行」是**合法成功**，Flyway 照样报通过 | 关键的数据修改后面跟一条**会失败的断言**，让静默失效变成硬失败 |
| 44 | 路径带 `[*]` 时 `JSON_EXTRACT` 返回**数组**（没匹配是 `[]`），`IS NOT NULL` 恒真 | 判断「有没有这个路径」要用 `JSON_CONTAINS_PATH` |
| 45 | `phaseStartedAt` 只有写入没有重置，「实际休息时长」把整组用时也算了进去 | 一个字段的语义要能一句话说清（「**当前阶段**的起点」）；说不清就会有人用错 |
| 46 | 记录接口是 **PUT 全量覆盖**（不是 PATCH），同一组再 PUT 一次会把没带的字段写 null | 「幂等重发」只保证不产生重复记录，**不保证字段不变**；重发时必须带全 |
| 47 | 字段搬家（reps → targetDurationSec）后，**漏改的界面印出 `null-null` 而不报错** | 改后端契约要 grep 所有读过旧字段的前端；漏掉的地方只在界面上现形 |
| 48 | 弹出菜单渲染在 overlay 路由里，**外层 rebuild 不会让已打开的菜单重建** | 菜单内容要在**菜单自己那棵树里** watch 状态，不能把外层的值传进去 |
| 49 | 一个功能看起来「没做」，实际是**三个已存在的 bug 叠在一起** | 动手前先把现状查清楚：那些 `null` 可能不是「还没做」而是「一直都错」 |
| 50 | **测试通过不能证明它连对了库** —— 测试库建了但从没接线，一直跑在开发库上 | 环境类改动要用**旁证**验收：查目标库的表数量/迁移记录，而不是看测试绿不绿 |
| 51 | 一条规则内联了**五处**，而实体上那个写得好、注释也对的方法**零调用者** | 「重复」往往不是没人写过，是写了没人用；动手加第六处之前先 grep 一遍 |
| 52 | 按字面实现文档会得到荒谬结果（一天测 3 次 → 那天权重变 3 倍 → **越勤快曲线越抖**） | 文档和直觉冲突时，回到**它要解决的问题**去读；定下来之后当场改文档 |
| 53 | 测试红了先别改代码：这次是**测试的期望**写错了（streak 语义） | 断言失败时先问「规格到底怎么说的」，规格里的原话往往就否掉了你的期望 |
| 54 | 用「有 from/to 字段的 DTO」当参数白名单，**放行了两个接口根本不看的参数** | 白名单要表达「这个接口接受什么」，不是「有哪些 DTO 可用」 |
| 55 | 会话的 `startedAt` 是客户端传的，不传默认 `now()` —— 造的 36 场训练全挤在今天 | 造数据/写测试**必须显式给时间戳**；这类默认值不会报错，只会让日期悄悄全一样 |
| 56 | `abandon` 只对 `IN_PROGRESS` 有效，循环里 `-o /dev/null` 把错误吞了，三场「已撤」一场没撤 | **把响应丢掉等于关掉错误通道**；批量脚本至少要 `-w "%{http_code}"` |
| 57 | 契约测试用 `isEqualByComparingTo`（数值比较），**守住了值却漏掉了精度** | 参考线写 `81.666667`、曲线点写 `81.67` —— 同一个数两种写法；「值相等」和「显示一致」是两件事 |
| 58 | 三次「测试红了」里有两次是**测试的期望**写错（streak 语义、PR 排序方向） | 断言失败先回规格里找原话，别急着改被测代码 |
| 59 | `WHERE name IN (10 个名字)` 只回来 9 条，**没核对条数就以为全在** | `IN` 查询里不存在的项静默消失；核对存在性要数「回来几条」，不是「回来的都是啥」 |
| 60 | 造数据的脚本用 `startedAt` 不传 → 36 场训练全挤在今天，日期全错但不报错 | 造数据脚本的**第一条自检**应该是「数据分布对不对」，不是「跑没跑完」 |
| 61 | fl_chart 的 `iterateThroughAxis` **无条件多补一个 `max` 刻度**，两个标签叠成 `9-79-14` | 越界检查挡不住「合法但离得太近」；标签位置要自己算，别交给库的 `interval` |
| 62 | 同一个多余刻度在另一个页面上**恰好没重叠**，差点被当成「没问题」放过 | 「看起来对」不是「对」；先想清楚它为什么对，再说要不要修 |
| 63 | Flutter 模板只在 **debug** manifest 里声明 INTERNET，release 包**一个权限都没申请** | release 包要单独验一次；权限缺失长得像网络故障 |
| 64 | 之前所有「真机验证」跑的都是 debug 包，而没人注意到 | 验证前先确认**验的是哪个产物**；`app-debug.apk` 和 `app-release.apk` 不是一回事 |
| 65 | 用**下标**记住「用户选的那一周」，切换时间范围后落到不相干的一周 | 跨「重新取数」保留的位置要存**语义值**（日期），不能存偏移量 |
| 66 | XML 注释里写了 `--dart-define`，aapt 直接拒绝解析 | XML 注释里不能出现 `--`；报错信息（`注释中不允许出现字符串 "--"`）很明确，别去猜别的地方 |
| 67 | MySQL 的唯一索引把 **NULL 当作互不相等**，可空列上的唯一键等于没建 | 参与唯一键的列一律 `NOT NULL` + 哨兵值；这类失效**不报错**，只在重放时冒出重复行 |
| 68 | `value` 取了 `DECIMAL(5,2)`，BMR（1200–2500）直接溢出 | 定列宽时拿**量程最大的那个指标**去试，不是拿最常见的 |
| 69 | `condition` 是 MySQL 保留字，用它当列名要处处加反引号，漏一处是运行期错 | 换个名字成本为零；保留字清单值得先扫一遍 |
| 70 | 「距上次测量」的变化量算出了**反的**（体重降了却显示 +1.01） | 不同条件（晨起/训练后）的读数相减，差的是测量时机；规格里其实写明了该用 7 日均值 |
| 71 | 部位列表在 series 响应里，而 series 不传部位就被拒 → **死循环**，用户永远选不了 | 对 GET 来说「把可选项列出来」严格优于「报错说漏了参数」 |
| 72 | 同一个 fl_chart 行为在 **Y 轴**上重演：`min` 也会被多补一个刻度，叠在相邻标签上 | 底轴能按下标过滤，Y 轴不能（连续值）；改法是让 min/max 落在刻度网格上 |
| 73 | 展开函数不带 `bw_factor`，快照里永远是 NULL，**自重容量一直是 0** | 而 0 正是「没记体重」的合法值——**合法值掩盖了断链**，直到有真体重才露出来 |
| 74 | 种子脚本号称「可重复执行」，实际第二次必撞唯一键 | 逻辑删除的表用 Mapper delete 只标 `deleted=1`，物理行还在；「没验过」的注释等于没有 |
| 75 | 新写的回归测试，**先把修复回退一次确认它真的会红** | 否则你只是写了三条永远绿的断言，而它们看起来像在保护什么 |
| 76 | 「上下各留 10% 余量」换成「对齐到网格」之后，10% 这条保证**静悄悄丢了** | 对齐是**离散**操作，百分比余量会被吃掉；两者要显式组合，不能假设一个包含另一个 |
| 77 | 删枚举值会让库里对应的行**映射不回来**，任何 selectList 都炸 | 改枚举范围时必须同时决定「历史行怎么办」；留着读不出来的行比删掉危险 |
| 78 | 一个「用户能不能自己测」的判据，一口气砍掉了整节 Must 需求 | 需求的优先级是「想要的程度」，不是「该不该做」；判据清楚时，Must 也可以不做 |
| 79 | 客户端为了好看把 0.466 四舍五入成 **0.5**，而 0.5 正是那个指标的阈值 → 「0.5 健康」 | 显示层「只是在美化」的想法很危险：它改掉的可能是语义 |
| 80 | Deurenberg 要性别 1/0，而枚举是 1/2 —— 代入 2 会算出女性比男性还低 | 公式的输入域和系统的枚举域**不是一回事**，中间必须显式映射 |
| 81 | 竞品的分类粒度看着很专业，但那是**查找维度**、不是统计维度 | 对照竞品时先问「它解决的是哪个问题」——我们的 6 分类是为了统计，不能因为别人有 17 个就跟着改 |
| 82 | `adb shell "uiautomator dump /sdcard/ui.xml"` 的路径又被 git-bash 转换 | 第 15 条写过的坑，今天又踩了一次——**已知坑也要真的避开，光记下来没用** |
| 83 | RN 应用的「分段控件 / 图标按钮」在 UI dump 里都是普通 TextView，看不出层级 | 别靠文字顺序猜结构，**截一张图看布局**比 dump 十次快 |
| 84 | 差点建议做日历视图，而 `METRICS 5.3` 早就论证过不做 | 给建议前先 grep 自己的文档——**已有的论证比新看到的产品更有分量** |
| 85 | `last("ORDER BY ...").orderByAsc(x)` 生成**两个 ORDER BY**，语法错误 | `last()` 永远拼在最末尾；自定义 ORDER BY 只能整条写进去 |
| 86 | `ORDER BY 字符串列` 走字母序，「手臂」排第一、「胸」在「核心」后 | 有序的枚举要显式 `FIELD()`；而那是第二份顺序，必须配契约测试 |
| 87 | 分页上限 100 而数据 125 条，界面少了 25 个**看不出任何异常** | 断言要写「请求上限时必须拿到全部」，数据超过上限那天它会红 |
| 88 | DB 里存了 Markdown，客户端原样打出星号；而 15 条老数据**早就**有同样的问题 | 格式不该存在数据里——每个消费者都要实现一遍渲染；强调靠措辞 |
| 89 | 重放迁移只改了 dev 库，测试库 checksum 不匹配 → **144 个测试一起挂** | 改了已应用的迁移，**两个库都要重放**；根因藏在 `Caused by` 链最底部 |
| 90 | 写 widget 时留了 `final X e = const X(...)` 占位字段没删（第二次了） | 先写构造函数再写字段；或者写完立刻 analyze |

> **测试方法本身的坑**：验证「篡改检测」时改了 Base64 的最后一个字符，
> 结果验签通过了——因为 86 个 Base64 字符 = 516 位，只有 512 位有效，
> **最后 4 位是填充位**。改中间的字符才是有效篡改。
> **教训：验证篡改检测时，要确保篡改真的改变了数据。**

> **这份清单本身就是这个项目最有价值的产出之一。**
> Windows 中文环境做 Java 开发，编码问题几乎必然遇到，且表现形式各不相同
> （源码乱码、日志乱码、命令行参数乱码、SQL 参数乱码）。
