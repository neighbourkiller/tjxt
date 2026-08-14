package com.tianji.aigc.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.aigc.entity.ChatMemoryMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMemoryMessageMapper extends BaseMapper<ChatMemoryMessage> {
}
