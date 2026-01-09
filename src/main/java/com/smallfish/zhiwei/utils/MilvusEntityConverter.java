package com.smallfish.zhiwei.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smallfish.zhiwei.model.BizKnowledge;
import io.milvus.param.dml.InsertParam;

import java.util.ArrayList;
import java.util.List;

public class MilvusEntityConverter {

    private static final Gson gson = new Gson();

    /**
     * 将实体列表转换为 Milvus 插入所需的字段列表
     */
    public static List<InsertParam.Field> toInsertFields(List<BizKnowledge> entities) {
        List<String> ids = entities.stream().map(BizKnowledge::getId).toList();
        List<String> contents = entities.stream().map(BizKnowledge::getContent).toList();
        List<List<Float>> vectors = entities.stream().map(BizKnowledge::getVector).toList();
        List<String> source = entities.stream().map(BizKnowledge::getSource).toList();
        List<JsonObject> metadataList = entities.stream()
                .map(entity -> gson.toJsonTree(entity.getMetadata()).getAsJsonObject())
                .toList();

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_ID, ids));
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_CONTENT, contents));
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_VECTOR, vectors));
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_METADATA, metadataList));
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_SOURCE, source));

        return fields;
    }

}
