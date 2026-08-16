package com.tianji.aigc.agent;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
public abstract class AbstractAgent implements Agent {

    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private ChatClient chatClient;
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS"; // 用于标记大模型输出的状态，防止中断后继续输出


    // 输出结束的标记
    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();

    @Override
    public String process(String question, String sessionId) {
        var requestId = this.generateRequestId();
        return getChatClientRequest(sessionId, requestId, question)
                .call()
                .content();
    }


    public Flux<ChatEventVO> processStream(String question, String sessionId) {
        var conversationId = ChatService.getConversationId(sessionId);
        var outputBuilder = new StringBuilder();
        var userId = UserContext.getUser();
        var requestId = this.generateRequestId();
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        return getChatClientRequest(sessionId, requestId, question)
                .stream()
                .chatResponse()
                .doFirst(() -> hashOps.put(sessionId, "true")) // 第一次输出内容时执行
                .doOnComplete(() -> {
                    // 只有大模型正常回答完成后，才生成标题并更新会话信息
                    hashOps.delete(sessionId);
                    this.chatSessionService.update(
                            sessionId,
                            question,
                            outputBuilder.toString(),
                            userId
                    );
                })
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

    private ChatClient.ChatClientRequestSpec getChatClientRequest(String sessionId, String requestId, String question) {
        return this.chatClient.prompt()
                .system(promptSystem -> promptSystem.text(this.systemMessage()).params(this.systemMessageParams()))
                .advisors(advisor -> advisor.advisors(this.advisors()).params(this.advisorParams(sessionId, requestId)))
                .tools(this.tools())
                .toolContext(this.toolContext(sessionId, requestId))
                .user(question);
    }

    /**
     * 保存停止输出的记录
     *
     * @param conversationId 对话id
     * @param content        大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }

    private String generateRequestId() {
        return IdUtil.fastSimpleUUID();
    }

    @Override
    public Map<String, Object> advisorParams(String sessionId, String requestId) {
        var conversationId = ChatService.getConversationId(sessionId);
        return Map.of(ChatMemory.CONVERSATION_ID, conversationId);
    }

    @Override
    public void stop(String sessionId) {
        var hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }
}
