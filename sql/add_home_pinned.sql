-- 首页推荐位：给套餐增加"置顶首页"能力
-- home_pinned: 是否置顶到首页（1 是 / 0 否）
-- home_sort:   置顶排序，越小越靠前（仅 home_pinned=1 时有效）
ALTER TABLE wedding_package
  ADD COLUMN home_pinned TINYINT DEFAULT 0 COMMENT '是否置顶首页 1是 0否' AFTER status,
  ADD COLUMN home_sort INT DEFAULT 0 COMMENT '置顶排序，越小越前' AFTER home_pinned,
  ADD INDEX idx_home_pinned (home_pinned, home_sort);
