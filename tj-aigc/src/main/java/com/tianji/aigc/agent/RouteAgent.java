package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RouteAgent extends AbstractAgent{

    private final SystemPromptConfig systemPromptConfig;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    @Override
    public String systemMessage() {
        return this.systemPromptConfig.getRouteAgentSystemMessage().get();
    }

    private final ChatClient routeChatClient;

    @Override
    public String process(String question, String sessionId) {
        return routeChatClient.prompt()
                .system(prompt -> prompt
                        .text(this.systemMessage())
                        .params(this.systemMessageParams()))
                .user(question)
                .call()
                .content();
    }
}
