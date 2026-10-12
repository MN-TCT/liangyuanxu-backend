package com.wedding.video.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wedding_video")
public class WeddingVideo {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String title;
    private String description;
    private String videoUrl;
    private String coverUrl;
    private Integer durationSeconds;
    /** 1上架 0下架 */
    private Integer status;
    /** 归属商家(用户 id) */
    private Integer companyId;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
