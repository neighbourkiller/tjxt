package com.tianji.aigc.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.DateUtils;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder()  // 标记输出结束
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .build();
    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final ChatMemory chatMemory;

    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 获取对话id
        var conversationId = ChatService.getConversationId(sessionId);

        // 大模型输出内容的缓存器，用于在输出中断后的数据存储
        var outputBuilder = new StringBuilder();

        var hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        // 生成请求id
        var requestId = IdUtil.simpleUUID();

        return this.chatClient.prompt()
                .system(promptSystem -> {
                    promptSystem
                            .text(this.systemPromptConfig.getChatSystemMessage().get()) // 设置系统提示语
                            .param("now", DateUtils.now()); // 设置当前时间参数
                })
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(Map.of(Constant.REQUEST_ID, requestId)) // 向工具上下文中传递请求id
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> hashOps.put(sessionId, "true")) // 第一次输出内容时执行
                .doOnComplete(() -> hashOps.delete(sessionId)) // 完成时执行，删除标识
                .doOnError(throwable -> hashOps.delete(sessionId)) // 出现异常时，删除标识
                .doOnCancel(() -> {
                            // 当输出被取消时，保存输出的内容到历史记录中
                            this.saveStopHistoryRecord(conversationId, outputBuilder.toString());
                        }
                )
                .takeWhile(s -> hashOps.get(sessionId) != null)
                .mapNotNull(chatResponse -> {
                    // 获取大模型的输出的内容
                    var result = chatResponse.getResult();
                    if (result == null || result.getOutput() == null) {
                        return null;
                    }

                    var finishReason = result.getMetadata().getFinishReason();
                    if (Constant.STOP.equalsIgnoreCase(finishReason)) {
                        log.debug("结束原因: {}", finishReason);
                        // 获取消息id，并将请求id存储到工具结果缓存中，以便后续工具调用可以使用
                        var messageId = chatResponse.getMetadata().getId();
                        ToolResultHolder.put(messageId, Constant.REQUEST_ID, requestId);
                    }

                    var text = result.getOutput().getText();
                    if (text == null || text.isEmpty()) {
                        return null;
                    }

                    // 追加到输出内容中
                    outputBuilder.append(text);


                    // 封装响应对象
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    var result = ToolResultHolder.get(requestId);
                    if (ObjectUtil.isNotEmpty(result)) {
                        // 删除工具结果缓存，避免内存泄漏
                        ToolResultHolder.remove(requestId);
                        // 如果工具结果不为空，则输出工具结果
                        return Flux.just(ChatEventVO.builder()
                                .eventType(ChatEventTypeEnum.PARAM.getValue())
                                .eventData(result)
                                .build(), STOP_EVENT);
                    }
                    return Flux.just(STOP_EVENT);// 结束标识
                }));
    }

    /**
     * 保存停止输出的记录
     *
     * @param conversationId 会话id
     * @param content        大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }


    @Override
    public void stop(String sessionId) {
        var hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }

}
