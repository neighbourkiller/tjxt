package com.tianji.aigc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.config.SessionProperties;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.enums.MessageTypeEnum;
import com.tianji.aigc.mapper.ChatSessionMapper;
import com.tianji.aigc.memory.MyAssistantMessage;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final SessionProperties sessionProperties;

    @Override
    public SessionVO createSession(Integer num) {
        var sessionVO = BeanUtil.toBean(sessionProperties, SessionVO.class);
        // 随机获取examples
        sessionVO.setExamples(RandomUtil.randomEleList(sessionProperties.getExamples(), num));

        // 随机生成sessionId
        sessionVO.setSessionId(IdUtil.fastSimpleUUID());

        // 构建持久化对象，并持久化
        var chatSession = ChatSession.builder()
                .sessionId(sessionVO.getSessionId())
                .userId(UserContext.getUser())
                .build();
        super.save(chatSession);

        return sessionVO;
    }

    @Override
    public List<SessionVO.Example> hotExample(Integer num) {
/*        List<SessionVO.Example> examples = sessionProperties.getExamples();


        return List.of();*/

        return RandomUtil.randomEleList(sessionProperties.getExamples(), num);
    }

    private final ChatMemory chatMemory;

    /**
     * 根据会话ID查询历史消息
     *
     * @param sessionId 会话ID
     * @return 历史消息列表
     */
    @Override
    public List<MessageVO> queryBySessionId(String sessionId) {
        // 根据会话ID获取对话ID
        String conversationId = ChatService.getConversationId(sessionId);
        // 从Redis中获取历史消息
        List<Message> messageList = this.chatMemory.get(conversationId);
        // 过滤并转换消息列表
        return StreamUtil.of(messageList)
                // 过滤掉非用户消息和助手消息
                .filter(message -> message.getMessageType() == MessageType.ASSISTANT || message.getMessageType() == MessageType.USER)
                // 转换为MessageVO对象
                .map(message -> {
                    if (message instanceof MyAssistantMessage myAssistantMessage) {
                        return MessageVO.builder()
                                .content(myAssistantMessage.getText())
                                .type(MessageTypeEnum.valueOf(myAssistantMessage.getMessageType().name()))
                                .params(myAssistantMessage.getParams())
                                .build();
                    }

                    return MessageVO.builder()
                            .content(message.getText())
                            .type(MessageTypeEnum.valueOf(message.getMessageType().name()))
                            .build();
                })
                .toList();
    }

    private final ChatModel chatModel;

    @Async
    @Override
    public void update(String sessionId, String question, String answer, Long userId) {
        ChatSession chatSession = super.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .one();
        if (chatSession == null) {
            return;
        }

        // 只在会话还没有标题时调用大模型，后续轮次仅更新时间
        if (StrUtil.isBlank(chatSession.getTitle())
                && StrUtil.isNotBlank(question)
                && StrUtil.isNotBlank(answer)) {
            String title = generateTitle(question, answer, sessionId);
            super.lambdaUpdate()
                    .eq(ChatSession::getId, chatSession.getId())
                    .and(wrapper -> wrapper
                            .isNull(ChatSession::getTitle)
                            .or()
                            .eq(ChatSession::getTitle, ""))
                    .set(ChatSession::getTitle, title)
                    .set(ChatSession::getUpdateTime, LocalDateTimeUtil.now())
                    .set(ChatSession::getUpdater, userId)
                    .update();
            return;
        }

        super.lambdaUpdate()
                .eq(ChatSession::getId, chatSession.getId())
                .set(ChatSession::getUpdateTime, LocalDateTimeUtil.now())
                .set(ChatSession::getUpdater, userId)
                .update();
    }

    private String generateTitle(String question, String answer, String sessionId) {
        try {
            var prompt = new Prompt(
                    List.of(
                            new SystemMessage("""
                                    你是聊天会话标题生成器。
                                    请根据用户问题和助手回答，总结出能准确概括本轮对话的中文标题。

                                    要求：
                                    1. 标题控制在8到20个汉字
                                    2. 只输出标题，不要解释
                                    3. 不要添加引号、句号或“标题：”前缀
                                    4. 不要继续回答用户问题
                                    """),
                            new UserMessage("""
                                    用户问题：
                                    %s

                                    助手回答：
                                    %s
                                    """.formatted(
                                    StrUtil.sub(question, 0, 2000),
                                    StrUtil.sub(answer, 0, 6000)
                            ))
                    ),
                    ChatOptions.builder()
                            .temperature(0.2)
                            .maxTokens(300)
                            .build()
            );

            var response = this.chatModel.call(prompt);
            var rawTitle = response.getResult().getOutput().getText();
            var title = cleanTitle(rawTitle);
            if (StrUtil.isNotBlank(title)) {
                log.debug("会话标题生成成功，sessionId={}, title={}", sessionId, title);
                return title;
            }
        } catch (Exception e) {
            log.warn("生成会话标题失败，sessionId={}", sessionId, e);
        }
        log.debug("会话标题生成失败，使用用户问题作为降级标题，sessionId={}, question={}", sessionId, question);
        // 标题生成失败时，使用用户问题作为降级标题
        return StrUtil.sub(question.strip(), 0, 100);
    }

    private String cleanTitle(String rawTitle) {
        if (StrUtil.isBlank(rawTitle)) {
            return null;
        }

        String title = rawTitle
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
                .replaceFirst("^标题\\s*[:：]\\s*", "")
                .replaceAll("^[\"'“”]+|[\"'“”]+$", "");

        return StrUtil.sub(title, 0, 100);
    }

    @Override
    public Map<String, List<ChatSessionVO>> getHistory() {
        var userId = UserContext.getUser();
        var list = super.lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .isNotNull(ChatSession::getTitle)
                .orderByDesc(ChatSession::getUpdateTime)
                .list();
        if (CollUtil.isEmpty(list)) {
            log.info("用户{}没有历史会话", userId);
            return Map.of();
        }
        var chatSerssionVOList = CollStreamUtil.toList(list, chatSession ->
                ChatSessionVO.builder()
                        .sessionId(chatSession.getSessionId())
                        .title(chatSession.getTitle())
                        .createTime(chatSession.getCreateTime())
                        .build()
        );
        final var TODAY = "当天";
        final var LAST_30_DAYS = "最近30天";
        final var LAST_YEAR = "最近1年";
        final var MORE_THAN_YEAR = "1年以上";
        var now = LocalDateTime.now().toLocalDate();

        // 按照时间范围分组
        return CollStreamUtil.groupByKey(chatSerssionVOList, vo -> {
            long between = Math.abs(ChronoUnit.DAYS.between(vo.getCreateTime().toLocalDate(), now));
            if (between == 0) {
                return TODAY;
            } else if (between <= 30) {
                return LAST_30_DAYS;
            } else if (between <= 365) {
                return LAST_YEAR;
            } else {
                return MORE_THAN_YEAR;
            }
        });
    }

    @Override
    public void deleteHistory(String sessionId) {
        var userId = UserContext.getUser();
        // 删除数据库中的会话记录
        var lambdaQueryWrapper = Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId);
        super.remove(lambdaQueryWrapper);

        // 删除Redis中的会话记录
        var conversationId = ChatService.getConversationId(sessionId);
        this.chatMemory.clear(conversationId);
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        var updateWrapper = Wrappers.<ChatSession>lambdaUpdate()
                .set(ChatSession::getTitle, StrUtil.sub(title, 0, 100)) // 限制标题长度为100个字符
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, UserContext.getUser());
        super.update(updateWrapper);
    }
}
