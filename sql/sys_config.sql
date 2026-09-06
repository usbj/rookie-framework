-- =============================================================================
-- 系统设置（system_config）模块数据库脚本
-- -----------------------------------------------------------------------------
-- 背景：
--   系统设置为键值形式，一个设置项对应一个值，值类型由 value_type 标识
--   （STRING / BOOLEAN / NUMBER / JSON）。数据库为唯一源，Redis 为永久缓存副本，
--   工具类 SysConfigUtil 只读缓存，启动时由 SysConfigWarmUpRunner 预热。
--
-- 本脚本包含：
--   1. 建表 sys_config（键值型设置项表，继承审计四列，不软删除，靠 is_system 保护内置项）
--   2. 内置设置项初始化数据（is_system=1，代码可能硬依赖的项，禁止删除/改键/改类型/停用）
--   3. 值类型字典 sys_config_value_type 及其字典数据（驱动前端 valueType 标签与下拉）
--   4. 系统设置菜单与按钮权限点（menu_id 69~74，挂在系统模块 menu_id=1 下）
--   5. admin 角色（role_id=1）菜单授权
--
-- 幂等：建表用 DROP TABLE IF EXISTS；字典/菜单/授权均用 INSERT ... ON DUPLICATE KEY UPDATE
--       或 INSERT ... SELECT ... WHERE NOT EXISTS，可重复执行。
-- 编号：现有 menu_id 最大 62（dict-data-permission.sql 预留 63~68），本脚本从 69 开始。
--       dict_id 从 12 开始（现有 1~11），dict_data_id 从 49 开始（现有最大 48）。
-- =============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 建表 sys_config
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `config_id`    bigint        NOT NULL AUTO_INCREMENT COMMENT '设置项主键',
  `config_key`   varchar(128)  NOT NULL                COMMENT '设置键（业务唯一，推荐「模块.子项.用途」点号分层，如 sys.user.initPassword）',
  `config_name`  varchar(128)  NOT NULL                COMMENT '设置项名称（展示用）',
  `config_value` varchar(2000) DEFAULT NULL            COMMENT '设置值（按 value_type 解释；JSON 类型时存 JSON 字符串）',
  `value_type`   varchar(16)   NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING 字符串 BOOLEAN 布尔 NUMBER 数值 JSON 对象/数组',
  `is_system`    tinyint       NOT NULL DEFAULT '0'    COMMENT '是否系统内置：0否 1是（内置项不可删、key 与类型不可改、不可停用，仅可改值/名称/备注）',
  `remark`       varchar(255)  DEFAULT NULL            COMMENT '备注说明',
  `status`       int           NOT NULL DEFAULT '1'    COMMENT '状态：1启用 0停用（停用后工具类读取回落默认值）',
  `create_by`    varchar(64)   NOT NULL                COMMENT '创建人',
  `create_time`  datetime      NOT NULL                COMMENT '创建时间',
  `update_by`    varchar(64)   NOT NULL                COMMENT '更新人',
  `update_time`  datetime      NOT NULL                COMMENT '更新时间',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_config_status` (`status`),
  KEY `idx_config_is_system` (`is_system`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统设置表（键值型，Redis 缓存）';

-- ----------------------------------------------------------------------------
-- 2. 内置设置项初始化数据（is_system=1）
--    这些项为系统基础配置示例，代码可能硬依赖，受内置项保护规则约束。
-- ----------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_id`, `config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1, 'sys.user.initPassword',   '用户初始密码',     '123456',  'STRING',  1, '新建用户与重置密码时的初始密码',           1, 'admin', NOW(), 'admin', NOW()),
  (2, 'sys.user.registerEnabled','是否开启自助注册', 'false',   'BOOLEAN', 1, 'true 开启登录页自助注册，false 关闭',       1, 'admin', NOW(), 'admin', NOW()),
  (3, 'sys.notice.keepDays',     '通知保留天数',     '90',      'NUMBER',  1, '超过该天数的已读通知可被清理任务回收',     1, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `config_name`  = VALUES(`config_name`),
  `config_value` = VALUES(`config_value`),
  `value_type`   = VALUES(`value_type`),
  `is_system`    = VALUES(`is_system`),
  `remark`       = VALUES(`remark`),
  `status`       = VALUES(`status`);

-- ----------------------------------------------------------------------------
-- 3. 值类型字典 sys_config_value_type（驱动前端 valueType 标签与下拉）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_dict` (`dict_id`, `dict_name`, `dict_key`, `status`, `remake`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES (22, '设置值类型', 'sys_config_value_type', 1, '系统设置项的值类型', NOW(), 'admin', NOW(), 'admin')
ON DUPLICATE KEY UPDATE
  `dict_name` = VALUES(`dict_name`),
  `status`    = VALUES(`status`),
  `remake`    = VALUES(`remake`);

INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_id`, `dict_key`, `dict_data_label`, `dict_data_value`, `remark`, `dict_data_sort`, `tag_type`, `tag_effect`, `css_class`, `ext_json`, `is_default`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES
  (91, 22, 'sys_config_value_type', '字符串', 'STRING',  '普通字符串',    1, 'primary', 'light', '', NULL, '0', 1, NOW(), 'admin', NOW(), 'admin'),
  (92, 22, 'sys_config_value_type', '布尔',   'BOOLEAN', 'true/false 开关', 2, 'success', 'light', '', NULL, '0', 1, NOW(), 'admin', NOW(), 'admin'),
  (93, 22, 'sys_config_value_type', '数值',   'NUMBER',  '整数数值',      3, 'warning', 'light', '', NULL, '0', 1, NOW(), 'admin', NOW(), 'admin'),
  (94, 22, 'sys_config_value_type', 'JSON',   'JSON',    '对象或数组',    4, 'info',    'light', '', NULL, '0', 1, NOW(), 'admin', NOW(), 'admin')
ON DUPLICATE KEY UPDATE
  `dict_id`          = VALUES(`dict_id`),
  `dict_key`         = VALUES(`dict_key`),
  `dict_data_label`  = VALUES(`dict_data_label`),
  `dict_data_value`  = VALUES(`dict_data_value`),
  `remark`           = VALUES(`remark`),
  `dict_data_sort`   = VALUES(`dict_data_sort`),
  `tag_type`         = VALUES(`tag_type`),
  `tag_effect`       = VALUES(`tag_effect`),
  `status`           = VALUES(`status`);

-- ----------------------------------------------------------------------------
-- 4. 系统设置菜单与按钮权限点（menu_id 69~74，挂在系统模块 menu_id=1 下）
--    69 系统设置（二级菜单）   system:systemConfig
--    70 系统设置查询           system:systemConfig:quarry
--    71 系统设置详情           system:systemConfig:info
--    72 系统设置新增           system:systemConfig:add
--    73 系统设置修改           system:systemConfig:edit
--    74 系统设置删除           system:systemConfig:delete
--    75 系统设置刷新缓存       system:systemConfig:refresh
-- ----------------------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
VALUES
  (93, '系统设置',     'system:systemConfig',          1,  2, 'system-config', 0, '/system/system-config/index', 'Setting', 1, 'admin', NOW(), 'admin', NOW(), 0),
  (94, '系统设置查询', 'system:systemConfig:quarry',   93, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (95, '系统设置详情', 'system:systemConfig:info',     93, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (96, '系统设置新增', 'system:systemConfig:add',      93, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (97, '系统设置修改', 'system:systemConfig:edit',     93, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (98, '系统设置删除', 'system:systemConfig:delete',   93, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (99, '系统设置刷新', 'system:systemConfig:refresh',  93, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0)
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `perm_key`  = VALUES(`perm_key`),
  `parent_id` = VALUES(`parent_id`),
  `menu_type` = VALUES(`menu_type`),
  `route`     = VALUES(`route`),
  `path`      = VALUES(`path`),
  `icon`      = VALUES(`icon`),
  `status`    = VALUES(`status`),
  `delete`    = VALUES(`delete`);

-- ----------------------------------------------------------------------------
-- 5. admin 角色（role_id=1）菜单授权（69~75）
-- ----------------------------------------------------------------------------
# INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
# SELECT 1, m.menu_id
# FROM `sys_menu` m
# WHERE m.menu_id IN (69, 70, 71, 72, 73, 74, 75)
#   AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);
