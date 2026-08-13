package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.DateUtils;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;

    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();


    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 获取对话id
        var conversationId = ChatService.getConversationId(sessionId);

        return this.chatClient.prompt()
                .system(promptSystem -> {
                    promptSystem
                            .text(this.systemPromptConfig.getChatSystemMessage().get()) // 设置系统提示语
                            .param("now", DateUtils.now()); // 设置当前时间参数
                })
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID,conversationId))
                .user(question)
                .stream()
                .chatResponse()
                .<ChatEventVO>handle((chatResponse, sink) -> {
                    if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                        return;
                    }
                    String text = chatResponse.getResult()
                            .getOutput()
                            .getText();
                    if (text == null || text.isEmpty())
                        return;

                    sink.next(ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build());
                })
                .concatWithValues(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.STOP.getValue())
                        .build())
                .doFirst(() -> GENERATE_STATUS.put(sessionId, true)) // 订阅时标记为生成中
                .doOnError(throwable -> GENERATE_STATUS.remove(sessionId)) // 出现异常时，删除标识
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId)) // 完成时执行，删除标识
                .takeWhile(response -> { // 通过返回值来控制Flux流是否继续，true：继续，false：终止
                    return GENERATE_STATUS.getOrDefault(sessionId, false);
                });
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }
}
