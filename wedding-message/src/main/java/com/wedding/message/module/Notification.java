package com.wedding.message.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String targetRole;
    private String type;
    private String title;
    private String content;
    private Integer isRead;
    private LocalDateTime createdAt;
}
