-- =====================================================
-- 婚庆婚纱摄影套餐后台 - MySQL 初始化脚本
-- 由 mysql:8 容器 /docker-entrypoint-initdb.d/ 自动执行
-- =====================================================

-- 关键：初始化客户端连接使用 utf8mb4，确保中文种子数据正确入库
-- （否则 docker-entrypoint 默认 latin1 会把 UTF-8 字节二次编码成乱码）
SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------- 1. wedding_auth ----------
CREATE DATABASE IF NOT EXISTS wedding_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wedding_auth;
CREATE TABLE IF NOT EXISTS sys_user (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(50) DEFAULT '',
  role VARCHAR(20) DEFAULT 'admin',
  status TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_user (username, password_hash, nickname, role, status) VALUES
('admin', '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '超级管理员', 'admin', 1);

-- ---------- 2. wedding_package ----------
CREATE DATABASE IF NOT EXISTS wedding_package DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wedding_package;
CREATE TABLE IF NOT EXISTS package_category (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  sort INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO package_category (name, sort) VALUES
('婚纱摄影', 0), ('旅拍套餐', 1), ('婚礼跟拍', 2), ('儿童摄影', 3), ('全家福', 4);

CREATE TABLE IF NOT EXISTS wedding_package (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  category_id INT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  cover_url VARCHAR(500) DEFAULT '',
  price DECIMAL(10,2) DEFAULT 0,
  original_price DECIMAL(10,2) DEFAULT 0,
  duration_days INT DEFAULT 1,
  location VARCHAR(100) DEFAULT '',
  includes_json TEXT,
  stock INT DEFAULT 0,
  status TINYINT DEFAULT 1 COMMENT '1上架 0下架',
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (category_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, description) VALUES
(1, '经典婚纱照套餐', '', 3999.00, 5999.00, 1, '室内影棚 + 外景', '["摄影师1名","化妆师1名","婚纱礼服4套","精修30张","相册1本"]', 20, 1, '适合新人的经典婚纱拍摄套餐，含内景与外景。'),
(2, '三亚海边旅拍', '', 8888.00, 12800.00, 2, '三亚', '["两天拍摄","含2晚住宿","精修50张","航拍1组","全套底片"]', 10, 1, '海边浪漫旅拍，全程高端服务。'),
(3, '婚礼全天跟拍', '', 2680.00, 3280.00, 1, '不限', '["双机位","全天跟拍","精修40张","婚礼微电影"]', 50, 1, '记录婚礼全天的感动瞬间。'),
(4, '儿童成长记录套餐', '', 1588.00, 1999.00, 1, '室内影棚', '["2套造型","精修20张","成长相册"]', 30, 0, '记录孩子成长的每个阶段。');


-- ---------- 2.5 下拉选项配置（分类/地点/天数/服务，供套餐编辑下拉选择） ----------
CREATE TABLE IF NOT EXISTS option_config (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(32) NOT NULL COMMENT 'location=拍摄地点/days=拍摄天数/service=包含服务',
  label VARCHAR(128) NOT NULL,
  value VARCHAR(128) NOT NULL,
  sort INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO option_config (category, label, value, sort) VALUES
('location','室内影棚','室内影棚',0),('location','户外公园','户外公园',1),('location','三亚海边','三亚海边',2),('location','古镇景区','古镇景区',3),('location','酒店宴会厅','酒店宴会厅',4),('location','天台花园','天台花园',5),
('days','1天','1',0),('days','2天','2',1),('days','3天','3',2),('days','5天','5',3),
('service','摄影师1名','摄影师1名',0),('service','化妆师1名','化妆师1名',1),('service','精修30张','精修30张',2),('service','底片全送','底片全送',3),('service','相册1本','相册1本',4),('service','礼服2套','礼服2套',5),('service','婚礼跟拍','婚礼跟拍',6),('service','微电影1部','微电影1部',7),('service','酒店婚宴','酒店婚宴',8);
-- ---------- 3. wedding_booking ----------
CREATE DATABASE IF NOT EXISTS wedding_booking DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wedding_booking;
CREATE TABLE IF NOT EXISTS booking_order (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  customer_name VARCHAR(50) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  package_id INT UNSIGNED,
  package_name VARCHAR(100) DEFAULT '',
  plan_date DATE,
  status TINYINT DEFAULT 0 COMMENT '0待处理 1已联系 2已成交 3已取消',
  remark VARCHAR(500) DEFAULT '',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO booking_order (customer_name, phone, package_id, package_name, plan_date, status, remark) VALUES
('张先生', '13800001111', 1, '经典婚纱照套餐', '2026-10-10', 0, '想了解档期'),
('李女士', '13900002222', 2, '三亚海边旅拍', '2026-11-02', 1, '已电话沟通'),
('王先生', '13700003333', 3, '婚礼全天跟拍', '2026-10-25', 2, '已成交');

-- ---------- 4. wedding_video ----------
CREATE DATABASE IF NOT EXISTS wedding_video DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wedding_video;
CREATE TABLE IF NOT EXISTS wedding_video (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  description TEXT,
  video_url VARCHAR(500) NOT NULL,
  cover_url VARCHAR(500) DEFAULT '',
  duration_seconds INT DEFAULT 0,
  status TINYINT DEFAULT 1 COMMENT '1上架 0下架',
  created_by INT UNSIGNED DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 5. wedding_message ----------
CREATE DATABASE IF NOT EXISTS wedding_message DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wedding_message;
CREATE TABLE IF NOT EXISTS conversation (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL COMMENT '用户ID',
  company_id BIGINT NULL COMMENT '商家ID(婚庆公司用户ID)',
  package_id BIGINT NULL COMMENT '关联套餐ID(可选)',
  user_name VARCHAR(50) NOT NULL,
  user_phone VARCHAR(30) DEFAULT '',
  status TINYINT DEFAULT 0 COMMENT '0进行中 1已关闭',
  last_message VARCHAR(500) DEFAULT '',
  last_message_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_user (user_id),
  INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  conversation_id INT UNSIGNED NOT NULL,
  sender VARCHAR(20) NOT NULL COMMENT 'customer/admin',
  sender_name VARCHAR(50) DEFAULT '',
  content VARCHAR(1000) NOT NULL,
  msg_type VARCHAR(20) DEFAULT 'text',
  is_read TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  target_role VARCHAR(20) NOT NULL COMMENT 'admin/customer',
  type VARCHAR(30) NOT NULL COMMENT 'new_consult/new_booking/new_message',
  title VARCHAR(100) DEFAULT '',
  content VARCHAR(500) DEFAULT '',
  is_read TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_target (target_role, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
