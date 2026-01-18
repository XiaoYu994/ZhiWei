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
               .toList();
   }
}
