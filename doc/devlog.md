# 开发日志

本文件记录 rookie 项目中每次协作完成的代码开发任务。只有产生实际文件或代码变动的任务才记录；探讨规则、项目规划、文档框架搭建等不在此列。

**记录约定：**
- 只记录"已完成"的代码开发任务
- 记录文件路径和简要变更描述，不记录具体行号
- 同一轮对话完成的相关任务合并为一个条目
- 此约定后续可能变动，以文件内最新说明为准

---

## 2026-08-17
### 04:40 — 修复通知下拉滚动失效：改用 EP 原生 max-height 滚动机制

用户反馈：下拉高度只显示 6 条半（60vh 截断生效），但滚轮滚不动。排查 EP 2.14 源码发现：`ElDropdown` 内部用 `ElScrollbar` 包裹下拉内容（`.el-scrollbar__wrap { height:100%; overflow:auto }`），此前把 `max-height: 60vh` + `overflow-y: auto` 加在内层 `.el-dropdown-menu` 上，形成「外层 scrollbar wrap + 内层 ul」双层滚动容器，滚轮事件被外层吞掉、内层永远滚不动（仅高度被截断）。

- `rookie-ui/src/layout/components/NavBar/index.vue` — 通知下拉 `ElDropdown` 加 `max-height="60vh"` prop（EP 官方滚动机制：wrap 收到 `max-height` 内联样式后成为滚动容器，与 Select 下拉同款，自带滚动条）；全局样式删除 `.el-dropdown-menu` 上的 `max-height`/`overflow-y`（避免双层滚动容器），吸顶标题与 hover 残留修复保留；`NOTICE_MENU_SELECTOR` 改为 `.nav-bar-notice-dropdown .el-scrollbar__wrap`，滚动懒加载监听与自动补页判断（`scrollHeight - clientHeight`）都改挂/改量到 wrap
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 04:10 — 修复通知下拉无法下滑：打开时自动补页直到可滚动

用户反馈下拉栏无法下滑。根因：首屏每页 10 条在 `max-height: 60vh` 内往往刚好放得下（或只超出一点），菜单没有滚动条时滚轮无处可滚，滚动懒加载事件永远触发不了，用户看不到后面的通知。

- `rookie-ui/src/layout/components/NavBar/index.vue` — 新增 `ensureNoticeMenuScrollable`：下拉打开后（及列表数据每次变化后）检查菜单容器 `scrollHeight - clientHeight <= 2`（内容不满一屏）且 `hasMore` 时自动加载下一页，递归补页直到可滚动或没有更多，之后交给用户滚轮触发；下拉隐藏后不再自动补页（`isNoticeDropdownVisible` 守卫）；补页失败静默停止、滚动/重新打开时重试；滚动触发加载也补上 `.catch` 防未处理拒绝
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 03:50 — 通知下拉懒加载测试数据脚本

- `sql/sys_notice_lazy_load_test.sql` — 新建 20 条已发布通知测试数据（标题前缀「【懒加载测试】」）：全部 `publish_scope='ALL'`（admin 登录即全可见）、2 条置顶（测试置顶恒排最前）、3 条预置 admin 已读记录（第 3/5/12 条，未读数应显示 17）、1 条 `need_confirm=1`（第 6 条）、发布时间从近到远错开（第 11 条起跨页边界）；脚本可重复执行（先清理前缀数据再插入），用于验证通知下拉分页、滚动加载更多、未读徽标、置顶排序

## 2026-08-17
### 03:40 — 通知下拉懒加载（分页 + 滚动加载更多 + 独立未读数接口）

用户要求通知下拉在已有 max-height + 内部滚动基础上做懒加载，避免一次拉全量通知正文。改造思路：**后端 `/sys/notice/my` 改分页**（复用 `PageUtil.startPage` + PageHelper，排序与可见范围不变），**新增独立未读计数接口**（铃铛徽标不能再依赖已加载的部分列表）。

- `rookie-system/.../SysNoticeMapper.java` + `mapper/system/SysNoticeMapper.xml` — 新增 `countUnreadNoticesForUser`：未读 = 可见范围内 `NOT EXISTS (sys_notice_read where read_time is not null)`，可见范围 SQL 与 `getNoticesForUser` 保持一致（ALL / 分组 / 指定成员）
- `rookie-system/.../SysNoticeService.java` / `SysNoticeServiceImpl.java` — `getMyNotices` 返回类型 `List<SysNoticeVo>` 改为 `PageInfo<SysNoticeVo>`（`PageUtil.startPage()` 前置 + 分页装配 `hasRead`/`hasConfirmed`，每页逐条按读记录集合打标）；新增 `countUnreadNotices` 实现
- `rookie-system/.../SysNoticeController.java` — `/sys/notice/my` 响应改为 `Result<PageInfo<SysNoticeVo>>`（pageNum/pageSize 可选，默认 1/10）；新增 `GET /sys/notice/unread-count` 返回 `Result<Long>`
- `rookie-ui/src/api/system/notice.ts` — `getMyNoticesApi` 改为分页版 `getMyNoticesPageApi`（`getPage` 归一化为 records/total/pages）；新增 `getUnreadCountApi`
- `rookie-ui/src/stores/notice.ts` — 列表改分页累积：`hasMore`/`loadingMore` 标记 + `loadMoreNotices`（滚动到底追加下一页，防并发）；`unreadCount` 由「列表内取反统计」改为独立 ref，来自 `fetchUnreadCount` 接口；`markAsRead` 乐观更新时同步递减未读数；`resetNoticeState` 全量重置
- `rookie-ui/src/layout/components/NavBar/index.vue` — 下拉 `@visible-change`：显示时拉首屏 + 刷未读数，并在菜单容器（teleport 到 body，按 `.nav-bar-notice-dropdown .el-dropdown-menu` 选择器查找）挂载 scroll 监听，滚动近底部（`scrollTop + clientHeight >= scrollHeight - 8`）触发 `loadMoreNotices`，隐藏时卸载（含组件卸载兜底）；新增底部状态行「继续滚动加载更多 / 加载中… / 已加载全部」，首屏未加载完显示「加载中…」而非误报「暂无通知」
- `doc/api.md` — 消息通知「获取我的消息」改为分页四段式文档（Query 参数 + PageInfo 响应），新增「获取我的未读数」小节，原第 9/10 节顺延
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 03:10 — 头导航通知下拉最大高度限制 + 内部滚动

- `rookie-ui/src/layout/components/NavBar/index.vue` — 全局样式块（popper teleport 到 body 后 scoped 不命中）新增：`.nav-bar-notice-dropdown .el-dropdown-menu` 设 `max-height: 60vh` + `overflow-y: auto`（通知过多时菜单内滚动，不撑出视口）；`.nav-bar__notice-head` 设 `position: sticky; top: 0` 吸顶（背景 `--el-bg-color-overlay` 与 popper 同色，盖住滚动内容）
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 02:50 — 问号图标垂直居中微调

用户反馈问号偏上。原因：inline-flex 元素的默认 `vertical-align: baseline` 使其底部贴文字基线、视觉偏上。在 label flex 容器内用 `align-self: center` 强制垂直居中。

- `rookie-ui/src/views/system/menu/index.vue` — `.sort-help` 增加 `align-self: center`
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 02:40 — 问号图标放回标签「排序」后面（label 插槽直接子项）

用户要求问号放在标签文字后面。确认 EP 的 `.el-form-item__label` 为 inline-flex + `justify-content: flex-end`，插槽内容天然横排；此前竖排源于多包了一层 inline-flex span 被容器挤压。改为：**label 插槽直接放两个子项**——"排序"文本节点 + ElTooltip 包裹的 18px 文本问号 span（不包中间层），问号与文字同行、右对齐、margin-left 4px 间距；字体继承 label 与其他标签一致。

- `rookie-ui/src/views/system/menu/index.vue` — 排序表单项 label 插槽结构调整（文本 + 问号直接子项）；移除 `.sort-field` 输入框右侧方案
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 02:25 — 排序问号图标改纯文本实现（修复 231px 尺寸失控）

用户反馈图标渲染成 231×231px 并把输入框挤没。原因：Element Plus 的 `el-icon`（QuestionFilled）在该组合下尺寸继承失控（`font-size: inherit` 未吃到覆盖，按异常大尺寸渲染），且 ElTooltip 的 trigger 包裹影响 scoped 样式命中。改为**纯文本问号**：`<span class="sort-help">?</span>`，宽高固定 18px（输入框 32px 的一半）、圆形边框背景，尺寸完全由 CSS 控制、无任何继承依赖。

- `rookie-ui/src/views/system/menu/index.vue` — 排序表单项说明图标由 `QuestionFilled`（el-icon）改为纯文本 `?` span（固定 18px 圆形）；移除 `QuestionFilled` import 与旧样式
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 02:10 — 排序说明图标改放输入框右侧（放弃 label 内绝对定位）

用户反馈图标定位在输入框下方/大小异常。原因：label 区域固定 92px 且文字右对齐，紧贴输入框（input 从 92px 起），label 文字右侧无可用空间；此前绝对定位 `left: 94px` 恰好叠在输入框左边缘上。改为：图标放**输入框右侧**，`.sort-field` flex 水平排列 + `align-items: center` 垂直居中，`font-size: 16px` 固定大小（输入框 32px 的一半），悬浮说明不变。

- `rookie-ui/src/views/system/menu/index.vue` — 排序表单项内容改为 `.sort-field`（ElInputNumber + ElTooltip 图标）flex 布局；移除 `.sort-item` 绝对定位与 `.label-help` 样式
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 01:55 — 排序说明图标重构：label 属性原生渲染 + 图标绝对定位

用户反馈三点：标签与输入框未居中对齐、标签字体与其他标签不一致、问号图标过大且压住输入框。根因：`#label` 插槽内容脱离 EP label 的默认渲染（字体/行高/对齐异常）。改为：`label` 用普通属性（与其他标签原生一致），问号图标**绝对定位**于 label 文字右侧（left 基于 label-width 92px）、`top:50%+translateY` 与输入框垂直居中、固定 `font-size:16px`（输入框 32px 的一半），不参与文档流布局。

- `rookie-ui/src/views/system/menu/index.vue` — 排序表单项改回 `label="排序"` + `.sort-item` 定位参照；`.label-help` 改为绝对定位样式；移除 `#label` 插槽与 `label-help-wrap` 样式
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 01:40 — 修复排序 label 竖排：说明图标改行内结构

用户反馈排序标签"排/？/序"竖排。原因：label 插槽内用 `inline-flex` span 被容器挤压换行。改为纯行内结构：`display: inline` + `white-space: nowrap` 的包装 span，问号图标 `inline-block` + `vertical-align`，保证「排序」与图标恒同行。

- `rookie-ui/src/views/system/menu/index.vue` — 排序 label 插槽结构调整（`.label-help-wrap` inline + nowrap；`.label-help` inline-block），悬浮说明逻辑不变
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 01:30 — 菜单排序说明改为 label 后问号图标悬浮提示

- `rookie-ui/src/views/system/menu/index.vue` — 排序表单项改用 `#label` 插槽：「排序」文字 + `QuestionFilled` 问号图标（ElTooltip 包裹，悬浮显示"同级内按排序值升序展示（越小越靠前，可填负数）"）；问号图标悬停变主色、cursor: help；移除输入框上的 title 说明
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 01:20 — 菜单排序输入说明改为悬浮提示

- `rookie-ui/src/views/system/menu/index.vue` — 删除排序输入框下方的说明文字，改为 `title` 属性悬浮提示（鼠标悬停显示"同级内按排序值升序展示（越小越靠前，可填负数）"），并移除对应样式
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 01:10 — 菜单操作列分组修正：内联仅「编辑」，新增/启停/删除全部进更多

上轮改动把「停用/启用」「删除」移回了内联，与用户要求不符（用户只要求「新增」进更多）。修正为：内联仅「编辑」，更多下拉包含「新增 / 启停 / 删除」。

- `rookie-ui/src/views/system/menu/index.vue` — `getInlineMenuActions` 仅保留 `edit`；`getOverflowMenuActions` 返回除 `edit` 外的全部操作（新增/启停/删除）
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 01:00 — 菜单管理页 UI 调整（表格不再撑开视图 + 新增移入更多）

- `rookie-ui/src/views/system/menu/index.vue` —
  - 布局：`.system-menu-view` 加 `min-width: 0`，卡片及其内容区同样允许收缩，菜单树表格列宽总和超出时在表格内部横向滚动，不再把整个视图撑开产生页面级水平移动
  - 操作列：内联按钮固定为「编辑 / 启停 / 删除」，「新增」移入“更多”下拉（原逻辑为按数量截断，超过 3 个时前 2 个内联，新增/删除顺序不稳定）
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-17
### 00:30 — 修复菜单管理页渲染崩溃：formatCellValue 对数字 sort 调用 trim

用户反馈"改排序后菜单项及后面数据获取不到、路由跳转异常"，并贴出前端报错：`Uncaught (in promise) TypeError: value.trim is not a function at formatCellValue (index.vue:552) at index.vue:620`。定位：排序列（sort）传入数字，原 `formatCellValue = (value) => value && value.trim() ? value : '--'` 对数字 0 走短路不报错，对数字 1（sort 有值）执行 `1.trim()` 抛 TypeError，**表格渲染在该行中断，该行及后续行全部不渲染**——数据未丢失，是渲染崩溃导致"获取不到"（此前的孤儿节点丢弃修复保留，属防御性改进）。

- `rookie-ui/src/views/system/menu/index.vue` — `formatCellValue` 改为类型安全实现：空值（null/undefined/空串）返回 '--'，其余 `String(value).trim()`（数字/字符串均可），方法注释补充说明
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：后端与数据库（此前排查确认 85 条数据完整、排序 SQL 正常、无接口报错）

## 2026-08-16
### 23:50 — 修复菜单数据"消失"：buildMenuTree 孤儿节点静默丢弃

用户反馈"改排序后菜单管理数据项消失"。排查：数据库 85 条菜单全在、菜单接口无报错、前端无过滤逻辑。定位根因：`buildMenuTree` 对**父节点不在当前结果集**的节点（如菜单管理页按名称/状态筛选时父节点被过滤掉）**静默丢弃**，整棵子树在响应中缺失——"该条及其后数据获取不到"。用户在查询区残留筛选条件时编辑保存（改 sort），保存后按残留条件刷新即触发。

- `rookie-system/.../service/impl/SysMenuServiceImpl.java` — `buildMenuTree`：父节点不在结果集时改为**作为顶级节点保留**（不再静默丢弃），过滤场景下子树不再消失
- `rookie-system/.../service/impl/SysLoginServiceImpl.java` — 同名 `buildMenuTree` 同样修复（侧边栏菜单树防御）
- 验证：后端 `mvnw compile` 通过
- 说明：sort 与"消失"是相关性而非因果（sort 是编辑内容，触发条件是残留筛选 + 孤儿丢弃）；顺带确认菜单排序 SQL 与数据本身均正常（85 行全量、52/69 sort=1 已落库）

## 2026-08-16
### 22:50 — 修复菜单排序联调问题（树折叠误判"数据消失" + 排序规则/负数支持）

联调反馈"改排序后侧边栏顺序不变、菜单管理数据项消失"。排查结论：数据未丢失（sys_menu 85 行全在、无删除标记）、排序已生效（操作日志确认 sort 落库），两个表现均为前端观感/规则问题：

- 数据项"消失"：菜单管理页 ElTable 树默认折叠，刷新/重新拉取后二级菜单收起，误以为数据丢失
- 顺序"不变"：① 测试改的菜单（日志管理/系统设置 sort=1）本来就排在同级末尾，1>0 仍在末尾故无可见变化；② ElInputNumber min=0 导致无法设负数，排不到默认 0 的项前面

- `rookie-ui/src/views/system/menu/index.vue` — 修复：`fetchMenuList` 成功后自动展开全部节点（抽取 `expandAllRows`/`collapseAllRows`/`walkMenus`，折叠/展开按钮复用）；排序输入 min 改为 -9999（允许负数排到默认 0 项之前）；表单补充排序规则提示文案
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：后端与数据库（排序 SQL/数据均正常）；侧边栏菜单树在登录/刷新时重新拉取，修改排序后刷新页面即生效

## 2026-08-16
### 00:10 — 菜单排序支持 + 头像加载失败/为空兜底首字母

- `sql/sys_menu_sort.sql`（新建）— sys_menu 增加 `sort` 列（int NOT NULL DEFAULT 0，幂等：information_schema 判断列是否存在 + PREPARE 动态 ALTER）；存量数据归零（顺序由 `order by sort, menu_id` 兜底保持）；排序规则：同一父级下 sort 升序，相同按 menu_id
- `rookie-common/.../pojo/entity/SysMenu.java` / `rookie-system/.../pojo/vo/SysMenuVo.java` — 新增 `sort` 字段
- `rookie-system/.../resources/mapper/system/SysMenuMapper.xml` — resultMap 补 sort；quarrySysMenu 补 `order by parent_id, sort, menu_id`（原无排序）；getSysMenuByMenuIds / getSysMenuAllEnabled 排序改为 `parent_id, sort, menu_id`；addSysMenu / editSysMenuInfo 支持 sort 列
- `rookie-ui/src/types/api/system/menu.ts` — SysMenuRecord 新增 `sort?`
- `rookie-ui/src/views/system/menu/config.ts` — createDefaultMenuForm 新增 `sort: 0`
- `rookie-ui/src/views/system/menu/index.vue` — 树表格新增「排序」列（icon 后、状态前）；表单新增「排序」输入（ElInputNumber，0-9999，越小越靠前）
- `rookie-ui/src/components/UserAvatar.vue`（新建）— 用户头像公共组件：有 src 且加载成功显示图片，无 src 或 **@error 加载失败**时回退展示名首字母大写；src 变化重置失败标志；尺寸由外层容器控制
- `rookie-ui/src/layout/components/NavBar/index.vue` / `views/profile/index.vue` — 头像改用 UserAvatar（原 img/字母分支与专用样式删除，字母字号继承外层容器与原行为一致）
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 待用户操作：执行 `sql/sys_menu_sort.sql` 后重启后端（侧边栏/菜单管理页按 sort 排序生效）；菜单管理页可调整各菜单「排序」值，保存后刷新页面即按新顺序展示

## 2026-08-15
### 23:50 — 删除定时任务测试用 Demo 任务

测试完成，按用户要求清理测试产物。

- `rookie-system/.../task/DemoTask.java`（删除）— 定时任务测试任务类（含 task 目录）
- `sql/sys_job_demo.sql`（删除）— 测试任务数据脚本（「测试任务-每30秒」「测试任务-带参数」）
- 验证：后端 `mvnw compile` 通过
- 待用户操作：数据库 `sys_job` 表中的两条测试任务数据需一并清理（「任务管理」页删除，或执行 `DELETE FROM sys_job WHERE bean_name = 'demoTask';`），否则重启后任务仍会注册且因 Bean 不存在而执行失败

## 2026-08-15
### 23:40 — 系统监控模块 SQL 合并（sys_system_monitor.sql）

按用户要求把在线用户 / 定时任务 / 服务监控三个模块的 SQL 合并为一个脚本，菜单直接按最终结构书写（系统监控目录下：在线用户 → 定时任务 → 执行日志 → 服务监控）。

- `sql/sys_system_monitor.sql`（新建）— 合并 sys_online + sys_job + sys_monitor 三脚本：建表 sys_job/sys_job_log（改 **CREATE TABLE IF NOT EXISTS**，不再 DROP，老库重跑不丢任务数据）+ 设置项 sys.online.timeout + 完整菜单树（系统监控目录 + 4 菜单 + 按钮，按显示顺序插入，menu_id 自增序即侧边栏序）+ 末尾 parent 收敛（老库已存在菜单迁入系统监控目录）+ 清理遗留「任务管理」目录
- `sql/sys_online.sql` / `sql/sys_job.sql` / `sql/sys_monitor.sql` / `sql/sys_monitor_catalog.sql`（删除）— 被合并文件取代（catalog 的目录/收敛/清理功能已并入第 4/6/7 节）
- 修复原 catalog 脚本的 MySQL 报错：`DELETE FROM sys_menu ... NOT EXISTS (SELECT ... FROM sys_menu ...)` 直接引用目标表会抛 "You can't specify target table for update in FROM clause"，合并脚本改为**派生表物化**（`FROM (SELECT parent_id FROM sys_menu ...) AS child_ref`）后再关联判断，可正常执行
- 幂等：目录/菜单 NOT EXISTS、按钮 parent 反查、收敛 UPDATE 天然幂等、清理 DELETE 幂等；新旧库任意顺序重复执行均收敛到最终结构
- 未改动：`sys_config.sql`（系统设置模块菜单仍为独立脚本）、`sys_job_demo.sql`（测试任务数据）、`dict-data-permission.sql`、`rookie.sql`

## 2026-08-15
### 23:10 — 任务详情弹窗 + 服务监控 ECharts 图表化（含中间件扩展预留）+ 系统监控目录 + 侧边栏滚动条隐藏

- `rookie-ui/src/views/system/job/components/JobDetailDialog.vue`（新建）— 定时任务「查看详情」只读弹窗（ElDescriptions 展示全部配置，接弹窗栈，行数据直接展示不重复拉接口）
- `rookie-ui/src/views/system/job/index.vue` — 行操作新增「详情」按钮（权限 system:job:info，plain 样式置首）
- `rookie-system/.../monitor/MonitorProvider.java`（新建）— **监控扩展点接口**：type()/title()/collect()，接入 Redis/MySQL 等中间件只需实现并注册 Bean
- `rookie-system/.../monitor/ServerMonitorProvider.java`（新建）— 服务器监控源（迁移原 SysMonitorServiceImpl 采集逻辑，type=server）
- `rookie-system/.../pojo/vo/MonitorItemVo.java`（新建）— 聚合接口返回单元（type/title/data）
- `rookie-system/.../controller/SysMonitorController.java` — `GET /sys/monitor/server` 改为 `GET /sys/monitor/items`（注入 List<MonitorProvider> 聚合返回，单源失败置 null 不影响其他）
- `rookie-system/.../service/SysMonitorService.java` + `impl/SysMonitorServiceImpl.java`（删除）— 被 MonitorProvider 体系取代
- `sql/sys_monitor_catalog.sql`（新建）— 新增「系统监控」目录（perm_key=monitor，icon=Odometer，挂系统模块下）；在线用户/定时任务/执行日志/服务监控 4 个菜单 parent 改为该目录；删除原「任务管理」目录（job，先挪子菜单再删，带子菜单残留保护）；无 menu_id 增量风格
- `rookie-ui/package.json` — 新增依赖 `echarts`
- `rookie-ui/src/views/system/monitor/components/MonitorGauge.vue`（新建）— ECharts 仪表盘（CPU/内存使用率，≥90% 切危险色，配色从主题 CSS 变量读取，resize/dispose 生命周期）
- `rookie-ui/src/views/system/monitor/components/MonitorRing.vue`（新建）— ECharts 环形图（磁盘分区/堆内存，中心显示使用率）
- `rookie-ui/src/views/system/monitor/components/UsageBar.vue`（删除）— 被 ECharts 图表取代
- `rookie-ui/src/views/system/monitor/index.vue` — 重构为 items 驱动：遍历 MonitorItemRecord，type=server 渲染服务器卡片组（Gauge/Ring 图表 + 文字明细），**未知类型渲染占位卡**（预留中间件扩展的兜底）；自动/手动刷新逻辑保留
- `rookie-ui/src/types/api/system/monitor.ts` + `api/system/monitor.ts` — 新增 MonitorItemRecord 类型与 getMonitorItemsApi（/sys/monitor/items）
- `rookie-ui/src/layout/components/SideBar/index.vue` — 侧边栏滚动区隐藏滚动条（`.el-scrollbar__bar { display: none }`，内容仍可滚动）
- `doc/api.md` — 服务监控接口改为 /items（含聚合结构响应示例与扩展点说明）+ 更新日志条目
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：在线用户/定时任务/服务监控的接口与权限码（仅菜单归属变化，访问路径随之变化，前端刷新页面即重建菜单树与动态路由）

## 2026-08-15
### 22:30 — 定时任务测试用 Demo 任务

配合定时任务管理模块验证调度链路（用户测试需求）。

- `rookie-system/.../task/DemoTask.java`（新建）— 白名单包 `com.rookie.system.task` 下的测试任务（@Component("demoTask")）：`execute()` 无参方法打印执行日志（含当前时间）；`executeWithParam(String param)` 单参方法演示参数传递
- `sql/sys_job_demo.sql`（新建，非通用脚本）— 两条测试任务数据（幂等）：「测试任务-每30秒」（execute，每 30 秒）+「测试任务-带参数」（executeWithParam，每 1 分钟，params=hello-rookie）；注释说明测试完成后可删除本文件与任务数据
- 验证：后端 `mvnw compile` 通过
- 未改动：sys_job.sql 通用脚本（测试数据独立成文件，不污染跨项目脚本）

## 2026-08-15
### 22:10 — 服务监控模块（CPU / 内存 / 磁盘 / 系统 / JVM，零依赖）

读取运行服务所在服务器资源与 JVM 信息，零依赖实现（不引入 Oshi 等第三方库，便于多项目共用），仅实时快照不落库。

- `sql/sys_monitor.sql`（新建）— 服务监控菜单（perm_key=`system:monitor`，parent 反查系统模块）+ 查询按钮（`system:monitor:quarry`），无 menu_id 自增增量风格
- `rookie-system/.../pojo/vo/ServerMonitorVo.java`（新建）— 监控信息 VO（嵌套静态类：CpuInfo/MemoryInfo/DiskInfo/SystemInfo/JvmInfo/GcInfo，容量以字节为单位）
- `rookie-system/.../service/SysMonitorService.java` + `impl/SysMonitorServiceImpl.java`（新建）— 零依赖采集：`com.sun.management.OperatingSystemMXBean`（CPU 使用率双采样 300ms 取真实值、物理内存、系统负载）、`File.listRoots()`（磁盘分区）、`java.lang.management`（RuntimeMXBean/MemoryMXBean/GarbageCollectorMXBeans/ThreadMXBean/ClassLoadingMXBean）+ 系统属性（os/java.version/java.home/user.dir）；单项失败置 null 不影响整体
- `rookie-system/.../controller/SysMonitorController.java`（新建）— `GET /sys/monitor/server`（@PreAuthorize `system:monitor:quarry`，只读不记日志）
- `rookie-ui/src/types/api/system/monitor.ts` + `api/system/monitor.ts`（新建）— 监控类型与 `getServerMonitorApi`
- `rookie-ui/src/utils/format.ts` — 新增 `formatFileSize`（字节 → B/KB/MB/GB/TB）与 `formatDuration`（秒 → X天X小时X分X秒）
- `rookie-ui/src/views/system/monitor/index.vue` + `components/UsageBar.vue`（新建）— 监控页：工具栏（上次刷新时间/自动刷新开关默认 30s/手动刷新）+ 卡片网格（CPU 使用率、内存、磁盘分区、系统信息、JVM 跨两列含堆内存进度条与 GC 列表）；进度条自绘走主题变量（≥90% 切危险色），深浅模式天然适配
- `rookie-ui/src/constants/systemPermissions.ts` — 新增 `monitor: { quarry }` 权限键
- `doc/api.md` — 服务监控接口四段式文档（含完整响应示例与字段表）+ 更新日志条目
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：系统开机时长未采集（JDK 17 的 com.sun.management 接口无跨平台标准 API，用户需求未含此项）；监控历史/趋势为可选扩展，本轮未做

## 2026-08-15
### 21:40 — 定时任务管理模块（方案 A：Spring TaskScheduler 动态注册）+ sys_online.sql 菜单去 ID 通用化

方案 A 落地：任务元数据落 sys_job 表，启用任务启动时注册进调度器（CronTrigger），增删改/启停动态注册或取消；每次执行写 sys_job_log。调用目标限 `com.rookie.system.task` 包（白名单反射安全边界），方法须 public、无参或 String 单参。另按用户要求把 sys_online.sql 菜单改为无 menu_id 增量风格（跨项目可跑）。

- `sql/sys_online.sql` — 菜单插入**去掉 menu_id**（自增）与写死的 parent_id=1：parent 改为按 `perm_key='system'` 反查，幂等从 ON DUPLICATE KEY UPDATE 改为 NOT EXISTS + INSERT...SELECT（sys_menu 表无 perm_key 唯一键，去 ID 后无冲突触发点）；按钮 parent 反查 `system:online`；头注释补充通用化约定
- `sql/sys_job.sql`（新建）— 建表 sys_job（任务元数据）与 sys_job_log（执行日志）；任务管理目录（perm_key='job'）+ 定时任务（system:job）/执行日志（system:jobLog）子菜单 + 9 个按钮权限点；全部无 menu_id 增量风格（parent 反查）
- `rookie-common/.../pojo/entity/SysJob.java` / `SysJobLog.java`（新建）— 任务与执行日志实体
- `rookie-system/.../pojo/quarry/SysJobQuarry.java` / `SysJobLogQuarry.java`（新建）— 分页查询条件
- `rookie-system/.../pojo/vo/SysJobVo.java`（新建）— 任务展示 VO
- `rookie-system/.../mapper/SysJobMapper.java` + `SysJobMapper.xml`（新建）— 分页查询/详情/全部启用任务/增改删/状态；`SysJobLogMapper.java` + XML（新建）— 日志分页/新增/按任务级联删除
- `rookie-system/.../scheduler/SysJobScheduler.java`（新建）— 调度核心：独立 ThreadPoolTaskScheduler（5 线程，job-scheduler- 前缀）；启动时（ApplicationRunner）注册全部启用任务；`registerJob`（先取消旧调度再按新配置注册，停用仅取消）/`cancelJob`（cancel(false) 不中断执行中任务）/`runOnce`（手动执行）/`validateJobConfig`（保存前校验 cron 合法性 + Bean 存在 + 白名单包 + 方法存在）；执行包装：防重（ConcurrentHashMap+AtomicBoolean，执行中再次触发跳过并记日志）→ 反射调用（String 单参优先，其次无参，InvocationTargetException 解包）→ 记日志（耗时/结果/异常截断 2000）
- `rookie-system/.../service/SysJobService.java` + `impl/SysJobServiceImpl.java`（新建）— CRUD（保存前 validateJobConfig，落库后同步 registerJob/cancelJob）、启停（启用时把实体 status 置 1 再注册，防 registerJob 按旧状态跳过）、立即执行、日志分页；删除级联清理日志
- `rookie-system/.../controller/SysJobController.java`（新建）— `/sys/job/list`、`/{jobId}`、POST/PUT、DELETE、`/status`、`/run/{jobId}`、`/log/list`，@PreAuthorize 对齐 system:job:* 与 system:jobLog:quarry，写操作 @Log
- `rookie-ui/src/types/api/system/job.ts` + `api/system/job.ts`（新建）— 任务/日志类型与全部接口方法
- `rookie-ui/src/views/system/job/index.vue` + `config.ts`（新建）— 任务管理页：查询（任务名/状态/时间）+ 表格（状态 tag 渲染）+ 新增/编辑弹窗（cron 6 段式前端轻校验）+ 操作（编辑/停用/启用互斥按钮/立即执行/删除）；启停按钮因公共操作配置不支持动态文案，拆成两个 visible 互斥按钮
- `rookie-ui/src/views/system/job-log/index.vue` + `config.ts`（新建）— 执行日志页：查询（任务名/触发方式/状态/时间）+ 只读表格（状态 tag、异常信息截断展示）
- `rookie-ui/src/constants/systemPermissions.ts` — 新增 `job`（quarry/info/create/edit/delete/status/run）与 `jobLog`（quarry）权限键
- `doc/api.md` — 定时任务模块 8 接口四段式文档 + 更新日志条目；在线模块文档补充菜单增量脚本说明
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：无内置业务任务（任务 Bean 由用户后续在 `com.rookie.system.task` 包自行编写并在页面配置）；失败联动通知/错误日志体系为可选扩展，本轮未做；单机调度边界已注明（多实例需分布式锁或任务平台）

## 2026-08-15
### 21:10 — 文件存储规则优化（年/月/日分层 + 系统名命名）+ 删除文件测试页

上传路径改为「根路径/年/月/日」三层目录（头像 `avatar/年/月/日`），存储名改为「系统名称 + 年月日 + 毫秒时间戳 + 扩展名」；年月日与毫秒同源于上传时刻，下载/删除时从存储名提取毫秒反推日期目录，无需记录路径。测试页已完成使命，删除。

- `rookie-admin/src/main/resources/application.yml` — 新增 `rookie.system-name`（系统名称，默认 rookie，作为上传文件名前缀）；上传路径注释同步更新
- `rookie-system/.../service/impl/SysFileServiceImpl.java` — `store()`：目标目录 = 根/子目录(可选)/年/月/日（毫秒时间戳推导日期，目录与名字同源）；存储名 = 系统名 + yyyyMMdd + 毫秒 + 扩展名；同毫秒并发重名时时间戳 +1 重试。`resolveFilePath()`：白名单校验 → 正则 `(\d{8})(\d{13})(?:\.\w+)?$` 从存储名提取日期 + 毫秒 → 反推 年/月/日 目录，并校验时间戳反推日期与内嵌 8 位日期一致（防手工伪造）；`resolveDailyDir`/`toLocalDate` 辅助方法；移除 `IdUtil`（不再用 UUID）；类注释更新
- `rookie-system/.../service/SysFileService.java` — 接口 javadoc 同步存储规则说明
- `rookie-ui/src/router/index.ts` — 移除临时测试路由 `file-test`
- `rookie-ui/src/views/system/file-test/`（删除）— 文件上传/下载测试页
- `doc/api.md` — 文件管理模块说明、上传/头像接口的存储名与目录描述更新（新存储名示例），更新日志新增规则变更条目
- 验证：后端 `mvnw compile` 通过
- 未改动：上传/下载接口路径与参数（前端 API 封装透明，无需变更）；头像上传/删除流程自动适配新目录规则

## 2026-08-15
### 20:45 — 文件上传/下载前端测试页

验证后端轻量文件模块的临时测试入口：静态路由 /file-test 直连（不进侧边栏菜单），上传调 `POST /sys/file/upload`（选文件 → 上传 → 展示返回的存储名/原名/大小/扩展名并自动回填下载区），下载调 `GET /sys/file/download/{storedName}`（blob 触发浏览器保存，可指定保存文件名）。验证完成后可删除路由与页面。

- `rookie-ui/src/router/index.ts` — layout 下新增静态子路由 `file-test`（requiresAuth，title「文件上传/下载测试」，注释标明临时用途）
- `rookie-ui/src/views/system/file-test/index.vue`（新建）— 上传/下载测试页（BaseCard 布局、主题变量样式、方法级注释）
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-15
### 20:30 — 修复头像/文件上传 500：axios 默认 JSON 头把 FormData 序列化

联调发现 `POST /person/avatar` 报 500（`MultipartException: Current request is not a multipart request`）。根因：`http.ts` 实例默认 `Content-Type: application/json`，axios 1.16 的 `transformRequest` 检测到该头后把 FormData 走 `formDataToJSON` 序列化成 JSON（`node_modules/axios/lib/defaults/index.js:56`），请求到后端不再是 multipart，`RequestParamMethodArgumentResolver` 解析 `@RequestParam("file")` 时抛异常。`/sys/file/upload` 未测过但同样会踩坑。

- `rookie-ui/src/api/system/file.ts` — `uploadFileApi` 上传时显式传 `headers: { 'Content-Type': undefined }`：axios 合并 headers 时 undefined 覆盖实例默认值、`AxiosHeaders.toJSON` 过滤 undefined，最终由浏览器自动设置带 boundary 的 multipart 头；注释补充根因说明
- `rookie-ui/src/api/system/user.ts` — `uploadPersonalAvatarApi` 同样处理
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：后端（接口本身无误，错误日志正常落库 sys_error_log）；`http.ts` 实例默认头（JSON 请求由 axios transformRequest 自动补 application/json，仅 FormData 场景受影响）

## 2026-08-15
### 19:20 — 在线用户统计模块（在线人数/在线列表/强制下线/退出登录/登录IP）

无状态 JWT 体系无 session，"在线"改为基于 Redis 在线集合（ZSET，member=username，score=最后活跃时间戳）判定：TokenVerifyFilter 每个已登录请求写入活跃时间，score 距今超过阈值（sys_config `sys.online.timeout`，分钟，默认 30）视为离线，统计按分数区间实时计算。前端登录后每 60s 心跳维持挂机在线。

- `rookie-common/.../cache/RedisCache.java` — 新增 ZSET 操作：`zAdd`（写成员分数，幂等更新）/`zCount`（分数区间计数）/`zRevRangeWithScores`（倒序取成员与分数）/`zRem`（移除成员）/`zRemRangeByScore`（按分数区间批量移除，供在线集合剪枝）
- `rookie-framework/.../security/pojo/UserInfo.java` — 新增 `loginIp` 字段（登录时写入，随 UserInfo 缓存，在线列表展示）
- `rookie-framework/.../security/service/TokenService.java` — 新增 `deleteToken(username)`（退出/强踢时删除登录态缓存，旧 token 立即失效）与 `getUserInfoByUsername(username)`（在线列表读取展示信息，不走 JWT 解码）
- `rookie-framework/.../service/OnlineUserEntry.java`（新建）— 在线用户展示条目（username/nickName/loginIp/loginTime/lastActive）
- `rookie-framework/.../service/OnlineUserService.java` + `service/impl/OnlineUserServiceImpl.java`（新建）— 在线统计服务：`recordActivity`（写活跃时间）、`getOnlineCount`（阈值内计数，先按分数区间剪枝离线成员防集合无限累积）、`getOnlineUsers`（倒序列表，昵称/IP/登录时间从登录态缓存补充）、`removeOnline`（退出用）、`kickOfflineUser`（强踢 = 移除集合 + 删缓存，阻止对自己操作）；阈值读 `SysConfigUtil.getNumber("sys.online.timeout", 30)`，系统设置页可改、无需重启
- `rookie-framework/.../security/filter/TokenVerifyFilter.java` — 校验通过后调用 `recordActivity`（内部 try-catch，在线统计失败绝不干扰鉴权主流程）
- `rookie-system/.../service/impl/SysLoginServiceImpl.java` — 登录时用 hutool `JakartaServletUtil.getClientIP` 解析登录 IP 写入 UserInfo；新增 `logout`（在线集合移除 + 删登录态缓存，幂等）
- `rookie-system/.../service/SysLoginService.java` — 接口新增 `logout(username)`
- `rookie-system/.../controller/SysLoginController.java` — 新增 `POST /logout`（需登录，@Log）
- `rookie-system/.../controller/SysOnlineController.java`（新建）— `GET /sys/online/ping`（心跳，仅需登录、无 @Log、无按钮权限）、`GET /sys/online/count` 与 `GET /sys/online/list`（@PreAuthorize `system:online:quarry`）、`POST /sys/online/logout/{username}`（强踢，@Log DELETE，@PreAuthorize `system:online:kick`，后端阻止对自己操作）
- `sql/sys_online.sql`（新建）— 增量脚本：设置项 `sys.online.timeout`（NUMBER，默认 30，is_system=1，无 config_id 幂等插入）+ 菜单权限点 menu_id 100~102（在线用户 `system:online` / 在线查询 `system:online:quarry` / 在线强制下线 `system:online:kick`，挂系统模块下，admin 直通无需授权）
- `rookie-ui/src/utils/http.ts` — 自定义请求配置扩展 `silent`（模块扩展 axios 类型 + 拦截器：silent 请求业务/网络错误不弹 ElMessage，401 跳转逻辑不受影响），供心跳等高频静默请求用
- `rookie-ui/src/types/api/system/online.ts` + `api/system/online.ts`（新建）— `OnlineUserRecord` 类型、`pingOnlineApi`（silent 心跳）/`getOnlineCountApi`/`getOnlineListApi`/`kickOnlineUserApi`
- `rookie-ui/src/api/system/login.ts` — 新增 `logoutApi`（POST /logout）
- `rookie-ui/src/stores/user.ts` — `logout` 改为先调后端退出（失败静默降级，不阻塞本地清理）再清空本地登录态
- `rookie-ui/src/App.vue` — 登录后每 60s 心跳（`pingOnlineApi`，静默失败；未登录不启动；组件卸载清理定时器）
- `rookie-ui/src/constants/systemPermissions.ts` — 新增 `online: { quarry, kick }` 权限键
- `rookie-ui/src/views/system/online/index.vue` + `config.ts`（新建）— 在线用户页：当前在线人数统计 + 在线列表（账号/昵称/IP/登录时间/最后活跃，毫秒时间戳转 Date 后走 formatDateTime）+ 强制下线（二次确认、隐藏自身行、每 30s 自动刷新 + 手动刷新）；按 README.dev 主题清单检查
- `doc/api.md` — 新增在线模块四接口与退出登录接口四段式文档；认证管理补充登录 IP 说明
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：`SecurityConfig`（新接口落入 anyRequest().authenticated() 兜底，无需放行配置）、`LogAspect`、`sys_config.sql`（在线阈值走独立增量脚本，不与现有内置项冲突）；离线语义：关浏览器不主动下线，最长延迟一个阈值周期（默认 30 分钟）掉线，属 JWT 无状态体系的标准取舍

## 2026-08-15
### 18:55 — 文件上传/下载模块（轻量）+ 用户头像上传

承接轻量文件能力：上传路径用 application.yml 配置（`rookie.upload.path`，改后重启生效），不建表不落库、不建菜单不加按钮权限，接口仅需登录；头像上传复用同一存储体系，落到 `avatar/` 子目录并更新 `sys_user.avatar`。

- `rookie-admin/src/main/resources/application.yml` — 新增 `rookie.upload.path`（默认 `./upload`，相对路径按应用工作目录解析）与 `spring.servlet.multipart.max-file-size/max-request-size`（默认 1MB 过小，放宽到 50MB）
- `rookie-system/.../pojo/vo/FileUploadVo.java`（新建）— 上传结果 VO（storedName/originalName/size/ext）
- `rookie-system/.../service/SysFileService.java` + `impl/SysFileServiceImpl.java`（新建）— 文件存储服务：上传（空文件校验 → UUID+原扩展名存储名 → 绝对路径落盘）、下载（存储名白名单 `^[a-zA-Z0-9._-]+$` + normalize 前缀双重防路径穿越 → FileSystemResource 流式返回，attachment + RFC 5987 UTF-8 文件名）、头像专用（`avatar/` 子目录、仅 png/jpg/jpeg/gif/webp 且 ≤2MB、inline 图片响应、幂等删除旧头像）
- `rookie-system/.../controller/SysFileController.java`（新建）— `POST /sys/file/upload` + `GET /sys/file/download/{storedName}`（`originalName` 可选仅作展示名）；仅需登录，不标 @Log（LogAspect 会序列化方法参数，MultipartFile.getBytes() 会把文件整体读入内存转 JSON，开销不可接受）
- `rookie-system/.../pojo/vo/SysUserVo.java` — 新增 `avatar` 字段（BeanUtil 自动随 `/person` 返回）
- `rookie-system/.../mapper/SysUserMapper.java` + `SysUserMapper.xml` — 新增 `updateSysUserAvatar`（仅更新 avatar 列，独立于 editUserInfo 白名单）
- `rookie-system/.../service/SysLoginService.java` + `impl/SysLoginServiceImpl.java` — 新增 `uploadPersonalAvatar`（存新头像 → 更新 avatar 列，失败回滚删新文件 → 清理旧头像文件）与 `getPersonalAvatar`（按 avatar 存储名 inline 返回图片流）
- `rookie-system/.../controller/SysLoginController.java` — 新增 `POST /person/avatar`（multipart，仅需登录）与 `GET /person/avatar`（inline 图片流，前端 blob 加载）
- `rookie-ui/src/utils/http.ts` — 新增 `getBlob`（blob 方式 GET，供下载与头像读取；`<img>` 无法携带 Token 请求头）
- `rookie-ui/src/types/api/system/file.ts` + `api/system/file.ts`（新建）— `FileUploadResult` 类型、`uploadFileApi`（FormData 上传）、`downloadFileApi`/`saveBlobAsFile`（blob 下载保存，延迟 revoke 兼容 Firefox）
- `rookie-ui/src/types/api/system/user.ts` — `SysUserProfile` 新增 `avatar?`；`api/system/user.ts` 新增 `uploadPersonalAvatarApi`/`fetchPersonalAvatarApi`
- `rookie-ui/src/stores/user.ts` — 新增 `avatarUrl`（blob objectURL）与 `refreshAvatar`/`updateAvatar`/`clearAvatarUrl`；`fetchUserProfile` 拉资料后同步刷新头像（内部容错，失败回退字母占位）；退出登录 revoke objectURL
- `rookie-ui/src/layout/components/NavBar/index.vue` — 用户入口头像：有头像显示图片（圆形铺满），无头像保留字母占位
- `rookie-ui/src/views/profile/index.vue` — 头像区改为可点击上传（隐藏 file input，前端预检类型/大小与后端口径一致；悬停遮罩提示"更换头像"，上传中禁用），上传成功后经 store 刷新资料与头像
- `doc/api.md` — 新增文件管理模块两接口四段式文档；认证管理新增上传/获取个人头像两接口；`/person` 响应补充 avatar 字段
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：`SecurityConfig`（新接口落入 anyRequest().authenticated() 全局兜底）、`editUserInfo` 白名单（不碰 avatar）、`sys_config` 体系（上传路径不落 sys_config，按需求选 application.yml 配置）

## 2026-08-14
### 13:00 — 注册开关改专用公开接口：收回系统设置按 key 读取的游客放行

前端不能随意获取系统设置，将上一轮对 `GET /sys/system-config/configKey/{configKey}` 的 permitAll 收回（保持仅需登录），改为后端提供注册开关专用公开接口。

- `rookie-system/.../controller/SysRegisterController.java` — 新增 `GET /register/enabled`（公开、无 @Log）：读取 `SysConfigUtil.getBoolean("sys.user.registerEnabled", false)` 返回 `Result<Boolean>`；类注释补充说明"注册开关查询走专用接口，不放宽系统设置按 key 读取，避免游客任意读取系统设置"
- `rookie-framework/.../config/SecurityConfig.java` — 放行规则改为 `/register` + `/register/enabled`；**移除** `GET /sys/system-config/configKey/**` 的 permitAll（该接口恢复仅需登录）
- `rookie-ui/src/api/system/login.ts` — 新增 `getRegisterEnabledApi`（GET /register/enabled）
- `rookie-ui/src/views/register.vue` / `rookie-ui/src/views/login.vue` — 注册开关判断由 `fetchSysConfig('sys.user.registerEnabled')` 改为调用 `getRegisterEnabledApi`（`result.data === true`），不再直接读取系统设置接口；失败仍按关闭处理
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：`SysConfigController` 的 configKey 接口本身（注释语义"仅需登录"本就正确）；系统设置模块管理接口权限不变

## 2026-08-14
### 12:40 — 弹窗栈"第二个弹窗瞬间关闭"根因修复 + profile 性别选项对齐字典

- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — **去掉 ElDialog 的 destroy-on-close**：根因是"添加成员"子弹窗（GroupMemberAddDialog）组件嵌在主弹窗 ElDialog 内部，主弹窗被弹窗栈隐藏（visible=false）时 destroy-on-close 在关闭动画后销毁内容，**内嵌子弹窗组件随之卸载**（其 onBeforeUnmount 还会从弹窗栈移除自身），导致子弹窗打开后约 300ms 瞬间消失（用户报的"打开第二个后第二个瞬间关闭"）。去掉 destroy-on-close 后主弹窗隐藏期间内容保留（display:none），子弹窗组件不卸载；状态重置由 open() 显式完成（localMembers/originalUserIds 重置），不依赖内容销毁。notice-content 页的子弹窗在页面层（SharedTablePanel 外），不受编辑弹窗 hide 影响，本无此问题
- `rookie-ui/src/composables/useDialogStack.ts` — 头部注释补充约定：参与栈互斥且内含其他弹窗组件的弹窗不能使用 destroy-on-close（否则 hide 会把内嵌子弹窗一并卸载）；状态重置应由组件的 open() 显式完成
- `rookie-ui/src/views/profile/index.vue` — 性别选项对齐 `sys_user_sex` 字典（男='0' 女='1'）：schema options、form 默认值、回显与表单更新兜底值由 '1' 改为 '0'（此前前端男='1' 女='0' 与字典相反，注册页按字典对齐，两处不一致导致性别显示错乱）
- 验证：前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过

## 2026-08-14
### 12:20 — 个人中心修复 + 修改密码独立接口 + 弹窗栈关闭路径修复

承接上午的注册/公告/弹窗栈任务，修复联调发现的三类问题：① `PUT /person` 修改资料失败（update_by 未填充）；② 修改密码与资料编辑分离（独立按钮/弹窗/接口）；③ 弹窗栈基于 `@close` 事件的关闭路径失效（Element Plus 的 `close` 事件只在点 X/遮罩/Esc 时触发，程序置 v-model=false 不触发）。

- `rookie-system/.../service/impl/SysLoginServiceImpl.java` — `modifyPersonalDetails` 补 `sysUser.setUpdateBy(当前登录用户名)`：`editUserInfo` 固定更新 `update_by`（NOT NULL 列），此前未填充导致 SQL 违反非空约束抛"用户信息更改失败"（管理员编辑用户走了 `SysUserServiceImpl` 的 setUpdateBy 所以正常，个人中心路径漏了）
- `rookie-system/.../pojo/ModifyPasswordBody.java`（新建）— 修改密码请求体（oldPassword/newPassword）
- `rookie-system/.../service/SysLoginService.java` + `impl/SysLoginServiceImpl.java` — 新增 `modifyPersonalPassword(userId, body)`：校验原密码非空、新密码 6-20 位，`passwordEncoder.matches` 校验原密码（错误抛"原密码错误"），复用 `resetSysUserPassword` 加密落库；与资料编辑完全分离
- `rookie-system/.../controller/SysLoginController.java` — 新增 `PUT /person/password`（@Log、需登录、无 @PreAuthorize，与 /person 系列一致）
- `rookie-ui/src/views/profile/components/ModifyPasswordDialog.vue`（新建）— 修改密码独立弹窗（原密码/新密码/确认新密码，ElForm 校验），提交 `PUT /person/password`，成功提示后关闭；已接入弹窗栈（useDialogStack）
- `rookie-ui/src/views/profile/index.vue` — 移除资料表单中的 password 字段（历史遗留：`editUserInfo` 从不更新 password，前端传了也写不进库），"资料修改"面板新增"修改密码"按钮打开独立弹窗
- `rookie-ui/src/api/system/user.ts` + `rookie-ui/src/types/api/system/user.ts` — 新增 `modifyPersonalPasswordApi` / `ModifyPasswordRequestData`；`UpdatePersonalProfilePayload` 移除 password
- `rookie-ui/src/components/SharedFormPanel.vue` / `notice-group/components/GroupMemberTransfer.vue` / `GroupMemberAddDialog.vue` — 弹窗栈关闭路径修复：原来在 ElDialog `@close` 事件里做栈守卫与出栈，但 Element Plus 的 `close` 事件**只在用户点 X/遮罩/Esc 时触发**，程序设置 v-model=false（取消/完成/保存成功/栈 hide）不触发，导致：栈 hide 后守卫标志残留（后续用户关闭被误吞）、点"完成"关子弹窗不出栈（主弹窗不恢复）。统一改为 `watch(visible)` 处理关闭路径（v-model 变化必然触发）：next=false 且非栈隐藏 → 出栈恢复上一个（SharedFormPanel 另通知父层 cancel/update:visible）；next=false 且栈隐藏 → 消费守卫标志
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过
- 未改动：`editUserInfo` 白名单（不碰 password）与 `setUserId` 强覆盖；登录后不强制重登（旧 token 有效期不变，下次登录用新密码）

## 2026-08-14
### 11:40 — 公告详情放开权限 + 用户自助注册（系统设置开关 + 默认角色）+ 弹窗栈式互斥

三件事：① 公告详情接口改为公开（门户场景）；② 新增用户自助注册（注册开关在系统设置配置、新用户绑定默认角色）；③ 前端弹窗改为栈式互斥（打开新弹窗关闭旧弹窗、关闭当前弹窗自动恢复上一个）。

- `rookie-system/.../controller/SysNoticeController.java` — `GET /sys/notice/{noticeId}` 移除 `@PreAuthorize("system:notice:info")`，公告详情改为公开接口（游客可访问）
- `rookie-framework/.../config/SecurityConfig.java` — 新增放行：`GET /sys/notice/{数字ID}`（RegexRequestMatcher 精确匹配，`/my`、`/list`、`/read`、`/confirm` 仍要求认证）、`POST /register`、`GET /sys/system-config/configKey/**`（游客读取公开配置值，仅返回单值字符串不暴露元信息）
- `rookie-system/.../pojo/RegisterBody.java`（新建）— 注册请求体（username/password/nickName/phoneNumber/sex）
- `rookie-system/.../service/SysLoginService.java` + `impl/SysLoginServiceImpl.java` — 新增 `register()`（@Transactional）：开关兜底（`SysConfigUtil.getBoolean("sys.user.registerEnabled", false)` 关闭即拒）→ 参数校验（username ≤12 位字母数字下划线、password 6-20 位、phoneNumber 11 位）→ 唯一性校验（usernameIsExistOrNot / phoneIsExistOrNot）→ BCrypt 加密落库（createBy/updateBy 用 username，无登录态）→ `getDefaultRole()` 绑定默认角色（`addUserRoleInfo`，不硬编码角色 id，无默认角色则拒绝）
- `rookie-system/.../controller/SysRegisterController.java`（新建）— `POST /register`（@Log、无 @PreAuthorize），独立于带 TODO 的 SysLoginController
- `sql/sys_config_register.sql`（新建）— 注册开关配置项增量插入（`sys.user.registerEnabled`，BOOLEAN，默认 false），**不含 config_id 主键**（AUTO_INCREMENT 自增，二开项目复用不冲突），靠 `uk_config_key` + `ON DUPLICATE KEY UPDATE` 幂等，与 `sql/sys_config.sql` 中同 key 行（config_id=2）幂等共存；`rookie.sql` 未改动
- `rookie-ui/src/views/register.vue`（新建）— 注册页（对齐 login.vue 视觉与明暗主题）；onMounted 调 `fetchSysConfig('sys.user.registerEnabled')` 判断开关，非 'true' 显示"注册功能未开放"并禁用提交；前端校验（用户名/密码/确认密码/手机号）+ loading；成功提示后跳转登录页（不自动登录）
- `rookie-ui/src/router/index.ts` — 新增 `/register` 静态路由（`meta.public: true`）；守卫 public 分支补"已登录访问 /login|/register 跳 /"
- `rookie-ui/src/api/system/login.ts` + `rookie-ui/src/types/api/system/login.ts` — 新增 `registerApi` / `RegisterRequestData`
- `rookie-ui/src/views/login.vue` — 底部"注册账号"入口按 `fetchSysConfig('sys.user.registerEnabled') === 'true'` 显隐（读取失败默认隐藏，与后端默认关闭一致）；`__options` 恢复两端对齐（左侧注册、右侧忘记密码）
- `rookie-ui/src/composables/useDialogStack.ts`（新建）— 弹窗栈（模块级单例）：`open`（隐藏当前栈顶后入栈）/ `close`（出栈并恢复上一个）/ `remove`（卸载时只出栈不恢复）；栈条目以组件实例 Symbol 身份标识，防同名组件多实例冲突；hide/show 只切可见性、不重置内部状态
- `rookie-ui/src/components/SharedFormPanel.vue` — dialog 模式接入弹窗栈：内部 `innerVisible` 镜像（由 props.visible 同步，hide/show 不联动父层）、`hide()/show()` 原语、`hidingByStack` 守卫区分"栈隐藏"与"用户关闭"（隐藏触发的 close 不误 emit cancel/update:visible）、取消按钮改为只关内部可见性（统一走 ElDialog close 事件）
- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — 接入弹窗栈：拆 `open()`（重置 localMembers + 注册栈 + 显示）与 `hide()/show()`（只切 visible 不重置），移除 watch(visible→open) 重置副作用（恢复时不丢本地增删改）
- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberAddDialog.vue` — 接入弹窗栈：`open()` 注册栈（自动隐藏主弹窗），ElDialog `@close` 统一出栈恢复主弹窗（覆盖完成/X/Esc/遮罩所有关闭路径）
- 效果：编辑/成员管理弹窗 → 添加成员子弹窗打开时主弹窗自动隐藏（状态保留）→ 关闭子弹窗自动恢复主弹窗；无上一个弹窗时正常关闭；并列弹窗（详情/日志等从页面打开）行为不变
- 验证：后端 `mvnw compile` 通过；前端 `npx vue-tsc -p tsconfig.app.json --noEmit` 通过（`npm run type-check` 的 `--build` 增量写 node_modules/.tmp 被环境拒绝，改用 noEmit 验证）
- 未改动：`/login`、`/person` 链路；`editUserInfo` 白名单与 `setUserId` 强覆盖；`sys_config.sql` 原样（注册开关同 key 行与新文件幂等共存）；`rookie.sql` 原样

## 2026-07-13
### — 字典缓存一致性：新增后端清字典缓存接口，前端"刷新字典缓存"先清后端再重拉

此前 `SysDictDataServiceImpl.getSysDictDataByDictKey` 命中 Redis 缓存（`sys_dict_name:&lt;dictKey&gt;`）即直接返回、不查库；前端"刷新字典缓存"按钮只清前端 localStorage 并 `initializeDictionaries(true)`，但后端缓存仍在，重拉的 `/sys/dist/data/type/{dictKey}` 依旧返回旧字典。表现：SQL 直插新增 `sys_notice_scope` 的 USER 项后，前端通知范围下拉仍只显示 ALL/GROUP。根因是 mysql 与 redis 缓存一致性问题（`SysDictDataController` 顶部早有 TODO 标记）。本次加一个后端清缓存接口，让前端刷新流程覆盖后端缓存。

- `rookie-system/.../service/SysDictDataService.java` — 接口新增 `clearDictDataCache()`
- `rookie-system/.../service/impl/SysDictDataServiceImpl.java` — 实现 `clearDictDataCache()`：调 `DictUtil.clearDictData()` 清空全部 `sys_dict_name:*` 键，下次按 dictKey 取值才重新查库并回填缓存
- `rookie-system/.../controller/SysDictDataController.java` — 新增 `DELETE /sys/dist/data/cache`，调 `clearDictDataCache`，公共接口仅需登录（与前端"刷新字典缓存"按钮现状对齐，不新增按钮权限项）；把顶部"说明 mysql 和 redis 缓存一致性问题"的 TODO 注释改为正常说明，指向本接口
- `rookie-ui/src/api/system/dict.ts` — 新增 `clearDictDataCacheApi()` → `DELETE /sys/dist/data/cache`
- `rookie-ui/src/views/system/dict/index.vue` — `handleRefreshDictCache` 改为：先 `clearDictDataCacheApi()` 清后端 Redis 缓存 → 再 `dictStore.clearDictCache()` 清前端 → `initializeDictionaries(true)` 重拉，顺序保证后端缓存清掉后重拉才查库
- 验证：后端 `mvn -pl rookie-system -am compile` 通过；前端 `npx vue-tsc --noEmit` 通过
- 未改动：`DictUtil` 本身、`SysConfigUtil` 缓存机制、字典增删改时 `setDictData` 回填（仍保留，写操作路径缓存仍然一致）；现有 `systemPermissions.ts` 不新增 dict 刷新权限键

## 2026-07-13
### — 通知管理新增"指定成员"发送范围（USER）

此前通知 `publish_scope` 只支持 `ALL` 全员 / `GROUP` 指定分组，定向只能按预先建好的通知分组发送，缺少"临时勾选若干具体成员、无需先建分组"的能力。本次新增 `USER` 范围，方案 B：新增 `sys_notice_user_rel(notice_id, user_id)` 关联表承载"通知→具体用户"的直接关系，前端复用通知分组模块的 `GroupMemberAddDialog` 搜索+加入交互。`sys_notice_read` 仍保持"用户首次读取时懒生成"语义不变（它是已读跟踪表，不能反推发送范围）。

- `sql/rookie.sql` — `sys_notice_group_rel` 建表语句后新增 `sys_notice_user_rel`（`id/notice_id/user_id` + `uk_notice_user_rel(notice_id,user_id)` 唯一键 + notice/user 双向索引），风格/引擎/字符集与 `sys_notice_group_rel` 一致；字典 `sys_notice_scope` 在 ALL(15)/GROUP(16) 后新增 `USER` 项（id=49，sort=3，tagType=danger）
- `rookie-system/src/main/java/com/rookie/system/pojo/SysNoticeUserRel.java` — 新建，字段 `id/noticeId/userId` + 全参构造，照抄 `SysNoticeGroupRel` 结构
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeUserRelMapper.java` — 新建，方法 `insertSysNoticeUserRel/deleteSysNoticeUserRelByNoticeId/getSysNoticeUserRelByNoticeId/getTargetUsersByNoticeId`
- `rookie-system/src/main/resources/mapper/system/SysNoticeUserRelMapper.xml` — 新建：批量 foreach 插入、按 noticeId 删除/查询、`getTargetUsersByNoticeId` JOIN `sys_user` 取展示信息（userId/username/nickName/phoneNumber/status）
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/NoticeTargetUserVo.java` — 新建，指定成员回显展示 VO（userId/username/nickName/phoneNumber/status）
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeVo.java` — 新增字段 `targetUserIds: List<Long>`（提交用）+ `targetUsers: List<NoticeTargetUserVo>`（回显用）及 getter/setter
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeServiceImpl.java` — 注入 `SysNoticeUserRelMapper`；`getSysNoticeInfo` 回显 `targetUserIds`/`targetUsers`；`addSysNoticeInfo`/`editSysNoticeInfo` 调用新的 `addTargetUserRelIfNeeded`（编辑先删后建，仿 group_rel）；`deleteSysNoticeInfo` 级联删 `sys_notice_user_rel`；新增私有 `addTargetUserRelIfNeeded`
- `rookie-system/src/main/resources/mapper/system/SysNoticeMapper.xml` — `getNoticesForUser` 的可见范围 OR 条件追加一段 `notice_id IN (SELECT notice_id FROM sys_notice_user_rel WHERE user_id=#{userId})`，USER 范围消息对被指名用户可见
- `rookie-ui/src/types/api/system/notice.ts` — 新增 `NoticeTargetUserRecord` 接口；`SysNoticeRecord` 增 `targetUserIds?`、`targetUsers?`
- `rookie-ui/src/views/system/notice/notice-content/config.ts` — `createDefaultNoticeForm` 增 `targetUserIds:[]`；`createNoticeSchema` 增 `targetUserIds` 字段（`inputType:'custom'`，`visibleWhen: publishScope==='USER'`，formOrder=8，原 content/remark 顺延）
- `rookie-ui/src/views/system/notice/notice-content/index.vue` — 引入 `GroupMemberAddDialog`（复用分组模块的搜索+加入交互）与 `NoticeTargetUserRecord`/`SysUserFormData` 类型；新增本地 `targetMembers`（单一真源，存展示信息）+ `targetUserAddDialog` ref + `targetUserIdSet` 计算属性（excludeUserIds 控制"已加入"禁用态）；`openCreateDialog` 清空、`openEditDialog` 由后端 `targetUsers` 回显、`handleFormModelUpdate` 保护 `targetUserIds` 不被公共表单 update 清空、新增 `handleOpenTargetUserAddDialog/handleAddTargetUser/handleRemoveTargetUser`；`handleSubmitForm` 在 `publishScope==='USER'` 时由 `targetMembers` 派生 `targetUserIds`，否则清空（防脏数据）；template 新增 `#field-targetUserIds` slot（添加按钮 + 已选成员 tag 列表，tag closable 移除）与独立的 `GroupMemberAddDialog` 实例；style 增对应 class。注：本轮曾误以为前端未刷新字典导致 USER 不出现在下拉，在 onMounted 加 `ensureDictLoaded('sys_notice_scope', true)` 强刷——后查明根因在后端 Redis 字典缓存（见下条），此误改已回退
- `doc/api.md` — 消息通知 2/3/4/5/8 节同步：详情响应说明加 `targetUserIds`/`targetUsers`，新增/编辑请求体加 `targetUserIds`，删除级联加 `userRel`，我的消息可见范围加 USER 命中
- 未改动：`@PreAuthorize` 注解群（指定成员复用 `system:notice:add`/`edit` 权限，不新增按钮权限项）、`sys_notice_read` 机制、铃铛/stores 逻辑
- 验证：后端 `mvn -pl rookie-system -am compile` 通过；前端 `npx vue-tsc --noEmit` 通过

## 2026-07-13
### — 登录失败返回真实原因（修复前端只看到"请求失败"）

登录失败时前端统一弹"请求失败"看不到原因，但错误日志后端正常记录——根因在 `GlobalExceptionHandler`：`authenticationManager.authenticate()` 在用户不存在/密码错/账号锁定时抛 `BadCredentialsException`/`InternalAuthenticationServiceException`（均为 `AuthenticationException` 子类），而全局处理器无对应 `@ExceptionHandler`，被 `@ExceptionHandler(Exception.class)` 兜底成 `Result.error()` = `{code:500, msg:"请求失败"}`。前端 `http.ts` 拦截器逻辑正确（原样弹 `payload.msg`），是后端把 msg 设成了无信息量字面量。前端无改动，本次纯后端最小侵入修复。

- `rookie-framework/src/main/java/com/rookie/framework/handle/GlobalExceptionHandler.java` — 新增 `@ExceptionHandler(AuthenticationException.class)` + 私有 `resolveAuthMsg`：按子类映射可读消息——`BadCredentialsException`→"用户名或密码错误"、`LockedException`→"账号已锁定…"、`DisabledException`→"账号已禁用…"、`AccountExpiredException`→"账号已过期…"、`CredentialsExpiredException`→"密码已过期…"，其它 `AuthenticationException` 退回"用户名或密码错误"避免泄露内部细节。沿用 `ResultEnum.COMMON_ERROR`（500）**不返回 401**，避免前端 `http.ts` 把 401 当"登录态失效"触发 `redirectToLogin` 重载登录页、清空用户已输入的账号密码；`log.error` + `recordErrorLog` 保持与通用分支一致，确保错误日志仍正常落库。新增对应 import：`AuthenticationException`、`BadCredentialsException`、`LockedException`、`DisabledException`、`AccountExpiredException`、`CredentialsExpiredException`、`ResultEnum`
- 未改动：`UserDetailServiceImpl`（用户不存在返回 null → 触发 `InternalAuthenticationServiceException`→仍落入新 handler 显示"用户名或密码错误"）、`SecurityConfig`、`@PreAuthorize` 注解群、前端 `http.ts`/`login.ts`
- 验证：`mvn -q -pl rookie-framework -am compile` 通过

## 2026-07-10
### — admin 超级管理员全权限直通（菜单 + 按钮鉴权 + 绕过缓存）

此前 admin 只在后端按钮权限做了兜底（`UserDetailServiceImpl` 走 `selectAllPermKey()` 加载全部 permKey），但有两处缺口导致「新建接口/权限后 admin 用不了，必须先改角色权限」：①菜单树 `getUserMenuTreeByUserId` → `getSysMenuByRoleList` 纯靠 `sys_role_menu` 授权，admin 看到的菜单 = 给 admin 角色授过的菜单，新菜单没给 admin 授权就看不到，前端 `buttonPermissionKeys`（从菜单树提取按钮节点）也缺；②登录时 `UserInfo`（含 permissions 快照）被 `TokenService` 缓存进 Redis，`TokenVerifyFilter` 每次从缓存取 `UserInfo` 做 `@PreAuthorize` 鉴权，新权限的 perm_key 进不进缓存，要等 token 过期/重登。本次改造成果：只要检测到用户是 admin，所有目录/菜单/按钮权限自动获取且都能用，新建权限没更新缓存也能立刻用，**不修改任何现有 `@PreAuthorize` 注解**。

- `rookie-framework/src/main/java/com/rookie/framework/security/pojo/UserInfo.java` — 新增 `private boolean admin;` + `isAdmin()/setAdmin()`，toString 带上 admin。admin 标记随 UserInfo 缓存，但不随菜单增删变化（仅随 admin 角色授予/撤销变化），无缓存陈旧问题；老缓存 hutool 反序列化缺字段默认 false，仅影响已登录 admin，重登即恢复
- `rookie-framework/src/main/java/com/rookie/framework/security/service/UserDetailServiceImpl.java` — `isAdmin` 提升为方法级变量（原局部变量作用域够不到第二个 try 块），`userInfo.setPermissions(list)` 后追加 `userInfo.setAdmin(isAdmin)`
- `rookie-framework/src/main/java/com/rookie/framework/security/expression/AdminBypassExpressionRoot.java` — 新建。`implements MethodSecurityExpressionOperations`（**不继承 SecurityExpressionRoot**，因其 hasAuthority/hasAnyAuthority/hasRole/hasAnyRole 在 6.5 是 final 不可重写）。持有 Authentication + 默认 root 委托：admin 命中时 hasAuthority/hasAnyAuthority/hasRole/hasAnyRole/hasPermission 短路 true，其余方法转发默认 root 保留原生语义（ROLE_ 前缀、PermissionEvaluator、trustResolver 等不重复实现）
- `rookie-framework/src/main/java/com/rookie/framework/security/expression/AdminBypassMethodSecurityExpressionHandler.java` — 新建。继承 `DefaultMethodSecurityExpressionHandler`，重写 **public** `createEvaluationContext(Supplier<Authentication>, MethodInvocation)`（6.5 实际走此入口，重写 protected 的 createSecurityExpressionRoot 不生效因被 private Supplier 路径绕过）：admin 时用 super 的 protected `createSecurityExpressionRoot` 拿默认 root 作委托、包成 AdminBypassExpressionRoot、装入公共 `StandardEvaluationContext`（package-private 的 MethodSecurityEvaluationContext 外部包不可用）+ setBeanResolver；非 admin 时 `return super.createEvaluationContext(...)` 完整走 Spring Security 默认鉴权，行为零变化
- `rookie-framework/src/main/java/com/rookie/framework/config/SecurityConfig.java` — 注册 `@Bean MethodSecurityExpressionHandler` 为自定义 handler，`@EnableMethodSecurity` 自动检测并替换默认实现；import 补 MethodSecurityExpressionHandler
- `rookie-system/src/main/java/com/rookie/system/mapper/SysMenuMapper.java` — 新增 `getSysMenuAllEnabled()`（全部启用且未删除菜单，含按钮节点）
- `rookie-system/src/main/resources/mapper/system/SysMenuMapper.xml` — 新增 `getSysMenuAllEnabled` SQL：`select * from sys_menu where delete=0 and status=1 order by parent_id, menu_id`，复用 sysMenuVo resultMap
- `rookie-system/src/main/java/com/rookie/system/service/SysMenuService.java` + `impl/SysMenuServiceImpl.java` — 新增 `getSysMenuAllEnabled()`，直接调 mapper，不经 role_menu
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysLoginServiceImpl.java` — `getUserMenuTreeByUserId` 先判 admin（sysRoles 含 roleKey=="admin" 且启用），admin 走 `getSysMenuAllEnabled()` 拿全部启用菜单，否则走原 `getSysMenuByRoleList`；buildMenuTree 逻辑不变

前端无改动：`buttonPermissionKeys`（stores/navigation.ts）从后端菜单树提取按钮权限，菜单树含按钮节点即自然齐全。所有现有 `@PreAuthorize` 注解（约 60+ 个）一律未动。验证：`mvn -pl rookie-admin -am compile` 全量编译通过。

### — 系统设置前端获取方式改造：全量拉取 → 按 key 单项拉取

原前端系统设置与字典一致，登录后全量拉取所有启用项到内存 + localStorage，组件按 key 同步读缓存。问题：系统设置可能含不宜整体暴露给前端的关键信息（初始密码、阈值、密钥类配置），全量接口把所有启用项的值一股脑下发存在安全隐患。本次改为若依风格——前端按需通过接口获取**指定设置项**的值再运用，不再全量拉取。字典保持全量不变（字典是展示用枚举数据，无敏感信息）。

- `rookie-system/src/main/java/com/rookie/system/controller/SysConfigController.java` — 删除 GET /sys/system-config/list-all 全量接口；新增 GET /sys/system-config/configKey/{configKey} 按 key 取值接口（公共读取，仅需登录，不加 @PreAuthorize/@Log），只返回 configValue 字符串，不暴露 valueType/isSystem/remark 元信息，命中且启用返回值，未命中/停用返回 null
- `rookie-system/src/main/java/com/rookie/system/service/SysConfigService.java` — 删除 listAllEnabledSysConfig()；新增 getConfigValueByKey(configKey)，复用 SysConfigUtil.getConfig 只读缓存（不走数据库，与「工具只读缓存」约束一致），命中且 status=1 返回 configValue，否则 null
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysConfigServiceImpl.java` — 实现 getConfigValueByKey，删除 listAllEnabledSysConfig 实现
- `rookie-ui/src/api/system/system-config.ts` — 删除 getSysConfigAllApi；新增 getSysConfigValueApi(configKey) 调 GET /sys/system-config/configKey/{configKey}，返回 ApiResult<string|null>
- `rookie-ui/src/stores/system-config.ts` — 重构为按 key 内存缓存 + 异步拉取：configMap 改为 Record<configKey, string|null>（只存值，只存已请求过的 key）；fetchSysConfig(key, force) 命中且非 force 复用缓存，否则调接口写缓存（null 也写入避免重复请求未命中项）；删除 initializeSysConfigs/getSysConfig/initialized/loading；clearSysConfigCache 保留
- `rookie-ui/src/composables/useSysConfig.ts` — 全部改为异步：getString/getBoolean/getNumber/getObject/getList 均返回 Promise，调用方需 await；删除 resolveSysConfig（不再有整条记录概念）；类型转换逻辑不变，只是从异步拿到的字符串上做
- `rookie-ui/src/router/index.ts` — 删除 sysConfigStore import、守卫首次加载段的 initializeSysConfigs() 调用、非首次加载段的 if(!initialized) 兜底块（系统设置不再启动全量预加载）
- `rookie-ui/src/stores/user.ts` — logout 仍调 sysConfigStore.clearSysConfigCache()（方法保留，清理逻辑不变）
- `doc/api.md` — 删除「7. 获取全部启用系统设置」章节，新增「7. 按设置键获取设置值」章节

### — 字典系统补无权限全量接口（修复普通用户字典功能不可用）

延续系统设置「公共读取接口」思路排查字典，发现一个遗留 bug：前端字典初始化（`initializeDictionaries`）第一步调的是 `GET /sys/dict/list`，该接口加 `@PreAuthorize('system:dict:quarry')`，**没有字典管理权限的普通用户会 403**，连 dictKey 列表都拿不到，导致普通业务页的字典下拉/标签全部失效。`GET /sys/dist/data/type/{dictKey}`（按 key 拉数据项）本身已无 `@PreAuthorize`、仅需登录，问题只出在「取 dictKey 列表」这一步。本次补一个无权限的全量字典类型接口替代它，字典数据按 key 拉取流程不变。

- `rookie-system/src/main/java/com/rookie/system/controller/SysDictController.java` — 新增 `GET /sys/dict/all`（无 @PreAuthorize、无 @Log，仅需登录），返回全部启用字典类型基础信息（不含数据项）。与 `GET /sys/dict/list` 区分：本接口面向所有登录用户，list 仍保留权限给字典管理页用
- `rookie-system/src/main/java/com/rookie/system/service/SysDictService.java` — 新增 `listAllEnabledDict()` 返回 `List<SysDictVO>`
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysDictServiceImpl.java` — 实现 `listAllEnabledDict`：构造 `DictQuarry(status=1)` 直接调 `sysDictMapper.quarrySysDict`（不走 PageUtil，避免被分页插件截断），新增 `toDictVoList` 私有方法转 VO 列表
- `rookie-ui/src/api/system/dict.ts` — 新增 `getSysDictAllApi()` 调 `GET /sys/dict/all`，返回 `ApiResult<SysDictRecord[]>`；`getSysDictPageApi` 保留（字典管理页仍用有权限分页接口）
- `rookie-ui/src/stores/dict.ts` — `initializeDictionaries` 改调 `getSysDictAllApi()` 取 dictKey 列表（原来是 `getSysDictPageApi({pageNum:1,pageSize:500,status:1})`），从 `result.data` 取数组替代 `result.records`；按 key 拉数据项流程不变
- `doc/api.md` — 字典模块新增「6. 获取全部启用字典类型（前端初始化）」章节

## 2026-07-07
### 16:28 — 前端系统设置加载 + 表格 fixed 列 hover 重叠修复 + 时间格式化全局处理

三件事：① 前端系统设置全量加载（对标字典 store，启动拉取启用项到内存，页面按 key 读）；② 表格 fixed 列 hover 重叠：上一版错误添加的 fixed 列 CSS（z-index:2 + 实色背景 !important）破坏了 EP 原生 sticky 遮挡，导致行 hover 时被遮挡内容浮出与操作列重叠，本次撤销该 CSS 恢复 EP 原生行为；③ 后端 Jackson 全局把 Date 序列化为 yyyy-MM-dd HH:mm:ss（全量保留时间数据），前端 formatDateTime 智能截断（时分秒全 0 只展示年月日）并新增 formatDate 强制只年月日。

- `rookie-system/src/main/java/com/rookie/system/service/SysConfigService.java` — 新增 listAllEnabledSysConfig() 方法（返回全部启用设置项 VO 列表，供前端启动加载）
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysConfigServiceImpl.java` — 实现 listAllEnabledSysConfig，调 getAllSysConfig 复用 toConfigVoList 转换
- `rookie-system/src/main/java/com/rookie/system/controller/SysConfigController.java` — 新增 GET /sys/system-config/list-all 接口，不加 @PreAuthorize（公共读取，需登录即可，对标字典 /type/{dictKey}），不加 @Log
- `rookie-admin/src/main/resources/application.yml` — spring.jackson 配置 date-format: yyyy-MM-dd HH:mm:ss + time-zone: GMT+8，全局 Date 序列化统一格式，避免前端拿到 ISO-8601 原始字符串未格式化
- `rookie-ui/src/api/system/system-config.ts` — 追加 getSysConfigAllApi，调 GET /sys/system-config/list-all
- `rookie-ui/src/stores/system-config.ts` — 新建，对标 dict store。defineStore('system-config')，configMap 按 configKey 索引、initialized/loading、localStorage 持久化（key rookie-system-config-cache）、initializeSysConfigs(force) 全量拉取、getSysConfig(key)、clearSysConfigCache()；与字典区别：体量小启动加载一次，不按 key 懒加载
- `rookie-ui/src/composables/useSysConfig.ts` — 新建，对标 useDict。getString/getBoolean/getNumber/getObject/getList + resolveSysConfig，按 valueType 转换，记录不存在/停用/转换失败回落 defaultValue；非响应式直接返回（对标 resolveDictLabel）
- `rookie-ui/src/stores/user.ts` — logout() 追加 sysConfigStore.clearSysConfigCache()，与 dictStore 清理同处
- `rookie-ui/src/router/index.ts` — 守卫首次加载段（动态路由注册后）追加 sysConfigStore.initializeSysConfigs()，非首次加载分支追加 if(!initialized) 兜底，与 dict 接入平行
- `rookie-ui/src/assets/main.css` — 撤销上一版错误添加的 fixed 列 CSS（.el-table__fixed-right/__fixed-left 的 z-index:2 与多段实色背景）。该改动用 !important 覆盖了 EP 原生固定列的 background:inherit，破坏 sticky 列层叠上下文，反而导致行 hover 时被固定列遮挡的普通列内容浮上来与操作列重叠。EP 2.14 固定列用 position:sticky + background:inherit + z-index 自带正确遮挡，无需额外覆盖；删除后恢复 EP 原生遮挡行为，main.css el-table 段落回到项目原版
- `rookie-ui/src/utils/format.ts` — formatDateTime 智能截断：时分秒全 0 时只返回 yyyy-MM-dd，否则完整 yyyy-MM-dd HH:mm:ss；新增 formatDate 强制只返回 yyyy-MM-dd（供只需要年月日的字段）；formatDisplayValue 内部 Date 走 formatDateTime 自动受益

## 2026-07-03
### 18:27 — 系统设置（system_config）模块后端 + 前端 + SQL 全量落地

新增系统设置模块：键值型设置项，值类型由 value_type 标识（STRING/BOOLEAN/NUMBER/JSON）。数据库为唯一源，Redis 为永久缓存副本（key 前缀 `sys_config:`），工具类 `SysConfigUtil` 只读缓存、不走数据库，启动时由 `SysConfigWarmUpRunner` 预热全部启用项；增改删由 Service 同步维护缓存，「刷新缓存」接口清空后立即重新预热。内置项（is_system=1）受保护：禁止删除、禁止改 configKey/valueType、禁止停用，仅可改值/名称/备注。

- `sql/sys_config.sql` — 新建。建表 sys_config（键值型，继承审计四列，不软删除，靠 is_system 保护内置项）+ 3 条内置设置项初始化数据（sys.user.initPassword / sys.user.registerEnabled / sys.notice.keepDays）+ 值类型字典 sys_config_value_type 及 4 条字典数据 + 菜单 69~75（系统设置二级菜单 + 6 个按钮权限 quarry/info/add/edit/delete/refresh）+ admin 授权；INSERT...ON DUPLICATE KEY UPDATE 幂等
- `rookie-common/src/main/java/com/rookie/common/pojo/entity/SysConfig.java` — 新建，系统设置实体，继承 BaseEntity，字段 configId/configKey/configName/configValue/valueType/isSystem/remark/status
- `rookie-common/src/main/java/com/rookie/common/util/SysConfigUtil.java` — 新建，系统设置缓存工具（对标 DictUtil，static 方法）。setConfig/getConfig(只读缓存)/removeConfig/clearAllConfig + 类型化读取 getString/getBoolean/getNumber/getObject/getList（带 defaultValue，转换失败/停用/缓存未命中回落默认值，不走数据库）
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysConfigVo.java` — 新建，系统设置 VO
- `rookie-system/src/main/java/com/rookie/system/pojo/quarry/SysConfigQuarry.java` — 新建，列表查询条件（configKey/configName/status）
- `rookie-system/src/main/java/com/rookie/system/mapper/SysConfigMapper.java` — 新建，6 个方法（quarry/add/edit/delete/getById/getAll）
- `rookie-system/src/main/resources/mapper/system/SysConfigMapper.xml` — 新建，insert 用 trim+if 动态列 + useGeneratedKeys 回写主键，update 用 set+if 动态列，getAllSysConfig 供启动预热与刷新预热
- `rookie-system/src/main/java/com/rookie/system/service/SysConfigService.java` — 新建，6 方法接口
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysConfigServiceImpl.java` — 新建，增改删事务内操作 DB 后同步缓存；内置项保护校验（删除抛 ServiceException、编辑禁改键/类型/停用）；新增强制 isSystem=0；refreshCache 清空后重新预热全部启用项
- `rookie-system/src/main/java/com/rookie/system/controller/SysConfigController.java` — 新建，6 接口（list/info/add/edit/delete/refresh），写操作加 @Log + @PreAuthorize，权限码 system:systemConfig:*
- `rookie-system/src/main/java/com/rookie/system/runner/SysConfigWarmUpRunner.java` — 新建，ApplicationRunner 启动预热钩子，启动时查全部启用设置项写入 Redis，预热失败不阻断启动仅记日志
- `rookie-ui/src/types/api/system/system-config.ts` — 新建，SysConfigRecord/SysConfigListQuery/SysConfigPageResult，对齐后端 SysConfigVo/SysConfigQuarry
- `rookie-ui/src/api/system/system-config.ts` — 新建，6 接口封装（list/get/create/update/delete + refreshSysConfigCacheApi），对标 dict.ts
- `rookie-ui/src/constants/systemPermissions.ts` — 追加 systemConfig 组（create/edit/delete/refresh，双权限码格式）
- `rookie-ui/src/views/system/system-config/config.ts` — 新建，查询/表单 schema、默认值、校验规则；createSysConfigSchema 按 mode/isSystem/valueType 动态生成（configValue 控件随 valueType 切换 text/switch/number/textarea，内置项编辑禁用 configKey/valueType/status）；SysConfigFormModel 把 configValue 放宽为 string|number|boolean 适配不同控件
- `rookie-ui/src/views/system/system-config/index.vue` — 新建，系统设置页，仿 dict index.vue；列表 CRUD + 刷新缓存按钮（调后端 refreshSysConfigCacheApi，与字典刷新走前端本地缓存不同）；内置项行隐藏删除按钮；BOOLEAN 类型 configValue 回显归一化为布尔、提交转回 "true"/"false" 字符串；watch valueType 变化重置 configValue 初值；onMounted 预加载值类型字典；vue-tsc type-check 通过
- `doc/api.md` — 接口更新日志追加 2026-07-03 系统设置模块条目；末尾新增「系统设置模块」章节共 6 个接口四段式文档
- 补充：设置值 configValue 调整为必填——后端 addSysConfig/editSysConfig 增加空值校验（BOOLEAN 的 "false"、NUMBER 的 "0" 非空放行），前端 sysConfigFormRules 增加 configValue required 规则

## 2026-07-02
### 20:03 — 菜单管理 parentId 约定修复 + 公共表单校验提示去框

- `rookie-ui/src/views/system/menu/config.ts` — createDefaultMenuForm 默认 parentId 由 0 改为 -1，与后端 buildMenuTree 顶级约定对齐
- `rookie-ui/src/views/system/menu/index.vue` — parentMenuOptions「顶级目录」选项值由 0 改为 -1，避免选中顶级后存盘导致菜单从列表消失
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysMenuServiceImpl.java` — buildMenuTree 顶级判定处补注释说明 parentId=-1 约定（不改逻辑）
- `rookie-ui/src/assets/main.css` — .el-form-item__error 去掉背景/边框/圆角/阴影/padding，改为纯文字提示（danger 色 + xs 字号）；错误提示用绝对定位脱离文档流（top:100%），校验出现/消失不改变 form-item 高度与 margin，下方输入框不跳动；文字在默认 18px 下间隙内垂直居中（行高 12px + padding-top 3px）

### 21:48 — 管理员直通权限加载去掉 system: 前缀限制

- `rookie-framework/src/main/resources/mapper/security/UserInfoMapper.xml` — selectAllPermKey 去掉 `perm_key like 'system:%:%'` 前缀过滤，改为 `menu_type = 3`（按钮型）+ status=1 + delete=0 + perm_key 非空，覆盖任意前缀模块的按钮权限键
- `rookie-framework/src/main/java/com/rookie/framework/security/mapper/UserInfoMapper.java` — selectAllPermKey 方法注释同步更新（不限前缀）

### 22:04 — 补齐字典数据模块权限点（独立 SQL 脚本）

- `sql/dict-data-permission.sql` — 新建文件。新增「字典数据」二级菜单节点（menu_id=63，挂在字典管理 23 下，path=/system/dict-data/index）及 5 个按钮权限点（64-68：system:dictData:quarry/info/edit/add/delete），perm_key 与后端 SysDictDataController 的 @PreAuthorize 对齐；并给 admin 角色（role_id=1）补 role_menu 关联。全量 INSERT...WHERE NOT EXISTS 幂等写法

## 2026-06-20
### 18:30 — 消息通知模块 Service、Controller、Mapper 补全 + 实体清理

- `rookie-system/src/main/java/com/rookie/system/pojo/SysNoticeGroupMember.java` — 删除 memberType/memberCode/memberName 字段，memberId 改为 userId
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeGroupMemberMapper.java` — 同步删除 edit/多余方法
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMemberMapper.xml` — 同步清理字段映射和 SQL
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeMapper.java` — 新增 softDeleteSysNotice、getNoticesForUser
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeReadMapper.java` — 新增 getByNoticeAndUser
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeGroupMapper.java` — 新增 quarrySysNoticeGroup
- `rookie-system/src/main/resources/mapper/system/SysNoticeMapper.xml` — 新增 softDeleteSysNotice、getNoticesForUser（ALL + GROUP 联合查询）
- `rookie-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml` — 新增 getByNoticeAndUser
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMapper.xml` — 新增 quarrySysNoticeGroup
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeVo.java` — 新建，含 groupIds 和 noticeGroups 扩展字段
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeGroupVo.java` — 新建，含 members 扩展字段
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeService.java` — 新建，10 个方法（CRUD + publish/revoke + markAsRead/confirmNotice + getMyNotices）
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeServiceImpl.java` — 新建，级联 groupRel 增删、已读幂等、确认补齐逻辑
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeGroupService.java` — 新建，7 个方法（CRUD + addMembers/removeMembers）
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeGroupServiceImpl.java` — 新建，分组增删时级联清理关联数据
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeController.java` — 新建，10 个接口端点
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeGroupController.java` — 新建，7 个接口端点
- `sql/sys_notice.sql` — sys_notice_group_member 表删除 member_type 等冗余列，唯一键调整为 (group_id, user_id)

### 18:50 — 通知模块问题修复：已读标记、事务、分页、分组列表改进

- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeVo.java` — 新增 hasRead、hasConfirmed 字段
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeService.java` — getMyNotices 签名改为接收 Long userId
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeServiceImpl.java` — addSysNoticeInfo/editSysNoticeInfo/deleteSysNoticeInfo 加 @Transactional；getMyNotices 中注入 hasRead/hasConfirmed 到返回 VO（由 controller 提取 userId）
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeController.java` — getMyNotices 改为从 SecurityContextHolder 获取当前用户并传入 service
- `rookie-system/src/main/java/com/rookie/system/pojo/quarry/NoticeGroupQuarry.java` — 新建，分组查询 DTO（groupName/groupCode/status）
- `rookie-system/src/main/java/com/rookie/system/mapper/SysNoticeGroupMapper.java` — quarrySysNoticeGroup 签名改为接收 NoticeGroupQuarry
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMapper.xml` — quarrySysNoticeGroup SQL 改为动态查询
- `rookie-system/src/main/java/com/rookie/system/service/SysNoticeGroupService.java` — quarrySysNoticeGroup 返回 PageInfo 并接收 Quarry；removeMembers 签名改为 (Long groupId, List<Long> memberIds)
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysNoticeGroupServiceImpl.java` — addSysNoticeGroupInfo/deleteSysNoticeGroupInfo 加 @Transactional；removeMembers 加同组校验 + 批量删除
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeGroupController.java` — 分组列表返回 PageInfo 并接收 Quarry；移除成员改为 DELETE /{groupId}/members + @RequestBody List

## 2026-06-21
### 18:17 — 字典数据列表标签列改造：类型/风格/类名合并为单一标签展示列

- `rookie-ui/src/types/components/data-display/index.ts` — 新增 `SharedFieldRenderType`（`text`/`tag`）、`SharedFieldTagType`、`SharedFieldTagRenderOptions`（支持 `labelField`/`typeField`/`effectField`/`classField` 跨字段读取标签文字、颜色、风格、类名）；`SharedFieldSchemaItem` 新增 `renderType`/`tagRender` 字段
- `rookie-ui/src/components/SharedTablePanel.vue` — 新增 `tag` 渲染分支与 `resolveTagDisplay` 方法，按 schema 配置把单元格渲染为 `ElTag`（含跨字段取色、空值回落 info、占位文本）；导出 `ElTag` 及新类型
- `rookie-ui/src/views/system/dict-data/config.ts` — 抽出共享常量 `TAG_TYPE_MAP`；新增 `tagPreview` 虚拟列（仅表格展示），跨字段读取 dictDataLabel/tagType/tagEffect/cssClass 组合渲染标签；tagType/tagEffect/cssClass/extJson 四列 `tableVisible` 改为 false（表单保留），去掉表格专属配置并顺延 formOrder

### 19:48 — 通知管理（内容管理 + 分组管理）前端页面与菜单 SQL

- `rookie-ui/src/types/api/system/notice.ts` — 新建，定义 SysNoticeRecord/SysNoticeListQuery/SysNoticePageResult、SysNoticeGroupRecord/SysNoticeGroupMemberRecord/SysNoticeGroupListQuery/SysNoticeGroupPageResult，对齐后端 SysNoticeVo/SysNoticeGroupVo/SysNoticeGroupMember
- `rookie-ui/src/api/system/notice.ts` — 新建，封装通知 CRUD + 发布/撤回、分组 CRUD + 成员增删（addNoticeGroupMembersApi/removeNoticeGroupMembersApi，移除成员传成员记录 id）
- `rookie-ui/src/constants/systemPermissions.ts` — 新增 notice（create/edit/delete/publish/revoke）与 noticeGroup（create/edit/delete/member）权限 key 分组
- `rookie-ui/src/views/system/notice/config.ts` — 新建，通知类型/级别/范围/状态固定枚举与 tagTypeMap，createNoticeQuerySchema/createNoticeSchema（类型/级别/范围/状态列用 renderType:'tag' 渲染）、noticeFormRules
- `rookie-ui/src/views/system/notice/index.vue` — 新建，通知内容管理页，对齐 dict 页结构；行操作含编辑/发布（status≠PUBLISHED）/撤回（status=PUBLISHED）/详情/删除；关联分组用 field-groupIds 插槽（publishScope=GROUP 时多选分组）；详情用 ElDescriptions 只读弹窗展示正文
- `rookie-ui/src/views/system/notice-group/config.ts` — 新建，分组状态选项、createNoticeGroupQuerySchema/createNoticeGroupSchema（memberCount 列通过 formatter 读 row.members.length 计算）、noticeGroupFormRules
- `rookie-ui/src/views/system/notice-group/index.vue` — 新建，通知分组管理页，对齐 dict 页结构；行操作含编辑/管理成员/删除；用户数据源延迟到首次打开成员管理时按需拉取
- `rookie-ui/src/views/system/notice-group/components/GroupMemberTransfer.vue` — 新建，封装 ElTransfer 成员管理穿梭框，保存时按目标集合与原始集合做差集分别调用添加/移除接口，移除时按 userId 还原成员记录 id
- `rookie-ui/src/assets/main.css` — 新增 ElTransfer 主题覆盖（面板、表头、搜索框、项、按钮统一跟随主题变量，深浅模式适配）
- `README.md`（根） — 第 12 节主题适配清单"已完成适配区域"补充 ElTransfer、ElDescriptions
- `sql/sys_notice_menu.sql` — 新建，插入通知管理菜单（一级目录 36 + 通知内容 37 及 7 个按钮权限 + 通知分组 45 及 6 个按钮权限），perm_key 与前端权限 key 对齐，path 指向 src/views 组件，ON DUPLICATE KEY UPDATE 幂等
- `sql/sys_notice_menu_admin.sql` — 新建，给超级管理员（角色 1）授权通知管理菜单 36-51

### 20:39 — 侧边栏嵌套目录（目录套目录）UI 与祖先链逻辑优化

- `rookie-ui/src/layout/components/SideBar/components/SideBarMenuButton.vue` — 取消 `depth*18px` 内边距缩进（避免与父容器缩进叠加导致深层文字被挤），按钮 padding 固定；depth 改为层级弱化用途，新增 `is-nested` 修饰类弱化非顶层节点图标与字重
- `rookie-ui/src/layout/components/SideBar/components/SideBarSection.vue` — `__children` 去掉固定 margin/border，改为单一 padding-left 缩进；仅顶层目录（depth=0）展开时通过 `--root` 修饰类画一条贯通引导线，嵌套子目录不再重复画线避免平行双竖线；折叠态 popover 宽度 220→260、链接加省略号容纳"系统模块 / 通知管理 / 通知内容"长标签；展开过渡 max-height 520→760 容纳深嵌套
- `rookie-ui/src/stores/navigation.ts` — `activeDirectory`（单层父）替换为 `activeDirectoryTrail`（按 parentId 递归向上收集完整祖先目录链），`breadcrumbs` 改为 `[...祖先链, 当前菜单]` 正确支持三级嵌套，`syncByPath` 自动展开改为展开整条祖先链（刷新/直进深层菜单时上层目录不再丢失可见性）

### 21:27 — 头导航通知按钮完善 + 通知页面目录重构 + UI README 架构说明

- `rookie-ui/src/api/system/notice.ts` — 追加 `getMyNoticesApi` / `markAsReadApi` / `confirmNoticeApi` 当前用户侧接口
- `rookie-ui/src/stores/notice.ts` — 新建，管理"我的通知"列表、未读计数（`unreadCount`）、已读标记（乐观更新 `hasRead`）、详情查询（`getNoticeById`）、退出清理（`resetNoticeState`）
- `rookie-ui/src/router/index.ts` — 路由守卫首次加载完成段追加 `noticeStore.fetchMyNotices()`，退出登录 catch 分支追加 `noticeStore.resetNoticeState()`；导入 `useNoticeStore`
- `rookie-ui/src/layout/components/NavBar/index.vue` — 移除本地空 `notifications` ref，改用 `useNoticeStore` 的 `myNotices` 映射为 `NotificationItem` 展示（标题 / 正文截断摘要 / 类型中文标签 / 发布时间）；未读徽标改读 `noticeStore.unreadCount`
- `rookie-ui/src/layout/index.vue` — `openNoticePrompt` 改为从 notice store 取完整正文（`content`）展示详情，打开时调 `markAsRead` 标记已读
- `rookie-ui/src/layout/components/PromptPanel.vue` — notice 模式正文区加 `max-height: 42vh` + `overflow-y: auto` 支持超长正文滚动
- `rookie-ui/src/types/components/theme/index.ts` — `NotificationItem` 扩展 `content`（完整正文）和 `needConfirm` 字段
- `rookie-ui/src/views/system/notice/notice-content/` — 原 `views/system/notice/` 移动并重命名（内容管理）
- `rookie-ui/src/views/system/notice/notice-group/` — 原 `views/system/notice-group/` 移动至此，与 notice-content 平级
- `sql/sys_notice_menu.sql` — 菜单名称更新为"内容管理""分组管理"；`route` 更新为 `notice-content` / `notice-group`；`path` 同步指向新目录；`parent_id` 改为 1（挂到系统模块下）
- `rookie-ui/README.md` — 「项目说明」后新增「项目架构（目录）说明」章节，涵盖顶层文件与 `src/` 各子目录功能、特定名称文件约定

### 15:16 — 通知详情弹窗抽组件 + 枚举改字典系统

- `rookie-ui/src/components/NoticeDetailDialog.vue` — 新建，通知详情只读弹窗，复用 ElDialog（与公共表单弹窗同款遮罩 `--el-overlay-color`，统一背景视觉），ElDescriptions 展示元信息，枚举字段走字典系统翻译，正文区限高滚动
- `rookie-ui/src/layout/index.vue` — 通知详情改用 `NoticeDetailDialog`，移除 PromptPanel notice 模式相关状态（promptMode/promptTitle/promptContent/promptNoticeMeta），保留 markAsRead 即时已读逻辑
- `rookie-ui/src/layout/components/PromptPanel.vue` — 删除（notice 模式移至 NoticeDetailDialog，prompt 模式无消费方）
- `rookie-ui/src/types/components/prompt/index.ts` — 删除（随 PromptPanel 一并清理）
- `rookie-ui/src/layout/components/NavBar/index.vue` — 通知下拉分类标签改走 `resolveDictLabel('sys_notice_type', ...)` 字典翻译，移除内联 NOTICE_TYPE_LABEL 映射
- `rookie-ui/src/views/system/notice/notice-content/config.ts` — noticeType/level/publishScope/status 四个枚举字段移除硬编码 options 与 tagTypeMap，改用 `dictKey`（`sys_notice_type` / `sys_notice_level` / `sys_notice_scope` / `sys_notice_status`），表格自动渲染 DictTag、表单自动渲染字典选项
- `rookie-ui/src/views/system/notice/notice-content/index.vue` — 详情弹窗 `resolveEnumLabel` 改为接收 dictKey 走字典翻译，移除 noticeTypeOptions 等枚举导入，引入 useDict

### 20:51 — 通知正文 Markdown 编辑 + 详情改博客排版

- `rookie-ui/package.json` — 新增依赖 `@kangc/v-md-editor@next`（2.x，Vue 3 版 markdown 编辑器）与 `highlight.js`（代码块高亮，github 主题需显式注入 Hljs 实例）
- `rookie-ui/env.d.ts` — 追加 v-md-editor ambient 类型声明（库 package.json 的 types 字段指向不存在的目录，未真正发布类型）：主入口默认导出是带 `install`/`use`/`lang.use` 的编辑器插件对象（组件名 v-md-editor），`lib/preview.js` 导出带 `install` 的预览插件对象（组件名 v-md-preview），`lib/theme/github.js` 导出带 `install(app, config)` 的主题插件对象（config.Hljs 经 `use(plugin, config)` 透传），`lib/lang/zh-CN` 语言包
- `rookie-ui/src/utils/markdown.ts` — 新建，幂等注册：`VueMarkdownEditor.lang.use('zh-CN', zhCN)` + `VueMarkdownEditor.use(githubTheme, { Hljs: hljs })` 注入 highlight.js，再 `app.use(VueMarkdownEditor)` + `app.use(VueMarkdownPreview)` 全局注册编辑器/预览组件（@next 版无 VMdEditor/VMdPreview 具名导出，必须走 app.use 全局注册）
- `rookie-ui/src/main.ts` — 在 `createApp` 后调 `setupVmdEditor(app)`，按需引入 base-editor / preview / github 主题 + `highlight.js/styles/github.css` 配色
- `rookie-ui/src/types/vue-components.d.ts` — 新建，独立模块声明文件，用 `declare module 'vue'` 增强 `GlobalComponents` 注册 `v-md-editor`/`v-md-preview`（单独成文件避免与 env.d.ts 的 ambient 声明互相干扰，顶层 `import type` 让文件成模块从而正确生效为 augmentation）
- `rookie-ui/src/components/MarkdownEditor.vue` — 新建，直接用 `<v-md-editor>` 全局标签（由 vue-components.d.ts 提供类型），封装为受控组件供公共表单 `inputType:'markdown'` 复用
- `rookie-ui/src/components/MarkdownPreview.vue` — 新建，直接用 `<v-md-preview>` 全局标签，只读展示供通知详情等博客式正文渲染复用
- `rookie-ui/src/types/components/data-display/index.ts` — `SharedFieldInputType` 新增 `'markdown'`
- `rookie-ui/src/components/SharedFormPanel.vue` — dialog 与 panel 两种模式各加 `markdown` 渲染分支（复用 MarkdownEditor），追加 `.shared-form-panel__markdown` 全宽样式
- `rookie-ui/src/views/system/notice/notice-content/config.ts` — 正文 `content` 字段 `inputType` 由 `textarea` 改为 `markdown`，移除 rows 配置
- `rookie-ui/src/components/NoticeDetailDialog.vue` — 排版由 ElDescriptions 改为博客式：大标题 → 元信息标签条（类型/级别/范围/状态走 DictTag 渲染带色 ElTag，置顶/需确认走 flag 标记）→ 信息条（时间/分组/路由）→ MarkdownPreview 渲染正文 → 备注脚注
- `rookie-ui/src/views/system/notice/notice-content/index.vue` — 详情弹窗复用 `NoticeDetailDialog`，移除内联 ElDescriptions/ElDialog 块及随之失效的 useDict/resolveEnumLabel/formatDateTime 引入
- `rookie-ui/src/assets/main.css` — 追加 v-md-editor 主题覆写：编辑器外壳/工具栏/编辑区/预览区背景与文字、代码块/表格/引用/分隔线/行内代码/kbd 统一跟随 `--rookie-*` 主题变量；`[data-theme='dark']` 作用域覆盖 highlight.js 代码块背景为基础深色，深色模式不再白底刺眼；详情弹窗内预览区收紧默认左右大内边距
- `README.md`（根） — 第 12 节主题适配清单"已完成适配区域"补充 v-md-editor 编辑/预览 + highlight.js 代码块相关条目

### 22:40 — v-md-editor 预览崩溃修复 + 公共表单弹窗内容区滚动

- `rookie-ui/src/utils/markdown.ts` — 预览组件 created 读 `themeConfig.markdownParser` 为 undefined 崩溃根因：编辑器与预览是两个独立 vMdParser 实例，主题只 use 到编辑器上；补 `VueMarkdownPreview.use(githubTheme, { Hljs: hljs })` 让预览 parser 也拿到主题配置
- `rookie-ui/env.d.ts` — `lib/preview.js` ambient 声明补 `use` 方法
- `rookie-ui/src/components/SharedFormPanel.vue` — ElDialog 加 `shared-form-dialog` class，追加样式：弹窗 `max-height: calc(100vh - 80px)` 固定最大高、`display:flex` 纵向布局，body `flex:1 + overflow-y:auto` 内容区滚动，header/footer `flex:none` 固定不随内容滚动（长表单如带 markdown 编辑器的通知表单不再把弹窗撑出屏幕）

## 2026-06-22
### 23:05 — 通知详情正文不显示修复 + 详情弹窗字段调整 + 确认按钮 + 发布者

- `rookie-ui/src/components/MarkdownPreview.vue` — 正文不显示根因：v-md-preview 组件的 prop 名是 `text` 而非 `modelValue`，`<v-md-preview :model-value="...">` 传不进去；改为对外收 `modelValue`、对内绑 `:text`，预览区正常渲染
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysNoticeVo.java` — 新增 `createBy` 字段及 getter/setter（发布者，BeanUtil.toBean 自动从 SysNotice 复制，my 接口与列表接口均带上）
- `rookie-ui/src/types/api/system/notice.ts` — `SysNoticeRecord` 新增 `createBy?: string`（发布者）
- `rookie-ui/src/types/components/theme/index.ts` — `NotificationItem` 新增 `isTop?: boolean`（下拉项置顶标记）
- `rookie-ui/src/stores/notice.ts` — 新增 `confirmNotice(noticeId)` 方法调 `confirmNoticeApi`，乐观更新本地 `hasConfirmed=true`，已确认则跳过请求
- `rookie-ui/src/components/NoticeDetailDialog.vue` — 详情弹窗字段调整：移除状态 DictTag、跳转路由行、置顶/需确认 flag 标记；新增发布者行；关联分组行改为仅 `publishScope=GROUP` 时展示（整行跨列避免长分组名截断）；新增 footer，`needConfirm=1 且 !hasConfirmed` 时右下角展示"确认"按钮（已确认自动隐藏），点击向父层抛 `confirm` 事件
- `rookie-ui/src/layout/components/NavBar/index.vue` — `toNotificationItem` 映射补 `isTop`；下拉项标题行置顶通知展示"置顶"小标签（与未读小圆点并列）；新增 `.nav-bar__notice-top` 样式
- `rookie-ui/src/layout/index.vue` — 新增 `handleConfirmNotice` 调 `noticeStore.confirmNotice`，`NoticeDetailDialog` 监听 `@confirm` 事件接入

### 23:40 — 公共表单弹窗内部滚动修复（scoped 命不中 teleported 元素）

- `rookie-ui/src/components/SharedFormPanel.vue` — 上轮弹窗滚动样式没生效根因：`ElDialog` 的 `inheritAttrs:false` 把外部 `class="shared-form-dialog"` 经 `$attrs` 透传到 `DialogContent`，与 `el-dialog` 落在**同一元素**（非父子），且 `ElDialog` teleport 到 body，scoped 的 `data-v` 锚点不在 teleported 子树内，所以 scoped `:deep(.el-dialog)` 选择器匹配不到；改为把弹窗滚动样式从 `<style scoped>` 移到单独的非 scoped 全局 `<style>` 块，用同元素并集选择器 `.shared-form-dialog.el-dialog` 命中；`ElDialog` 加 `top="40px"` prop 生成 inline `top` 控制距顶 40px（覆盖默认 `15vh`，避免与 `max-height` 叠加超出底部），`max-height: calc(100vh - 80px)` 保证弹窗永不超过窗口，body `flex:1 + overflow-y:auto` 内容区内部滚动，header/footer 固定

### 23:55 — dict 缓存补刷 + 表单字段精简 + 关联分组按范围显隐 + 通知栏 hover 残留修复

- `rookie-system/src/main/java/com/rookie/system/service/impl/SysDictDataServiceImpl.java` — `addSysDictData` 新增字典数据后未刷新 Redis 缓存，导致通过字典管理页新增的项（如 `sys_notice_status` 的 REVOKED）数据库已有但前端拿不到（`getSysDictDataByDictKey` 优先走缓存命中旧数据），而 `editSysDictDataInfo` 有正确刷新；补 `DictUtil.setDictData` 与编辑对齐，新增后立即刷新缓存
- `rookie-ui/src/types/components/data-display/index.ts` — `SharedFieldSchemaItem` 新增 `visibleWhen?: (model) => boolean` 动态显隐回调，让表单字段可根据当前模型中其他字段值决定是否渲染整个表单项
- `rookie-ui/src/components/SharedFormPanel.vue` — `formFields` computed 在 `formVisible !== false` 后追加 `visibleWhen` 过滤，为关联分组按发布范围动态显隐提供基础
- `rookie-ui/src/views/system/notice/notice-content/config.ts` — 表单精简：删除 `routePath`、`expireTime` 字段配置；`status` 改为 `formVisible: false`（仅表格展示）；`publishTime` 改为 `formVisible: false`（仅表格展示，由发布接口确定）；`groupIds` 加 `visibleWhen: (model) => String(model.publishScope) === 'GROUP'` 全员时隐藏整个表单项；`createDefaultNoticeForm` 移除 `routePath`/`expireTime`/`publishTime`；`noticeFormRules` 移除 status 校验
- `rookie-ui/src/types/api/system/notice.ts` — `SysNoticeRecord` 删除 `routePath`/`expireTime` 字段，补回上轮误删的 `remark`
- `rookie-ui/src/views/system/notice/notice-content/index.vue` — `handleSubmitForm` 去掉 `routePath` 拼接；`field-groupIds` 插槽移除外层 `v-if`/`v-else`（由 `visibleWhen` 统一控制显隐）；移除 `.system-notice-view__hint` 样式
- `rookie-ui/src/components/NoticeDetailDialog.vue` — 移除 `expireTime` 展示行（字段已删除）
- `rookie-ui/src/layout/components/NavBar/index.vue` — 新增非 scoped 全局 `<style>` 块，`.nav-bar-notice-dropdown .el-dropdown-menu__item:not(:hover):not(:focus)` 覆写背景色为 transparent，防止鼠标移出下拉后最后划过的通知项残留 hover/focus 高亮

### 2026-06-23
### 00:10 — 通知表格"已撤回"标签回显修复：前端字典缓存跳过刷新

- `rookie-ui/src/stores/dict.ts` — `initializeDictionaries` 内部调 `fetchDictDataByKey(dictKey, force)` 时 `force` 原值 false：虽然后端 Redis 层已正确刷新，但前端 `loadedKeySet` 从 localStorage 恢复后使 `fetchDictDataByKey` 判定 key 已存在，直接返回旧缓存（缺少 REVOKED 项）而跳过后端请求；改为硬编码 `true`，初始化阶段始终从后端拉取最新字典数据，避免本地缓存导致新增字典项无法进入前端

### 18:40 — 分组成员管理弹窗重做：穿梭框改搜索+分页+标签区

- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — 废弃 ElTransfer 穿梭框，重写为搜索+分页+标签区形态：顶部字段类型（昵称/用户名/手机号）select + 关键词输入框 + 搜索/重置按钮（复用 `getSysUserPageApi` 现有分页接口，无需后端改动）；中部已选成员 `ElTag` closable 标签区（空态"暂无成员"），`selectedUserMap` 缓存昵称、拿不到兜底 `用户#{userId}`；下部 `ElTable` 用户候选分页表格（昵称/用户名/手机号/状态/操作 5 列，操作列按 `selectedUserIds` Set 切"加入"primary / "已加入"disabled plain）+ `ElPagination`；加入/移除为本地乐观更新，保存时与原始 `props.members` 的 userId 集合做差集分别调 add/remove 接口（逻辑与原穿梭框一致），已选状态跨页跨搜索保持
- `rookie-ui/src/views/system/notice/notice-group/index.vue` — 移除 `userOptions` ref、`fetchUserOptions` 方法（原 `pageSize: 500` 一次性拉全量用户灌穿梭框，用户量大时 DOM 爆炸+全表 like 慢）、`getSysUserPageApi` import；`openMemberManage` 去掉"用户数据源未就绪则预拉"分支，用户候选数据延迟到弹窗内部按搜索条件分页拉取；模板 `<GroupMemberTransfer>` 去掉 `:user-options` prop；文件头与相关注释"穿梭框"改为"成员管理弹窗"
- `rookie-ui/src/assets/main.css` — 删除 `.el-transfer-*` 主题覆写块（穿梭框已废弃，全项目无其他消费方）
- `README.md`（根） — 第 12 节主题适配清单"已完成适配区域"移除 `ElTransfer` 条目
- `rookie-ui/README.md` — 目录说明中 main.css 描述去掉"穿梭框"、notice-group 目录描述"成员穿梭框"改为"成员管理弹窗"

### 19:30 — 分组成员管理改双弹窗结构 + 后端关联补全成员展示字段

- `rookie-system/src/main/java/com/rookie/system/pojo/SysNoticeGroupMember.java` — 新增 `username`/`nickName`/`phoneNumber`/`status` 展示字段（不对应表列，由关联查询填充）及 getter/setter
- `rookie-system/src/main/resources/mapper/system/SysNoticeGroupMemberMapper.xml` — `SysNoticeGroupMemberResultMap` 扩展 username/nickName/phoneNumber/status 映射；`getSysNoticeGroupMemberByGroupId` 改为 `left join sys_user` 返回展示字段
- `rookie-ui/src/types/api/system/notice.ts` — `SysNoticeGroupMemberRecord` 补 `phoneNumber?`/`status?`，注释改为说明展示字段由后端关联填充
- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberAddDialog.vue` — 新建，"添加成员"子弹窗：字段类型（昵称/用户名/手机号）select + 关键词搜索 + 用户分页表格（昵称/用户名/手机号/状态/操作），行内"加入"按钮向父层抛 `add`，`excludeUserIds` 控制已加入禁用态，`append-to-body` 叠在主弹窗之上，空态 `ElEmpty` 提示换词
- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — 主弹窗由标签区改为成员表格（昵称/用户名/手机号/状态/移除 5 列）+ 工具栏（人数 + "添加成员"按钮开子弹窗）；`localMembers` 拷贝 props.members 本地乐观增删，`originalUserIds` 记录原始集合，保存时做差集调 add/remove 接口；昵称兜底 `用户#{userId}`；子弹窗 ref 嵌在主弹窗内（destroy-on-close 关闭时一并销毁）

### 20:05 — 分组成员移除改标记态：点移除先标记，保存才统一删除

- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberTransfer.vue` — 本地成员类型扩展 `pendingRemove` 标记；点"移除"不再从表格删行，改为 toggle `pendingRemove`（行 `is-pending-remove` 类变灰 + 删除线，按钮变"撤销"可恢复）；`handleSave` 差集逻辑改为：未标记移除且非原始 → addMembers，标记 pendingRemove 且原始（有记录 id）→ removeMembers；工具栏人数改读 `activeUserIds.size`（不含待移除），追加"（待移除 N）"提示；`excludeUserIds` 改用 `activeUserIds`，已标记移除的成员可在子弹窗重新"加入"触发撤销标记恢复

### 20:35 — 添加成员子弹窗重置 BUG 修复：重置不再拉全量用户

- `rookie-ui/src/views/system/notice/notice-group/components/GroupMemberAddDialog.vue` — `resetSearch` 原实现清空关键词后又调 `fetchUserPage()`，关键词为空等于无筛选拉全量用户第 1 页（与子弹窗"搜了才展示"的定位相悖）；改为只清空 keyword/searchField 与 `userPageState`（records 置空、分页归位），不调接口；`handleSearch` 加空关键词守卫，空关键词时走 `resetSearch` 清空表格，避免按回车/点搜索时拉全量

## 2026-06-27
### 16:50 — 日志管理模块（操作日志 + 错误日志）后端完整落地

新增操作日志与错误日志两套系统：操作日志通过 `@Log` 注解 + AOP 切面在请求线程同步采集、成功异步落库、失败同步落库拿主键；错误日志通过 `GlobalExceptionHandler` 统一采集请求来源异常，并预留定时任务/异步任务等多来源扩展。两表通过 `sys_error_log.oper_log_id` 关联，操作日志列表对失败行返回 `errorLogId` 供前端跳转。所有枚举字段进字典系统驱动前端标签映射。

- `sql/sys_oper_log.sql` — 新建 sys_oper_log 操作日志表（含 IP/OS/浏览器/设备类型等环境字段，status 用 TINYINT 0/1，不继承 BaseEntity）
- `sql/sys_error_log.sql` — 新建 sys_error_log 错误日志表（只记来源/关联/异常三件套/时间/操作人，HTTP 环境信息归操作日志；含 source_type 区分错误来源）
- `sql/sys_log_dict_init.sql` — 新建 4 个字典初始化脚本：sys_oper_business_type、sys_oper_device_type、sys_oper_status、sys_error_source_type（幂等写法，对齐 sys_dict/sys_dict_data 真实表结构）
- `sql/sys_log_menu_init.sql` — 新建日志管理菜单脚本（menu_id 52~62：一级目录"日志管理" + 操作日志/错误日志两个菜单 + 各 4 个按钮权限，INSERT...ON DUPLICATE KEY UPDATE 幂等）
- `sql/sys_log_menu_admin.sql` — 新建超级管理员（role_id=1）日志菜单授权脚本
- `rookie-common/src/main/java/com/rookie/common/annotation/Log.java` — 新建操作日志注解（title/businessType/isSaveRequestData/isSaveResponseData）
- `rookie-common/src/main/java/com/rookie/common/enums/BusinessType.java` — 新建业务类型枚举（OTHER/INSERT/UPDATE/DELETE/GRANT/EXPORT/IMPORT/CLEAN，code 对齐字典值）
- `rookie-common/src/main/java/com/rookie/common/enums/DeviceType.java` — 新建设备类型枚举（PC/MOBILE/TABLET/UNKNOWN）
- `rookie-common/src/main/java/com/rookie/common/enums/ErrorSourceType.java` — 新建错误来源枚举（REQUEST/SCHEDULED/ASYNC/EVENT/INIT/OTHER）
- `rookie-common/src/main/java/com/rookie/common/pojo/entity/SysOperLog.java` — 新建操作日志实体（不继承 BaseEntity，日志只追加）
- `rookie-common/src/main/java/com/rookie/common/pojo/entity/SysErrorLog.java` — 新建错误日志实体（不继承 BaseEntity，operLogId/operName 可空）
- `rookie-framework/pom.xml` — 新增 spring-boot-starter-aop 依赖（支撑 @Log 切面）
- `rookie-framework/src/main/java/com/rookie/framework/aspectj/LogAspect.java` — 新建操作日志切面，@Around 单切面：请求线程同步采集注解/方法/请求上下文/UA 解析/操作人，成功异步 saveAsync、失败同步 saveAndGetId 拿 oper_id 塞 request attribute 供错误日志关联，异常继续上抛交 GlobalExceptionHandler
- `rookie-framework/src/main/java/com/rookie/framework/config/AsyncConfig.java` — 新建异步配置，logExecutor 线程池（DiscardOldestPolicy 拒绝策略，日志可丢不拖业务）+ AsyncUncaughtExceptionHandler 将 @Async 异常记入错误日志（ASYNC 来源）
- `rookie-framework/src/main/java/com/rookie/framework/filter/RequestCachingFilter.java` — 新建请求体缓存过滤器，ContentCachingRequestWrapper 包装 request 解决 @RequestBody 只能读一次问题，HIGHEST_PRECEDENCE 保证最先执行
- `rookie-framework/src/main/java/com/rookie/framework/service/OperLogService.java` — 新建操作日志服务接口（依赖倒置：framework 定义，system 实现），saveAndGetId 同步拿主键 + saveAsync 异步落库
- `rookie-framework/src/main/java/com/rookie/framework/service/ErrorLogService.java` — 新建错误日志服务接口，统一错误采集落库入口
- `rookie-framework/src/main/java/com/rookie/framework/handle/GlobalExceptionHandler.java` — 增强：新增 recordErrorLog 方法，ServiceException 与 Exception 两个 handler 均异步写错误日志（REQUEST 来源），title 取请求路径，oper_name 从 SecurityContext 取（无登录态置空），oper_log_id 从 request attribute 取关联
- `rookie-system/src/main/java/com/rookie/system/pojo/quarry/OperLogQuarry.java` — 新建操作日志查询条件
- `rookie-system/src/main/java/com/rookie/system/pojo/quarry/ErrorLogQuarry.java` — 新建错误日志查询条件
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysOperLogVo.java` — 新建操作日志 VO，含 errorLogId 关联字段供前端跳转
- `rookie-system/src/main/java/com/rookie/system/pojo/vo/SysErrorLogVo.java` — 新建错误日志 VO，含 operLogId 关联字段
- `rookie-system/src/main/java/com/rookie/system/mapper/SysOperLogMapper.java` — 新建操作日志 Mapper（分页查询/新增回写主键/详情/批量删除/清空）
- `rookie-system/src/main/java/com/rookie/system/mapper/SysErrorLogMapper.java` — 新建错误日志 Mapper，含 getErrorLogByOperLogIds 支持操作日志列表两段式关联查询
- `rookie-system/src/main/resources/mapper/system/SysOperLogMapper.xml` — 新建操作日志 Mapper XML，列表直接映射 VO、insert 用 trim+if 动态列、清空用 truncate
- `rookie-system/src/main/resources/mapper/system/SysErrorLogMapper.xml` — 新建错误日志 Mapper XML，getErrorLogByOperLogIds 只取 error_id/oper_log_id 两列
- `rookie-system/src/main/java/com/rookie/system/service/SysOperLogService.java` — 新建操作日志管理服务接口（列表/详情/删除/清空）
- `rookie-system/src/main/java/com/rookie/system/service/SysErrorLogService.java` — 新建错误日志管理服务接口
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysOperLogServiceImpl.java` — 新建，同时实现 SysOperLogService 与 framework 的 OperLogService；quarryOperLog 用两段式查询（操作日志单表分页 + IN 查关联错误日志）拼装 errorLogId，与表体量解耦；saveAsync 标 @Async("logExecutor")
- `rookie-system/src/main/java/com/rookie/system/service/impl/SysErrorLogServiceImpl.java` — 新建，同时实现 SysErrorLogService 与 framework 的 ErrorLogService；saveAsync 标 @Async("logExecutor")
- `rookie-system/src/main/java/com/rookie/system/controller/SysOperLogController.java` — 新建操作日志 Controller（4 个接口，均带 @PreAuthorize 对齐菜单 perm_key）
- `rookie-system/src/main/java/com/rookie/system/controller/SysErrorLogController.java` — 新建错误日志 Controller（4 个接口，均带 @PreAuthorize）
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeController.java` — 给 5 个写操作（新增/编辑/删除/发布/撤回）加 @Log 注解，作为操作日志切面的验证接入点
- `doc/api.md` — 接口更新日志追加 2026-06-27 日志管理模块条目；末尾新增"一、操作日志""二、错误日志"两章共 8 个接口的四段式文档
- `doc/devlog.md` — 追加本条开发日志

### 22:36 — 业务接口 @Log 注解全覆盖 + 错误日志链路验证

为全部 7 个业务 Controller 的写操作补齐 @Log 注解（读操作不加，避免日志噪声），共 31 处，使操作日志覆盖系统的增删改类重要业务操作；同时新建临时测试接口验证错误日志采集与操作日志-错误日志关联链路，验证通过后删除。

- `rookie-system/src/main/java/com/rookie/system/controller/SysUserController.java` — 4 个写操作加 @Log：添加用户(INSERT)/编辑用户(UPDATE)/删除用户(DELETE)/更改用户状态(UPDATE)，title 统一"用户管理"
- `rookie-system/src/main/java/com/rookie/system/controller/SysRoleController.java` — 5 个写操作加 @Log：添加角色(INSERT)/编辑角色(UPDATE)/删除角色(DELETE)/更改角色状态(UPDATE)/设置默认角色(GRANT)，title 统一"角色管理"
- `rookie-system/src/main/java/com/rookie/system/controller/SysMenuController.java` — 4 个写操作加 @Log：添加菜单(INSERT)/编辑菜单(UPDATE)/删除菜单(DELETE)/更改菜单状态(UPDATE)，title 统一"菜单管理"
- `rookie-system/src/main/java/com/rookie/system/controller/SysDictController.java` — 3 个写操作加 @Log：添加字典(INSERT)/编辑字典(UPDATE)/删除字典(DELETE)，title 统一"字典管理"（该 Controller 原无 @Operation 注解，仅加 @Log 未补 swagger 注解）
- `rookie-system/src/main/java/com/rookie/system/controller/SysDictDataController.java` — 3 个写操作加 @Log：添加字典数据(INSERT)/编辑字典数据(UPDATE)/删除字典数据(DELETE)，title 统一"字典数据"
- `rookie-system/src/main/java/com/rookie/system/controller/SysNoticeGroupController.java` — 5 个写操作加 @Log：添加分组(INSERT)/编辑分组(UPDATE)/删除分组(DELETE)/添加成员(INSERT)/移除成员(DELETE)，title 统一"通知分组"
- `rookie-system/src/main/java/com/rookie/system/controller/SysLoginController.java` — 2 个写操作加 @Log：登录(OTHER，title"登录管理")/更改个人数据(UPDATE，title"个人信息")；登录接口未认证时切面采集 oper_name 为空属预期（LoginBody 在 oper_param 中可查）
- `rookie-system/src/main/java/com/rookie/system/controller/SysLogTestController.java` — 临时新建错误日志测试接口 GET /sys/logTest/error?type=business|unknown，带 @Log 触发 ServiceException/RuntimeException 验证错误日志落库与 oper_log_id 关联链路；验证通过后已删除（净效果为零，仅作记录）

## 2026-06-28
### 10:40 — 日志管理前端页面（操作日志 + 错误日志）

承接日志管理后端模块，落地操作日志与错误日志两个管理页面，覆盖列表分页、字典驱动筛选、只读详情、批量删除、清空五类操作，并打通「操作日志失败行 → 错误日志详情」「错误日志请求来源 → 操作日志详情」的双向跳转。

- `rookie-ui/src/types/api/system/log.ts` — 新建，定义 SysOperLogRecord / SysErrorLogRecord（对齐后端 SysOperLogVo / SysErrorLogVo，含 errorLogId / operLogId 关联字段）与分页查询参数类型
- `rookie-ui/src/api/system/log.ts` — 新建，封装操作日志 4 接口（list/详情/批量删除/清空）与错误日志 4 接口；批量删除走 `/{ids}` 逗号拼接路径参数对齐后端 @PathVariable Long[]
- `rookie-ui/src/constants/systemPermissions.ts` — 新增 operLog / errorLog 两个权限组（quarry/info/delete/clean，对齐 sys_log_menu_init.sql 的 perm_key）
- `rookie-ui/src/views/system/log/oper-log/config.ts` — 新建，操作日志筛选+表格字段配置；businessType/deviceType/status 均走字典系统（dictKey），requestMethod 为固定取值使用内置选项，无新增编辑表单字段
- `rookie-ui/src/views/system/log/oper-log/index.vue` — 新建操作日志页面：筛选分页、字典标签渲染、只读详情弹窗（ElDescriptions 展示请求参数/返回结果 JSON）、表格多选+批量删除、清空；失败行展示「错误日志」按钮跳转错误日志详情
- `rookie-ui/src/views/system/log/error-log/config.ts` — 新建，错误日志筛选+表格字段配置；sourceType 走字典系统（dictKey），无新增编辑表单字段
- `rookie-ui/src/views/system/log/error-log/index.vue` — 新建错误日志页面：筛选分页、字典标签渲染、只读详情弹窗（ElDescriptions + 完整堆栈 pre 展示）、表格多选+批量删除、清空；支持从操作日志页 query 携带 errorId 自动打开详情，请求来源错误日志详情弹窗内提供「查看操作日志」反向跳转
- `doc/devlog.md` — 追加本条开发日志
- `rookie-system/src/main/java/com/rookie/system/controller/SysLogTestController.java` — 临时新建错误日志测试接口 GET /sys/logTest/error?type=business|unknown，带 @Log 触发 ServiceException/RuntimeException 验证错误日志落库与 oper_log_id 关联链路；验证通过后已删除（净效果为零，仅作记录）

### 14:20 — 日志详情弹窗 UI 优化

承接日志管理前端页面，重构操作日志与错误日志的只读详情弹窗：弃用边框式 ElDescriptions，改用连贯定义表（dl/dt/dd 共享细边线、单容器统一圆角收边）+ 标题栏徽标聚合 + 左侧色条提示条跳转入口 + 可复制代码块，让信息层级克制连贯、深浅模式协调，并统一两个页面的详情视觉语言。

- `rookie-ui/src/views/system/log/components/LogCodeBlock.vue` — 新建，日志详情弹窗复用的代码/长文本块；等宽字体展示 + 限高滚动 + 一键复制（空内容不渲染复制按钮，复制态主色高亮短切反馈）
- `rookie-ui/src/views/system/log/components/LogDetailField.vue` — 过程中临时新建的「标签+值」信息单元，后因独立成盒的碎片感回调改回连贯表格方案，已删除（净效果为零，仅作记录）
- `rookie-ui/src/views/system/log/oper-log/index.vue` — 详情弹窗重构：标题栏聚合模块标题与业务类型/状态徽标；基础信息改用连贯定义表（标签列固定 140px、请求地址/方法等长字段整行等宽横向滚动）；失败操作以左侧色条提示条形式给出「查看错误日志」入口；请求参数/返回结果改用 LogCodeBlock，支持复制
- `rookie-ui/src/views/system/log/error-log/index.vue` — 详情弹窗重构：标题栏聚合错误来源徽标；基础信息/异常类型/异常消息改用连贯定义表；请求来源错误以提示条给出「查看操作日志」入口；完整堆栈改用 LogCodeBlock，支持复制
- `doc/devlog.md` — 追加本条开发日志

### 15:10 — 登录页「记住本次登录」移除与「忘记密码」兜底提示

按 token 后端自带过期时间、前端切换存储范围无意义，以及账号回填本地浏览器存在安全隐患的判断，删除登录页「记住本次登录」复选框；并因后端无任何找回/重置密码接口、无邮件短信通道，将「忘记密码」做成前端兜底提示，避免引导用户进入提交后会报错的自助找回表单。

- `rookie-ui/src/views/login.vue` — 删除 `form.remember` 字段与「记住本次登录」复选框及相关样式；`__options` 容器由两端对齐改为右对齐保留忘记密码按钮原视觉位置；新增 `handleForgotPassword`，点击「忘记密码」弹出 ElMessageBox 提示"本系统暂未开放自助找回密码通道，请联系系统管理员重置密码"
- `doc/devlog.md` — 追加本条开发日志

### 16:00 — 系统首页落地页重构

重构 dashboard 落地页第一个欢迎卡片，并解决「快捷入口 / 模块概览」一栏内容偏多、另一栏偏少的失衡问题。

- `rookie-ui/src/views/dashboard/index.vue` — hero 欢迎区由「左侧文案 + 右排两个散落身份盒」收敛为「问候语（按时段动态生成） + 一行 pill 账户信息」，结构更克制有重心；移除原冗长约落地页定位的描述段；快捷入口不再从菜单再取一遍以避免与模块概览抢同一批项，改为收拢 3 个前端闭环的高频动作（个人中心/刷新概览/退出登录）；模块概览改为展示全部一级模块且不再截断到 6 个，每项带模块图标 + 页面入口数；两栏改用统一「小卡 + auto-fill minmax(160px,1fr) 网格」渲染，项多则多排、项少则少排，密度天然对齐；删除底部「使用提示」冗余文案卡；KPI 概览卡图标开关并补深浅变量一致；`vue-tsc` type-check 通过
- `doc/devlog.md` — 追加本条开发日志

### 16:40 — README 拆分为对外展示版与开发文档版

为兼顾远程仓库对外展示与开发协作，将根目录与 rookie-ui 内的 README 拆为对外精简 README 与开发文档 README.dev.md 两份。

- `README.md` → `README.dev.md`（根）—原 README 经 `git mv` 重命名后，又因 dev 文档不进 git 的约定改用 `git rm --cached -f` 移出索引并加入 `.gitignore`（本地文件保留）；标题改「开发文档」并在文档说明段补充与对外 README 的关系；仓库结构树、doc 整理约定、graphify 阅读顺序里的自指改指向 `README.dev.md`
- `rookie-ui/README.md` → `rookie-ui/README.dev.md`（前端）—同上流程移出 git 索引并加入 `.gitignore`（本地文件保留）；第 12 节自指「本 README」改为「本 `README.dev.md`」
- `.gitignore` —「Local documentation scratch」段下新增 `/README.dev.md` 与 `/rookie-ui/README.dev.md` 两行，使两份 dev 文档被 git 忽略、不进仓库历史
- `README.md`（根，新建·进仓库）—新写对外展示版 README：项目简介、功能特性、技术栈、仓库结构、快速开始（后端编译启动 + 前端 dev/build/type-check）、JWT 鉴权约定（Token 头无 Bearer）、文档指引表指向 README.dev.md 与 rookie-ui/README.dev.md；不含 DB 密码等敏感信息
- `doc/devlog.md` — 追加本条开发日志

### 17:00 — 项目版本统一升级到 1.0.0

将全仓后端 Maven 多模块与前端工程的项目版本从 0.0.1-SNAPSHOT / 0.0.0 统一升到 1.0.0。

- `pom.xml`（根）—`<version>0.0.1-SNAPSHOT</version>` 改为 `1.0.0`；`<rookie.version>0.0.1-SNAPSHOT</rookie.version>` 改为 `1.0.0`（dependencyManagement 用该属性统一管理模块间依赖版本，子模块自动同步）
- `rookie-admin/pom.xml` / `rookie-common/pom.xml` / `rookie-framework/pom.xml` / `rookie-system/pom.xml` — `<parent>` 中 `<version>0.0.1-SNAPSHOT</version>` 改为 `1.0.0`，与根 pom 版本一致以正确解析父项目
- `rookie-ui/package.json` — `version` 由 `0.0.0` 改为 `1.0.0`，与后端项目版本对齐
- `doc/devlog.md` — 追加本条开发日志

- `rookie-system/src/main/java/com/rookie/system/controller/SysLogTestController.java` — 临时新建错误日志测试接口 GET /sys/logTest/error?type=business|unknown，带 @Log 触发 ServiceException/RuntimeException 验证错误日志落库与 oper_log_id 关联链路；验证通过后已删除（净效果为零，仅作记录）

### 17:40 — 后端鉴权补齐：业务接口 @PreAuthorize + admin 直通兜底

此前除 `SysOperLogController`/`SysErrorLogController` 已加 `@PreAuthorize` 外，用户/角色/菜单/字典/字典数据/通知/通知分组 7 个业务 controller 共约 43 个接口无任何鉴权，任意登录用户即可增删改用户、角色、菜单、字典，属严重缺口；同时 `UserDetailServiceImpl` 留有「设置管理员 admin 获取全部权限」的 TODO，admin 全权限仅靠 `*_admin.sql` 逐菜单授权维护、缺兜底。本次补齐 endpoint 级鉴权并实现 admin 直通。

- `rookie-framework/src/main/java/com/rookie/framework/security/mapper/UserInfoMapper.java` + `rookie-framework/src/main/resources/mapper/security/UserInfoMapper.xml` — 新增 `selectAllPermKey()`：查询 `sys_menu` 中启用且未删除的按钮型权限（`perm_key LIKE 'system:%:%'`），供 admin 直通兜底加载
- `rookie-framework/src/main/java/com/rookie/framework/security/service/UserDetailServiceImpl.java` — 移除原 TODO；加载角色后判断是否含启用且 `roleKey=='admin'` 的角色，若是则 `selectAllPermKey()` 全量加载按钮权限（admin 直通），否则走原「角色 → 已启用角色的 menuId → perm_key」常规链路；补注释说明两条加载路径的数据流
- `rookie-system/.../controller/SysUserController.java`（6 接口）、`SysRoleController.java`（7 接口）、`SysMenuController.java`（6 接口）、`SysDictController.java`（5 接口）、`SysDictDataController.java`（5 接口，`/type/{dictKey}` 公共读取不加）、`SysNoticeController.java`（7 接口，`/my`、`/read`、`/confirm` 个人向不加）、`SysNoticeGroupController.java`（7 接口，加/删成员均用 `system:noticeGroup:member`）— 每个方法加 `@PreAuthorize("hasAuthority('system:<module>:<action>')")`，`perm_key` 与 `sys_menu_init.sql`/`sys_notice_menu.sql` 现网数据一致；用户状态接口 DB 无独立 `system:user:status`，复用 `system:user:edit`
- `doc/api.md` — 接口更新日志新增 2026-06-28 一条，说明本次鉴权补齐范围与 401/403 返回约定
- `doc/devlog.md` — 追加本条开发日志

### 21:25 — 抽导数据库为单一初始化脚本 rookie.sql，清理旧 init 脚本

此前 sql/ 目录散落 11 个分场景建表/初始化脚本（菜单、字典、通知、日志各一批 + admin 授权），既与现网库结构易产生漂移、也难一次性落地。本次由当前运行库抽导为单一脚本，保留菜单/字典/角色/用户角色关联等关键数据，业务运行期产生的日志/通知数据只留表结构。

- `sql/rookie.sql`（新建）— 由 `mysqldump --no-data` 出 14 张表 DDL（每表带 `DROP TABLE IF EXISTS`），再以 `--no-create-info --skip-extended-insert` 导出关键数据 INSERT 合并而成；头部含 `CREATE DATABASE IF NOT EXISTS rookie` + `SET FOREIGN_KEY_CHECKS=0/1` 包裹；保留 `sys_menu`(56) / `sys_dict`(11) / `sys_dict_data`(37) / `sys_role`(admin+visitor 2) / `sys_role_menu`(67) / `sys_user`(仅 admin#1 + rookie#2) / `sys_user_role`(仅 (1,1)+(2,5)) 全量数据；`sys_notice` / `sys_notice_group` / `sys_notice_group_member` / `sys_notice_group_rel` / `sys_notice_read` / `sys_oper_log` / `sys_error_log` 七张表只保留表结构不导数据；用户密码为 BCrypt 哈希原样保留
- 验证：sed 替换库名到 `rookie_test_dump` 临时库整脚本执行零报错，校验 14 表 + 关键数据量与预期吻合后删除临时库
- 删除旧的 11 个脚本：`dict.sql` / `sys_error_log.sql` / `sys_log_dict_init.sql` / `sys_log_menu_admin.sql` / `sys_log_menu_init.sql` / `sys_menu_init.sql` / `sys_notice.sql` / `sys_notice_menu.sql` / `sys_notice_menu_admin.sql` / `sys_oper_log.sql` / `sys_role_menu_admin.sql`
- `README.md` / `README.dev.md` —仓库结构树与快速开始段把「sql/ 下脚本」描述改为「单文件 `rookie.sql`：建库 + 14 张表 + 关键数据初始化」
- `doc/api.md` — 鉴权补齐条目中 `sql/sys_menu_init.sql`、`sys_notice_menu.sql` 旧路径引用改为 `sql/rookie.sql` 的 `sys_menu` 现网数据
- `doc/devlog.md` — 追加本条开发日志

> 历史日志条目中提及的旧脚本文件名（如 `sys_menu_init.sql`、`sys_notice_menu_admin.sql` 等）保留原样，作为当时事实记录，不改写

### 12:12 — 定时任务白名单改为配置驱动

为支持下游 knowhub 将自身 `com.knowhub.task` 包中的任务纳入 rookie 定时任务管理，将调度器原本写死的单一白名单改为配置项驱动的多前缀列表，同时保持默认配置下的原有行为。

- `rookie-system/src/main/java/com/rookie/system/scheduler/SysJobScheduler.java` — 新增 `task.bean-package-prefixes` 配置注入；保存前校验和执行期校验统一按白名单前缀列表判断
- `rookie-admin/src/main/resources/application.yml` — 增加任务白名单配置，默认保留 `com.rookie.system.task`；使用逗号分隔单行格式以兼容 `@Value` 的 `List<String>` 注入
- `rookie-ui/src/views/system/job/config.ts` — 更新任务 Bean 白名单 placeholder，改为提示遵循配置项范围
- `doc/devlog.md` — 追加本条开发日志
