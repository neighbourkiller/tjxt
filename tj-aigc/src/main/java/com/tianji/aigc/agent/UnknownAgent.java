package com.tianji.aigc.agent;

import com.tianji.aigc.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

@Component
public class UnknownAgent extends AbstractAgent {
    @Override
    public String systemMessage() {
        return """
                你是天机学堂AI课程业务助手。
                当用户询问与课程业务无关的问题时，
                请礼貌说明当前只能处理课程相关问题。
                如果是问候语“你好”或“您好”，
                请礼貌回应：“你好！有什么我可以帮你的吗？”或类似回应，在回答时加入自我介绍。
                """;
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.UNKNOWN;
    }
}
