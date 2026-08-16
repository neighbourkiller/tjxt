package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.tools.OrderTools;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class BuyAgent extends AbstractAgent{

    private final SystemPromptConfig systemPromptConfig; // 系统提示语配置类，用于获取购买智能体的系统提示语
    private final OrderTools orderTools; // 订单工具类，用于处理购买相关的操作

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.BUY;
    }

    @Override
    public String systemMessage() {
        return systemPromptConfig.getBuyAgentSystemMessage().get();
    }

    @Override
    public Object[] tools() {
        return new Object[]{orderTools};
    }

    @Override
    public Map<String, Object> toolContext(String sessionId, String requestId) {
        var UserId = UserContext.getUser();
        return Map.of(
                Constant.USER_ID, UserId,
                Constant.REQUEST_ID, requestId);
    }
}
