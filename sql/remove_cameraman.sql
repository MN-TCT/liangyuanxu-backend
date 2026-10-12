-- 职位去重：摄影师与摄像师只保留摄影师，删除所有摄像师（模板与各商家）
SET NAMES utf8mb4;
USE wedding_auth;
DELETE FROM staff_role_config WHERE role_code = '摄像师';
