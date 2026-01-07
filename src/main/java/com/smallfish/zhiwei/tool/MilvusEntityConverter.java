package com.smallfish.zhiwei.tool;

import com.google.gson.JsonObject;
import com.smallfish.zhiwei.entity.BizKnowledge;
import io.milvus.param.dml.InsertParam;
import java.util.ArrayList;
import java.util.List;

public class MilvusEntityConverter {


    /**
     * 将实体列表转换为 Milvus 插入所需的字段列表
     */
    public static List<InsertParam.Field> toInsertFields(List<BizKnowledge> entities) {
        List<String> ids = entities.stream().map(BizKnowledge::getId).toList();
        List<String> contents = entities.stream().map(BizKnowledge::getContent).toList();
        List<List<Float>> vectors = entities.stream().map(BizKnowledge::getVector).toList();
        List<JsonObject> metadatas = entities.stream().map(BizKnowledge::getMetadata).toList();

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_ID, ids));
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_CONTENT, contents));
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_VECTOR, vectors));
        fields.add(new InsertParam.Field(BizKnowledge.FIELD_METADATA, metadatas));

        return fields;
    }

}
