-- =============================================================
-- 通知下拉懒加载测试数据（20 条已发布通知）
-- 使用场景：验证通知下拉「分页 + 滚动加载更多 + 未读徽标 + 置顶排序」
-- 适用账号：admin（user_id=1），全部 publish_scope='ALL'，登录即可见
-- 数据构成：
--   * 20 条全部 PUBLISHED，发布时间从近到远错开（前 10 条集中，第 11 条起每天 1 条）
--   * 2 条置顶（is_top=1，测试置顶恒排最前，不随分页错位）
--   * 3 条预置已读记录（第 3 / 5 / 12 条，admin 已读），其余 17 条未读 → 铃铛徽标应显示 17
--   * 1 条 need_confirm=1（第 6 条，测试详情弹窗确认按钮）
-- 可重复执行：脚本先清理本脚本产生的旧数据再插入
-- 测试完清理（或直接重跑本脚本）：DELETE FROM sys_notice WHERE title LIKE '【懒加载测试】%';
-- =============================================================

-- 1) 清理上次执行产生的测试数据（先已读记录，后通知主体）
DELETE FROM sys_notice_read
WHERE user_id = 1
  AND notice_id IN (SELECT notice_id FROM sys_notice WHERE title LIKE '【懒加载测试】%');

DELETE FROM sys_notice
WHERE title LIKE '【懒加载测试】%';

-- 2) 插入 20 条已发布通知
INSERT INTO sys_notice
(`title`, `content`, `notice_type`, `level`, `publish_scope`, `status`, `is_top`, `need_confirm`, `publish_time`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
VALUES
('【懒加载测试】置顶·系统停机维护公告',
 '## 系统停机维护公告\n\n因机房电力改造，系统将于本周六 **00:00 - 06:00** 停机维护，期间无法访问，请提前保存工作内容。\n\n- 影响范围：全部业务模块\n- 预计恢复：周六 06:00\n- 如有疑问请联系运维组',
 'NOTICE', 'URGENT', 'ALL', 'PUBLISHED', 1, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), '置顶公告：测试置顶恒排最前', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】置顶·国庆假期值班安排',
 '## 国庆假期值班安排\n\n请各部门按《值班表》安排人员值守，值班期间保持手机畅通。\n\n| 日期 | 部门 | 值班人 |\n| --- | --- | --- |\n| 10-01 | 运维部 | 张三 |\n| 10-02 | 运维部 | 李四 |',
 'NOTIFY', 'IMPORTANT', 'ALL', 'PUBLISHED', 1, 0, DATE_SUB(NOW(), INTERVAL 47 HOUR), '置顶通知：测试置顶 + 表格渲染', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】v1.0.0 版本发布说明',
 '## v1.0.0 版本发布说明\n\n本次版本正式发布，新增以下能力：\n\n1. 消息通知懒加载：下拉滚动加载，不再一次拉全量\n2. 独立未读计数：铃铛徽标走专用统计接口\n3. 更多稳定性优化与缺陷修复\n\n请各业务线在 **本周内** 完成验证。',
 'NOTIFY', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 10 MINUTE), '已读：版本发布', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】账号密码策略调整提醒',
 '## 账号密码策略调整提醒\n\n自下月起，密码复杂度要求调整为：\n\n- 至少 10 位\n- 必须包含大小写字母与数字\n- 每 90 天强制更换一次\n\n请提前修改密码，避免影响登录。',
 'REMIND', 'IMPORTANT', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 25 MINUTE), '未读：密码策略', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】本周五例会通知',
 '## 本周五例会通知\n\n本周五 **14:00** 在 3 楼大会议室召开全员例会，议程如下：\n\n- 上季度工作总结\n- 下季度目标拆解\n- 自由讨论\n\n请准时参加，如有事假请提前在 OA 提交。',
 'NOTIFY', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 40 MINUTE), '已读：例会通知', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】数据库备份任务确认单',
 '## 数据库备份任务确认单\n\n本周日凌晨的数据库全量备份已完成，请 **确认本次备份结果**：\n\n- 备份节点：192.168.1.10\n- 备份大小：1.2 GB\n- 校验状态：成功\n\n本条为需确认通知，请在详情弹窗点击「确认」。',
 'NOTIFY', 'IMPORTANT', 'ALL', 'PUBLISHED', 0, 1, DATE_SUB(NOW(), INTERVAL 55 MINUTE), '需确认：备份确认单', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】安全补丁更新公告',
 '## 安全补丁更新公告\n\n本周发布安全补丁 **CVE-2026-0817**，修复一处高危漏洞，请各环境尽快完成更新：\n\n1. 测试环境：周二前\n2. 预发环境：周三前\n3. 生产环境：周五前',
 'NOTICE', 'URGENT', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 70 MINUTE), '未读：安全补丁', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】新员工入职指引',
 '## 新员工入职指引\n\n欢迎新同事加入！入职第一天请完成以下事项：\n\n- 领取工牌与门禁卡（前台）\n- 开通企业邮箱与 OA 账号（IT 部）\n- 加入部门群并完成新人培训报名\n\n详细流程见《员工手册》第三章。',
 'NOTIFY', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 85 MINUTE), '未读：入职指引', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】服务器迁移预告',
 '## 服务器迁移预告\n\n为提升访问速度，计划将核心业务服务器迁移至新机房：\n\n- 迁移窗口：下周五 22:00 - 次日 02:00\n- 期间服务降级为只读模式\n- 迁移完成后 IP 不变\n\n给您带来不便敬请谅解。',
 'NOTICE', 'IMPORTANT', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 100 MINUTE), '未读：迁移预告', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】季度绩效考核通知',
 '## 季度绩效考核通知\n\n本季度绩效考核将于下周一启动，请各团队负责人：\n\n1. 完成组内成员自评收集\n2. 组织绩效面谈\n3. 周五前提交考核表\n\n考核标准见 OA 文档中心。',
 'NOTIFY', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 115 MINUTE), '未读：季度考核', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】机房巡检安排（第 11 条：跨页边界）',
 '## 机房巡检安排\n\n本月机房巡检计划如下：\n\n- 巡检人：王五\n- 巡检范围：A/B/C 三间机房\n- 巡检项：温湿度、UPS、消防设备\n\n巡检记录需当日在系统中提交。',
 'NOTICE', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 130 MINUTE), '未读：第 11 条，滚动加载第二页的边界', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】版本回滚演练总结',
 '## 版本回滚演练总结\n\n上周五的回滚演练已完成，演练结论如下：\n\n- 回滚耗时：平均 12 分钟\n- 成功率：100%\n- 待改进：回滚脚本日志增强\n\n感谢各团队配合。',
 'NOTIFY', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 145 MINUTE), '已读：回滚演练', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】值班表调整提醒',
 '## 值班表调整提醒\n\n因人员变动，下周值班表已调整，请相关同事留意：\n\n- 周一至周三：赵六\n- 周四至周日：钱七\n\n如有冲突请及时联系行政部。',
 'REMIND', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 160 MINUTE), '未读：值班调整', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】会议室预定规则更新',
 '## 会议室预定规则更新\n\n自即日起，会议室预定规则调整如下：\n\n- 单次预定最长 4 小时\n- 提前 24 小时可取消\n- 超时未使用将自动释放\n\n请按新规则预定，避免资源浪费。',
 'NOTICE', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 175 MINUTE), '未读：会议室规则', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】API 文档更新通知',
 '## API 文档更新通知\n\n开放平台 API 文档已更新至 v2.3，主要变更：\n\n- 新增 `/v2/orders/batch` 批量查询接口\n- 废弃旧版 `/v1/orders` 翻页参数\n- 补充限流与错误码说明\n\n对接方请尽快升级。',
 'NOTIFY', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 190 MINUTE), '未读：API 文档', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】日志数据清理提醒',
 '## 日志数据清理提醒\n\n系统操作日志保留 180 天，近期将执行一次归档清理：\n\n- 清理范围：2025 年及以前的操作日志\n- 清理方式：导出归档后物理删除\n- 预计影响：无\n\n如有历史追溯需求请提前导出。',
 'REMIND', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 205 MINUTE), '未读：日志清理', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】前端框架升级培训报名',
 '## 前端框架升级培训报名\n\n下周开展 Vue3 工程化专题培训，欢迎报名：\n\n- 时间：周三 09:30 - 17:00\n- 地点：培训教室 2\n- 名额：40 人，报满即止\n\n报名截止：本周四 18:00。',
 'NOTICE', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 220 MINUTE), '未读：培训报名', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】运维工单填写规范',
 '## 运维工单填写规范\n\n为提高工单流转效率，请按以下规范填写：\n\n1. 标题注明系统与问题类型\n2. 描述附复现步骤与截图\n3. 紧急工单需电话同步值班人\n\n不规范工单将被退回。',
 'NOTIFY', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 235 MINUTE), '未读：工单规范', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】下月版本规划公告',
 '## 下月版本规划公告\n\n下月版本计划已评审通过，核心内容：\n\n- 移动端适配优化\n- 报表中心全新改版\n- 消息中心站内信升级\n\n详细排期见项目计划表。',
 'NOTICE', 'NORMAL', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 250 MINUTE), '未读：版本规划', 'admin', NOW(), 'admin', NOW(), 0),
('【懒加载测试】账号安全自查提醒',
 '## 账号安全自查提醒\n\n请各同事完成一次账号安全自查：\n\n- 检查是否使用弱密码\n- 检查是否开启二次验证\n- 检查异地登录记录\n\n如发现异常请立即联系安全组。',
 'REMIND', 'IMPORTANT', 'ALL', 'PUBLISHED', 0, 0, DATE_SUB(NOW(), INTERVAL 265 MINUTE), '未读：安全自查（第 20 条，列表末尾）', 'admin', NOW(), 'admin', NOW(), 0);

-- 3) 给 admin（user_id=1）预置 3 条已读记录（对应第 3 / 5 / 12 条）
INSERT INTO sys_notice_read (`notice_id`, `user_id`, `read_time`, `confirm_status`, `confirm_time`)
SELECT notice_id, 1, NOW(), 0, NULL
FROM sys_notice
WHERE title IN (
    '【懒加载测试】v1.0.0 版本发布说明',
    '【懒加载测试】本周五例会通知',
    '【懒加载测试】版本回滚演练总结'
)
  AND `delete` = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_notice_read r
      WHERE r.notice_id = sys_notice.notice_id AND r.user_id = 1
  );
