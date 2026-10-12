package com.wedding.message.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wedding.common.AuthContext;
import com.wedding.common.BizException;
import com.wedding.common.PageQuery;
import com.wedding.common.Result;
import com.wedding.message.mapper.ChatMessageMapper;
import com.wedding.message.mapper.ConversationMapper;
import com.wedding.message.mapper.NotificationMapper;
import com.wedding.message.module.ChatMessage;
import com.wedding.message.module.Conversation;
import com.wedding.message.module.Notification;
import com.wedding.message.ws.ChatPushService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final ConversationMapper convMapper;
    private final ChatMessageMapper msgMapper;
    private final NotificationMapper notifMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ChatPushService pushService;

    public MessageController(ConversationMapper convMapper, ChatMessageMapper msgMapper,
                             NotificationMapper notifMapper, JdbcTemplate jdbcTemplate,
                             ChatPushService pushService) {
        this.convMapper = convMapper;
        this.msgMapper = msgMapper;
        this.notifMapper = notifMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.pushService = pushService;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("message-service ok");
    }

    private void notify(String targetRole, String type, String title, String content) {
        Notification n = new Notification();
        n.setTargetRole(targetRole);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        notifMapper.insert(n);
    }

    /** 批量填充会话的商家名称/头像与套餐名称（跨库查询 wedding_auth / wedding_package） */
    private void fillConversationInfo(List<Conversation> rows) {
        if (rows == null || rows.isEmpty()) return;
        List<Long> companyIds = new ArrayList<>();
        List<Long> packageIds = new ArrayList<>();
        for (Conversation c : rows) {
            if (c.getCompanyId() != null && !companyIds.contains(c.getCompanyId())) companyIds.add(c.getCompanyId());
            if (c.getPackageId() != null && !packageIds.contains(c.getPackageId())) packageIds.add(c.getPackageId());
        }
        Map<Long, Map<String, Object>> companyMap = new HashMap<>();
        if (!companyIds.isEmpty()) {
            StringBuilder ph = new StringBuilder();
            for (int i = 0; i < companyIds.size(); i++) {
                if (i > 0) ph.append(',');
                ph.append('?');
            }
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT id, shop_name, nickname, avatar FROM wedding_auth.sys_user WHERE id IN (" + ph + ")",
                    companyIds.toArray());
            for (Map<String, Object> u : users) {
                companyMap.put(((Number) u.get("id")).longValue(), u);
            }
        }
        Map<Long, String> pkgMap = new HashMap<>();
        if (!packageIds.isEmpty()) {
            try {
                StringBuilder ph = new StringBuilder();
                for (int i = 0; i < packageIds.size(); i++) {
                    if (i > 0) ph.append(',');
                    ph.append('?');
                }
                List<Map<String, Object>> pkgs = jdbcTemplate.queryForList(
                        "SELECT id, name FROM wedding_package.wedding_package WHERE id IN (" + ph + ")",
                        packageIds.toArray());
                for (Map<String, Object> p : pkgs) {
                    pkgMap.put(((Number) p.get("id")).longValue(), String.valueOf(p.get("name")));
                }
            } catch (Exception ignored) {
                // 套餐库不可用时忽略
            }
        }
        for (Conversation c : rows) {
            if (c.getCompanyId() != null) {
                Map<String, Object> u = companyMap.get(c.getCompanyId());
                if (u != null) {
                    Object shop = u.get("shop_name");
                    Object nick = u.get("nickname");
                    c.setCompanyName(shop != null && StringUtils.hasText(String.valueOf(shop))
                            ? String.valueOf(shop)
                            : (nick == null ? "" : String.valueOf(nick)));
                    c.setCompanyAvatar(u.get("avatar") == null ? "" : String.valueOf(u.get("avatar")));
                }
            }
            if (c.getPackageId() != null) {
                c.setPackageName(pkgMap.getOrDefault(c.getPackageId(), ""));
            }
        }
    }

    // 客户发起咨询（公开）：创建会话 + 首条消息 + 商家提醒
    @PostMapping("/conversations")
    public Result<Map<String, Object>> createConversation(@RequestBody Map<String, Object> body) {
        Long userId = body.get("user_id") == null ? null : Long.valueOf(body.get("user_id").toString());
        Long companyId = body.get("company_id") == null ? null : Long.valueOf(body.get("company_id").toString());
        Long packageId = body.get("package_id") == null ? null : Long.valueOf(body.get("package_id").toString());
        String userName = (String) body.get("user_name");
        String content = (String) body.get("content");
        if (!StringUtils.hasText(userName) || !StringUtils.hasText(content)) {
            throw new BizException("姓名和咨询内容不能为空");
        }
        // 如果已有同用户同商家的进行中会话，直接返回
        if (userId != null && companyId != null) {
            Conversation exist = convMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                    .eq(Conversation::getUserId, userId)
                    .eq(Conversation::getCompanyId, companyId)
                    .eq(Conversation::getStatus, 0)
                    .orderByDesc(Conversation::getId).last("limit 1"));
            if (exist != null) {
                ChatMessage m = new ChatMessage();
                m.setConversationId(exist.getId());
                m.setSender("customer");
                m.setSenderName(userName);
                m.setContent(content);
                m.setMsgType("text");
                m.setIsRead(0);
                msgMapper.insert(m);
                Conversation up = new Conversation();
                up.setId(exist.getId());
                up.setLastMessage(content);
                up.setLastMessageAt(LocalDateTime.now());
                convMapper.updateById(up);
                // 实时推送：新消息推给商家
                if (exist.getCompanyId() != null) {
                    pushService.pushMessage(exist.getCompanyId().intValue(), exist.getId(), m);
                    pushService.pushConversationUpdate(exist.getCompanyId().intValue(), exist.getId());
                }
                Map<String, Object> data = new HashMap<>();
                data.put("conversation_id", exist.getId());
                return Result.ok(data, "已加入咨询");
            }
        }
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setCompanyId(companyId);
        c.setPackageId(packageId);
        c.setUserName(userName);
        c.setUserPhone((String) body.getOrDefault("user_phone", ""));
        c.setStatus(0);
        c.setLastMessage(content);
        c.setLastMessageAt(LocalDateTime.now());
        convMapper.insert(c);
        ChatMessage m = new ChatMessage();
        m.setConversationId(c.getId());
        m.setSender("customer");
        m.setSenderName(userName);
        m.setContent(content);
        m.setMsgType("text");
        m.setIsRead(0);
        msgMapper.insert(m);
        notify("admin", "new_consult", "新的咨询", userName + " 发起了咨询：" + content);
        // 实时推送：新会话 + 首条消息推给商家
        if (c.getCompanyId() != null) {
            pushService.pushMessage(c.getCompanyId().intValue(), c.getId(), m);
            pushService.pushConversationUpdate(c.getCompanyId().intValue(), c.getId());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("conversation_id", c.getId());
        return Result.ok(data, "咨询已提交");
    }

    // 用户端：获取我的会话列表（含商家名称/头像、未读数）；支持分页
    @GetMapping("/my-conversations")
    public Result<Object> myConversations(@RequestParam Long userId,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer pageSize) {
        int pg = page == null ? 1 : PageQuery.normalizePage(page);
        int ps = pageSize == null ? 20 : PageQuery.normalizePageSize(pageSize);
        var wrapper = Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getLastMessageAt);
        List<Conversation> list;
        long total;
        if (page == null) {
            // 兼容旧调用：返回全量数组
            list = convMapper.selectList(wrapper);
            total = list.size();
        } else {
            Page<Conversation> p = convMapper.selectPage(new Page<>(pg, ps), wrapper);
            list = p.getRecords();
            total = p.getTotal();
        }
        // 每条会话统计商家未读消息（sender=admin 且未读）
        for (Conversation c : list) {
            Long unread = msgMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getConversationId, c.getId())
                    .eq(ChatMessage::getSender, "admin")
                    .eq(ChatMessage::getIsRead, 0));
            c.setUnreadCustomer(unread);
        }
        fillConversationInfo(list);
        if (page == null) {
            return Result.ok(list);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", pg);
        data.put("pageSize", ps);
        data.put("hasMore", (long) pg * ps < total);
        return Result.ok(data);
    }

    // 用户端：未读消息总数（个人中心展示）
    @GetMapping("/my-unread-count")
    public Result<Map<String, Object>> myUnreadCount(@RequestParam Long userId) {
        List<Conversation> list = convMapper.selectList(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getUserId, userId));
        long total = 0;
        for (Conversation c : list) {
            total += msgMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getConversationId, c.getId())
                    .eq(ChatMessage::getSender, "admin")
                    .eq(ChatMessage::getIsRead, 0));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("count", total);
        data.put("conversations", list.size());
        return Result.ok(data);
    }

    // 用户端：获取单个会话消息
    @GetMapping("/conversations/{id}/user-messages")
    public Result<List<ChatMessage>> userMessages(@PathVariable Integer id) {
        List<ChatMessage> list = msgMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, id).orderByAsc(ChatMessage::getId));
        // 标记商家消息为已读
        ChatMessage update = new ChatMessage();
        update.setIsRead(1);
        msgMapper.update(update, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getConversationId, id)
                .eq(ChatMessage::getSender, "admin")
                .eq(ChatMessage::getIsRead, 0));
        return Result.ok(list);
    }

    // 商家会话列表（按当前登录商家过滤）
    @GetMapping("/conversations")
    public Result<Map<String, Object>> conversations(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     @RequestParam(required = false) Integer status,
                                                     HttpServletRequest request) {
        Integer userId = AuthContext.userId(request);
        String role = AuthContext.role(request);
        boolean isAdmin = "admin".equals(role);
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Page<Conversation> pg = new Page<>(page, pageSize);
        convMapper.selectPage(pg, Wrappers.<Conversation>lambdaQuery()
                .eq(status != null, Conversation::getStatus, status)
                // 非管理员只看自己商家的会话；管理员看全部
                .eq(!isAdmin && userId != null, Conversation::getCompanyId, userId)
                .orderByDesc(Conversation::getUpdatedAt));
        for (Conversation c : pg.getRecords()) {
            Long unread = msgMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getConversationId, c.getId())
                    .eq(ChatMessage::getSender, "customer")
                    .eq(ChatMessage::getIsRead, 0));
            c.setUnreadCustomer(unread);
        }
        fillConversationInfo(pg.getRecords());
        Map<String, Object> data = new HashMap<>();
        data.put("list", pg.getRecords());
        data.put("total", pg.getTotal());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.ok(data);
    }

    // 会话消息（并自动标记客户消息已读）
    @GetMapping("/conversations/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Integer id) {
        List<ChatMessage> list = msgMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, id).orderByAsc(ChatMessage::getId));
        ChatMessage update = new ChatMessage();
        update.setIsRead(1);
        msgMapper.update(update, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getConversationId, id)
                .eq(ChatMessage::getSender, "customer")
                .eq(ChatMessage::getIsRead, 0));
        return Result.ok(list);
    }

    // 商家回复
    @PostMapping("/conversations/{id}/admin-message")
    public Result<Void> adminMessage(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (!StringUtils.hasText(content)) {
            throw new BizException("回复内容不能为空");
        }
        ChatMessage m = new ChatMessage();
        m.setConversationId(id);
        m.setSender("admin");
        m.setSenderName("管理员");
        m.setContent(content);
        m.setMsgType("text");
        m.setIsRead(0);
        msgMapper.insert(m);
        Conversation up = new Conversation();
        up.setId(id);
        up.setLastMessage(content);
        up.setLastMessageAt(LocalDateTime.now());
        convMapper.updateById(up);
        // WebSocket 实时推送：商家回复推给用户（userId）
        Conversation conv = convMapper.selectById(id);
        if (conv != null && conv.getUserId() != null) {
            pushService.pushMessage(conv.getUserId().intValue(), id, m);
            pushService.pushConversationUpdate(conv.getUserId().intValue(), id);
        }
        return Result.ok(null, "回复成功");
    }

    // 用户端回复（公开，模拟用户端）
    @PostMapping("/conversations/{id}/customer-message")
    public Result<Void> customerMessage(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (!StringUtils.hasText(content)) {
            throw new BizException("消息内容不能为空");
        }
        Conversation c = convMapper.selectById(id);
        if (c == null) {
            throw new BizException(404, "会话不存在");
        }
        ChatMessage m = new ChatMessage();
        m.setConversationId(id);
        m.setSender("customer");
        m.setSenderName(c.getUserName());
        m.setContent(content);
        m.setMsgType("text");
        m.setIsRead(0);
        msgMapper.insert(m);
        Conversation up = new Conversation();
        up.setId(id);
        up.setLastMessage(content);
        up.setLastMessageAt(LocalDateTime.now());
        convMapper.updateById(up);
        notify("admin", "new_message", "收到新消息", c.getUserName() + "：" + content);
        // WebSocket 实时推送：用户消息推给商家（companyId）
        if (c.getCompanyId() != null) {
            pushService.pushMessage(c.getCompanyId().intValue(), id, m);
            pushService.pushConversationUpdate(c.getCompanyId().intValue(), id);
        }
        return Result.ok(null, "消息已发送");
    }

    // 标记会话已读
    @PutMapping("/conversations/{id}/read")
    public Result<Void> read(@PathVariable Integer id) {
        ChatMessage update = new ChatMessage();
        update.setIsRead(1);
        msgMapper.update(update, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getConversationId, id)
                .eq(ChatMessage::getSender, "customer")
                .eq(ChatMessage::getIsRead, 0));
        return Result.ok(null, "已标记已读");
    }

    // 开启/关闭会话
    @PutMapping("/conversations/{id}/status")
    public Result<Void> status(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            throw new BizException("缺少 status");
        }
        Conversation up = new Conversation();
        up.setId(id);
        up.setStatus(status);
        convMapper.updateById(up);
        return Result.ok(null, "会话状态已更新");
    }

    // ============ 提醒 ============
    @GetMapping("/notifications")
    public Result<Map<String, Object>> notifications(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     @RequestParam(required = false, defaultValue = "0") int unread) {
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Page<Notification> pg = new Page<>(page, pageSize);
        notifMapper.selectPage(pg, Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getTargetRole, "admin")
                .eq(unread == 1, Notification::getIsRead, 0)
                .orderByDesc(Notification::getId));
        Map<String, Object> data = new HashMap<>();
        data.put("list", pg.getRecords());
        data.put("total", pg.getTotal());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.ok(data);
    }

    @GetMapping("/notifications/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        Long count = notifMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getTargetRole, "admin").eq(Notification::getIsRead, 0));
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return Result.ok(data);
    }

    @PutMapping("/notifications/read")
    public Result<Void> notificationsRead(@RequestBody(required = false) Map<String, Integer> body) {
        Integer id = body == null ? null : body.get("id");
        Notification update = new Notification();
        update.setIsRead(1);
        if (id != null) {
            notifMapper.update(update, Wrappers.<Notification>lambdaUpdate()
                    .eq(Notification::getId, id).eq(Notification::getTargetRole, "admin"));
        } else {
            notifMapper.update(update, Wrappers.<Notification>lambdaUpdate()
                    .eq(Notification::getTargetRole, "admin").eq(Notification::getIsRead, 0));
        }
        return Result.ok(null, "已标记已读");
    }
}
