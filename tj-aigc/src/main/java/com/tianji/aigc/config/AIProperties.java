package com.tianji.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.prompt")
public class AIProperties {

    private System system; // 系统提示语，用于课程推荐、购买业务

    @Data
    public static class System {
        private Chat chat; // 系统提示语，用于课程推荐、购买业务
        private Chat routeAgent; // 系统提示语，用于路由智能体
        private Chat recommendAgent; // 系统提示语，用于推荐智能体
        private Chat buyAgent; // 系统提示语，用于购买智能体
        private Chat consultAgent; // 系统提示语，用于咨询智能体
        private Chat knowledgeAgent; // 系统提示语，用于知识智能体


        @Data
        public static class Chat {
            private String dataId;
            private String group = "DEFAULT_GROUP";
            private long timeoutMs = 20000L; // 读取的超时时间，单位毫秒
        }
    }
}