-- =============================================================================
-- 注册功能配置项（系统设置 sys_config 增量插入）
-- -----------------------------------------------------------------------------
-- 用途：
--   为「用户自助注册」功能提供注册开关配置项：
--     sys.user.registerEnabled（BOOLEAN，默认 false 关闭）
--   后端 POST /register 强校验该开关（关闭即拒绝），前端登录页/注册页按该值
--   显隐注册入口（公共读取接口 GET /sys/system-config/configKey/{configKey}）。
--
-- 说明（本文件为 rookie 与二开项目通用增量脚本）：
--   1. 不包含 config_id 主键列，由 AUTO_INCREMENT 自增，避免与各项目已有数据主键冲突；
--   2. 幂等依赖 sys_config.config_key 唯一键 uk_config_key + ON DUPLICATE KEY UPDATE，
--      可重复执行不会产生重复行；
--   3. 与 sql/sys_config.sql 中已有的同 key 行（config_id=2）幂等共存：
--      重复执行仅按本脚本更新值，不影响其他配置项；
--   4. 前置条件：需先执行 sql/sys_config.sql 建好 sys_config 表。
-- =============================================================================

SET NAMES utf8mb4;

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('sys.user.registerEnabled', '是否开启自助注册', 'false', 'BOOLEAN', 1, 'true 开启登录页自助注册，false 关闭', 1, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `config_name`  = VALUES(`config_name`),
  `config_value` = VALUES(`config_value`),
  `value_type`   = VALUES(`value_type`),
  `is_system`    = VALUES(`is_system`),
  `remark`       = VALUES(`remark`),
  `status`       = VALUES(`status`);
