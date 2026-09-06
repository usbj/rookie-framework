-- ============================================================================
-- rookie 基础管理系统 数据库初始化脚本
-- 生成方式：由当前运行库抽导，保留菜单 / 字典 / 角色 / 用户角色关联等关键数据
-- 用户仅保留两个：admin(user_id=1, 超级管理员) 与 rookie(user_id=2, 普通用户)
-- 角色：admin(role_id=1) + visitor(role_id=5) 全保留
-- 通知 / 通知分组 / 通知分组关联 / 通知阅读 / 操作日志 / 错误日志 七张表
--   仅保留表结构，业务运行期产生的数据不初始化
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 库与编码
-- ----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `rookie` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `rookie`;

-- ----------------------------------------------------------------------------
-- 建表 DDL（14 张表）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(255) NOT NULL COMMENT '字典名称',
  `dict_key` varchar(255) NOT NULL COMMENT '字典类型',
  `status` int NOT NULL DEFAULT '1' COMMENT '字典状态',
  `remake` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_by` varchar(255) NOT NULL,
  `update_time` datetime NOT NULL,
  `update_by` varchar(255) NOT NULL,
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_id` (`dict_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
  `dict_data_id` bigint NOT NULL AUTO_INCREMENT,
  `dict_id` bigint NOT NULL COMMENT '数据所属表格',
  `dict_key` varchar(255) NOT NULL COMMENT '所属字典名称',
  `dict_data_label` varchar(255) NOT NULL COMMENT '数据名称标签',
  `dict_data_value` varchar(255) NOT NULL COMMENT '数据真值',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `dict_data_sort` tinyint NOT NULL DEFAULT '1' COMMENT '数据排序',
  `tag_type` varchar(20) DEFAULT NULL COMMENT '标签类型',
  `tag_effect` varchar(20) DEFAULT NULL COMMENT '标签视觉效果',
  `css_class` varchar(64) DEFAULT NULL COMMENT '自定义css类名',
  `ext_json` json DEFAULT NULL COMMENT '拓展json',
  `is_default` char(1) DEFAULT '0' COMMENT '是否为默认显示数据(1是,0否)',
  `status` int NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL,
  `create_by` varchar(255) NOT NULL,
  `update_time` datetime NOT NULL,
  `update_by` varchar(255) NOT NULL,
  PRIMARY KEY (`dict_data_id`),
  KEY `dict_data_dict_dict_id_fk` (`dict_id`),
  CONSTRAINT `dict_data_dict_dict_id_fk` FOREIGN KEY (`dict_id`) REFERENCES `sys_dict` (`dict_id`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `sys_error_log`;
CREATE TABLE `sys_error_log` (
  `error_id` bigint NOT NULL AUTO_INCREMENT COMMENT '错误日志主键',
  `source_type` varchar(20) NOT NULL DEFAULT 'REQUEST' COMMENT '错误来源：REQUEST请求触发 SCHEDULED定时任务 ASYNC异步任务 EVENT事件监听 INIT启动初始化 OTHER其他',
  `oper_log_id` bigint DEFAULT NULL COMMENT '关联操作日志ID（仅REQUEST来源且接口带@Log时可能有值）',
  `title` varchar(255) NOT NULL DEFAULT '' COMMENT '错误简述：请求来源填URL，定时任务填任务名，异步任务填方法名等',
  `oper_name` varchar(50) DEFAULT NULL COMMENT '操作人员（请求来源且有登录态时填；其他来源为空）',
  `exception_type` varchar(255) NOT NULL DEFAULT '' COMMENT '异常类全名',
  `exception_msg` text COMMENT '异常消息',
  `exception_stack` longtext COMMENT '完整堆栈',
  `error_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '错误时间',
  PRIMARY KEY (`error_id`),
  KEY `idx_error_log_time` (`error_time`),
  KEY `idx_error_log_type` (`exception_type`),
  KEY `idx_error_log_source` (`source_type`),
  KEY `idx_error_log_name` (`oper_name`),
  KEY `idx_error_log_oper` (`oper_log_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统错误日志记录表';
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(16) NOT NULL COMMENT '菜单名称',
  `perm_key` varchar(64) DEFAULT NULL COMMENT '权限字符',
  `parent_id` bigint NOT NULL DEFAULT '-1' COMMENT '上级路由的id',
  `menu_type` int NOT NULL COMMENT '菜单类型（1一级菜单，2二级菜单，3按钮）',
  `route` varchar(32) DEFAULT NULL COMMENT '组件路由',
  `backlinks` int DEFAULT '-1' COMMENT '外链链接',
  `path` varchar(64) DEFAULT NULL COMMENT '组件相对路径',
  `icon` varchar(16) DEFAULT NULL COMMENT '图标',
  `status` int NOT NULL DEFAULT '1',
  `create_by` varchar(18) NOT NULL,
  `create_time` datetime NOT NULL,
  `update_by` varchar(18) NOT NULL,
  `update_time` datetime NOT NULL,
  `delete` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `notice_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息主键',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `content` text NOT NULL COMMENT '正文内容',
  `notice_type` varchar(32) NOT NULL DEFAULT 'NOTICE' COMMENT '消息类型：NOTICE公告 NOTIFY通知 REMIND提醒',
  `level` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '消息级别：NORMAL普通 IMPORTANT重要 URGENT紧急',
  `publish_scope` varchar(32) NOT NULL DEFAULT 'ALL' COMMENT '发布范围：ALL全员 GROUP分组',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿 PUBLISHED已发布 REVOKED已撤回',
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶：0否 1是',
  `need_confirm` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要确认：0否 1是',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `route_path` varchar(255) DEFAULT NULL COMMENT '前端路由路径',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete` tinyint NOT NULL DEFAULT '0' COMMENT '删除标记：0未删除 1已删除',
  PRIMARY KEY (`notice_id`),
  KEY `idx_notice_type` (`notice_type`),
  KEY `idx_notice_status` (`status`),
  KEY `idx_notice_scope` (`publish_scope`),
  KEY `idx_notice_publish_time` (`publish_time`),
  KEY `idx_notice_delete` (`delete`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知主表';
DROP TABLE IF EXISTS `sys_notice_group`;
CREATE TABLE `sys_notice_group` (
  `group_id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知分组主键',
  `group_name` varchar(100) NOT NULL COMMENT '通知分组名称',
  `group_code` varchar(100) NOT NULL COMMENT '通知分组编码',
  `group_desc` varchar(255) DEFAULT NULL COMMENT '通知分组描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用 1启用',
  `create_by` varchar(64) NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `uk_notice_group_code` (`group_code`),
  KEY `idx_notice_group_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知分组表';
DROP TABLE IF EXISTS `sys_notice_group_member`;
CREATE TABLE `sys_notice_group_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分组成员主键',
  `group_id` bigint NOT NULL COMMENT '通知分组主键',
  `user_id` bigint DEFAULT NULL COMMENT '成员关联主键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_group_member` (`group_id`,`user_id`),
  KEY `idx_notice_group_member_group` (`group_id`),
  KEY `idx_notice_group_member_match` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知分组成员表';
DROP TABLE IF EXISTS `sys_notice_group_rel`;
CREATE TABLE `sys_notice_group_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息分组关联主键',
  `notice_id` bigint NOT NULL COMMENT '消息主键',
  `group_id` bigint NOT NULL COMMENT '通知分组主键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_group_rel` (`notice_id`,`group_id`),
  KEY `idx_notice_group_rel_notice` (`notice_id`),
  KEY `idx_notice_group_rel_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知与分组关联表';
DROP TABLE IF EXISTS `sys_notice_user_rel`;
CREATE TABLE `sys_notice_user_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息指定成员关联主键',
  `notice_id` bigint NOT NULL COMMENT '消息主键',
  `user_id` bigint NOT NULL COMMENT '被指定的接收用户主键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user_rel` (`notice_id`,`user_id`),
  KEY `idx_notice_user_rel_notice` (`notice_id`),
  KEY `idx_notice_user_rel_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知与指定成员关联表';
DROP TABLE IF EXISTS `sys_notice_read`;
CREATE TABLE `sys_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '已读记录主键',
  `notice_id` bigint NOT NULL COMMENT '消息主键',
  `user_id` bigint NOT NULL COMMENT '用户主键',
  `read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
  `confirm_status` tinyint NOT NULL DEFAULT '0' COMMENT '确认状态：0未确认 1已确认',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`,`user_id`),
  KEY `idx_notice_read_user` (`user_id`),
  KEY `idx_notice_read_notice` (`notice_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知已读记录表';
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) NOT NULL DEFAULT '' COMMENT '模块标题（@Log 的 title）',
  `business_type` varchar(20) NOT NULL DEFAULT 'OTHER' COMMENT '业务类型：OTHER INSERT UPDATE DELETE GRANT EXPORT IMPORT CLEAN',
  `method` varchar(200) NOT NULL DEFAULT '' COMMENT '方法名（类名.方法名）',
  `request_method` varchar(10) NOT NULL DEFAULT '' COMMENT '请求方式 GET/POST/PUT/DELETE',
  `oper_name` varchar(50) NOT NULL DEFAULT '' COMMENT '操作人员（用户名）',
  `oper_url` varchar(255) NOT NULL DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) NOT NULL DEFAULT '' COMMENT '操作主机IP',
  `oper_os` varchar(50) NOT NULL DEFAULT '' COMMENT '操作系统（UA解析）',
  `oper_browser` varchar(50) NOT NULL DEFAULT '' COMMENT '浏览器（UA解析）',
  `device_type` varchar(20) NOT NULL DEFAULT '' COMMENT '设备类型：PC/MOBILE/TABLET/UNKNOWN',
  `oper_param` text COMMENT '请求参数（JSON）',
  `json_result` text COMMENT '返回结果（JSON，失败时可留空）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '操作状态：0正常 1异常',
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `cost_time` bigint NOT NULL DEFAULT '0' COMMENT '耗时（毫秒）',
  PRIMARY KEY (`oper_id`),
  KEY `idx_oper_log_time` (`oper_time`),
  KEY `idx_oper_log_name` (`oper_name`),
  KEY `idx_oper_log_status` (`status`),
  KEY `idx_oper_log_biz` (`business_type`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志记录表';
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(16) NOT NULL COMMENT '角色名称',
  `role_level` int NOT NULL COMMENT '角色等级',
  `role_key` varchar(12) NOT NULL,
  `status` int NOT NULL DEFAULT '1' COMMENT '角色状态',
  `is_default` int NOT NULL DEFAULT '0' COMMENT '是否为默认角色（1是，0否）',
  `create_by` varchar(18) NOT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` varchar(18) NOT NULL COMMENT '更改者',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `delete` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `sys_role_pk` (`role_key`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL,
  `menu_id` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(12) NOT NULL COMMENT '用户账号',
  `password` varchar(64) NOT NULL COMMENT '用户密码',
  `nick_name` varchar(18) NOT NULL COMMENT '用户昵称',
  `sex` char(2) DEFAULT '0' COMMENT '用户性别',
  `phone_number` char(11) DEFAULT NULL COMMENT '用户电话',
  `avatar` varchar(64) DEFAULT NULL COMMENT '用户头像',
  `status` int DEFAULT '1' COMMENT '账号状态',
  `create_by` varchar(18) NOT NULL COMMENT '创造者',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` varchar(18) NOT NULL COMMENT '更新者',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `delete` int DEFAULT '0' COMMENT '是否被删除（0否，1是）',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `sys_user_pk` (`username`),
  UNIQUE KEY `sys_user_pk_2` (`phone_number`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint DEFAULT NULL,
  `role_id` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 初始化数据
-- 依赖顺序：sys_menu -> sys_role -> sys_dict -> sys_dict_data
--          -> sys_user -> sys_role_menu -> sys_user_role
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role` VALUES (1,'admin',1,'admin',1,0,'creator','2025-03-11 22:14:20','creator','2025-03-11 22:14:20',0);
INSERT INTO `sys_role` VALUES (5,'visitor',1,'visitor',1,1,'admin','2026-05-19 17:14:53','admin','2026-05-19 17:16:47',0);
INSERT INTO `sys_role_menu` VALUES (1,1);
INSERT INTO `sys_role_menu` VALUES (1,2);
INSERT INTO `sys_role_menu` VALUES (1,3);
INSERT INTO `sys_role_menu` VALUES (1,4);
INSERT INTO `sys_role_menu` VALUES (1,5);
INSERT INTO `sys_role_menu` VALUES (1,6);
INSERT INTO `sys_role_menu` VALUES (1,7);
INSERT INTO `sys_role_menu` VALUES (1,8);
INSERT INTO `sys_role_menu` VALUES (1,9);
INSERT INTO `sys_role_menu` VALUES (1,10);
INSERT INTO `sys_role_menu` VALUES (1,11);
INSERT INTO `sys_role_menu` VALUES (1,12);
INSERT INTO `sys_role_menu` VALUES (1,13);
INSERT INTO `sys_role_menu` VALUES (1,14);
INSERT INTO `sys_role_menu` VALUES (1,15);
INSERT INTO `sys_role_menu` VALUES (1,16);
INSERT INTO `sys_role_menu` VALUES (1,17);
INSERT INTO `sys_role_menu` VALUES (1,18);
INSERT INTO `sys_role_menu` VALUES (1,19);
INSERT INTO `sys_role_menu` VALUES (1,20);
INSERT INTO `sys_role_menu` VALUES (1,21);
INSERT INTO `sys_role_menu` VALUES (1,22);
INSERT INTO `sys_role_menu` VALUES (1,23);
INSERT INTO `sys_role_menu` VALUES (1,24);
INSERT INTO `sys_role_menu` VALUES (1,25);
INSERT INTO `sys_role_menu` VALUES (1,26);
INSERT INTO `sys_role_menu` VALUES (1,27);
INSERT INTO `sys_role_menu` VALUES (1,28);
INSERT INTO `sys_role_menu` VALUES (1,29);
INSERT INTO `sys_role_menu` VALUES (1,30);
INSERT INTO `sys_role_menu` VALUES (1,31);
INSERT INTO `sys_role_menu` VALUES (1,32);
INSERT INTO `sys_role_menu` VALUES (1,33);
INSERT INTO `sys_role_menu` VALUES (1,34);
INSERT INTO `sys_role_menu` VALUES (1,35);
INSERT INTO `sys_role_menu` VALUES (5,1);
INSERT INTO `sys_role_menu` VALUES (5,2);
INSERT INTO `sys_role_menu` VALUES (5,8);
INSERT INTO `sys_role_menu` VALUES (5,16);
INSERT INTO `sys_role_menu` VALUES (5,23);
INSERT INTO `sys_role_menu` VALUES (1,36);
INSERT INTO `sys_role_menu` VALUES (1,37);
INSERT INTO `sys_role_menu` VALUES (1,38);
INSERT INTO `sys_role_menu` VALUES (1,39);
INSERT INTO `sys_role_menu` VALUES (1,40);
INSERT INTO `sys_role_menu` VALUES (1,41);
INSERT INTO `sys_role_menu` VALUES (1,42);
INSERT INTO `sys_role_menu` VALUES (1,43);
INSERT INTO `sys_role_menu` VALUES (1,44);
INSERT INTO `sys_role_menu` VALUES (1,45);
INSERT INTO `sys_role_menu` VALUES (1,46);
INSERT INTO `sys_role_menu` VALUES (1,47);
INSERT INTO `sys_role_menu` VALUES (1,48);
INSERT INTO `sys_role_menu` VALUES (1,49);
INSERT INTO `sys_role_menu` VALUES (1,50);
INSERT INTO `sys_role_menu` VALUES (1,51);
INSERT INTO `sys_role_menu` VALUES (1,52);
INSERT INTO `sys_role_menu` VALUES (1,53);
INSERT INTO `sys_role_menu` VALUES (1,54);
INSERT INTO `sys_role_menu` VALUES (1,55);
INSERT INTO `sys_role_menu` VALUES (1,56);
INSERT INTO `sys_role_menu` VALUES (1,57);
INSERT INTO `sys_role_menu` VALUES (1,58);
INSERT INTO `sys_role_menu` VALUES (1,59);
INSERT INTO `sys_role_menu` VALUES (1,60);
INSERT INTO `sys_role_menu` VALUES (1,61);
INSERT INTO `sys_role_menu` VALUES (1,62);
INSERT INTO `sys_menu` VALUES (1,'系统模块','system',-1,1,'system',0,NULL,'system',1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (2,'用户管理','system:user',1,2,'user',0,'/system/user/index','user',1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (3,'用户查询','system:user:quarry',2,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (4,'用户信息','system:user:info',2,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (5,'用户修改','system:user:edit',2,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (6,'用户新增','system:user:add',2,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (7,'用户删除','system:user:delete',2,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (8,'角色管理','system:role',1,2,'role',0,'/system/role/index','role',1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (9,'角色查询','system:role:quarry',8,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (10,'角色信息','system:role:info',8,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (11,'角色修改','system:role:edit',8,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (12,'角色新增','system:role:add',8,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (13,'角色删除','system:role:delete',8,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (14,'角色状态','system:role:status',8,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (15,'默认角色','system:role:default',8,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (16,'菜单管理','system:menu',1,2,'menu',0,'/system/menu/index','menu',1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (17,'菜单查询','system:menu:quarry',16,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (18,'菜单信息','system:menu:info',16,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (19,'菜单修改','system:menu:edit',16,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (20,'菜单新增','system:menu:add',16,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (21,'菜单删除','system:menu:delete',16,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (22,'菜单状态','system:menu:status',16,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (23,'字典管理','system:dict',1,2,'dict',0,'/system/dict/index','dict',1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (24,'字典查询','system:dict:quarry',23,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (25,'字典信息','system:dict:info',23,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (26,'字典修改','system:dict:edit',23,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (27,'字典新增','system:dict:add',23,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (28,'字典删除','system:dict:delete',23,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (35,'用户状态','system:user:status',2,3,NULL,0,NULL,NULL,1,'admin','2026-05-18 22:37:54','admin','2026-05-18 22:37:54',0);
INSERT INTO `sys_menu` VALUES (36,'通知管理','notice',1,1,'notice',0,NULL,'BellFilled',1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:31:05',0);
INSERT INTO `sys_menu` VALUES (37,'内容管理','system:notice',36,2,'notice-content',0,'/system/notice/notice-content/index','Message',1,'admin','2026-06-21 20:22:04','admin','2026-06-21 22:06:40',0);
INSERT INTO `sys_menu` VALUES (38,'通知查询','system:notice:quarry',37,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (39,'通知信息','system:notice:info',37,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (40,'通知新增','system:notice:add',37,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (41,'通知修改','system:notice:edit',37,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (42,'通知删除','system:notice:delete',37,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (43,'通知发布','system:notice:publish',37,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (44,'通知撤回','system:notice:revoke',37,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (45,'分组管理','system:noticeGroup',36,2,'noticeGroup',0,'/system/notice/notice-group/index','Promotion',1,'admin','2026-06-21 20:22:04','admin','2026-06-21 21:50:01',0);
INSERT INTO `sys_menu` VALUES (46,'分组查询','system:noticeGroup:quarry',45,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (47,'分组信息','system:noticeGroup:info',45,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (48,'分组新增','system:noticeGroup:add',45,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (49,'分组修改','system:noticeGroup:edit',45,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (50,'分组删除','system:noticeGroup:delete',45,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (51,'分组成员管理','system:noticeGroup:member',45,3,NULL,0,NULL,NULL,1,'admin','2026-06-21 20:22:04','admin','2026-06-21 20:22:04',0);
INSERT INTO `sys_menu` VALUES (52,'日志管理','log',1,1,'log',0,NULL,'log',1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (53,'操作日志','system:operLog',52,2,'oper-log',0,'/system/log/oper-log/index','log',1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (54,'操作日志查询','system:operLog:quarry',53,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (55,'操作日志详情','system:operLog:info',53,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (56,'操作日志删除','system:operLog:delete',53,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (57,'操作日志清空','system:operLog:clean',53,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (58,'错误日志','system:errorLog',52,2,'error-log',0,'/system/log/error-log/index','log',1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (59,'错误日志查询','system:errorLog:quarry',58,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (60,'错误日志详情','system:errorLog:info',58,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (61,'错误日志删除','system:errorLog:delete',58,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_menu` VALUES (62,'错误日志清空','system:errorLog:clean',58,3,NULL,0,NULL,NULL,1,'admin','2026-06-27 16:27:10','admin','2026-06-27 16:27:10',0);
INSERT INTO `sys_dict` VALUES (1,'用户性别','sys_user_sex',1,NULL,'2025-07-26 10:17:35','creator','2025-07-26 10:17:39','creator');
INSERT INTO `sys_dict` VALUES (2,'日志','sys_log',1,'订单状态字典','2026-05-14 14:36:32','admin','2026-06-21 17:49:45','admin');
INSERT INTO `sys_dict` VALUES (3,'菜单类型','sys_menu_type',1,'标注当前菜单类型','2026-05-19 16:17:59','admin','2026-05-19 16:17:59','admin');
INSERT INTO `sys_dict` VALUES (4,'通知类型','sys_notice_type',1,'通知的类型','2026-06-21 15:58:51','admin','2026-06-21 15:58:51','admin');
INSERT INTO `sys_dict` VALUES (5,'通知状态','sys_notice_status',1,'通知的状态','2026-06-21 17:43:15','admin','2026-06-21 17:43:15','admin');
INSERT INTO `sys_dict` VALUES (6,'通知等级','sys_notice_level',1,'','2026-06-21 18:30:38','admin','2026-06-21 19:21:27','admin');
INSERT INTO `sys_dict` VALUES (7,'通知范围','sys_notice_scope',1,'','2026-06-21 22:19:01','admin','2026-06-21 22:19:01','admin');
INSERT INTO `sys_dict` VALUES (8,'操作业务类型','sys_oper_business_type',1,'操作日志的业务类型','2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict` VALUES (9,'操作设备类型','sys_oper_device_type',1,'操作日志的设备类型(UA解析)','2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict` VALUES (10,'操作状态','sys_oper_status',1,'操作日志的执行状态','2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict` VALUES (11,'错误来源类型','sys_error_source_type',1,'错误日志的触发来源','2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (1,1,'sys_user_sex','男','0',NULL,1,NULL,NULL,NULL,NULL,'0',1,'2025-07-26 10:35:46','creator','2025-07-26 10:35:48','creator');
INSERT INTO `sys_dict_data` VALUES (2,1,'sys_user_sex','女','1',NULL,1,NULL,NULL,NULL,NULL,'0',1,'2025-07-26 10:36:12','creator','2025-07-26 10:36:13','creator');
INSERT INTO `sys_dict_data` VALUES (3,1,'sys_user_sex','未知','3',NULL,2,NULL,NULL,NULL,NULL,'0',1,'2025-07-26 12:01:23','admin','2025-11-22 23:16:07','admin');
INSERT INTO `sys_dict_data` VALUES (4,3,'sys_menu_type','目录','1','',1,'success','dark','',NULL,'0',1,'2026-05-19 16:29:50','admin','2026-05-19 16:44:00','admin');
INSERT INTO `sys_dict_data` VALUES (5,3,'sys_menu_type','菜单','2','',1,'primary','light','',NULL,'0',1,'2026-05-19 16:30:12','admin','2026-05-19 16:37:29','admin');
INSERT INTO `sys_dict_data` VALUES (6,3,'sys_menu_type','按钮','3','',1,'info','dark','',NULL,'0',1,'2026-05-19 16:30:43','admin','2026-05-19 16:44:04','admin');
INSERT INTO `sys_dict_data` VALUES (7,4,'sys_notice_type','公告','NOTICE','',1,'warning','light','',NULL,'0',1,'2026-06-21 16:15:32','admin','2026-06-21 17:41:01','admin');
INSERT INTO `sys_dict_data` VALUES (8,4,'sys_notice_type','通知','NOTIFY','',1,'success','light','',NULL,'0',1,'2026-06-21 16:17:28','admin','2026-06-21 17:41:06','admin');
INSERT INTO `sys_dict_data` VALUES (9,4,'sys_notice_type','提醒','REMIND','',1,'primary','light','',NULL,'0',1,'2026-06-21 16:18:03','admin','2026-06-21 17:41:10','admin');
INSERT INTO `sys_dict_data` VALUES (10,5,'sys_notice_status','草稿','DRAFT','',1,'info','plain','',NULL,'0',1,'2026-06-21 18:24:53','admin','2026-06-21 18:24:53','admin');
INSERT INTO `sys_dict_data` VALUES (11,5,'sys_notice_status','已发布','PUBLISHED','',1,'success','light','',NULL,'0',1,'2026-06-21 18:26:23','admin','2026-06-21 18:26:28','admin');
INSERT INTO `sys_dict_data` VALUES (12,5,'sys_notice_status','已撤回','REVOKED','',1,'danger','light','',NULL,'0',1,'2026-06-21 18:27:21','admin','2026-06-22 21:55:51','admin');
INSERT INTO `sys_dict_data` VALUES (13,6,'sys_notice_level','普通','NORMAL','',1,'primary','light','',NULL,'0',1,'2026-06-21 19:10:32','admin','2026-06-22 16:56:32','admin');
INSERT INTO `sys_dict_data` VALUES (14,6,'sys_notice_level','重要','IMPORTANT','',1,'warning','light','',NULL,'0',1,'2026-06-21 19:11:04','admin','2026-06-22 16:58:16','admin');
INSERT INTO `sys_dict_data` VALUES (15,7,'sys_notice_scope','全体成员','ALL','',1,'primary','light','',NULL,'0',1,'2026-06-22 15:05:31','admin','2026-06-22 15:05:31','admin');
INSERT INTO `sys_dict_data` VALUES (16,7,'sys_notice_scope','指定分组','GROUP','',2,'success','light','',NULL,'0',1,'2026-06-22 15:06:19','admin','2026-06-22 15:06:19','admin');
INSERT INTO `sys_dict_data` VALUES (49,7,'sys_notice_scope','指定成员','USER','',3,'danger','light','',NULL,'0',1,'2026-07-13 10:00:00','admin','2026-07-13 10:00:00','admin');
INSERT INTO `sys_dict_data` VALUES (17,6,'sys_notice_level','紧急','URGENT','',1,'danger','light','',NULL,'0',1,'2026-06-22 16:57:37','admin','2026-06-22 16:57:37','admin');
INSERT INTO `sys_dict_data` VALUES (18,8,'sys_oper_business_type','其他','OTHER','其他操作',1,'info','light',NULL,NULL,'1',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (19,8,'sys_oper_business_type','新增','INSERT','新增操作',2,'success','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (20,8,'sys_oper_business_type','修改','UPDATE','修改操作',3,'warning','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (21,8,'sys_oper_business_type','删除','DELETE','删除操作',4,'danger','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (22,8,'sys_oper_business_type','授权','GRANT','授权操作',5,'primary','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (23,8,'sys_oper_business_type','导出','EXPORT','导出操作',6,'primary','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (24,8,'sys_oper_business_type','导入','IMPORT','导入操作',7,'primary','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (25,8,'sys_oper_business_type','清空','CLEAN','清空操作',8,'danger','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (33,9,'sys_oper_device_type','PC端','PC','桌面浏览器',1,'','',NULL,NULL,'1',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (34,9,'sys_oper_device_type','移动端','MOBILE','手机浏览器',2,'success','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (35,9,'sys_oper_device_type','平板','TABLET','平板设备',3,'warning','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (36,9,'sys_oper_device_type','未知','UNKNOWN','无法识别',4,'info','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (40,10,'sys_oper_status','正常','0','操作成功',1,'success','light',NULL,NULL,'1',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (41,10,'sys_oper_status','异常','1','操作失败',2,'danger','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (43,11,'sys_error_source_type','请求触发','REQUEST','HTTP请求触发',1,'primary','light',NULL,NULL,'1',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (44,11,'sys_error_source_type','定时任务','SCHEDULED','定时任务报错',2,'warning','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (45,11,'sys_error_source_type','异步任务','ASYNC','异步任务报错',3,'warning','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (46,11,'sys_error_source_type','事件监听','EVENT','事件监听报错',4,'warning','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (47,11,'sys_error_source_type','启动初始化','INIT','启动/初始化阶段报错',5,'danger','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_dict_data` VALUES (48,11,'sys_error_source_type','其他','OTHER','其他来源报错',6,'info','light',NULL,NULL,'0',1,'2026-06-27 16:27:09','admin','2026-06-27 16:27:09','admin');
INSERT INTO `sys_user` VALUES (1,'admin','$2a$10$DmGhQbmQJiPFFyTD5xshRefwehrUfNrNklaeZVgoRyFkS50vkch7S','admin','1','19937359196',NULL,1,'creator','2025-03-11 21:49:38','admin','2026-05-18 17:29:19',0);
INSERT INTO `sys_user` VALUES (2,'rookie','$2a$10$09ODZ/dtvLxhB6dpP/XVQ.vgtwRpROixbKVh/vyPk5g/ahA5Bezoa','rookie','0','13866666666',NULL,1,'admin','2025-03-24 22:34:02','admin','2026-05-19 17:15:06',0);
INSERT INTO `sys_user_role` VALUES (1,1);
INSERT INTO `sys_user_role` VALUES (2,5);

SET FOREIGN_KEY_CHECKS = 1;
