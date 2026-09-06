-- =============================================================================
-- 系统监控模块合并脚本（在线用户 + 定时任务 + 服务监控）
-- -----------------------------------------------------------------------------
-- 本文件合并自 sql/sys_online.sql + sql/sys_job.sql + sql/sys_monitor.sql
-- （旧文件已删除，本文件为其一体化替代），内容包含：
--   1. 建表 sys_job（任务表）与 sys_job_log（执行日志表）
--   2. 系统设置项 sys.online.timeout（在线判定阈值，分钟）
--   3. 完整菜单树（最终结构）：
--      「系统监控」目录（perm_key='monitor'）下，按序排列：
--        在线用户（system:online）   → 在线查询 / 在线强制下线
--        定时任务（system:job）      → 任务查询/详情/新增/修改/删除/启停/立即执行
--        执行日志（system:jobLog）   → 日志查询
--        服务监控（system:monitor）  → 监控查询
--
-- 通用化约定（rookie 与二开项目均可重复执行）：
--   - 菜单不写 menu_id（AUTO_INCREMENT 自增），避免与其他项目已有菜单主键冲突；
--   - sys_menu 表无 perm_key 唯一键，幂等靠 NOT EXISTS 判断 perm_key 是否已存在；
--   - 目录/菜单 parent_id 一律按 perm_key 反查，兼容其他项目菜单结构；
--   - 建表用 CREATE TABLE IF NOT EXISTS（不 DROP），老库重跑不丢任务数据；
--   - 末尾对已存在菜单做 parent 收敛（迁入系统监控目录）并清理遗留的
--     「任务管理」目录（job），保证新旧库任意顺序执行都收敛到最终结构。
--
-- 老库迁移说明：若已单独执行过早期的 sys_online.sql / sys_job.sql /
-- sys_monitor.sql / sys_monitor_catalog.sql，本文件重复执行无副作用；
-- 已手动调整过数据库的项目同样可重复执行本文件校验收敛。
-- =============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 建表 sys_job（定时任务表）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_job` (
  `job_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '任务主键',
  `job_name`        varchar(64)  NOT NULL                COMMENT '任务名称',
  `bean_name`       varchar(128) NOT NULL                COMMENT 'Spring Bean 名称（限定 com.rookie.system.task 包下）',
  `method_name`     varchar(128) NOT NULL                COMMENT '执行方法名（无参或单个 String 参数）',
  `cron_expression` varchar(64)  NOT NULL                COMMENT 'cron 表达式（Spring 6 段式）',
  `params`          varchar(500) DEFAULT NULL            COMMENT '执行参数（可选；方法为 String 单参时传入）',
  `status`          int          NOT NULL DEFAULT '1'    COMMENT '状态：1启用 0停用',
  `remark`          varchar(255) DEFAULT NULL            COMMENT '备注说明',
  `create_by`       varchar(64)  NOT NULL                COMMENT '创建人',
  `create_time`     datetime     NOT NULL                COMMENT '创建时间',
  `update_by`       varchar(64)  NOT NULL                COMMENT '更新人',
  `update_time`     datetime     NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`job_id`),
  KEY `idx_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务表';

-- ----------------------------------------------------------------------------
-- 2. 建表 sys_job_log（任务执行日志表）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_job_log` (
  `log_id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `job_id`        bigint       NOT NULL                COMMENT '任务主键',
  `job_name`      varchar(64)  NOT NULL                COMMENT '任务名称（冗余，任务删除后日志仍可读）',
  `trigger_type`  varchar(16)  NOT NULL                COMMENT '触发方式：AUTO 自动 MANUAL 手动',
  `invoke_target` varchar(255) NOT NULL                COMMENT '调用目标（bean.method）',
  `job_params`    varchar(500) DEFAULT NULL            COMMENT '执行参数（冗余）',
  `status`        int          NOT NULL                COMMENT '执行状态：0成功 1失败',
  `cost_time`     bigint       DEFAULT NULL            COMMENT '耗时（毫秒）',
  `exception_msg` varchar(2000) DEFAULT NULL           COMMENT '异常信息（失败时）',
  `execute_time`  datetime     NOT NULL                COMMENT '执行时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_log_job_id` (`job_id`),
  KEY `idx_log_execute_time` (`execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务执行日志表';

-- ----------------------------------------------------------------------------
-- 3. 系统设置项：在线判定阈值（增量插入，幂等依赖 uk_config_key）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('sys.online.timeout', '在线判定阈值（分钟）', '30', 'NUMBER', 1, '最后活跃时间超过该分钟数视为离线，与 token 有效期同口径', 1, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `config_name`  = VALUES(`config_name`),
  `config_value` = VALUES(`config_value`),
  `value_type`   = VALUES(`value_type`),
  `is_system`    = VALUES(`is_system`),
  `remark`       = VALUES(`remark`),
  `status`       = VALUES(`status`);

-- ----------------------------------------------------------------------------
-- 4. 「系统监控」目录（parent 反查系统模块 perm_key='system'）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '系统监控', 'monitor', m.menu_id, 1, 'monitor', 0, NULL, 'Odometer', 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'monitor' AND `menu_type` = 1 AND `delete` = 0);

-- ----------------------------------------------------------------------------
-- 5. 系统监控目录下的菜单与按钮权限点（按显示顺序：在线用户 → 定时任务 → 执行日志 → 服务监控）
-- ----------------------------------------------------------------------------

-- 5.1 在线用户（route=online → 组件 views/system/online/index.vue）
INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '在线用户', 'system:online', m.menu_id, 2, 'online', 0, '/system/online/index', 'Monitor', 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'monitor'
  AND m.menu_type = 1
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:online' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '在线查询', 'system:online:quarry', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:online'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:online:quarry' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '在线强制下线', 'system:online:kick', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:online'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:online:kick' AND `delete` = 0);

-- 5.2 定时任务（route=job → 组件 views/system/job/index.vue）
INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '定时任务', 'system:job', m.menu_id, 2, 'job', 0, '/system/job/index', 'Clock', 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'monitor'
  AND m.menu_type = 1
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '任务查询', 'system:job:quarry', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:job'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job:quarry' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '任务详情', 'system:job:info', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:job'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job:info' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '任务新增', 'system:job:add', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:job'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job:add' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '任务修改', 'system:job:edit', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:job'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job:edit' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '任务删除', 'system:job:delete', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:job'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job:delete' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '任务启停', 'system:job:status', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:job'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job:status' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '立即执行', 'system:job:run', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:job'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:job:run' AND `delete` = 0);

-- 5.3 执行日志（route=job-log → 组件 views/system/job-log/index.vue）
INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '执行日志', 'system:jobLog', m.menu_id, 2, 'job-log', 0, '/system/job-log/index', 'Document', 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'monitor'
  AND m.menu_type = 1
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:jobLog' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '日志查询', 'system:jobLog:quarry', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:jobLog'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:jobLog:quarry' AND `delete` = 0);

-- 5.4 服务监控（route=monitor → 组件 views/system/monitor/index.vue）
INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '服务监控', 'system:monitor', m.menu_id, 2, 'monitor', 0, '/system/monitor/index', 'Cpu', 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'monitor'
  AND m.menu_type = 1
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:monitor' AND `delete` = 0);

INSERT INTO `sys_menu` (`menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT '监控查询', 'system:monitor:quarry', m.menu_id, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
FROM `sys_menu` m
WHERE m.perm_key = 'system:monitor'
  AND m.delete = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'system:monitor:quarry' AND `delete` = 0);

-- ----------------------------------------------------------------------------
-- 6. 收敛：把已存在（老库迁移）的 4 个菜单 parent 修正为系统监控目录
-- ----------------------------------------------------------------------------
UPDATE `sys_menu` m
JOIN `sys_menu` d ON d.perm_key = 'monitor' AND d.menu_type = 1 AND d.delete = 0
SET m.parent_id = d.menu_id,
    m.update_time = NOW()
WHERE m.perm_key IN ('system:online', 'system:job', 'system:jobLog', 'system:monitor')
  AND m.delete = 0;

-- ----------------------------------------------------------------------------
-- 7. 清理：删除遗留的「任务管理」目录（job，其下子菜单已在上一步收敛挪走）
--    注意：子查询经派生表物化后再引用目标表，避免 MySQL
--    "You can't specify target table for update in FROM clause" 错误。
-- ----------------------------------------------------------------------------
DELETE FROM `sys_menu`
WHERE `perm_key` = 'job'
  AND `menu_type` = 1
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `parent_id` FROM `sys_menu` WHERE `parent_id` > 0 AND `delete` = 0) AS child_ref
    WHERE child_ref.parent_id = `sys_menu`.menu_id
  );
