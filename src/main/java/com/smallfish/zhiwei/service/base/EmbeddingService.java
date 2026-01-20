package com.smallfish.zhiwei.service.base;

import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量嵌入服务 spring ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

   private final EmbeddingModel embeddingModel;

    // text-embedding-v4 支持 8192 tokens。
    // 假设最坏情况 1 char = 1.3 tokens，8192 / 1.3 ≈ 6300。
    // 设定 6000 为绝对安全线。
    private static final int MAX_SAFE_LENGTH = 6000;

    /**
     *  生成单个向量
     * @param text 用户输入
     * @return 返回向量
     */
    public List<Float> generateEmbedding(String text) {
        String safeText = truncate(text);
        final float[] vectorArray  = embeddingModel.embed(safeText);
        // 将 float[] 转换为 List<Float> (Milvus SDK 需要 List)
        return Convert.toList(Float.class, vectorArray);
    }

    /**
     *  批量生成向量
     * @param texts 用户输入 list集合
     * @return 返回向量
     */
    public List<List<Float>> generateEmbedding(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> safeTexts = texts.stream()
                .map(this::truncate)
                .toList();

        final List<float[]> vectorArrays  = embeddingModel.embed(safeTexts);
        return vectorArrays
                .stream()
                .map(each -> Convert.toList(Float.class,each))
                .toList();
    }

    /**
     * 安全截断逻辑
     */
    private String truncate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= MAX_SAFE_LENGTH) {
            return text;
        }
        log.warn("Embedding 输入文本超长 ({} chars), 已自动截断至 {}. 前缀: {}",
                text.length(), MAX_SAFE_LENGTH, text.substring(0, 20));
        return text.substring(0, MAX_SAFE_LENGTH);
    }
}
