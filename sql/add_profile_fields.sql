-- 个人中心：sys_user 增加店铺名称 / 头像 / 简介字段
SET NAMES utf8mb4;
USE wedding_auth;
ALTER TABLE sys_user
  ADD COLUMN shop_name VARCHAR(50) NULL COMMENT '店铺名称（商家）' AFTER nickname,
  ADD COLUMN avatar VARCHAR(500) NULL COMMENT '头像地址' AFTER phone,
  ADD COLUMN introduction VARCHAR(500) NULL COMMENT '个人简介' AFTER avatar;
