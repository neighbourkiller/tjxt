package com.tianji.aigc.config;

import com.tianji.aigc.mapper.ChatMemoryMessageMapper;
import com.tianji.aigc.memory.jdbc.MysqlChatMemoryRepository;
import com.tianji.aigc.memory.RedisChatMemoryRepository;
import com.tianji.aigc.memory.mongodb.MongoDBChatMemoryRepository;
import com.tianji.aigc.tools.CourseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAIConfig {


    @Value("${tj.ai.memory.max:100}")
    private Integer maxMessages;

    /**
     * 配置 ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 Advisor loggerAdvisor, // 日志记录器
                                 Advisor messageChatMemoryAdvisor, // 对话记忆
                                 CourseTools courseTools  // 工具类
    ) {
        return chatClientBuilder
                .defaultAdvisors(loggerAdvisor, messageChatMemoryAdvisor) //添加 Advisor 功能增强
                .defaultTools(courseTools) // 添加课程工具
                .build();
    }

    /**
     * 日志记录器
     */
    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    /**
     * Redis
     *
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "tj.ai.memory",
            name = "type",
            havingValue = "Redis",
            matchIfMissing = true
    )
    public ChatMemoryRepository redisChatMemoryRepository() { // 按配置文件属性来确认是否创建
        return new RedisChatMemoryRepository();
    }

    /**
     * MySQL
     *
     */

    @Bean
    @ConditionalOnProperty(
            prefix = "tj.ai.memory",
            name = "type",
            havingValue = "MySQL"
    )
    public ChatMemoryRepository mysqlChatMemoryRepository(ChatMemoryMessageMapper  memoryMessageMapper) {
        return new MysqlChatMemoryRepository(memoryMessageMapper);
    }


    /**
     * MongoDB
     *
     */

    @Bean
    @ConditionalOnProperty(
            prefix = "tj.ai.memory",
            name = "type",
            havingValue = "MongoDB"
    )
    public ChatMemoryRepository mongoDBChatMemoryRepository() {
        return new MongoDBChatMemoryRepository();
    }


    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        // 基于 chatMemoryRepository 对象构建 chatMemory 对象
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(this.maxMessages) // 最多保存 100 条对话, 如果超出的话，会自动删除最旧的对话
                .build();
    }

    @Bean
    public Advisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        // 创建基于 chatMemory 的 Advisor 对象
//        return PromptChatMemoryAdvisor.builder(chatMemory).build();
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }


}
