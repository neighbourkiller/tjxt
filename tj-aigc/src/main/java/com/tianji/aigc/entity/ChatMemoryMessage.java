package com.tianji.aigc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * MySQL聊天记录实体类
 */


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_memory_message")
public class ChatMemoryMessage {

    @TableId(type= IdType.ASSIGN_ID)
    private Long id;

    private String conversationId;

    private Integer messageIndex;

    private String messageJson;

    private LocalDateTime createTime;

}
