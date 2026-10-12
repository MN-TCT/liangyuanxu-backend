# 婚庆婚纱摄影套餐后台管理 - Spring Boot 微服务后端

基于 **Spring Boot 3 + Maven 多模块** 的微服务后端，前端使用 vue-vben-admin（vben 框架）。

## 架构

```
浏览器 (vue-vben-admin)
   │
   ▼
wedding-gateway (Spring Cloud Gateway, :3000)     ← 唯一入口
   ├── /api/auth     ──► wedding-auth      (:3001)   数据库 wedding_auth
   ├── /api/package  ──► wedding-package   (:3002)   数据库 wedding_package
   ├── /api/booking  ──► wedding-booking   (:3003)   数据库 wedding_booking
   ├── /api/video    ──► wedding-video     (:3004)   数据库 wedding_video
   └── /api/message  ──► wedding-message   (:3005)   数据库 wedding_message
```

- 每个微服务连接**独立数据库**，符合微服务数据隔离原则。
- 认证：`auth` 服务用 **jjwt** 签发 JWT，各服务通过共享密钥（`JwtUtil`）自行校验。密钥长度 ≥ 256 位（HMAC-SHA256 要求）。
- 网关：**Spring Cloud Gateway**（WebFlux 响应式），配置文件路由，无需注册中心。

## 技术栈

| 项 | 版本/选型 |
|----|-----------|
| JDK | 17 |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.1 |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8 |
| JWT | jjwt 0.12.5 |
| 前端 | vue-vben-admin (Vue3 + Ant Design Vue) |

## 模块结构

```
backend-springboot/
├── pom.xml                 # 父工程（聚合 7 个模块）
├── wedding-common/         # 公共模块：Result 统一响应、BizException、JwtUtil、JwtAuthFilter、全局异常
├── wedding-gateway/        # 网关：Spring Cloud Gateway 路由转发
├── wedding-auth/           # 认证服务：登录、注册、JWT 签发
├── wedding-package/        # 套餐服务：套餐 + 分类 CRUD
├── wedding-booking/        # 预约服务：客户预约订单 + 统计
├── wedding-video/          # 视频服务：商家上传婚庆视频、用户端浏览
├── wedding-message/        # 消息服务：对话咨询 + 消息提醒
├── start-all.bat           # 一键启动全部 6 个服务
└── stop-all.bat            # 一键停止全部服务
```

## 构建与启动

```bash
cd C:\Users\ZLT\Desktop\vben_admin\backend-springboot

# 1. 构建（首次需联网下载依赖）
mvn clean package -DskipTests

# 2. 启动全部微服务（前台分离进程，可在任意终端运行）
start-all.bat
# 停止
stop-all.bat
```

启动后访问 `http://localhost:3000`（网关）。各服务健康检查：`http://localhost:3000/api/auth/health` 等。

> 数据库已建好并复用（auth/package/booking/video/message 五个库），无需重新建表。默认管理员 `admin / 123456`。

> ⚠️ 内存提示：本机同时运行 6 个 JVM，脚本已给每个 JVM 分配小堆（-Xmx180m）。若启动失败提示内存不足，请关闭部分大内存程序（如多个 VSCode、浏览器）后重试。

## 接口清单（统一经网关 http://localhost:3000）

### 认证 /api/auth
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /login | 登录，返回 token | 否 |
| POST | /register | 注册账号 | 否 |

### 套餐与分类 /api/package
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET  | /list | 套餐列表（page/pageSize/keyword/categoryId/status） | 否 |
| GET  | /:id | 套餐详情 | 否 |
| GET  | /categories | 分类列表 | 否 |
| POST | /categories | 新建分类 | 是 |
| PUT  | /categories/:id | 更新分类 | 是 |
| DELETE | /categories/:id | 删除分类 | 是 |
| POST | / | 新建套餐 | 是 |
| PUT  | /:id | 更新套餐 | 是 |
| DELETE | /:id | 删除套餐 | 是 |

### 预约订单 /api/booking
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET  | /list | 订单列表（page/pageSize/status/keyword） | 是 |
| GET  | /:id | 订单详情 | 是 |
| POST | / | 客户提交预约（公开） | 否 |
| PUT  | /:id | 更新订单/状态 | 是 |
| DELETE | /:id | 删除订单 | 是 |
| GET  | /stats/overview | 订单状态统计 | 是 |

### 婚庆视频 /api/video
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /upload | 商家上传视频（multipart: file+title+description+cover_url+duration_seconds） | 是 |
| GET  | /list | 视频列表（all=1 商家看全部含下架；默认仅上架，用户端浏览） | 否 |
| GET  | /:id | 视频详情 | 否 |
| PUT  | /:id | 更新视频 | 是 |
| DELETE | /:id | 删除视频（含本地文件） | 是 |
| GET  | /static/* | 视频文件播放（浏览器直接访问 video_url） | 否 |

### 对话咨询 + 消息提醒 /api/message
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /conversations | 客户发起咨询（建会话+首条消息+商家提醒） | 否 |
| GET  | /conversations | 商家会话列表（含客户未读数） | 是 |
| GET  | /conversations/:id/messages | 会话消息（自动标记客户消息已读） | 是 |
| POST | /conversations/:id/admin-message | 商家回复 | 是 |
| POST | /conversations/:id/customer-message | 用户端回复（公开，触发新消息提醒） | 否 |
| PUT  | /conversations/:id/read | 标记会话已读 | 是 |
| PUT  | /conversations/:id/status | 开启/关闭会话 | 是 |
| GET  | /notifications | 商家提醒列表（new_consult/new_message） | 是 |
| GET  | /notifications/unread-count | 未读提醒数（前端角标） | 是 |
| PUT  | /notifications/read | 标记提醒已读 | 是 |

鉴权方式：请求头 `Authorization: Bearer <token>`。

## 数据库表（已在 MySQL 中）

| 库 | 表 | 关键字段 |
|----|-----|----------|
| wedding_auth | sys_user | username, password_hash, nickname, role, status |
| wedding_package | package_category / wedding_package | 分类、套餐（价格/包含内容/库存/上下架） |
| wedding_booking | booking_order | customer_name, phone, package_id, status(0-3) |
| wedding_video | wedding_video | title, video_url, cover_url, status(1上架0下架) |
| wedding_message | conversation / chat_message / notification | 会话、消息(sender customer/admin)、提醒(is_read) |

## 前端（vue-vben-admin）对接

vben 前端 `apps/web-antd/.env.development` 的 `VITE_GLOB_API_URL=/api`，与网关前缀对齐。对接方式：
- **推荐**：在 `apps/web-antd/vite.config.mts` 的 `server.proxy` 加一条 `/api → http://localhost:3000` 转发，免跨域。
- 登录接口 `POST /api/auth/login`（body `{username,password}`），返回 `data.token`；请求需带 `Authorization: Bearer <token>`。
