# 上游 rookie 待调整项 —— 公告门户 & 个人信息编辑

> 本文用于梳理 knowhub 前台「公告弹窗」与「个人中心 · 资料编辑」两个已落地功能在对接过程中，**上游 rookie 仓库需要改动 / 澄清 / 不需要改**的清单。
> knowhub 侧的对接代码均已实现，本文只从「上游需要做什么」的角度反向汇总。
> 最后更新：2026-08-12

---

## 0. 阅读约定

- 「rookie / 上游」指 `rookie-system`、`rookie-common`、`rookie-framework` 等基础框架层；「knowhub」指本仓库自有的业务层（`com.knowhub.*`）。
- 每条结论给出：**当前现状 → knowhub 侧已做什么 → rookie 侧是否需要改 / 需要改什么**。
- 「无需改动」是重要结论，不是省略——明确写出以免上游误改。

---

## 1. 公告（前台公开公告门户）

### 1.1 功能与接口快照（knowhub 侧已实现）

knowhub 在 `com.knowhub.controller.portal.NoticePortalController` 新增了三个 `permitAll`（无需 `@PreAuthorize`，走 `/portal/**` 放行）的前台公开接口：

| 方法 | 路径 | 语义 |
| --- | --- | --- |
| GET | `/portal/notice/list` | 群发（`publish_scope='ALL'`）+ 已发布（`status='PUBLISHED'`）+ 未删（`delete=0`）公告分页列表，可选 `noticeType` 过滤，置顶优先 + 发布时间倒序 |
| GET | `/portal/notice/{noticeId}` | 公开详情，非群发 / 未发布 / 不存在一律返 404（防私发公告被穿透） |
| POST | `/portal/notice/confirm/{noticeId}` | 登录用户对 `needConfirm=1` 的群发公告"确认"，未登录抛 401 |

可选参数 `pageNum/pageSize` 仍由 `PageUtil.startPage` 从请求参数读取，与后台分页规范一致。

### 1.2 knowhub 侧自包含、不依赖 rookie 改动的部分

knowhub 侧公告门户**完全自包含**，没有复用 rookie-system 的 `SysNoticeMapper` / `SysNoticeServiceImpl` / `SysNoticeController`，而是另起一套只读链路：

- `com.knowhub.mapper.common.NoticePortalMapper` + `mapper/common/NoticePortalMapper.xml`
  - `listPublicNotices(noticeType)`：只读查询，where 铁律 `publish_scope='ALL' AND status='PUBLISHED' AND delete=0`，置顶优先 + 时间倒序。
  - `getPublicNoticeById(noticeId)`：同 where + `notice_id`，命中返 `com.rookie.common.pojo.entity.SysNotice`，非 ALL / 未发布 / 不存在返 null（controller 转 404）。
- `com.knowhub.mapper.common.NoticeReadMapper` + `mapper/common/NoticeReadMapper.xml`
  - `getConfirmStatus / getConfirmedNoticeIds / insertConfirmRecord / updateConfirmStatus`：对 `sys_notice_read` 表的只读 + 确认 upsert。
- `com.knowhub.service.common.impl.NoticePortalService` / `com.knowhub.service.common.NoticePortalServiceImpl`
  - 列表批量回填 `hasConfirmed`（一次性 `IN` 查确认集合，避免 N+1）。
  - 详情逐条回填。
  - `confirmNotice` 校验存在性 + `needConfirm=1` 后 upsert `sys_notice_read`。
- `com.knowhub.pojo.common.vo.NoticePortalVo`
  - 字段：`noticeId / title / content / noticeType / level / isTop / needConfirm(Integer 0/1) / hasConfirmed(Boolean, 仅登录用户回填, 未登录恒 false) / publishTime / expireTime / createBy / createTime`。
  - 与 rookie `SysNoticeVo` 的差异：公开版只暴露群发已发布公告；不暴露分组 / 指定成员私发的目标用户集合；`hasConfirmed` 仅登录用户回填，未登录为 `false`（前端据此隐藏"确认"按钮）。

**结论：上述链路 knowhub 侧已自包含落地，rookie 侧无需新增 / 修改任何类或 SQL。**

### 1.3 复用 rookie 的部分（只依赖、不改）

knowhub 公告门户**复用了 rookie-common 的只读实体**，但只是读，不要求 rookie 改：

- `com.rookie.common.pojo.entity.SysNotice` —— 列表 / 详情查询的返回实体类型。该实体需覆盖列：`noticeId / title / content / noticeType / level / isTop / needConfirm / publishScope / status / delete / publishTime / expireTime / createBy / createTime`。
  - 若上游后续给 `sys_notice` 加列，knowhub 的 `SysNotice` 实体会随之同步（rookie-common 升级即可），无需 knowhub 改代码。
- `com.rookie.framework.security.pojo.UserInfo` —— permitAll 区取登录态用：`SecurityContextHolder` 的 principal 为 `UserInfo` 时取 `getUserId()`，否则按访客处理（`currentUserOrNull()` 范式，与 `BlogPortalServiceImpl` 一致）。
- `com.rookie.common.util.PageUtil.startPage / copyPageInfo` —— 分页规范。

### 1.4 上游 rookie 是否需要改？

**结论：rookie 侧无需为此功能做任何改动。**

理由：
1. knowhub 的公开公告链路是**前台自包含**的（独立 mapper / xml / service / vo），不调用 rookie `SysNoticeController` / `SysNoticeServiceImpl` 的任何方法。
2. "确认"语义与后台 `rookie-system` 的 `SysNoticeServiceImpl.confirmNotice` **同表（`sys_notice_read`）同语义**——knowhub 直接在自己侧 upsert，不通过后台接口，因此不需要 rookie 开放新的确认接口。
3. `sys_notice` 表结构由上游维护，knowhub 只读，不需要上游新增列或改字段。

### 1.5 可选的后续优化项（面向上游，非必须）

以下不是对接必须，仅在 rookie 后续推进公开前台时可选采纳：

- **后台下发接口与前台共用语义的统一**：如果上游后续也要做"自己的前台公告门户"，建议直接复用 knowhub 已建立的「`publish_scope='ALL'` + `status='PUBLISHED'` + `delete=0`」铁律与 404 穿透防护口径，避免再次分散实现导致私发公告对访客泄漏。
- **`SysNotice` 实体字段命名**：当前 `needConfirm`（Integer 0/1）、`isTop`、`publishScope` 等字段语义稳定；若上游有计划把"是否确认"从独立 `sys_notice_read` 表迁到主表快照，请同步知会（knowhub 目前按独立事实表 upsert，迁移会改变读写路径）。
- **`sys_notice_read` 表的列约定**：knowhub 的 upsert 依赖 `read_time`、`confirm_status`、`confirm_time` 三列。若上游对该表有 DDL 调整，请保留这三列语义不变。

---

## 2. 个人信息编辑（个人中心 · 修改资料）

### 2.1 功能与接口快照（rookie 现状）

rookie `SysLoginController`（`rookie-system`）已有两条个人中心接口，knowhub 前端个人中心编辑页直接对接：

| 方法 | 路径 | 现状语义 |
| --- | --- | --- |
| GET | `/person` | 取当前登录用户个人资料：从 `SecurityContextHolder` 取 `UserInfo.userId` → `SysLoginService.getPersonalDetails(userId)` → `sysUserMapper.getSysUserInfoById` → 返回 `SysUserVo` |
| PUT | `/person` | 改个人资料：强取当前 userId 覆盖入参（`sysUserVo.setUserId(userInfo.getUserId())`），调 `SysLoginService.modifyPersonalDetails(sysUserVo)` |

`modifyPersonalDetails` 内部 `BeanUtil.toBean(sysUserVo, SysUser.class)` 后调 `sysUserMapper.editUserInfo(sysUser)`。

`SysUserMapper.xml#editUserInfo` 的 `<if>` 白名单字段：
```xml
<if test="username!='' and username!=null"> username=#{username},</if>
<if test="nickName!='' and nickName!=null">nick_name=#{nickName},</if>
<if test="phoneNumber!='' and phoneNumber!=null">phone_number=#{phoneNumber},</if>
<if test="sex!='' and sex!=null">sex=#{sex},</if>
<if test="status!=null">`status`=#{status},</if>
```

### 2.2 关键安全约束（rookie 现状已正确，**不要改坏**）

- `PUT /person` 控制器内 `sysUserVo.setUserId(userInfo.getUserId())`：**忽略前端传入的 userId，强制以当前登录态为准**。这是防止越权改他人资料的关键防线，上游若重构登录系统（控制器头注释 `TODO:重构登录系统，理清 UserInfo,SysUserVO 的区分`）时必须保留此语义。
- `editUserInfo` 白名单 `<if>`：**不更新 password 字段**。密码修改走独立的 `resetSysUserPassword`（`SysLoginServiceImpl`，使用 `passwordEncoder.encode`），不能混在资料编辑接口里。

### 2.3 knowhub 侧已做的对接决策

knowhub 前端个人中心编辑页（profile 编辑）对接 `PUT /person`，并基于以下已确认决策：

- **不增加密码字段**：rookie 原前端编辑表单里的 password 字段是"历史遗留"——`editUserInfo` SQL 从不更新它，前端传了也写入不进库。knowhub 编辑页**移除密码字段**，密码修改走独立的"修改密码"入口（调 `resetSysUserPassword`）。
- **不增加头像上传字段**：`editUserInfo` 当前白名单不含 `avatar`；如需头像，应另立上传接口 + `avatar` 字段持久化，不在本接口里塞。当前 knowhub 编辑页不暴露头像字段。
- **可编辑字段范围**：`nickName`（昵称）/ `phoneNumber`（手机号）/ `sex`（性别，String：`'1'` 男 / `'0'` 女）/ `username`（用户名，业务上建议只读展示，不开放改）/ `status`（状态，业务上不对个人开放，前端不下发）。

### 2.4 上游 rookie 是否需要改？

**结论：rookie 侧 `/person` 链路无需为 knowhub 个人中心编辑做任何改动。**

理由：
1. `editUserInfo` 的 `<if>` 白名单（username / nickName / phoneNumber / sex / status）已覆盖前端可编辑的全部字段。
2. `setUserId` 强覆盖保留越权防护，前端无需也无法越权改他人资料。
3. 密码不在此接口、头像不在此接口——与 knowhub 前端"不在此处改密码 / 不在此处传头像"的决策对齐，互不冲突。

### 2.5 建议上游后续推进（非对接必须，标注备查）

以下与 knowhub 个人中心相关，但**不是本次对接的阻塞项**，列出供上游规划参考：

- **`PUT /person` 加 `@PreAuthorize` 或明确登录注解**：当前控制器方法无任何权限注解，仅依赖 `/person` 路径在 Security 配置里属"已认证区"。建议补 `@PreAuthorize("isAuthenticated()")` 显式表达登录要求，避免上游 Security 配置变化时误放开未登录访问。
- **`editUserInfo` 不要顺手放开 password / avatar**：若后续要支持个人改头像，**请另立 `PUT /person/avatar` 之类专用接口**，并把 `avatar` 写库与上游文件存储（OSS / 本地）的 objectKey 关联起来，而不是往 `editUserInfo` 加 `avatar` 分支（会破坏"资料编辑不改敏感字段"的语义分层）。
- **`username` 是否允许个人修改的口径**：当前 SQL 允许改 `username`，knowhub 前端按"只读展示"处理。若上游明确允许个人修改用户名，请知会前端放开；若不允许，建议在 SQL 里去掉 `username` 的 `<if>`，SQL 与业务口径对齐，避免旁路改用户名。
- **`SysLoginController` 头部 `TODO:重构登录系统`**：上游重构时务必保留 §2.2 的两条安全语义（`setUserId` 强覆盖 + `editUserInfo` 不碰 password），否则会引入越权 / 密码旁路写风险。

---

## 3. 汇总表

| 功能 | rookie 是否需要改 | 性质 |
| --- | --- | --- |
| 公告公开列表 / 详情（`/portal/notice/*`） | **否** | knowhub 侧自包含，只读复用 `SysNotice` 实体与 `sys_notice` 表 |
| 公告"确认"（`/portal/notice/confirm/{id}`） | **否** | knowhub 侧直接 upsert `sys_notice_read`，与后台同表同语义，不走后台接口 |
| 公告 `SysNotice` 实体 / `sys_notice` 表 | **否** | 上游维护即可，knowhub 只读 |
| 个人资料获取（`GET /person`） | **否** | 已存在，knowhub 直接对接 |
| 个人资料编辑（`PUT /person`） | **否** | `editUserInfo` 白名单已覆盖 nickName/phoneNumber/sex/status，`setUserId` 防越权 |
| 个人头像编辑 | — | 当前无此能力，需要时上游另立专用接口（不在 `PUT /person` 内） |
| 个人修改密码 | **否** | 走 `resetSysUserPassword`，knowhub 独立"修改密码"入口对接 |

> 一句话总结：**两个功能对上游 rookie 都是"无需改动"**——公告门户 knowhub 自包含、只读复用上游表与实体；个人资料编辑完整复用 `PUT /person`，安全语义（`setUserId` 强覆盖、`editUserInfo` 不碰 password）由上游现状已正确提供并须保留。上游后续推进时只需"不要改坏"上述两条安全语义，可选地补 `@PreAuthorize` 显式注解、另立头像专用接口。