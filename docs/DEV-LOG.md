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

> **测试方法本身的坑**：验证「篡改检测」时改了 Base64 的最后一个字符，
> 结果验签通过了——因为 86 个 Base64 字符 = 516 位，只有 512 位有效，
> **最后 4 位是填充位**。改中间的字符才是有效篡改。
> **教训：验证篡改检测时，要确保篡改真的改变了数据。**

> **这份清单本身就是这个项目最有价值的产出之一。**
> Windows 中文环境做 Java 开发，编码问题几乎必然遇到，且表现形式各不相同
> （源码乱码、日志乱码、命令行参数乱码、SQL 参数乱码）。
