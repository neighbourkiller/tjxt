package com.tianji.aigc.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 基于路由的智能体服务实现类
 */

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ROUTE")
// 仅在配置文件中设置ti.ai.chat-type=ROUTE时才会加载该类
public class AgentServiceImpl implements ChatService {

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 先把问题发送给路由智能体，获取路由结果
        Agent routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        var routeResult = routeAgent.process(question, sessionId);
        // 尝试将结果转换为AgentTypeEnum
        AgentTypeEnum agentType = AgentTypeEnum.agentNameOf(routeResult);
        var agent = this.findAgentByType(agentType);
        if (null == agent) {
            // 未找到对应的智能体，返回原始结果
            return Flux.just(ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .eventData(routeResult)
                    .build());
        }
        return agent.processStream(question, sessionId);
    }

    private Agent findAgentByType(AgentTypeEnum agentTypeEnum) {
        // 查找Spring容器中所有的Agent实例
        var agents = SpringUtil.getBeansOfType(Agent.class);
        // 根据agentType查找对应的智能体实例
        // 这里可以使用Spring的ApplicationContext或者其他方式获取Bean

        return agents.values().stream()
                .filter(agent -> agent.getAgentType() == agentTypeEnum)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void stop(String sessionId) {
            this.findAgentByType(AgentTypeEnum.ROUTE).stop(sessionId);
    }
}
