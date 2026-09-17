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
| `muscle=CHEST` | 15 |
| `equipment=DUMBBELL` | 20 |
| `metricType=REPS_ONLY` | 24 |
| `onlyCustom=true` | 1 |
| 分页 `page=1&size=5` | 5 条记录，total=91 |
| 中文搜索 `卧推` | **6**（窄距/杠铃/上斜/下斜/哑铃/上斜哑铃） |
| 中文搜索 `深蹲` | 5 |
| 组合筛选 `CHEST + DUMBBELL` | 4 |
| 模式筛选 `SQUAT` | 9 |

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

> **测试方法本身的坑**：验证「篡改检测」时改了 Base64 的最后一个字符，
> 结果验签通过了——因为 86 个 Base64 字符 = 516 位，只有 512 位有效，
> **最后 4 位是填充位**。改中间的字符才是有效篡改。
> **教训：验证篡改检测时，要确保篡改真的改变了数据。**

> **这份清单本身就是这个项目最有价值的产出之一。**
> Windows 中文环境做 Java 开发，编码问题几乎必然遇到，且表现形式各不相同
> （源码乱码、日志乱码、命令行参数乱码、SQL 参数乱码）。
