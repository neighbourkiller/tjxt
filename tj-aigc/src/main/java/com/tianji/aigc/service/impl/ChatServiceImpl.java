package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        return this.chatClient.prompt()
                .system(promptSystem ->{promptSystem
                        .text(this.systemPromptConfig.getChatSystemMessage().get()) // 设置系统提示语
                        .param("now", DateUtils.now()); // 设置当前时间参数
                } )
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
                        .build());
    }
}
