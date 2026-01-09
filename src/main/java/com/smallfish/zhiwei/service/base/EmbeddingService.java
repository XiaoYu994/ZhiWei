package com.smallfish.zhiwei.service.base;

import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量嵌入服务 spring ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

   private final EmbeddingModel embeddingModel;

    /**
     *  生成单个向量
     * @param text 用户输入
     * @return 返回向量
     */
   public List<Float> generateEmbedding(String text) {
       final float[] vectorArray  = embeddingModel.embed(text);
       // 将 float[] 转换为 List<Float> (Milvus SDK 需要 List)
       return Convert.toList(Float.class, vectorArray);
   }

    /**
     *  批量生成向量
     * @param texts 用户输入 list集合
     * @return 返回向量
     */
   public List<List<Float>> generateEmbedding(List<String> texts) {
       final List<float[]> vectorArrays  = embeddingModel.embed(texts);
       return vectorArrays
               .stream()
               .map(each -> Convert.toList(Float.class,each))
               .collect(Collectors.toList());
   }

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vector1 向量1
     * @param vector2 向量2
     * @return 余弦相似度 [-1, 1]
     */
    public float calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }
        // 优化：将 List 转为数组再计算，避免循环中的 get() 开销和拆箱
        // 如果你的输入本身就是 float[]，那就更快了
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        int size = vector1.size();
        for (int i = 0; i < size; i++) {
            float v1 = vector1.get(i); // 自动拆箱只发生一次
            float v2 = vector2.get(i);

            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        // 防止除以零
        if (norm1 == 0 || norm2 == 0) {
            return 0.0f;
        }

        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
