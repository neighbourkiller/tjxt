package com.tianji.aigc.memory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import org.springframework.ai.chat.messages.*;

import java.util.Map;

/**
 * 消息转换工具类，提供消息对象与JSON字符串之间的转换功能，主要用于Redis存储格式转换
 */
public class MessageUtil {

    /**
     * 将Message对象转换为Redis存储格式的JSON字符串
     *
     * @param message 需要转换的原始消息对象
     * @return 符合Redis存储规范的JSON字符串
     */
    public static String toJson(Message message) {
        var myMessage = BeanUtil.toBean(message, MyMessage.class);
        // 设置消息内容
        myMessage.setTextContent(message.getText());
        if (message instanceof AssistantMessage assistantMessage) {
            // 设置工具调用信息
            myMessage.setToolCalls(assistantMessage.getToolCalls());

            // 如果AssistantMessage中包含工具调用信息，则尝试从ToolResultHolder中获取相关参数并设置到MyMessage中
//            if (assistantMessage.hasToolCalls()) {
            // 从消息的元数据中获取消息ID
            var messageId = MapUtil.getStr(
                    message.getMetadata(), Constant.ID
            );
            // 如果消息ID不为空，则尝试从ToolResultHolder中获取请求ID和相关参数
            if (messageId != null) {
                var requestId = Convert.toStr(
                        ToolResultHolder.get(
                                messageId,
                                Constant.REQUEST_ID
                        )
                );
                // 从ToolResultHolder中获取与请求ID相关的参数
                var params = ToolResultHolder.get(requestId);
                // 如果参数不为空，则将其设置到MyMessage中
                if (ObjectUtil.isNotEmpty(params)) {
                    myMessage.setParams(params);
                }
                // 移除ToolResultHolder中与消息ID相关的缓存，避免内存泄漏
                ToolResultHolder.remove(messageId);
            }
//            }
        }

        // 设置工具响应信息
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            myMessage.setToolResponses(toolResponseMessage.getResponses());
        }

        return JSONUtil.toJsonStr(myMessage);
    }

    /**
     * 将Redis存储的JSON字符串反序列化为对应的Message对象
     *
     * @param json Redis存储的JSON格式消息数据
     * @return 对应类型的Message对象
     * @throws RuntimeException 当无法识别的消息类型时抛出异常
     */
    public static Message toMessage(String json) {
        var myMessage = JSONUtil.toBean(json, MyMessage.class);
        var messageType = MessageType.valueOf(myMessage.getMessageType());
        switch (messageType) {
            case SYSTEM -> {
                return new SystemMessage(myMessage.getTextContent());
            }
            case USER -> {
                return UserMessage.builder()
                        .text(myMessage.getTextContent())
                        .metadata(myMessage.getMetadata())
                        .media(myMessage.getMedia())
                        .build();
            }
            case ASSISTANT -> {
//                return new AssistantMessage(myMessage.getTextContent(), myMessage.getMetadata(), myMessage.getToolCalls());
                // 使用自定义的MyAssistantMessage类来创建AssistantMessage对象，以便包含额外的参数信息
                return new MyAssistantMessage(
                        myMessage.getTextContent(),
                        myMessage.getMetadata(),
                        myMessage.getToolCalls(),
                        myMessage.getMedia(),
                        myMessage.getParams());
            }
            case TOOL -> {
                return new ToolResponseMessage(myMessage.getToolResponses(), myMessage.getMetadata());
            }
        }

        throw new RuntimeException("Message data conversion failed.");
    }

}
