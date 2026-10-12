-- =====================================================
-- 演示数据：批量商家（婚庆公司） + 对应套餐产品
-- 用途：本地演示 / 数据显示效果验证
-- 可重复执行：先清理本脚本生成的演示数据，再重新插入
-- 依赖：wedding_auth（sys_user / staff_role_config）、wedding_package（wedding_package）
-- =====================================================
SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------- 0. 清理旧的演示数据（用户名以 demo_ 开头） ----------
DELETE FROM wedding_package.wedding_package WHERE company_id IN (
  SELECT id FROM wedding_auth.sys_user WHERE username LIKE 'demo_%'
);
DELETE FROM wedding_auth.staff_role_config WHERE company_id IN (
  SELECT id FROM wedding_auth.sys_user WHERE username LIKE 'demo_%'
);
DELETE FROM wedding_auth.sys_user WHERE username LIKE 'demo_%';

-- ---------- 1. 批量商家账号（密码统一 123456，bcrypt） ----------
-- 密码哈希对应明文：123456
USE wedding_auth;
INSERT INTO sys_user
  (username, password_hash, nickname, shop_name, phone, avatar, introduction, role, status,
   contact_name, address, business_type, license_no)
VALUES
('demo_yunshang',  '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '云裳婚纱摄影',   '云裳婚纱摄影',     '13800010001', '', '专注高端婚纱摄影 12 年，拥有 3000㎡ 实景基地，主打轻奢与韩式风格。', 'wedding_company', 1, '林云裳', '合肥市蜀山区长江西路 888 号', '婚纱摄影,婚礼跟拍', '91340100MA2DEMO01X'),
('demo_huajian',  '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '花间集婚庆',     '花间集婚庆策划',   '13800010002', '', '一站式婚礼策划，擅长中式、森系、草坪婚礼，提供全案定制服务。', 'wedding_company', 1, '苏花间', '合肥市包河区万达广场 B 座 1801', '婚礼策划,婚宴布置', '91340100MA2DEMO02X'),
('demo_sanya',    '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '三亚蓝湾旅拍',   '三亚蓝湾旅拍',     '13800010003', '', '三亚本地旅拍团队，海景 / 游艇 / 沙滩多场景，含住宿与接送。', 'wedding_company', 1, '陈蓝湾', '海南省三亚市海棠湾海棠北路 66 号', '旅拍,婚纱摄影', '91460200MA2DEMO03X'),
('demo_guofeng',  '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '国风记·中式婚典', '国风记中式婚典',   '13800010004', '', '专注明制汉婚、秀禾服、凤冠霞帔，还原传统中式婚礼仪式感。', 'wedding_company', 1, '周国风', '南京市秦淮区夫子庙贡院街 12 号', '中式婚礼,汉服婚典', '91320100MA2DEMO04X'),
('demo_xingchen', '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '星辰影像工作室', '星辰影像工作室',   '13800010005', '', '婚礼跟拍 / 微电影 / 航拍团队，双机位起步，主打纪实电影感。', 'wedding_company', 1, '吴星辰', '合肥市政务区潜山路 190 号', '婚礼跟拍,婚礼微电影', '91340100MA2DEMO05X'),
('demo_tongnian', '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '童颜儿童摄影',   '童颜儿童摄影',     '13800010006', '', '专注 0-12 岁儿童成长记录，安全影棚，专业引导师全程陪伴。', 'wedding_company', 1, '郑童颜', '合肥市庐阳区四里河路 55 号', '儿童摄影,全家福', '91340100MA2DEMO06X'),
('demo_quanfu',   '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '团圆全家福馆',   '团圆全家福馆',     '13800010007', '', '三代同堂全家福、亲子照、长辈纪念照，提供上门拍摄服务。', 'wedding_company', 1, '孙团圆', '合肥市瑶海区长江东路 300 号', '全家福,亲子摄影', '91340100MA2DEMO07X'),
('demo_weimei',   '$2a$10$daGoF3JwsMuKfw3y3ta4m.9E6KLGhxt2w3VL6MaZF3g/5tSauh7gi', '唯美时光婚纱馆', '唯美时光婚纱馆',   '13800010008', '', '老牌婚纱摄影机构，经典欧式内景 + 城市外景，性价比之选。', 'wedding_company', 1, '何唯美', '合肥市高新区黄山路 600 号', '婚纱摄影', '91340100MA2DEMO08X');

-- ---------- 2. 各商家自定义职位（staff_role_config） ----------
-- 为每个演示商家配置 4 个常用职位
INSERT INTO staff_role_config (role_code, role_name, sort, company_id)
SELECT 'photographer', '摄影师', 0, id FROM sys_user WHERE username LIKE 'demo_%'
UNION ALL SELECT 'makeup_artist', '跟妆师', 1, id FROM sys_user WHERE username LIKE 'demo_%'
UNION ALL SELECT 'host', '主持人', 2, id FROM sys_user WHERE username LIKE 'demo_%'
UNION ALL SELECT 'lighting', '灯光师', 3, id FROM sys_user WHERE username LIKE 'demo_%';

-- ---------- 3. 批量套餐产品（wedding_package） ----------
USE wedding_package;
-- 3.0 确保基础分类存在（按名称幂等补齐，避免因分类被删导致 JOIN 失败）
INSERT INTO package_category (name, sort)
SELECT '婚纱摄影', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM package_category WHERE name = '婚纱摄影');
INSERT INTO package_category (name, sort)
SELECT '旅拍套餐', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM package_category WHERE name = '旅拍套餐');
INSERT INTO package_category (name, sort)
SELECT '婚礼跟拍', 2 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM package_category WHERE name = '婚礼跟拍');
INSERT INTO package_category (name, sort)
SELECT '儿童摄影', 3 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM package_category WHERE name = '儿童摄影');
INSERT INTO package_category (name, sort)
SELECT '全家福', 4 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM package_category WHERE name = '全家福');
-- 3.1 云裳婚纱摄影
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_yunshang') u
JOIN package_category c ON c.name = '婚纱摄影'
JOIN (
  SELECT '轻奢韩式婚纱照' AS name, 6999.00 AS price, 9999.00 AS orig, 1 AS days, '室内影棚 + 城市外景' AS loc,
         '["摄影师1名","化妆师1名","婚纱礼服6套","精修50张","相册2本","底片全送"]' AS inc, 15 AS stock, 1 AS status, 1 AS pin, 1 AS sort,
         '韩式清新风格，含 3000㎡ 实景基地全天拍摄。' AS descr
  UNION ALL SELECT '经典欧式婚纱照', 4599.00, 6599.00, 1, '室内影棚', '["摄影师1名","化妆师1名","礼服4套","精修35张","相册1本"]', 20, 1, 0, 0, '欧式宫廷实景，经典不过时。'
  UNION ALL SELECT '高定旅拍·厦门站', 8899.00, 12800.00, 2, '厦门鼓浪屿', '["两天拍摄","含2晚海景房","精修60张","航拍1组","全套底片"]', 8, 1, 0, 0, '厦门鼓浪屿浪漫旅拍，全程跟拍。'
) t;

-- 3.2 花间集婚庆策划
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_huajian') u
JOIN package_category c ON c.name = '婚礼跟拍'
JOIN (
  SELECT '森系草坪婚礼全案' AS name, 19800.00 AS price, 26800.00 AS orig, 1 AS days, '户外草坪' AS loc,
         '["婚礼策划","场地布置","花艺设计","司仪1名","摄影摄像双机位"]' AS inc, 10 AS stock, 1 AS status, 1 AS pin, 2 AS sort,
         '森系主题草坪婚礼，含全案策划与现场执行。' AS descr
  UNION ALL SELECT '中式明制汉婚全案', 22800.00, 29800.00, 1, '酒店宴会厅', '["秀禾服2套","凤冠霞帔","传统仪式","八抬大轿","司仪1名"]', 6, 1, 0, 0, '还原明制婚礼仪式，庄重典雅。'
  UNION ALL SELECT '轻奢水晶主题婚礼', 15800.00, 21800.00, 1, '酒店宴会厅', '["水晶吊顶布置","灯光设计","司仪1名","摄影双机位"]', 12, 1, 0, 0, '轻奢水晶主题，浪漫高级。'
) t;

-- 3.3 三亚蓝湾旅拍
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_sanya') u
JOIN package_category c ON c.name = '旅拍套餐'
JOIN (
  SELECT '三亚海棠湾海景旅拍' AS name, 9888.00 AS price, 13800.00 AS orig, 3 AS days, '三亚海棠湾' AS loc,
         '["三天拍摄","含3晚五星酒店","精修80张","游艇1组","航拍1组","全套底片"]' AS inc, 10 AS stock, 1 AS status, 1 AS pin, 3 AS sort,
         '海棠湾海景 + 游艇 + 沙滩多场景，高端旅拍。' AS descr
  UNION ALL SELECT '三亚蜈支洲岛轻旅拍', 6888.00, 9280.00, 2, '三亚蜈支洲岛', '["两天拍摄","含2晚住宿","精修50张","航拍1组"]', 15, 1, 0, 0, '海岛轻旅拍，轻松出片。'
  UNION ALL SELECT '三亚亲子旅拍', 5288.00, 7280.00, 2, '三亚亚龙湾', '["两天拍摄","含2晚亲子房","精修45张","儿童造型3套"]', 12, 1, 0, 0, '适合带娃出行的家庭旅拍。'
) t;

-- 3.4 国风记·中式婚典
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_guofeng') u
JOIN package_category c ON c.name = '婚纱摄影'
JOIN (
  SELECT '明制汉服婚照' AS name, 5688.00 AS price, 7888.00 AS orig, 1 AS days, '古镇景区' AS loc,
         '["明制汉服2套","专业妆造","精修40张","古风实景","相册1本"]' AS inc, 18 AS stock, 1 AS status, 0 AS pin, 0 AS sort,
         '明制汉服婚照，古风实景拍摄。' AS descr
  UNION ALL SELECT '秀禾服中式婚照', 4288.00, 5888.00, 1, '室内影棚', '["秀禾服2套","专业妆造","精修30张","相册1本"]', 20, 1, 0, 0, '经典秀禾服中式婚照。'
  UNION ALL SELECT '凤冠霞帔·汉婚仪式', 16800.00, 22800.00, 1, '酒店宴会厅', '["凤冠霞帔","八抬大轿","传统六礼","司仪1名","摄影双机位"]', 5, 1, 0, 0, '完整汉婚仪式，仪式感拉满。'
) t;

-- 3.5 星辰影像工作室
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_xingchen') u
JOIN package_category c ON c.name = '婚礼跟拍'
JOIN (
  SELECT '婚礼全天跟拍·双机位' AS name, 3680.00 AS price, 4680.00 AS orig, 1 AS days, '不限' AS loc,
         '["双机位","全天跟拍","精修50张","婚礼微电影"]' AS inc, 30 AS stock, 1 AS status, 1 AS pin, 4 AS sort,
         '双机位全天跟拍，记录婚礼每个瞬间。' AS descr
  UNION ALL SELECT '婚礼微电影·电影感', 6880.00, 8880.00, 1, '不限', '["三机位","电影级调色","5分钟微电影","航拍1组"]', 15, 1, 0, 0, '电影级婚礼微电影，质感大片。'
  UNION ALL SELECT '婚礼航拍跟拍', 4680.00, 5880.00, 1, '不限', '["航拍1组","地面双机位","精修40张"]', 20, 1, 0, 0, '空中 + 地面双视角跟拍。'
) t;

-- 3.6 童颜儿童摄影
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_tongnian') u
JOIN package_category c ON c.name = '儿童摄影'
JOIN (
  SELECT '宝宝周岁纪念照' AS name, 1588.00 AS price, 2088.00 AS orig, 1 AS days, '室内影棚' AS loc,
         '["3套造型","专业引导师","精修25张","成长相册"]' AS inc, 25 AS stock, 1 AS status, 0 AS pin, 0 AS sort,
         '宝宝周岁纪念，安全影棚拍摄。' AS descr
  UNION ALL SELECT '儿童成长记录套餐', 1988.00, 2688.00, 1, '室内影棚', '["4套造型","精修30张","成长相册","底片全送"]', 20, 1, 0, 0, '记录孩子成长的每个阶段。'
  UNION ALL SELECT '亲子互动写真', 2288.00, 2988.00, 1, '户外公园', '["亲子造型3套","外景拍摄","精修35张"]', 18, 1, 0, 0, '温馨亲子互动写真。'
) t;

-- 3.7 团圆全家福馆
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_quanfu') u
JOIN package_category c ON c.name = '全家福'
JOIN (
  SELECT '三代同堂全家福' AS name, 1288.00 AS price, 1688.00 AS orig, 1 AS days, '室内影棚' AS loc,
         '["全家福1组","精修20张","相框1个"]' AS inc, 30 AS stock, 1 AS status, 0 AS pin, 0 AS sort,
         '三代同堂，团圆纪念。' AS descr
  UNION ALL SELECT '长辈纪念照', 888.00, 1188.00, 1, '室内影棚', '["单人造型2套","精修15张","相框1个"]', 40, 1, 0, 0, '为长辈留下珍贵纪念。'
  UNION ALL SELECT '上门全家福拍摄', 1888.00, 2488.00, 1, '上门服务', '["上门拍摄","精修25张","相册1本"]', 15, 1, 0, 0, '摄影师上门，居家拍摄更自然。'
) t;

-- 3.8 唯美时光婚纱馆
INSERT INTO wedding_package (category_id, name, cover_url, price, original_price, duration_days, location, includes_json, stock, status, home_pinned, home_sort, company_id, description)
SELECT c.id, t.name, '', t.price, t.orig, t.days, t.loc, t.inc, t.stock, t.status, t.pin, t.sort, u.id, t.descr
FROM (SELECT id FROM wedding_auth.sys_user WHERE username='demo_weimei') u
JOIN package_category c ON c.name = '婚纱摄影'
JOIN (
  SELECT '经典内景婚纱照' AS name, 2999.00 AS price, 4299.00 AS orig, 1 AS days, '室内影棚' AS loc,
         '["摄影师1名","化妆师1名","礼服4套","精修30张","相册1本"]' AS inc, 30 AS stock, 1 AS status, 0 AS pin, 0 AS sort,
         '经典内景婚纱照，高性价比之选。' AS descr
  UNION ALL SELECT '城市外景婚纱照', 3699.00, 4999.00, 1, '城市外景', '["摄影师1名","化妆师1名","礼服5套","精修35张"]', 25, 1, 0, 0, '城市地标外景，时尚大片。'
  UNION ALL SELECT '夜景灯光婚纱照', 4299.00, 5888.00, 1, '天台花园', '["夜景灯光","摄影师1名","化妆师1名","精修40张"]', 20, 1, 0, 0, '夜景灯光婚纱照，氛围感十足。'
) t;

-- ---------- 4. 校验 ----------
SELECT '商家数' AS item, COUNT(*) AS cnt FROM wedding_auth.sys_user WHERE username LIKE 'demo_%'
UNION ALL SELECT '套餐数', COUNT(*) FROM wedding_package.wedding_package WHERE company_id IN (
  SELECT id FROM wedding_auth.sys_user WHERE username LIKE 'demo_%');
