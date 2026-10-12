-- 婚庆后台数据库清空脚本：清空所有业务数据，仅保留系统基础种子
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

-- ---------- wedding_auth ----------
USE wedding_auth;
TRUNCATE TABLE sys_user;
TRUNCATE TABLE staff;
TRUNCATE TABLE staff_request;
TRUNCATE TABLE staff_role_config;
INSERT INTO sys_user (username, password_hash, nickname, role, status) VALUES
('admin', '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '超级管理员', 'admin', 1);

-- ---------- wedding_package ----------
USE wedding_package;
TRUNCATE TABLE package_category;
TRUNCATE TABLE wedding_package;
TRUNCATE TABLE option_config;
INSERT INTO package_category (name, sort) VALUES
('婚纱摄影', 0), ('旅拍套餐', 1), ('婚礼跟拍', 2), ('儿童摄影', 3), ('全家福', 4);
INSERT INTO option_config (category, label, value, sort) VALUES
('location','室内影棚','室内影棚',0),('location','户外公园','户外公园',1),('location','三亚海边','三亚海边',2),('location','古镇景区','古镇景区',3),('location','酒店宴会厅','酒店宴会厅',4),('location','天台花园','天台花园',5),
('days','1天','1',0),('days','2天','2',1),('days','3天','3',2),('days','5天','5',3),
('service','摄影师1名','摄影师1名',0),('service','化妆师1名','化妆师1名',1),('service','精修30张','精修30张',2),('service','底片全送','底片全送',3),('service','相册1本','相册1本',4),('service','礼服2套','礼服2套',5),('service','婚礼跟拍','婚礼跟拍',6),('service','微电影1部','微电影1部',7),('service','酒店婚宴','酒店婚宴',8);

-- ---------- wedding_booking ----------
USE wedding_booking;
TRUNCATE TABLE booking_order;
TRUNCATE TABLE booking_staff_assignment;

-- ---------- wedding_video ----------
USE wedding_video;
TRUNCATE TABLE wedding_video;

-- ---------- wedding_message ----------
USE wedding_message;
TRUNCATE TABLE conversation;
TRUNCATE TABLE chat_message;
TRUNCATE TABLE notification;

SET FOREIGN_KEY_CHECKS=1;
