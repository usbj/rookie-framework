-- =============================================================================
-- 菜单排序字段（sys_menu.sort）增量脚本
-- -----------------------------------------------------------------------------
-- 背景：
--   sys_menu 原表没有排序字段，菜单/目录展示顺序只能依赖插入顺序（menu_id），
--   菜单管理页也无法调整顺序。本脚本新增 sort 列：
--     - 排序规则：同一 parent 下按 sort 升序（越小越靠前），sort 相同按 menu_id 兜底；
--     - 菜单管理页「排序」输入框可调整（保存后刷新侧边栏即生效）。
--
-- 本脚本包含：
--   1. 给 sys_menu 增加 sort 列（int NOT NULL DEFAULT 0，幂等：
--      通过 information_schema 判断列是否已存在，已存在则跳过）
--   2. 初始化：存量数据 sort 归零（保持当前 menu_id 顺序，由兜底排序保证不变）
--
-- 通用化约定：rookie 与二开项目均可重复执行。
-- =============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 增加 sort 列（列已存在时跳过，避免重复 ALTER 报错）
-- ----------------------------------------------------------------------------
SET @sort_col_exists := (
  SELECT COUNT(*)
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'sys_menu'
    AND `COLUMN_NAME` = 'sort'
);
SET @ddl := IF(@sort_col_exists = 0,
  'ALTER TABLE `sys_menu` ADD COLUMN `sort` int NOT NULL DEFAULT 0 COMMENT ''排序（越小越靠前，同级内生效）'' AFTER `icon`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. 初始化：存量数据归零（顺序不变，由 order by sort, menu_id 兜底保证）
-- ----------------------------------------------------------------------------
UPDATE `sys_menu` SET `sort` = 0 WHERE `sort` IS NULL OR `sort` = 0;
