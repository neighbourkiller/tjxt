package com.tianji.aigc.agent;

import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest
class ConsultAgentTest {

    @Resource
    private ConsultAgent consultAgent;

    @Test
    public void processStream() throws InterruptedException {
        String question = "详细说明一下课程的信息：高级产品运营策略与实践";
        String sessionId = "12345";
        UserContext.setUser(123L);
        Flux<ChatEventVO> flux = consultAgent.processStream(question, sessionId);
//        flux.blockLast();
        flux.subscribe(System.out::println);

        Thread.sleep(50000);
    }

}
