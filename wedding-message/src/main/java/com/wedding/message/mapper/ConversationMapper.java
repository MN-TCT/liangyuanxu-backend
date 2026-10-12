package com.wedding.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wedding.message.module.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
