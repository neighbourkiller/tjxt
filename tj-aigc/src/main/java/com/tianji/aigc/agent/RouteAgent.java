package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
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

/*    @Override
    public Object[] tools() {
        return super.tools();
    }

    @Override
    public Map<String, Object> toolContext(String sessionId, String requestId) {
        return super.toolContext(sessionId, requestId);
    }

    @Override
    public List<Advisor> advisors() {
        return super.advisors();
    }

    @Override
    public Map<String, Object> systemMessageParams() {
        return super.systemMessageParams();
    }*/
}
