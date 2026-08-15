package com.tianji.aigc.controller;


import cn.hutool.core.collection.CollStreamUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {
    // 向量存储服务
    private final VectorStore vectorStore;
    // 嵌入模型服务
    private final EmbeddingModel embeddingModel;
    // Elasticsearch 客户端
    private final ElasticsearchClient elasticsearchClient;
    // ES索引名称
    @Value("${spring.ai.vectorstore.elasticsearch.index-name}")
    private String ES_INDEX_NAME;

    @PostMapping
    public void saveVectorStore(@RequestParam("messages") List<String> messages) {

        log.info("保存向量数据到 VectorStore: {}", messages);
        // 将消息列表转换为向量并保存到 VectorStore
        List<Document> documents = CollStreamUtil.toList(messages, message -> Document.builder()
                .text(message)
                .build()
        );
        this.vectorStore.add(documents);
        log.info("向量数据保存成功, 数量: {}", documents.size());
    }

    @GetMapping
    public EmbeddingResponse textToVector(@RequestParam("message") String text) {
        return this.embeddingModel.embedForResponse(List.of(text));
    }

    @GetMapping("/search")
    public List<Document> searchVector(@RequestParam("message") String message) {
        return this.vectorStore.similaritySearch(SearchRequest.builder()
                .query(message)
                .topK(5)
                .build());
    }

    @GetMapping("/search/all")
    public List<Document> searchAll() throws IOException {
        /*return this.vectorStore.similaritySearch(SearchRequest.builder()
                .query("")
                .topK(Integer.MAX_VALUE)  // 获取所有向量
                .build());*/

        return elasticsearchClient.search(s -> s
                        .index(ES_INDEX_NAME)
                        .size(10_000)
                        .query(q -> q.matchAll(m -> m)),
                Document.class
        ).hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
    }

    @DeleteMapping
    public void deleteVector(@RequestParam("ids") List<String> ids) {
        this.vectorStore.delete(ids);
    }
}
