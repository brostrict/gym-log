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

> **测试方法本身的坑**：验证「篡改检测」时改了 Base64 的最后一个字符，
> 结果验签通过了——因为 86 个 Base64 字符 = 516 位，只有 512 位有效，
> **最后 4 位是填充位**。改中间的字符才是有效篡改。
> **教训：验证篡改检测时，要确保篡改真的改变了数据。**

> **这份清单本身就是这个项目最有价值的产出之一。**
> Windows 中文环境做 Java 开发，编码问题几乎必然遇到，且表现形式各不相同
> （源码乱码、日志乱码、命令行参数乱码、SQL 参数乱码）。
