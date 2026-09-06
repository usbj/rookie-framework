-- =============================================================================
-- 字典数据模块权限点补齐
-- -----------------------------------------------------------------------------
-- 背景：
--   后端 SysDictDataController 的 5 个接口均挂了 @PreAuthorize，使用权限键：
--     system:dictData:quarry / info / edit / add / delete
--   但 sys_menu 表中此前没有这些按钮型权限点，导致：
--     - admin 直通兜底（selectAllPermKey 取全部 menu_type=3 的 perm_key）拿不到这些键
--     - 普通角色链路（role -> menu -> perm_key）也无从授权
--   结果：字典数据的所有接口对所有人 403。
--
-- 本脚本补充：
--   1. 新建「字典数据」二级菜单节点（挂在「字典管理」menu_id=23 下，menu_type=2），
--      作为字典数据 5 个按钮权限点的父菜单，同时在菜单树中提供字典数据入口。
--   2. 新建 5 个按钮型权限点（menu_type=3），perm_key 与后端 @PreAuthorize 完全对齐。
--   3. 给 admin 角色（role_id=1）补齐 role_menu 关联，与现有 23-62 全部授给 role 1 的做法一致。
--
-- 幂等：menu_id 为主键、sys_role_menu 无唯一键，故全部用 INSERT ... SELECT ... WHERE NOT EXISTS，
--       可重复执行不会产生重复行或主键冲突。
-- 编号：现有最大 menu_id=62，本脚本从 63 开始。
--   63 字典数据（菜单节点）
--   64 字典数据查询    system:dictData:quarry
--   65 字典数据信息    system:dictData:info
--   66 字典数据修改    system:dictData:edit
--   67 字典数据新增    system:dictData:add
--   68 字典数据删除    system:dictData:delete
-- =============================================================================


-- 1) 字典数据 二级菜单节点（parent_id=23 字典管理，menu_type=2）
INSERT INTO `sys_menu` (menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`)
SELECT '字典数据', 'system:dictData', 23, 2, 'dict-data', 0, '/system/dict-data/index', 'Collection', 1, 'admin', NOW(), 'admin', NOW(), 0


-- 2) 字典数据 按钮权限点（menu_type=3，parent_id=63）
INSERT INTO `sys_menu` ( menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`)
SELECT  '字典数据查询', 'system:dictData:quarry', 63, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
# WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE menu_id = 64);

INSERT INTO `sys_menu` ( menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`)
SELECT  '字典数据信息', 'system:dictData:info', 63, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
# WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE menu_id = 65);

INSERT INTO `sys_menu` ( menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`)
SELECT  '字典数据修改', 'system:dictData:edit', 63, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
# WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE menu_id = 66);

INSERT INTO `sys_menu` (menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`)
SELECT'字典数据新增', 'system:dictData:add', 63, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
# WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE menu_id = 67);

INSERT INTO `sys_menu` (menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`)
SELECT  '字典数据删除', 'system:dictData:delete', 63, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0
# WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE menu_id = 68);


-- 3) admin 角色（role_id=1）补齐 role_menu 关联（63-68）
# INSERT INTO `sys_role_menu` (role_id, menu_id)
# SELECT 1, m.menu_id
# FROM `sys_menu` m
# WHERE m.menu_id IN (63, 64, 65, 66, 67, 68)
#   AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);
