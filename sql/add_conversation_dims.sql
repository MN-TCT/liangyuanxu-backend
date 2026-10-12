-- 咨询会话表扩展：支持按 用户/商家/套餐 维度建立会话
-- 与 wedding-message 模块 Conversation 实体对齐
SET NAMES utf8mb4;
USE wedding_message;

ALTER TABLE conversation
  ADD COLUMN user_id BIGINT NULL COMMENT '用户ID' AFTER id,
  ADD COLUMN company_id BIGINT NULL COMMENT '商家ID(婚庆公司用户ID)' AFTER user_id,
  ADD COLUMN package_id BIGINT NULL COMMENT '关联套餐ID(可选)' AFTER company_id,
  ADD INDEX idx_user (user_id),
  ADD INDEX idx_company (company_id);
