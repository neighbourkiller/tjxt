CREATE TABLE chat_memory_message (
                                     id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                     conversation_id VARCHAR(128) NOT NULL COMMENT '会话 ID',
                                     message_index INT NOT NULL COMMENT '会话内消息顺序，从 0 开始',
                                     message_json JSON NOT NULL COMMENT 'Spring AI Message 序列化结果',
                                     create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     PRIMARY KEY (id),
                                     UNIQUE KEY uk_chat_memory_conversation_index (conversation_id, message_index),
                                     KEY idx_chat_memory_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天记忆消息';