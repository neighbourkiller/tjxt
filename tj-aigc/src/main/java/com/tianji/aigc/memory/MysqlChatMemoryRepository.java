package com.tianji.aigc.memory;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.aigc.entity.ChatMemoryMessage;
import com.tianji.aigc.mapper.ChatMemoryMessageMapper;
import com.tianji.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Transactional
public class MysqlChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMemoryMessageMapper memoryMessageMapper;


    @Override
    public List<String> findConversationIds() {
        return memoryMessageMapper.selectObjs(
                        Wrappers.<ChatMemoryMessage>lambdaQuery()
                                .select(ChatMemoryMessage::getConversationId)
                                .groupBy(ChatMemoryMessage::getConversationId)
                ).stream()
                .map(String.class::cast)
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return memoryMessageMapper.selectList(
                        Wrappers.<ChatMemoryMessage>lambdaQuery()
                                .select(ChatMemoryMessage::getMessageJson)
                                .eq(ChatMemoryMessage::getConversationId, conversationId)
                                .orderByAsc(ChatMemoryMessage::getMessageIndex)
                ).stream()
                .map(row -> MessageUtil.toMessage(row.getMessageJson()))
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.notEmpty(messages, "消息列表不能为空");

        deleteByConversationId(conversationId);
        for (int i = 0; i < messages.size(); i++) {
            memoryMessageMapper.insert(ChatMemoryMessage.builder()
                    .conversationId(conversationId)
                    .messageIndex(i)
                    .messageJson(MessageUtil.toJson(messages.get(i)))
                    .createTime(DateUtils.now())
                    .build());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        memoryMessageMapper.delete(
                Wrappers.<ChatMemoryMessage>lambdaQuery()
                        .eq(ChatMemoryMessage::getConversationId, conversationId)
        );
    }
}
