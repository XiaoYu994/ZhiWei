package com.smallfish.zhiwei.common.constant;

/*
*  milvus 向量数据库常量类
* */
public class MilvusConstants {
    /**
     * Milvus 数据库名称
     */
    public static final String MILVUS_DB_NAME = "default";

    /**
     * Milvus 集合名称
     */
    public static final String MILVUS_COLLECTION_NAME = "biz";

    /**
     * 向量维度（阿里 text-embedding-v4 向量模型） 支持64~2048维用户自定义向量维度
     */
    public static final int VECTOR_DIM = 1024;  // 设置为 1024

    /**
     * ID字段最大长度
     */
    public static final int ID_MAX_LENGTH = 256;

    /**
     * Content字段最大长度
     */
    public static final int CONTENT_MAX_LENGTH = 8192;

    /*
    *  Source字段最大长度
    * */
    public static final int SOURCE_MAX_LENGTH = 1024;

    /**
     * 默认分片数
     */
    public static final int DEFAULT_SHARD_NUMBER = 2;

    private MilvusConstants() {
        // 工具类，禁止实例化
    }
}
