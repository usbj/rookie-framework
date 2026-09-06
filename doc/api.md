# API 接口文档

**更新日志格式约定：**
- 每次接口更新在「接口更新日志」章节追记一条，一条日志推荐覆盖 5–8 个接口或一个模块的完整变更
- 不满记录门限时可先将变更记在中间暂存区，满足后再合并为正式日志记录
- 单条日志描述本次变更涉及的范围、接口清单和简要说明
- 此约定后续可能取消或调整

**文档格式约定：**
- 每个接口统一划分为四个子节：基本信息、请求头、请求体（含参数说明）、响应示例
- 若无请求头/请求体，写"无"
- 参数说明统一使用表格；若请求体为类接收，参数表格即该类属性的描述；若含嵌套类，在下方另起一表

---

## 接口更新日志

### 2026-08-15 — 服务监控改为可插拔监控源聚合接口 + 系统监控目录

- 服务监控接口由 `GET /sys/monitor/server` 改为 **`GET /sys/monitor/items`**（需登录 + `system:monitor:quarry`）：聚合全部监控源（`MonitorProvider`）的实时数据，返回 `type / title / data` 数组；当前含 `server`（服务器 CPU/内存/磁盘/系统/JVM），**后续接入 Redis / MySQL 等中间件只需实现 MonitorProvider 并注册 Bean，接口自动纳入**；单个监控源采集失败不影响其他项（该项 data 为 null）
- 菜单结构调整：新增「系统监控」目录（perm_key=`monitor`），在线用户（system:online）、定时任务（system:job）、执行日志（system:jobLog）、服务监控（system:monitor）移入其下，原「任务管理」目录（job）删除；对应菜单访问路径变化（如在线用户 `/system/online` → `/system/monitor/online`），脚本见 `sql/sys_monitor_catalog.sql`
- 定时任务「查看详情」为前端交互新增（复用既有列表数据展示，无接口变更）

### 2026-08-15 — 服务监控模块（服务器 CPU / 内存 / 磁盘 / 系统 / JVM）

- 新增 `GET /sys/monitor/server` 服务器监控信息（需登录 + `system:monitor:quarry`，只读不记日志）：返回 CPU（核心数/使用率/系统负载）、物理内存（总量/已用/可用/使用率）、磁盘（各分区）、系统基本信息（OS/版本/架构/主机名/当前时间）、JVM 数据（版本/启动时间/运行时长/堆与非堆内存/GC 统计/线程/类加载）
- 零依赖实现（`java.lang.management` + `com.sun.management.OperatingSystemMXBean` + `File.listRoots()`，不引入第三方库，便于多项目共用）；仅实时快照展示，不落库
- 说明：接口内做一次 CPU 双采样（约 300ms）属正常延迟；Windows 下系统负载不可用返回 null
- 新增 `sql/sys_monitor.sql`：服务监控菜单 + 查询按钮（无 menu_id 自增增量风格，parent 按 perm_key 反查，可跨项目重复执行）

### 2026-08-15 — 定时任务管理模块（方案 A：Spring TaskScheduler 动态注册）

- 新增定时任务管理全套接口（`/sys/job/**`）：分页列表、详情、新增、编辑、删除、启停、立即执行（权限 `system:job:*`，写操作 @Log）。任务元数据落 `sys_job` 表，启用任务启动即注册进调度器（CronTrigger），增删改/启停动态注册或取消，改配置即生效无需重启
- 新增执行日志接口 `GET /sys/job/log/list`（权限 `system:jobLog:quarry`）：每次执行（自动/手动）记录触发方式、调用目标、耗时、结果与异常信息
- 调用目标为 Spring Bean 方法（beanName + methodName），**限定 `com.rookie.system.task` 包**（白名单校验，防止任意 Bean 方法被反射调度），方法须为 public、无参或单个 String 参数（参数来自 `params` 字段）；cron 为 Spring 6 段式表达式，保存前校验合法性
- 防重：同一任务执行中再次触发直接跳过并记录日志；执行失败只记 `sys_job_log`（不落操作/错误日志体系）
- 边界：单机调度（多实例部署时任务会重复执行，需另行引入分布式锁或任务平台）
- 新增 `sql/sys_job.sql`：建表 + 任务管理目录/菜单/按钮权限（菜单不写 menu_id 的自增增量风格，`parent_id` 按 `perm_key` 反查，可跨项目重复执行）

### 2026-08-15 — 文件存储规则优化：按 年/月/日 分层 + 系统名命名

- 上传存储规则调整（接口本身不变）：文件按「根路径/年/月/日」三层目录分层存储（头像为 `avatar/年/月/日`）；存储名由「UUID + 原扩展名」改为「系统名称（`rookie.system-name`，application.yml 新增配置，默认 rookie）+ 年月日（yyyyMMdd）+ 毫秒时间戳 + 扩展名」（如 `rookie202608152011191234567.png`）
- 下载/删除定位：从存储名提取毫秒时间戳反推 年/月/日 目录（与内嵌日期一致性校验），无需额外记录路径；旧存储名（UUID 格式）将无法再被下载接口定位，属预期行为

### 2026-08-15 — 在线用户统计模块（在线人数/列表/强踢）+ 退出登录 + 登录 IP

- 新增 `GET /sys/online/ping` 在线心跳（需登录，不加按钮权限）：前端登录后每 60s 调用维持挂机在线；实际活跃记录由 `TokenVerifyFilter` 在每个已登录请求写入 Redis 在线集合（ZSET，member=username，score=最后活跃时间戳）
- 新增 `GET /sys/online/count` 在线人数与 `GET /sys/online/list` 在线列表（需登录，权限 `system:online:quarry`）：在线判定阈值 = 系统设置 `sys.online.timeout`（NUMBER，分钟，默认 30，系统设置页可改），最后活跃超过阈值视为离线；列表返回账号/昵称/登录IP/登录时间/最后活跃
- 新增 `POST /sys/online/logout/{username}` 强制下线（需登录，权限 `system:online:kick`，@Log）：移除在线集合 + 删除登录态缓存，旧 token 立即失效（下次请求 401）；不能对当前登录账号操作
- 新增 `POST /logout` 退出登录（需登录，@Log）：从在线集合移除当前用户 + 删除登录态缓存，幂等
- 登录接口 `POST /login`：登录时解析并记录登录 IP（随登录态缓存，在线列表展示）
- 新增 `sql/sys_online.sql`：设置项 `sys.online.timeout` + 菜单权限点 menu_id 100~102（执行后需在后端系统设置页确认阈值、admin 直通无需角色授权）
- 离线语义：关浏览器不主动下线，最长延迟一个阈值周期（默认 30 分钟）掉线

### 2026-08-14 — 文件上传/下载模块（轻量）+ 用户头像上传

- 新增 `POST /sys/file/upload` 上传文件（multipart，需登录）：不落库，文件按「UUID + 原扩展名」存入本地上传路径（`rookie.upload.path`，application.yml 可配置）；返回 `storedName/originalName/size/ext`
- 新增 `GET /sys/file/download/{storedName}` 下载文件（需登录）：按存储名流式返回（attachment，`originalName` 可选仅作展示名），存储名白名单 + normalize 双重防路径穿越；文件大小上限由 `spring.servlet.multipart.max-file-size` 控制（默认 50MB）
- 新增 `POST /person/avatar` 上传个人头像（multipart，需登录）：仅图片（png/jpg/jpeg/gif/webp，≤2MB），存入上传路径 `avatar/` 子目录，更新 `sys_user.avatar` 并清理旧头像；`/person` 响应新增 `avatar` 字段
- 新增 `GET /person/avatar` 获取个人头像（需登录）：inline 返回头像图片字节流，前端 blob 加载转 objectURL 展示
- 说明：上传/头像接口**不记操作日志**——`LogAspect` 会序列化方法参数，MultipartFile 的 `getBytes()` 会把文件整体读入内存再转 JSON，超大文件下开销不可接受

### 2026-08-14 — 公告详情公开 + 用户自助注册 + 注册开关专用接口

- `GET /sys/notice/{noticeId}` 消息通知详情：移除 `@PreAuthorize("system:notice:info")`，改为**公开接口**（游客可访问，公告详情门户场景）。SecurityConfig 用 `RegexRequestMatcher` 按 `GET /sys/notice/{数字ID}` 精确放行，`/list`、`/my`、`/read`、`/confirm` 仍要求认证（`/list` 的方法级 `@PreAuthorize` 不变）。
- 新增 `POST /register` 用户自助注册（公开接口，`@Log` 记录）。受系统设置 `sys.user.registerEnabled`（BOOLEAN，默认 false）控制：未开启时返回"注册功能未开放"；开启后校验参数与唯一性（用户名 ≤12 位字母数字下划线、密码 6-20 位、手机号 11 位），密码 BCrypt 加密入库，绑定系统默认角色（`getDefaultRole()`，`sys_role.is_default=1`），注册即启用、不自动登录。注册开关配置项见 `sql/sys_config_register.sql`（无主键增量插入，rookie 与二开项目通用）。
- 新增 `GET /register/enabled` 注册开关状态查询（公开接口）：供登录页/注册页判断注册入口显隐。**前端不直接读取系统设置接口**——`/sys/system-config/configKey/{configKey}` 保持"仅需登录"，不放宽给游客（避免游客任意获取系统设置）。

### 2026-08-14 — 个人中心修复与修改密码独立接口

- `PUT /person` 修改个人信息：修复 `update_by` 未填充导致的"用户信息更改失败"（`editUserInfo` 固定更新 `update_by` NOT NULL 列，Service 现从登录态填充当前用户名）。
- 新增 `PUT /person/password` 修改个人密码（需要登录，`@Log` 记录）：请求体 `oldPassword`/`newPassword`，校验原密码（BCrypt matches）后加密落库，与资料编辑完全分离；`editUserInfo` 白名单保持不碰 password（密码修改不再经过资料编辑接口，前端资料表单中的 password 字段已移除）。

### 2026-07-07 — 系统设置模块补充前端加载接口

新增 `GET /sys/system-config/list-all` 接口（公共读取，需登录即可，不加按钮权限），返回全部启用设置项，供前端登录后全量加载到内存缓存（对标字典启动加载）。

### 2026-07-03 — 系统设置模块（system_config）

本批次新增系统设置模块的完整接口，包括分页查询、详情、新增、编辑、删除、刷新缓存。

- 系统设置（6 个）

系统设置为键值型，值类型由 `value_type` 标识（STRING/BOOLEAN/NUMBER/JSON）。数据库为唯一源，Redis 为永久缓存副本（key 前缀 `sys_config:`），工具类 `SysConfigUtil` 只读缓存、不走数据库，启动时由 `SysConfigWarmUpRunner` 预热。内置项（`is_system=1`）受保护：禁止删除、禁止修改 `configKey` 与 `valueType`、禁止停用，仅可改值/名称/备注。「刷新缓存」接口清空后立即从数据库重新预热全部启用项。所有接口均经 `@PreAuthorize` 鉴权，权限 key 与 `sql/sys_config.sql` 中的按钮权限对齐（`system:systemConfig:*`）。

### 2026-06-28 — 后端鉴权补齐：业务接口 @PreAuthorize + admin 直通兜底

本批次给此前仅有 `@PreAuthorize` 的日志模块之外的 7 个业务 controller 全量补齐 endpoint 级鉴权，并在权限加载层实现 admin 直通兜底。

- 用户管理（6 个）、角色管理（7 个）、菜单管理（6 个）、字典管理（5 个）、字典数据（5 个）、消息通知（7 个）、通知分组（7 个）

合计 43 个接口加注 `@PreAuthorize("hasAuthority('system:<module>:<action>')")`，`perm_key` 与 `sql/rookie.sql` 中 `sys_menu` 现网数据一致（字典数据为 `system:dictData:*`）。个接口（`GET /sys/dist/data/type/{dictKey}` 字典公共读取、`/sys/notice/my`、`/read`、`/confirm` 个人向）保持仅需登录、不加按钮权限。

鉴权不通过时由 `AccessDeniedHandlerImpl` 统一返回 `{code:403, msg:"请求访问：<uri>，但没有权限，无法访问系统资源"；未登录访问由 `AuthenticationEntryPointImpl` 返回 `{code:401, msg:"...认证失败..."}`。超级管理员（`sys_role.role_key='admin'`）登录时在 `UserDetailServiceImpl` 中直通加载 `sys_menu` 全部按钮权限作为兜底，不再单纯依赖 `*_admin.sql` 逐菜单授权。

### 2026-06-27 — 日志管理模块（操作日志 + 错误日志）

本批次新增日志管理模块的完整接口，包括操作日志与错误日志的列表、详情、批量删除、清空。

- 操作日志（4 个）
- 错误日志（4 个）

合计 8 个接口。

操作日志列表对失败行返回关联的 `errorLogId`，供前端"错误日志"按钮跳转；错误日志详情返回 `operLogId`，供反向跳转操作日志。所有接口均经 `@PreAuthorize` 鉴权，权限 key 与 `sys_log_menu_init.sql` 中的按钮权限对齐。

### 2026-06-20 18:30 — 消息通知模块 + 通知分组

本批次新增消息通知模块的完整接口，包括消息主表的全生命周期管理、分组投递、个人消息查询、已读/确认。

- 消息通知（10 个）
- 通知分组（7 个）

合计 17 个接口。

消息删除采用软删除（`delete=1`）；`getMyNotices` 支持 ALL 全员 + GROUP 分组联合查询，返回值含 `hasRead`/`hasConfirmed` 字段。

### 2026-06-20 — 首版文档：登录、用户、角色、菜单、字典、字典数据

本批次补齐截至当前所有已完成的接口。

- 登录模块（4 个）、用户管理（6 个）、角色管理（7 个）、菜单管理（6 个）、字典管理（5 个）、字典数据（6 个）

合计 34 个接口。

---

## 通用响应说明

所有接口使用 `com.rookie.common.pojo.Result<T>` 包裹响应体：
- `code`: 200 成功，其他值失败（500 通用服务端错误）
- `msg`: 成功时固定 `"请求成功"`，失败时携带错误描述
- `data`: 成功时携带业务数据，失败时为 `null`

列表类接口使用 PageHelper 分页，返回 `PageInfo` 对象，内含 `list`、`total`、`pageNum`、`pageSize`。

需认证的接口通过 Spring Security + JWT 拦截，前端需在请求头中携带 `Token: <令牌值>`（无前缀，无 Bearer），由 `TokenVerifyFilter` (#34) 校验。

---

# 系统模块

## 一、认证管理

### 1. 登录

#### 1.1 基本信息
**请求接口：** `/login`
**请求方式：** POST
**所需权限：** 无
**基本信息：** 接收用户登录凭据，返回 JWT token 字符串

#### 1.2 请求头
无

#### 1.3 请求体

| 参数名   | 参数说明 | 参数类型 | 是否必填 |
| -------- | -------- | -------- | -------- |
| username | 登录账号 | string   | 是       |
| password | 账号密码 | string   | 是       |

**示例：**
```json
{
  "username": "admin",
  "password": "123456"
}
```

#### 1.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": "eyJhbGciOiJIUzI1NiJ9.xxx"
}
```

**失败示例：**
```json
{
  "code": 500,
  "msg": "请求失败",
  "data": null
}
```

---

### 2. 个人信息

#### 2.1 基本信息
**请求接口：** `/person`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取当前登录用户的个人信息

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickName": "管理员",
    "phoneNumber": "13800138000",
    "sex": "1",
    "avatar": "a1b2c3d4e5f6.png",
    "status": 1,
    "createTime": "2025-01-01T00:00:00",
    "userRole": [],
    "roleId": []
  }
}
```

| 响应字段     | 参数说明       | 参数类型                   |
| ------------ | -------------- | -------------------------- |
| userId       | 用户 ID        | long                       |
| username     | 登录账号       | string                     |
| nickName     | 昵称           | string                     |
| phoneNumber  | 手机号         | string                     |
| sex          | 性别           | string                     |
| avatar       | 头像存储名（无头像为 null） | string（可空）  |
| status       | 状态           | integer                    |
| createTime   | 创建时间       | string                     |
| userRole     | 关联角色列表   | array\<SysRole\>           |
| roleId       | 关联角色 ID   | array\<long\>              |

---

### 3. 修改个人信息

#### 3.1 基本信息
**请求接口：** `/person`
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改当前登录用户的个人信息，userId 由服务端从 token 提取

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名      | 参数说明 | 参数类型 | 是否必填 |
| ----------- | -------- | -------- | -------- |
| nickName    | 昵称     | string   | 否       |
| phoneNumber | 手机号   | string   | 否       |
| sex         | 性别     | string   | 否       |

**示例：**
```json
{
  "nickName": "管理员2",
  "phoneNumber": "13900139000",
  "sex": "0"
}
```

#### 3.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

---

### 4. 修改个人密码

#### 4.1 基本信息
**请求接口：** `/person/password`
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改当前登录用户密码，与资料编辑（`PUT /person`）完全分离。校验原密码（BCrypt matches）通过后，新密码 BCrypt 加密落库；不更新其他任何资料字段（`editUserInfo` 白名单保持不碰 password）。修改成功后不自动登出，下次登录使用新密码

#### 4.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 4.3 请求体

| 参数名      | 参数说明        | 参数类型 | 是否必填 |
| ----------- | --------------- | -------- | -------- |
| oldPassword | 原密码          | string   | 是       |
| newPassword | 新密码（6-20 位） | string   | 是       |

#### 4.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

**失败示例（原密码错误）：**
```json
{
  "code": 500,
  "msg": "原密码错误",
  "data": null
}
```

---

### 5. 获取当前用户路由树

#### 4.1 基本信息
**请求接口：** `/person/routers`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 返回当前用户有权限访问的菜单树

#### 4.2 请求头
无

#### 4.3 请求体
无

#### 4.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": [
    {
      "menuId": 1,
      "menuName": "系统管理",
      "permKey": "system:manage",
      "parentId": 0,
      "menuType": 1,
      "route": "/system",
      "path": "system",
      "icon": "setting",
      "status": 1,
      "createTime": "2025-01-01T00:00:00",
      "sonMenus": []
    }
  ]
}
```

| 响应字段   | 参数说明   | 参数类型                   |
| ---------- | ---------- | -------------------------- |
| menuId     | 菜单 ID    | long                       |
| menuName   | 菜单名称   | string                     |
| permKey    | 权限标识   | string                     |
| parentId   | 父菜单 ID  | long                       |
| menuType   | 菜单类型   | integer                    |
| route      | 前端路由   | string                     |
| path       | 组件路径   | string                     |
| icon       | 图标       | string                     |
| status     | 状态       | integer                    |
| createTime | 创建时间   | string                     |
| sonMenus   | 子菜单列表 | array\<SysMenuVo\>（递归） |

---

### 6. 用户自助注册

#### 5.1 基本信息
**请求接口：** `/register`
**请求方式：** POST
**所需权限：** 公开（无需登录；注册开关 `sys.user.registerEnabled` 未开启时后端直接拒绝）
**基本信息：** 用户自助注册。受系统设置 `sys.user.registerEnabled`（BOOLEAN，默认 false）控制，关闭时返回"注册功能未开放"；开启后校验参数与唯一性，密码 BCrypt 加密存储，注册即启用（status=1），并自动绑定系统默认角色（`sys_role.is_default=1`，即 visitor），不自动登录

#### 5.2 请求头
无

#### 5.3 请求体

| 参数名      | 参数说明                          | 参数类型 | 是否必填 |
| ----------- | --------------------------------- | -------- | -------- |
| username    | 登录账号（≤12 位字母数字下划线）  | string   | 是       |
| password    | 用户密码（6-20 位）               | string   | 是       |
| nickName    | 用户昵称（空则默认取 username）   | string   | 否       |
| phoneNumber | 手机号（11 位）                   | string   | 否       |
| sex         | 性别（'0' 男 / '1' 女 / '3' 未知，空默认 '0'） | string   | 否       |

#### 5.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

**失败示例（开关未开启 / 用户名已存在）：**
```json
{
  "code": 500,
  "msg": "注册功能未开放",
  "data": null
}
```

---

### 7. 获取注册开关状态

#### 7.1 基本信息
**请求接口：** `/register/enabled`
**请求方式：** GET
**所需权限：** 公开（无需登录）
**基本信息：** 返回注册开关状态（登录页/注册页据此显隐注册入口与提示）。后端读取系统设置 `sys.user.registerEnabled`（BOOLEAN，默认 false），未配置或停用时视为关闭。**前端不直接读取系统设置接口**（`/sys/system-config/configKey/**` 保持仅需登录，避免游客任意获取系统设置）

#### 7.2 请求头
无

#### 7.3 请求体
无

#### 7.4 响应示例

**开启时：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

**关闭时：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": false
}
```

---

### 8. 上传个人头像

#### 8.1 基本信息
**请求接口：** `/person/avatar`
**请求方式：** POST（multipart/form-data）
**所需权限：** 需要登录
**基本信息：** 上传当前登录用户头像：后端校验图片类型（png/jpg/jpeg/gif/webp）与大小（≤2MB）后，存入本地上传路径（`rookie.upload.path`，application.yml 可配置）下 `avatar/年/月/日` 子目录，存储名为「系统名称 + 年月日 + 毫秒时间戳 + 扩展名」（如 `rookie202608152011191234567.png`）；更新 `sys_user.avatar` 列并清理旧头像文件。**不落文件表、不记操作日志**（操作日志切面会序列化方法参数，MultipartFile 的 `getBytes()` 会把图片整体读入内存转 JSON，开销不可接受）

#### 8.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 8.3 请求体
multipart/form-data：

| 参数名 | 参数说明                       | 参数类型 | 是否必填 |
| ------ | ------------------------------ | -------- | -------- |
| file   | 头像图片（png/jpg/jpeg/gif/webp，≤2MB） | file     | 是       |

#### 8.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": "a1b2c3d4e5f6.png"
}
```

**失败示例（类型/大小不符）：**
```json
{
  "code": 500,
  "msg": "头像仅支持 png/jpg/jpeg/gif/webp 格式",
  "data": null
}
```

---

### 9. 获取个人头像

#### 9.1 基本信息
**请求接口：** `/person/avatar`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 按当前登录用户 `sys_user.avatar` 存储名读取头像图片，**inline 内联返回图片字节流**（`image/png` 等，按扩展名推断）。前端以 blob 方式请求并转 objectURL 展示（`<img>` 标签无法携带 Token 请求头）。未设置头像或存储文件缺失时返回 `Result` 错误（业务码 500）

#### 9.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 9.3 请求体
无

#### 9.4 响应示例

**成功示例：** `Content-Type: image/png`，响应体为图片二进制流

**失败示例（未设置头像）：**
```json
{
  "code": 500,
  "msg": "尚未设置头像",
  "data": null
}
```

---

### 10. 退出登录

#### 10.1 基本信息
**请求接口：** `/logout`
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 退出当前登录：从在线集合移除当前用户 + 删除登录态缓存，旧 token 立即失效（后续请求 401）。幂等，重复调用直接返回成功。前端退出流程先调本接口再清空本地登录态

#### 10.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 10.3 请求体
无

#### 10.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

---

## 二、用户管理

**模块前缀：** `/sys/user`

### 1. 获取用户列表

#### 1.1 基本信息
**请求接口：** `/sys/user/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询用户列表（Query String 传参）

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名      | 参数说明 | 参数类型             | 是否必填 |
| ----------- | -------- | -------------------- | -------- |
| username    | 用户名   | string               | 否       |
| nickName    | 昵称     | string               | 否       |
| phoneNumber | 手机号   | string               | 否       |
| status      | 状态     | integer              | 否       |
| beginTime   | 起始时间 | string (ISO datetime) | 否       |
| endTime     | 截止时间 | string (ISO datetime) | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysUserVo>>`

---

### 2. 获取用户详情

#### 2.1 基本信息
**请求接口：** `/sys/user/{userId}`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取指定用户的详细信息

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysUserVo>`

---

### 3. 添加用户

#### 3.1 基本信息
**请求接口：** `/sys/user`
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 新增用户并关联角色

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名      | 参数说明    | 参数类型      | 是否必填 |
| ----------- | ----------- | ------------- | -------- |
| username    | 登录账号    | string        | 是       |
| password    | 密码        | string        | 是       |
| nickName    | 昵称        | string        | 否       |
| phoneNumber | 手机号      | string        | 否       |
| sex         | 性别        | string        | 否       |
| roleId      | 关联角色 ID | array\<long\> | 否       |

**示例：**
```json
{
  "username": "newuser",
  "password": "123456",
  "nickName": "新用户",
  "phoneNumber": "13800138000",
  "sex": "1",
  "roleId": [1, 2]
}
```

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 编辑用户

#### 4.1 基本信息
**请求接口：** /sys/user
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改用户信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `userId`（Long）

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除用户

#### 5.1 基本信息
**请求接口：** /sys/user/{userIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除用户


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：`userIds` — Long[]（逗号分隔） | **响应：** `Result<Boolean>`)

#### 5.4 响应示例
-

---

### 6. 更改用户状态

#### 6.1 基本信息
**请求接口：** /sys/user/status
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 更改用户状态


#### 6.2 请求头
无

#### 6.3 请求体
无（查询参数：`userId`(long)、`status`(integer) | **响应：** `Result<Boolean>`)

#### 6.4 响应示例
-

---

## 三、角色管理

**模块前缀：** `/sys/role`

### 1. 获取角色列表

#### 1.1 基本信息
**请求接口：** `/sys/role/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询角色列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| roleName  | 角色名称 | string   | 否       |
| status    | 状态     | integer  | 否       |
| beginTime | 起始时间 | string   | 否       |
| endTime   | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysRoleVo>>`

---

### 2. 获取角色详情

#### 2.1 基本信息
**请求接口：** /sys/role/{roleId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取角色详细信息（含权限列表）


#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysRoleVo>`（含 `rolePerm` 权限列表和 `permId` 权限 ID）

---

### 3. 添加角色

#### 3.1 基本信息
**请求接口：** `/sys/role` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名    | 参数说明    | 参数类型      | 是否必填 |
| --------- | ----------- | ------------- | -------- |
| roleName  | 角色名称    | string        | 是       |
| roleLevel | 角色层级    | integer       | 否       |
| roleKey   | 角色标识    | string        | 否       |
| permId    | 权限菜单 ID | array\<long\> | 否       |

**示例：**
```json
{
  "roleName": "部门管理员",
  "roleLevel": 2,
  "roleKey": "dept_admin",
  "permId": [1, 2, 3]
}
```

#### 3.4 响应示例
`Result<Boolean>`

### 4. 编辑角色

#### 4.1 基本信息
**请求接口：** /sys/role
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改角色信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `roleId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除角色

#### 5.1 基本信息
**请求接口：** /sys/role/{roleIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除角色


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Long[] | **响应：** `Result<Boolean>`)

#### 5.4 响应示例
-

---

### 6. 更改角色状态

#### 6.1 基本信息
**请求接口：** /sys/role/status
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 更改角色状态


#### 6.2 请求头
无

#### 6.3 请求体
无（查询参数：`roleId`(long)、`status`(integer) | **响应：** `Result<Boolean>`)

#### 6.4 响应示例
-

---

### 7. 设置默认角色

#### 7.1 基本信息
**请求接口：** /sys/role/default/{roleId}
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 新注册用户自动关联该角色，同时取消旧默认


#### 7.2 请求头
无

#### 7.3 请求体
无

#### 7.4 响应示例
新注册用户自动关联，同时取消旧默认角色 | **响应：** `Result<Boolean>`

---

## 四、菜单管理

**模块前缀：** `/sys/menu`

### 1. 获取菜单列表

#### 1.1 基本信息
**请求接口：** `/sys/menu/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取菜单树（子菜单递归嵌套在 `sonMenus`）

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| menuName  | 菜单名称 | string   | 否       |
| permKey   | 权限标识 | string   | 否       |
| status    | 状态     | integer  | 否       |
| beginTime | 起始时间 | string   | 否       |
| endTime   | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<List<SysMenuVo>>`

---

### 2. 获取菜单详情

#### 2.1 基本信息
**请求接口：** /sys/menu/{menuId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取菜单详细信息


#### 2.2 请求头
无

#### 2.3 请求体
无（路径参数：Integer )

#### 2.4 响应示例
`Result<SysMenuVo>`

---

### 3. 添加菜单

#### 3.1 基本信息
**请求接口：** `/sys/menu` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| menuName  | 菜单名称 | string   | 是       |
| parentId  | 父菜单 ID | long    | 否       |
| menuType  | 菜单类型 | integer  | 否       |
| route     | 前端路由 | string   | 否       |
| path      | 组件路径 | string   | 否       |
| icon      | 图标     | string   | 否       |
| permKey   | 权限标识 | string   | 否       |
| backlinks | 是否外链 | integer  | 否       |

**示例：**
```json
{
  "menuName": "用户管理",
  "permKey": "system:user:list",
  "parentId": 1,
  "menuType": 1,
  "route": "/system/user",
  "path": "system/user",
  "icon": "user",
  "backlinks": 0
}
```

#### 3.4 响应示例
`Result<Boolean>`

### 4. 编辑菜单

#### 4.1 基本信息
**请求接口：** /sys/menu
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改菜单信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `menuId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除菜单

#### 5.1 基本信息
**请求接口：** /sys/menu/{menuIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除菜单


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Integer[] )

#### 5.4 响应示例
`Result<Boolean>`

---

### 6. 更改菜单状态

#### 6.1 基本信息
**请求接口：** /sys/menu/status
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 更改菜单状态


#### 6.2 请求头
无

#### 6.3 请求体
无（查询参数：`menuId`(integer)、`status`(integer) | **响应：** `Result<Boolean>`)

#### 6.4 响应示例
-

---

## 五、字典管理

**模块前缀：** `/sys/dict`

### 1. 获取字典列表

#### 1.1 基本信息
**请求接口：** `/sys/dict/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询字典列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| dictName  | 字典名称 | string   | 否       |
| dictKey   | 字典键   | string   | 否       |
| status    | 状态     | integer  | 否       |
| beginTime | 起始时间 | string   | 否       |
| endTime   | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysDictVO>>`

---

### 2. 获取字典详情

#### 2.1 基本信息
**请求接口：** /sys/dict/{dictId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取字典详细信息


#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysDictVO>`

---

### 3. 添加字典

#### 3.1 基本信息
**请求接口：** `/sys/dict` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名   | 参数说明 | 参数类型 | 是否必填 |
| -------- | -------- | -------- | -------- |
| dictName | 字典名称 | string   | 是       |
| dictKey  | 字典键   | string   | 是       |
| remake   | 备注     | string   | 否       |
| status   | 状态     | integer  | 否       |

**示例：**
```json
{
  "dictName": "用户性别",
  "dictKey": "user_sex",
  "remake": "用于用户表单性别选择",
  "status": 1
}
```

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 编辑字典

#### 4.1 基本信息
**请求接口：** /sys/dict
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改字典信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `dictId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除字典

#### 5.1 基本信息
**请求接口：** /sys/dict/{dictId}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 删除字典


#### 5.2 请求头
无

#### 5.3 请求体
无

#### 5.4 响应示例
`Result<Boolean>`

---

### 6. 获取全部启用字典类型（前端初始化）

#### 6.1 基本信息
**请求接口：** `/sys/dict/all`
**请求方式：** GET
**所需权限：** 需要登录（不加按钮权限，公共读取，对标系统设置 `GET /sys/system-config/configKey/{configKey}`）
**基本信息：** 返回全部启用状态（status=1）的字典类型，不分页。供前端登录后初始化拿 dictKey 列表，再逐个调 `GET /sys/dist/data/type/{dictKey}` 拉数据项。与 `GET /sys/dict/list`（需 `system:dict:quarry` 权限）的区别：本接口面向所有登录用户，让没有字典管理权限的普通用户也能正常使用字典下拉/标签等基础功能

#### 6.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 6.3 请求体
无

#### 6.4 响应示例
`Result<List<SysDictVO>>`

```json
{
  "code": 200,
  "msg": "请求成功",
  "data": [
    {
      "dictId": 1,
      "dictName": "用户性别",
      "dictKey": "sys_user_sex",
      "status": 1,
      "remake": "",
      "createTime": "2026-07-07 10:00:00"
    }
  ]
}
```

---

## 六、字典数据

**模块前缀：** `/sys/dist/data`
> 当前为 `/sys/dist/data`（疑似 `/sys/dict/data` 拼写，后续可能修正）

### 1. 获取字典数据列表

#### 1.1 基本信息
**请求接口：** `/sys/dist/data/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询字典数据列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名        | 参数说明 | 参数类型 | 是否必填 |
| ------------- | -------- | -------- | -------- |
| dictId        | 字典 ID  | long     | 否       |
| dictKey       | 字典键   | string   | 否       |
| dictDataLabel | 数据标签 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysDictDataVo>>`

---

### 2. 获取字典数据详情

#### 2.1 基本信息
**请求接口：** /sys/dist/data/{dictDataId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 获取字典数据详细信息


#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysDictDataVo>`

---

### 3. 添加字典数据

#### 3.1 基本信息
**请求接口：** `/sys/dist/data` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名        | 参数说明 | 参数类型 | 是否必填 |
| ------------- | -------- | -------- | -------- |
| dictId        | 字典 ID  | long     | 是       |
| dictKey       | 字典键   | string   | 否       |
| dictDataLabel | 数据标签 | string   | 是       |
| dictDataValue | 数据值   | string   | 是       |
| dictDataSort  | 排序     | string   | 否       |
| tagType       | 标签类型 | string   | 否       |
| tagEffect     | 标签效果 | string   | 否       |
| cssClass      | CSS 类名 | string   | 否       |

**示例：**
```json
{
  "dictId": 1,
  "dictKey": "user_sex",
  "dictDataLabel": "男",
  "dictDataValue": "1",
  "dictDataSort": "1",
  "tagType": "success"
}
```

#### 3.4 响应示例
`Result<Boolean>`

### 4. 编辑字典数据

#### 4.1 基本信息
**请求接口：** /sys/dist/data
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改字典数据信息


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `dictDataId`（Long） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除字典数据

#### 5.1 基本信息
**请求接口：** /sys/dist/data/{dictDataId}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 删除字典数据


#### 5.2 请求头
无

#### 5.3 请求体
无

#### 5.4 响应示例
`Result<Boolean>`

---

### 6. 按字典键获取数据

#### 6.1 基本信息
**请求接口：** `/sys/dist/data/type/{dictKey}`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 根据字典键获取其下所有字典数据项（用于下拉框/单选框数据源）

#### 6.2 请求头
无

#### 6.3 请求体
无

#### 6.4 响应示例
`Result<List<SysDictDataVo>>`

---

# 通知模块

## 一、消息通知

**模块前缀：** `/sys/notice`

### 1. 获取消息通知列表

#### 1.1 基本信息
**请求接口：** `/sys/notice/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询消息通知列表（管理员视角）

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名       | 参数说明 | 参数类型 | 是否必填 |
| ------------ | -------- | -------- | -------- |
| title        | 标题     | string   | 否       |
| noticeType   | 消息类型 | string   | 否       |
| level        | 消息级别 | string   | 否       |
| publishScope | 发布范围 | string   | 否       |
| status       | 状态     | string   | 否       |
| beginTime    | 起始时间 | string   | 否       |
| endTime      | 截止时间 | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysNoticeVo>>`

---

### 2. 获取消息通知详情

#### 2.1 基本信息
**请求接口：** `/sys/notice/{noticeId}`
**请求方式：** GET
**所需权限：** 公开（无需登录，公告详情门户场景；SecurityConfig 按 `GET /sys/notice/{数字ID}` 精确放行，`/list`、`/my`、`/read`、`/confirm` 仍要求认证）
**基本信息：** 获取指定消息通知的详细信息。返回含 `groupIds`（关联分组 ID 列表）、`targetUserIds`（指定成员 ID 列表）、`targetUsers`（指定成员展示信息：userId/username/nickName/phoneNumber/status，供编辑弹窗回显）

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysNoticeVo>`

---

### 3. 添加消息通知

#### 3.1 基本信息
**请求接口：** `/sys/notice`
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 新增消息通知，当 `publishScope=GROUP` 时可同时关联分组；当 `publishScope=USER` 时可同时指定成员（`targetUserIds`）。

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名       | 参数说明   | 参数类型      | 是否必填 |
| ------------ | ---------- | ------------- | -------- |
| title        | 标题       | string        | 是       |
| content      | 正文     | string        | 是       |
| noticeType   | 消息类型   | string        | 否       |
| level        | 消息级别   | string        | 否       |
| publishScope | 发布范围   | string        | 否       |
| needConfirm  | 需确认     | integer       | 否       |
| expireTime   | 过期时间   | string        | 否       |
| routePath    | 前端路由   | string        | 否       |
| groupIds     | 关联分组 ID | array\<long\> | 否       |
| targetUserIds | 指定成员用户 ID | array\<long\> | 否     |

**示例：**
```json
{
  "title": "系统升级通知",
  "content": "系统将于本周六凌晨 2:00-4:00 进行升级维护",
  "noticeType": "NOTICE",
  "level": "IMPORTANT",
  "publishScope": "GROUP",
  "needConfirm": 1,
  "expireTime": "2026-07-01T00:00:00",
  "groupIds": [1, 2]
}
```

#### 3.4 响应示例
`Result<Boolean>`（消息主表 + group 关联 + user 关联在同一事务内插入）

---

### 4. 编辑消息通知

#### 4.1 基本信息
**请求接口：** /sys/notice
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改消息通知，编辑时先清旧 group 关联与 user 关联再重新插入（同一事务）


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `noticeId`（Long）。编辑时先清旧 group 关联与 user 关联再重新插入（同一事务） | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除消息通知

#### 5.1 基本信息
**请求接口：** /sys/notice/{noticeIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量软删除（delete=1），同时级联清理 groupRel、userRel 和 read 表（同一事务）


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Long[]（逗号分隔）)

#### 5.4 响应示例
批量软删除（`delete=1`），同时级联清理 groupRel 和 read 表（同一事务） | **响应：** `Result<Boolean>`

---

### 6. 发布消息通知

#### 6.1 基本信息
**请求接口：** /sys/notice/publish/{noticeId}
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** DRAFT → PUBLISHED，自动填入 publish_time


#### 6.2 请求头
无

#### 6.3 请求体
无

#### 6.4 响应示例
DRAFT → PUBLISHED，自动填入 `publish_time` | **响应：** `Result<Boolean>`

---

### 7. 撤回消息通知

#### 7.1 基本信息
**请求接口：** /sys/notice/revoke/{noticeId}
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** PUBLISHED → REVOKED


#### 7.2 请求头
无

#### 7.3 请求体
无

#### 7.4 响应示例
PUBLISHED → REVOKED | **响应：** `Result<Boolean>`

---

### 8. 获取我的消息（分页）

#### 8.1 基本信息
**请求接口：** `/sys/notice/my`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询当前用户可见的消息列表，供头导航通知下拉滚动懒加载使用。可见范围 = `publishScope=ALL` 全员消息 + 通过所属 group 命中的消息 + `publishScope=USER` 中直接指名该用户的消息。排序规则 `is_top desc, publish_time desc, notice_id desc`，与懒加载前一致。返回每条消息附带 `hasRead`（是否已读）和 `hasConfirmed`（是否已确认）字段。分页参数由 `PageUtil` 从请求参数读取，默认 pageNum=1 / pageSize=10

#### 8.2 请求头
无

#### 8.3 请求体
无

**请求参数（Query）：**

| 参数名   | 参数说明   | 参数类型 | 必填 | 默认值 |
| -------- | ---------- | -------- | ---- | ------ |
| pageNum  | 页码       | int      | 否   | 1      |
| pageSize | 每页条数   | int      | 否   | 10     |

#### 8.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "total": 12,
    "list": [
      {
        "noticeId": 1,
        "title": "系统升级通知",
        "content": "系统将于本周六凌晨进行升级维护",
        "noticeType": "NOTICE",
        "level": "IMPORTANT",
        "publishScope": "ALL",
        "status": "PUBLISHED",
        "needConfirm": 0,
        "hasRead": true,
        "hasConfirmed": false,
        "createTime": "2026-06-20T10:00:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 10,
    "pages": 2
  }
}
```

| 响应字段     | 参数说明      | 参数类型             |
| ------------ | ------------- | -------------------- |
| list         | 当前页通知列表 | array               |
| total        | 可见通知总数   | long               |
| pageNum      | 当前页码       | int                |
| pageSize     | 每页条数       | int                |
| pages        | 总页数         | int                |
| hasRead      | 当前用户是否已读 | boolean            |
| hasConfirmed | 当前用户是否已确认 | boolean           |
| 其余字段     | 同 `SysNotice` 实体 | —                |

---

### 9. 获取我的未读数

#### 9.1 基本信息
**请求接口：** `/sys/notice/unread-count`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 查询当前用户可见且未读的消息数，驱动头导航铃铛徽标。未读 = 可见范围内无 `read_time` 记录（`NOT EXISTS sys_notice_read`），可见范围与「获取我的消息」一致。懒加载后已加载列表只是部分数据，未读数必须走本独立计数接口

#### 9.2 请求头
无

#### 9.3 请求体
无

#### 9.4 响应示例
**成功示例：** `Result<Long>`，data 为未读消息数（如 `5`）

---

### 10. 标记已读

#### 9.1 基本信息
**请求接口：** /sys/notice/read/{noticeId}
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 将当前用户对该消息标记为已读，重复调用幂等


#### 9.2 请求头
无

#### 9.3 请求体
无

#### 9.4 响应示例
将当前用户对该消息标记为已读，重复调用幂等 | **响应：** `Result<Boolean>`

---

### 10. 确认消息

#### 10.1 基本信息
**请求接口：** /sys/notice/confirm/{noticeId}
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 对 needConfirm=1 的消息进行确认，若尚未标记已读则同时补齐


#### 10.2 请求头
无

#### 10.3 请求体
无

#### 10.4 响应示例
对 `needConfirm=1` 的消息进行确认。若尚未标记已读，同时补齐已读记录 | **响应：** `Result<Boolean>`

---

## 二、通知分组

**模块前缀：** `/sys/notice/group`

### 1. 获取分组列表

#### 1.1 基本信息
**请求接口：** `/sys/notice/group/list`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 分页查询通知分组列表，每条含 `members` 成员列表

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| groupName | 分组名称 | string   | 否       |
| groupCode | 分组编码 | string   | 否       |
| status    | 状态     | integer  | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysNoticeGroupVo>>`（`list` 中每项含 `members` — `SysNoticeGroupMember` 的 `{ id, groupId, userId }`）

---

### 2. 获取分组详情

#### 2.1 基本信息
**请求接口：** /sys/notice/group/{groupId}
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** -

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysNoticeGroupVo>`（含 `members`）

---

### 3. 添加分组

#### 3.1 基本信息
**请求接口：** `/sys/notice/group` | **请求方式：** POST

#### 3.2 请求头
无

#### 3.3 请求体

| 参数名    | 参数说明 | 参数类型                       | 是否必填 |
| --------- | -------- | ------------------------------ | -------- |
| groupName | 分组名称 | string                         | 是       |
| groupCode | 分组编码 | string                         | 是       |
| groupDesc | 分组描述 | string                         | 否       |
| members   | 成员列表 | array\<SysNoticeGroupMember\> | 否       |

**示例：**
```json
{
  "groupName": "运维团队",
  "groupCode": "ops_team",
  "groupDesc": "运维值班人员",
  "members": [{ "userId": 1 }, { "userId": 2 }]
}
```

**`members` 中 SysNoticeGroupMember 属性说明：**

| 参数名  | 参数说明   | 参数类型 | 是否必填 |
| ------- | ---------- | -------- | -------- |
| userId  | 用户 ID    | long     | 是       |

#### 3.4 响应示例
`Result<Boolean>`（分组主表 + 成员在同一事务内插入）

### 4. 编辑分组

#### 4.1 基本信息
**请求接口：** /sys/notice/group
**请求方式：** PUT
**所需权限：** 需要登录
**基本信息：** 修改分组信息，不处理 members 变更


#### 4.2 请求头
无

#### 4.3 请求体
同新增，必须携带 `groupId`（Long）。不处理 members 变更 | **响应：** `Result<Boolean>`

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除分组

#### 5.1 基本信息
**请求接口：** /sys/notice/group/{groupIds}
**请求方式：** DELETE
**所需权限：** 需要登录
**基本信息：** 批量删除，级联清理成员和 groupRel（同一事务）


#### 5.2 请求头
无

#### 5.3 请求体
无（路径参数：Long[]（逗号分隔）)

#### 5.4 响应示例
批量删除，级联清理成员和 groupRel（同一事务） | **响应：** `Result<Boolean>`

---

### 6. 批量添加成员

#### 6.1 基本信息
**请求接口：** /sys/notice/group/{groupId}/members
**请求方式：** POST
**所需权限：** 需要登录
**基本信息：** 向分组批量添加成员


#### 6.2 请求头
无

#### 6.3 请求体
`[1, 2, 3]` — 用户 ID 数组（`List<Long>`） | **响应：** `Result<Boolean>`

#### 6.4 响应示例
-

---

### 7. 批量移除成员

#### 7.1 基本信息
**请求接口：** `/sys/notice/group/{groupId}/members`
**请求方式：** DELETE

#### 7.2 请求头
无

#### 7.3 请求体

| 参数名 | 参数说明         | 参数类型      | 是否必填 |
| ------ | ---------------- | ------------- | -------- |
| —      | 成员记录主键 ID | array\<long\> | 是       |

**示例：** `[10, 11, 12]`（分组成员记录主键 ID，非 userId）

#### 7.4 响应示例
`Result<Boolean>`（移除前校验每个 id 是否属于该分组，不匹配则拒绝全量）

---

## 一、操作日志

### 1. 获取操作日志列表

#### 1.1 基本信息
**请求接口：** `/sys/operLog/list`
**请求方式：** GET
**所需权限：** `system:operLog:quarry`
**基本信息：** 分页查询操作日志列表，失败行（`status=1`）返回关联的 `errorLogId`，供前端"错误日志"按钮跳转

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名        | 参数说明                       | 参数类型 | 是否必填 |
| ------------- | ------------------------------ | -------- | -------- |
| title         | 模块标题（模糊）               | string   | 否       |
| businessType  | 业务类型（字典 sys_oper_business_type） | string   | 否       |
| operName      | 操作人员（模糊）               | string   | 否       |
| status        | 操作状态：0 正常 1 异常        | integer  | 否       |
| requestMethod | 请求方式                       | string   | 否       |
| beginTime     | 起始时间                       | string   | 否       |
| endTime       | 截止时间                       | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysOperLogVo>>`

---

### 2. 获取操作日志详情

#### 2.1 基本信息
**请求接口：** `/sys/operLog/{operId}`
**请求方式：** GET
**所需权限：** `system:operLog:info`
**基本信息：** 获取指定操作日志的详细信息

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysOperLogVo>`

---

### 3. 批量删除操作日志

#### 3.1 基本信息
**请求接口：** `/sys/operLog/{operIds}`
**请求方式：** DELETE
**所需权限：** `system:operLog:delete`
**基本信息：** 批量删除操作日志（物理删除）

#### 3.2 请求头
无

#### 3.3 请求体（路径参数）

| 参数名  | 参数说明       | 参数类型      | 是否必填 |
| ------- | -------------- | ------------- | -------- |
| operIds | 操作日志主键 ID | array\<long\> | 是       |

**示例：** `/sys/operLog/1,2,3`

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 清空操作日志

#### 4.1 基本信息
**请求接口：** `/sys/operLog/clean`
**请求方式：** DELETE
**所需权限：** `system:operLog:clean`
**基本信息：** 清空全部操作日志（TRUNCATE，不可恢复）

#### 4.2 请求头
无

#### 4.3 请求体
无

#### 4.4 响应示例
`Result<Boolean>`

---

## 二、错误日志

### 1. 获取错误日志列表

#### 1.1 基本信息
**请求接口：** `/sys/errorLog/list`
**请求方式：** GET
**所需权限：** `system:errorLog:quarry`
**基本信息：** 分页查询错误日志列表，请求来源的错误日志返回 `operLogId`，供前端"查看操作日志"按钮跳转

#### 1.2 请求头
无

#### 1.3 请求体（查询参数）

| 参数名       | 参数说明                           | 参数类型 | 是否必填 |
| ------------ | ---------------------------------- | -------- | -------- |
| sourceType   | 错误来源（字典 sys_error_source_type） | string   | 否       |
| title        | 错误简述（模糊）                   | string   | 否       |
| operName     | 操作人员（模糊）                   | string   | 否       |
| exceptionType | 异常类型（模糊）                  | string   | 否       |
| beginTime    | 起始时间                           | string   | 否       |
| endTime      | 截止时间                           | string   | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysErrorLogVo>>`

---

### 2. 获取错误日志详情

#### 2.1 基本信息
**请求接口：** `/sys/errorLog/{errorId}`
**请求方式：** GET
**所需权限：** `system:errorLog:info`
**基本信息：** 获取指定错误日志的详细信息（含完整堆栈）

#### 2.2 请求头
无

#### 2.3 请求体
无

#### 2.4 响应示例
`Result<SysErrorLogVo>`

---

### 3. 批量删除错误日志

#### 3.1 基本信息
**请求接口：** `/sys/errorLog/{errorIds}`
**请求方式：** DELETE
**所需权限：** `system:errorLog:delete`
**基本信息：** 批量删除错误日志（物理删除）

#### 3.2 请求头
无

#### 3.3 请求体（路径参数）

| 参数名  | 参数说明       | 参数类型      | 是否必填 |
| ------- | -------------- | ------------- | -------- |
| errorIds | 错误日志主键 ID | array\<long\> | 是       |

**示例：** `/sys/errorLog/1,2,3`

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 清空错误日志

#### 4.1 基本信息
**请求接口：** `/sys/errorLog/clean`
**请求方式：** DELETE
**所需权限：** `system:errorLog:clean`
**基本信息：** 清空全部错误日志（TRUNCATE，不可恢复）

#### 4.2 请求头
无

#### 4.3 请求体
无

#### 4.4 响应示例
`Result<Boolean>`

---

# 系统设置模块

## 一、系统设置

### 1. 获取系统设置列表

#### 1.1 基本信息
**请求接口：** `/sys/system-config/list`
**请求方式：** GET
**所需权限：** `system:systemConfig:quarry`
**基本信息：** 分页查询系统设置列表，支持按设置键、设置名称、状态、创建时间范围筛选

#### 1.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 1.3 请求体（查询参数）

| 参数名     | 参数说明                       | 参数类型 | 是否必填 |
| ---------- | ------------------------------ | -------- | -------- |
| configKey  | 设置键（模糊）                 | string   | 否       |
| configName | 设置名称（模糊）               | string   | 否       |
| status     | 状态：1 启用 0 停用            | integer  | 否       |
| beginTime  | 创建起始时间                   | string   | 否       |
| endTime    | 创建截止时间                   | string   | 否       |
| pageNum    | 页码                           | integer  | 否       |
| pageSize   | 每页条数                       | integer  | 否       |

#### 1.4 响应示例
`Result<PageInfo<SysConfigVo>>`

```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "list": [
      {
        "configId": 1,
        "configKey": "sys.user.initPassword",
        "configName": "用户初始密码",
        "configValue": "123456",
        "valueType": "STRING",
        "isSystem": 1,
        "remark": "新建用户与重置密码时的初始密码",
        "status": 1,
        "createTime": "2026-07-03 10:00:00",
        "updateTime": "2026-07-03 10:00:00"
      }
    ],
    "total": 3,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

---

### 2. 获取系统设置详情

#### 2.1 基本信息
**请求接口：** `/sys/system-config/{configId}`
**请求方式：** GET
**所需权限：** `system:systemConfig:info`
**基本信息：** 获取指定系统设置的详细信息

#### 2.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 2.3 请求体（路径参数）

| 参数名   | 参数说明       | 参数类型 | 是否必填 |
| -------- | -------------- | -------- | -------- |
| configId | 设置项主键 ID  | long     | 是       |

#### 2.4 响应示例
`Result<SysConfigVo>`

---

### 3. 新增系统设置

#### 3.1 基本信息
**请求接口：** `/sys/system-config`
**请求方式：** POST
**所需权限：** `system:systemConfig:add`
**基本信息：** 新增系统设置项并立即写入 Redis 缓存。新增项强制 `is_system=0`（内置项只能由初始化脚本写入）

#### 3.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 3.3 请求体

| 参数名       | 参数说明                                              | 参数类型 | 是否必填 |
| ------------ | ----------------------------------------------------- | -------- | -------- |
| configKey    | 设置键（业务唯一，推荐「模块.子项.用途」点号分层）    | string   | 是       |
| configName   | 设置名称                                              | string   | 是       |
| configValue  | 设置值（按 valueType 解释）                           | string   | 否       |
| valueType    | 值类型：STRING/BOOLEAN/NUMBER/JSON                    | string   | 是       |
| remark       | 备注说明                                              | string   | 否       |
| status       | 状态：1 启用 0 停用                                   | integer  | 是       |

#### 3.4 响应示例
`Result<Boolean>`

---

### 4. 编辑系统设置

#### 4.1 基本信息
**请求接口：** `/sys/system-config`
**请求方式：** PUT
**所需权限：** `system:systemConfig:edit`
**基本信息：** 编辑系统设置并刷新缓存。内置项（is_system=1）禁止修改 configKey/valueType、禁止停用，仅可改值/名称/备注

#### 4.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 4.3 请求体

| 参数名       | 参数说明                                              | 参数类型 | 是否必填 |
| ------------ | ----------------------------------------------------- | -------- | -------- |
| configId     | 设置项主键 ID                                         | long     | 是       |
| configKey    | 设置键（内置项不可改）                                | string   | 否       |
| configName   | 设置名称                                              | string   | 否       |
| configValue  | 设置值                                                | string   | 否       |
| valueType    | 值类型（内置项不可改）                                | string   | 否       |
| remark       | 备注说明                                              | string   | 否       |
| status       | 状态（内置项不可停用）                                | integer  | 否       |

#### 4.4 响应示例
`Result<Boolean>`

---

### 5. 删除系统设置

#### 5.1 基本信息
**请求接口：** `/sys/system-config/{configId}`
**请求方式：** DELETE
**所需权限：** `system:systemConfig:delete`
**基本信息：** 删除系统设置并清除缓存。内置项（is_system=1）禁止删除

#### 5.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 5.3 请求体（路径参数）

| 参数名   | 参数说明       | 参数类型 | 是否必填 |
| -------- | -------------- | -------- | -------- |
| configId | 设置项主键 ID  | long     | 是       |

#### 5.4 响应示例
`Result<Boolean>`

---

### 6. 刷新系统设置缓存

#### 6.1 基本信息
**请求接口：** `/sys/system-config/refresh`
**请求方式：** POST
**所需权限：** `system:systemConfig:refresh`
**基本信息：** 清空 Redis 中全部系统设置缓存，并立即从数据库重新预热全部启用项。与字典刷新（前端本地缓存）不同，系统设置缓存在后端 Redis

#### 6.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 6.3 请求体
无

#### 6.4 响应示例
`Result<Boolean>`

---

### 7. 按设置键获取设置值（前端按需读取）

#### 7.1 基本信息
**请求接口：** `/sys/system-config/configKey/{configKey}`
**请求方式：** GET
**所需权限：** 需要登录（不加按钮权限，公共读取，对标若依 `GET /system/config/configKey/{configKey}`；不放宽给游客，游客侧公开配置读取走专用接口，如注册开关 `GET /register/enabled`）
**基本信息：** 按设置键返回当前设置值，供前端按需读取运用，避免全量拉取暴露关键设置。只返回 `configValue` 字符串，不暴露 valueType/isSystem/remark 等元信息。命中且启用（status=1）返回值，未命中或停用返回 `null`（业务码仍 200）

#### 7.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 7.3 路径参数
| 参数名    | 参数说明 | 参数类型 | 是否必填 |
| --------- | -------- | -------- | -------- |
| configKey | 设置键   | string   | 是       |

#### 7.4 请求体
无

#### 7.5 响应示例
`Result<String>`

命中：
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": "123456"
}
```

未命中或停用：
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": null
}
```

---

# 文件管理

**模块说明：** 轻量文件上传/下载模块，**不建表、不落库**。文件按「根路径/年/月/日」三层目录分层存储到本地上传路径（`rookie.upload.path`，application.yml 可配置，改后重启生效；相对路径按应用工作目录解析），上传接口返回存储名，下载凭存储名取文件。存储名 = 系统名称（`rookie.system-name`，application.yml 可配置）+ 年月日（yyyyMMdd）+ 毫秒时间戳 + 扩展名（如 `rookie202608152011191234567.png`），年月日与毫秒同源于上传时刻，下载/删除时由存储名反推日期目录；存储名只允许字母数字与 `.-_` 字符，下载/删除前做白名单校验 + 时间戳反推日期一致性校验 + normalize 前缀校验多重防路径穿越。接口仅需登录（SecurityConfig 全局兜底），不加按钮权限、不建菜单。**上传接口不记操作日志**（原因见头像接口说明）

**模块前缀：** `/sys/file`

### 1. 上传文件

#### 1.1 基本信息
**请求接口：** `/sys/file/upload`
**请求方式：** POST（multipart/form-data）
**所需权限：** 需要登录
**基本信息：** 上传任意文件（大小上限由 `spring.servlet.multipart.max-file-size` 控制，默认 50MB）。存储名 = 系统名称（`rookie.system-name`）+ 年月日（yyyyMMdd）+ 毫秒时间戳 + 扩展名（扩展名只保留字母数字，如 `rookie202608152011191234567.png`），文件按「根路径/年/月/日」分层存储，原始文件名仅用于展示与下载命名

#### 1.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 1.3 请求体
multipart/form-data：

| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| file   | 待上传文件         | file     | 是       |

#### 1.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "storedName": "rookie202608152011191234567.png",
    "originalName": "产品图.png",
    "size": 20480,
    "ext": "png"
  }
}
```

| 响应字段     | 参数说明                       | 参数类型 |
| ------------ | ------------------------------ | -------- |
| storedName   | 存储名（下载接口凭此名取文件） | string   |
| originalName | 原始文件名                     | string   |
| size         | 文件大小（字节）               | long     |
| ext          | 小写扩展名（无扩展名为空串）   | string   |

**失败示例（空文件）：**
```json
{
  "code": 500,
  "msg": "上传文件不能为空",
  "data": null
}
```

---

### 2. 下载文件

#### 2.1 基本信息
**请求接口：** `/sys/file/download/{storedName}`
**请求方式：** GET
**所需权限：** 需要登录
**基本信息：** 按存储名流式返回文件，**attachment 附件响应**（`Content-Type: application/octet-stream`；`originalName` 参数可选，仅用于浏览器保存时的展示文件名，不做存储定位；文件名经 RFC 5987 UTF-8 编码，兼容中文）。存储名非法（含 `/\`、空白或 `..` 绕出目录）或文件不存在时返回 `Result` 错误（业务码 500）

#### 2.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 2.3 路径/查询参数
| 参数名       | 参数说明                         | 参数类型 | 是否必填 |
| ------------ | -------------------------------- | -------- | -------- |
| storedName   | 上传接口返回的存储名             | string   | 是       |
| originalName | 保存时的展示文件名（可选）       | string   | 否       |

#### 2.4 请求体
无

#### 2.5 响应示例

**成功示例：** `Content-Type: application/octet-stream`，`Content-Disposition: attachment; filename*=UTF-8''产品图.png`，响应体为文件二进制流

**失败示例（文件不存在）：**
```json
{
  "code": 500,
  "msg": "文件不存在或已被删除",
  "data": null
}
```

---

# 在线用户

**模块说明：** 无状态 JWT 体系没有服务端 session，"在线"由后端 Redis 在线集合（ZSET）判定：`member = username`，`score = 最后活跃时间戳`，每个已登录请求经 `TokenVerifyFilter` 校验通过后写入活跃时间（前端另以心跳兜底挂机场景）；score 距今超过在线阈值视为离线，统计时按分数区间实时计算，无需定时清理。在线阈值 = 系统设置 `sys.online.timeout`（NUMBER，分钟，默认 30，管理员可在系统设置页调整，无需重启）。离线语义：关浏览器不主动下线，最长延迟一个阈值周期（默认 30 分钟）掉线

**模块前缀：** `/sys/online`

### 1. 在线心跳

#### 1.1 基本信息
**请求接口：** `/sys/online/ping`
**请求方式：** GET
**所需权限：** 需要登录（不加按钮权限，个人向，对标 /person 系列）
**基本信息：** 前端登录后每 60s 调用一次，作为定时触发点维持挂机用户在线状态（实际活跃记录由 TokenVerifyFilter 写入，本接口仅返回成功）

#### 1.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 1.3 请求体
无

#### 1.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

---

### 2. 在线人数

#### 2.1 基本信息
**请求接口：** `/sys/online/count`
**请求方式：** GET
**所需权限：** 需要登录 + `system:online:quarry`
**基本信息：** 返回当前在线人数（在线集合中最后活跃时间在阈值内的成员数）

#### 2.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 2.3 请求体
无

#### 2.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": 3
}
```

---

### 3. 在线用户列表

#### 3.1 基本信息
**请求接口：** `/sys/online/list`
**请求方式：** GET
**所需权限：** 需要登录 + `system:online:quarry`
**基本信息：** 返回在线用户列表（按最后活跃时间倒序），含账号/昵称/登录IP/登录时间/最后活跃时间；昵称、IP、登录时间来自用户登录态缓存，取不到时为空

#### 3.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 3.3 请求体
无

#### 3.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": [
    {
      "username": "admin",
      "nickName": "管理员",
      "loginIp": "127.0.0.1",
      "loginTime": 1755153600000,
      "lastActive": 1755157200000
    }
  ]
}
```

| 响应字段   | 参数说明                 | 参数类型 |
| ---------- | ------------------------ | -------- |
| username   | 登录账号                 | string   |
| nickName   | 昵称（可空）             | string   |
| loginIp    | 登录 IP（可空）          | string   |
| loginTime  | 登录时间（毫秒时间戳）   | long     |
| lastActive | 最后活跃时间（毫秒时间戳）| long     |

---

### 4. 强制下线

#### 4.1 基本信息
**请求接口：** `/sys/online/logout/{username}`
**请求方式：** POST
**所需权限：** 需要登录 + `system:online:kick`（@Log 记录）
**基本信息：** 强制指定账号下线：从在线集合移除 + 删除登录态缓存，旧 token 立即失效（该账号下一次请求返回 401，前端自动跳登录）。不能对当前登录账号操作（返回 500）

#### 4.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 4.3 路径参数
| 参数名   | 参数说明         | 参数类型 | 是否必填 |
| -------- | ---------------- | -------- | -------- |
| username | 被强制下线的账号 | string   | 是       |

#### 4.4 请求体
无

#### 4.5 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

**失败示例（对自己操作）：**
```json
{
  "code": 500,
  "msg": "不能强制下线当前登录账号",
  "data": null
}
```

---

# 定时任务

**模块说明：** 基于 Spring TaskScheduler 的动态定时任务管理。任务元数据落 `sys_job` 表，应用启动时把启用任务注册进调度器（CronTrigger 驱动），增删改/启停时动态注册或取消，改配置即生效无需重启；每次执行（自动/手动）写 `sys_job_log`。调用目标为 Spring Bean 方法（`beanName` + `methodName`），**限定 `com.rookie.system.task` 包**（白名单校验），方法须为 public、无参或单个 String 参数（参数来自 `params` 字段）；cron 为 Spring 6 段式表达式。防重：同一任务执行中再次触发直接跳过并记录日志。**边界：单机调度**，多实例部署时任务会重复执行（需另行引入分布式锁或任务平台）。**需先执行 `sql/sys_job.sql`**（建表 + 菜单权限）

**模块前缀：** `/sys/job`

### 1. 定时任务列表

#### 1.1 基本信息
**请求接口：** `/sys/job/list`
**请求方式：** GET
**所需权限：** 需要登录 + `system:job:quarry`
**基本信息：** 分页查询定时任务列表（PageHelper 分页，返回 PageInfo）

#### 1.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 1.3 查询参数
| 参数名    | 参数说明           | 参数类型 | 是否必填 |
| --------- | ------------------ | -------- | -------- |
| pageNum   | 页码               | int      | 否       |
| pageSize  | 每页条数           | int      | 否       |
| jobName   | 任务名称（模糊）   | string   | 否       |
| status    | 状态（1启用 0停用）| int      | 否       |
| beginTime | 创建时间起         | string   | 否       |
| endTime   | 创建时间止         | string   | 否       |

#### 1.4 请求体
无

#### 1.5 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "total": 1,
    "list": [
      {
        "jobId": 1,
        "jobName": "示例任务",
        "beanName": "demoTask",
        "methodName": "execute",
        "cronExpression": "0 0 2 * * ?",
        "params": null,
        "status": 1,
        "remark": "",
        "createTime": "2026-08-15 21:30:00",
        "updateTime": "2026-08-15 21:30:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

---

### 2. 定时任务详情

#### 2.1 基本信息
**请求接口：** `/sys/job/{jobId}`
**请求方式：** GET
**所需权限：** 需要登录 + `system:job:info`
**基本信息：** 按主键返回任务详情

#### 2.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 2.3 路径参数
| 参数名 | 参数说明 | 参数类型 | 是否必填 |
| ------ | -------- | -------- | -------- |
| jobId  | 任务主键 | long     | 是       |

#### 2.4 请求体
无

#### 2.5 响应示例
`Result<SysJobVo>`，字段同列表项。

---

### 3. 新增定时任务

#### 3.1 基本信息
**请求接口：** `/sys/job`
**请求方式：** POST
**所需权限：** 需要登录 + `system:job:add`（@Log 记录）
**基本信息：** 新增定时任务。保存前校验：cron 表达式合法（Spring 6 段式）、Bean 存在且在白名单包（`com.rookie.system.task`）、方法存在（public，无参或 String 单参）。`status=1` 时立即注册调度

#### 3.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 3.3 请求体

| 参数名         | 参数说明                                             | 参数类型 | 是否必填 |
| -------------- | ---------------------------------------------------- | -------- | -------- |
| jobName        | 任务名称                                             | string   | 是       |
| beanName       | Spring Bean 名称（限 `com.rookie.system.task` 包）   | string   | 是       |
| methodName     | 执行方法名（public，无参或 String 单参）             | string   | 是       |
| cronExpression | cron 表达式（Spring 6 段式，如 `0 0 2 * * ?`）        | string   | 是       |
| params         | 执行参数（方法为 String 单参时传入，可选）           | string   | 否       |
| status         | 状态（1启用 0停用）                                  | int      | 是       |
| remark         | 备注（可选）                                         | string   | 否       |

#### 3.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": true
}
```

**失败示例（cron 不合法）：**
```json
{
  "code": 500,
  "msg": "cron 表达式不合法：xxxxx",
  "data": null
}
```

**失败示例（不在白名单包）：**
```json
{
  "code": 500,
  "msg": "任务目标 Bean 不在白名单包内（com.rookie.system.task）",
  "data": null
}
```

---

### 4. 编辑定时任务

#### 4.1 基本信息
**请求接口：** `/sys/job`
**请求方式：** PUT
**所需权限：** 需要登录 + `system:job:edit`（@Log 记录）
**基本信息：** 编辑定时任务（请求体同新增，另含 `jobId`）。保存后重新注册调度（先取消旧调度；停用则只取消），新配置立即生效

#### 4.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 4.3 请求体
同「新增定时任务」+ `jobId`（long，必填）。

#### 4.4 响应示例
`Result<Boolean>`，成功 `data: true`。

---

### 5. 删除定时任务

#### 5.1 基本信息
**请求接口：** `/sys/job/{jobId}`
**请求方式：** DELETE
**所需权限：** 需要登录 + `system:job:delete`（@Log 记录）
**基本信息：** 删除定时任务：先取消调度再删记录，并级联清理该任务的执行日志

#### 5.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 5.3 路径参数
| 参数名 | 参数说明 | 参数类型 | 是否必填 |
| ------ | -------- | -------- | -------- |
| jobId  | 任务主键 | long     | 是       |

#### 5.4 请求体
无

#### 5.5 响应示例
`Result<Boolean>`，成功 `data: true`。

---

### 6. 定时任务启停

#### 6.1 基本信息
**请求接口：** `/sys/job/status`
**请求方式：** PUT
**所需权限：** 需要登录 + `system:job:status`（@Log 记录）
**基本信息：** 修改任务状态：`status=1` 启用（立即注册调度，基于最新配置）、`status=0` 停用（立即取消调度，正在执行的任务不受影响）

#### 6.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 6.3 查询参数
| 参数名 | 参数说明               | 参数类型 | 是否必填 |
| ------ | ---------------------- | -------- | -------- |
| jobId  | 任务主键               | long     | 是       |
| status | 目标状态（1启用 0停用）| int      | 是       |

#### 6.4 请求体
无

#### 6.5 响应示例
`Result<Boolean>`，成功 `data: true`。

---

### 7. 立即执行

#### 7.1 基本信息
**请求接口：** `/sys/job/run/{jobId}`
**请求方式：** POST
**所需权限：** 需要登录 + `system:job:run`（@Log 记录）
**基本信息：** 立即执行一次任务（手动触发，不走 cron 调度，执行日志 `triggerType=MANUAL`）。若任务正处于执行中（自动触发未结束），本次触发会被防重拦截并记录日志

#### 7.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 7.3 路径参数
| 参数名 | 参数说明 | 参数类型 | 是否必填 |
| ------ | -------- | -------- | -------- |
| jobId  | 任务主键 | long     | 是       |

#### 7.4 请求体
无

#### 7.5 响应示例
`Result<Boolean>`，成功 `data: true`。

---

### 8. 执行日志列表

#### 8.1 基本信息
**请求接口：** `/sys/job/log/list`
**请求方式：** GET
**所需权限：** 需要登录 + `system:jobLog:quarry`
**基本信息：** 分页查询任务执行日志（PageHelper 分页，按执行时间倒序）。每次执行（自动/手动）记录：触发方式、调用目标、参数、耗时、结果与异常信息；任务删除后日志仍保留

#### 8.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 8.3 查询参数
| 参数名      | 参数说明                   | 参数类型 | 是否必填 |
| ----------- | -------------------------- | -------- | -------- |
| pageNum     | 页码                       | int      | 否       |
| pageSize    | 每页条数                   | int      | 否       |
| jobId       | 任务主键（精确）           | long     | 否       |
| jobName     | 任务名称（模糊）           | string   | 否       |
| triggerType | 触发方式（AUTO/MANUAL）    | string   | 否       |
| status      | 执行状态（0成功 1失败）    | int      | 否       |
| beginTime   | 执行时间起                 | string   | 否       |
| endTime     | 执行时间止                 | string   | 否       |

#### 8.4 请求体
无

#### 8.5 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "total": 1,
    "list": [
      {
        "logId": 1,
        "jobId": 1,
        "jobName": "示例任务",
        "triggerType": "AUTO",
        "invokeTarget": "demoTask.execute",
        "jobParams": null,
        "status": 0,
        "costTime": 12,
        "exceptionMsg": null,
        "executeTime": "2026-08-15 22:00:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

| 响应字段      | 参数说明                       | 参数类型 |
| ------------- | ------------------------------ | -------- |
| logId         | 日志主键                       | long     |
| jobId         | 任务主键                       | long     |
| jobName       | 任务名称（冗余）               | string   |
| triggerType   | 触发方式：AUTO 自动 MANUAL 手动 | string   |
| invokeTarget  | 调用目标（bean.method）        | string   |
| jobParams     | 执行参数（可空）               | string   |
| status        | 执行状态：0成功 1失败          | int      |
| costTime      | 耗时（毫秒，可空）             | long     |
| exceptionMsg  | 异常信息（失败时，可空）       | string   |
| executeTime   | 执行时间                       | string   |

---

# 服务监控

**模块说明：** 可插拔监控源设计（`MonitorProvider` 扩展点）：每个监控源（服务器、未来的 Redis / MySQL 等中间件）一个 Provider，聚合接口一次返回全部监控源数据；**接入新中间件只需实现 MonitorProvider 并注册为 Bean，接口自动纳入，前端登记对应展示组件即可**。当前含 `server` 监控源：服务器 CPU / 内存 / 磁盘 / 系统 / JVM，**零依赖实现**（`java.lang.management` + `com.sun.management.OperatingSystemMXBean` + `File.listRoots()`）。仅实时快照展示，不落库。**需先执行 `sql/sys_monitor.sql`**（菜单权限）与 `sql/sys_monitor_catalog.sql`（系统监控目录）

**模块前缀：** `/sys/monitor`

### 1. 监控数据（聚合全部监控源）

#### 1.1 基本信息
**请求接口：** `/sys/monitor/items`
**请求方式：** GET
**所需权限：** 需要登录 + `system:monitor:quarry`
**基本信息：** 返回全部监控源的实时数据数组（`type / title / data`）。单个监控源采集失败不影响其他项（该项 `data` 为 null）。注意：服务器监控源内做一次 CPU 双采样（约 300ms）属正常延迟；Windows 下系统负载（systemLoad）不可用返回 null；容量字段均以字节为单位

#### 1.2 请求头
| 参数名 | 参数说明           | 参数类型 | 是否必填 |
| ------ | ------------------ | -------- | -------- |
| Token  | JWT 令牌（无前缀） | string   | 是       |

#### 1.3 请求体
无

#### 1.4 响应示例

**成功示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": [
    {
      "type": "server",
      "title": "服务器",
      "data": {
        "cpu": { "cores": 8, "usagePercent": 12.5, "systemLoad": 1.2 },
        "memory": { "total": 17179869184, "used": 7516192768, "available": 9663676416, "usagePercent": 43.75 },
        "disks": [
          { "name": "C:\\", "total": 511999754240, "used": 204800000000, "available": 307199754240, "usagePercent": 40.0 }
        ],
        "system": {
          "osName": "Windows 10",
          "osVersion": "10.0",
          "osArch": "amd64",
          "hostName": "DESKTOP-XXX",
          "currentTime": "2026-08-15 22:30:00"
        },
        "jvm": {
          "vmName": "OpenJDK 64-Bit Server VM",
          "vmVersion": "17.0.10+11",
          "javaVersion": "17.0.10",
          "javaHome": "D:\\java",
          "userDir": "D:\\java code\\project\\ultimately\\rookie",
          "startTime": 1755270000000,
          "uptimeSeconds": 3600,
          "heapUsed": 268435456,
          "heapCommitted": 536870912,
          "heapMax": 4294967296,
          "nonHeapUsed": 134217728,
          "threadCount": 42,
          "peakThreadCount": 56,
          "loadedClassCount": 6890,
          "gcs": [
            { "name": "G1 Young Generation", "count": 12, "timeMs": 380 },
            { "name": "G1 Old Generation", "count": 1, "timeMs": 45 }
          ]
        }
      }
    }
  ]
}
```

| 响应字段            | 参数说明                          | 参数类型 |
| ------------------- | --------------------------------- | -------- |
| data[].type         | 监控源类型（如 server）           | string   |
| data[].title        | 监控源展示名称（如「服务器」）    | string   |
| data[].data         | 监控源实时数据（结构随 type 而定，下方为 server 结构） | object |
| cpu.cores           | 可用处理器核心数                  | int      |
| cpu.usagePercent    | CPU 使用率（%，双采样）           | double   |
| cpu.systemLoad      | 系统 1 分钟平均负载（Windows 为 null）| double |
| memory.total        | 物理内存总量（字节）              | long     |
| memory.used         | 已用内存（字节）                  | long     |
| memory.available    | 可用内存（字节）                  | long     |
| memory.usagePercent | 内存使用率（%）                   | double   |
| disks[]             | 各根分区：name/total/used/available/usagePercent | array |
| system.osName       | 操作系统名称                      | string   |
| system.osVersion    | 操作系统版本                      | string   |
| system.osArch       | 系统架构                          | string   |
| system.hostName     | 主机名                            | string   |
| system.currentTime  | 服务器当前时间                    | string   |
| jvm.vmName          | JVM 名称                          | string   |
| jvm.vmVersion       | JVM 版本                          | string   |
| jvm.javaVersion     | Java 版本                         | string   |
| jvm.javaHome        | Java 安装路径                     | string   |
| jvm.userDir         | 应用启动路径                      | string   |
| jvm.startTime       | JVM 启动时间（毫秒时间戳）        | long     |
| jvm.uptimeSeconds   | JVM 已运行时长（秒）              | long     |
| jvm.heapUsed        | 堆内存已用（字节）                | long     |
| jvm.heapCommitted   | 堆内存已提交（字节）              | long     |
| jvm.heapMax         | 堆内存最大（字节，可空）          | long     |
| jvm.nonHeapUsed     | 非堆内存已用（字节）              | long     |
| jvm.threadCount     | 当前线程数                        | int      |
| jvm.peakThreadCount | 峰值线程数                        | int      |
| jvm.loadedClassCount| 已加载类数量                      | int      |
| jvm.gcs[]           | GC 统计：name/count/timeMs        | array    |
