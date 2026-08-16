package com.tianji.aigc;

import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;

@SpringBootTest
class ChatServiceImplIntegrationTest {

    @DynamicPropertySource
    static void openRouterProperties(DynamicPropertyRegistry registry) {
        String apiKey = System.getenv("OPENROUTER_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置环境变量 OPENROUTER_API_KEY，无法执行 OpenRouter 集成测试"
            );
        }

        registry.add("spring.ai.openai.api-key", () -> apiKey);
    }

    @Autowired
    private ChatService chatService;

    @Test
    void shouldConnectToOpenRouterAndStreamResponse() {
        List<ChatEventVO> chunks = chatService
                .chat("请只回复 OPENROUTER_OK", "integration-test")
                .doOnNext(chunk ->
                        System.out.println(
                                System.currentTimeMillis() + " -> " + chunk
                        )
                )
                .collectList()
                .block();

        Assertions.assertNotNull(chunks);
        Assertions.assertFalse(chunks.isEmpty());
        Assertions.assertEquals("[DONE]", chunks.get(chunks.size() - 1));

        String content = String.join((CharSequence) "", (CharSequence) chunks);
        Assertions.assertTrue(content.length() > "[DONE]".length());
    }
}