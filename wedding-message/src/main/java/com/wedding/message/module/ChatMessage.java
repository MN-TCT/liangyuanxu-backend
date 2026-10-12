package com.wedding.message.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer conversationId;
    /** customer/admin */
    private String sender;
    private String senderName;
    private String content;
    private String msgType;
    private Integer isRead;
    private LocalDateTime createdAt;
}
