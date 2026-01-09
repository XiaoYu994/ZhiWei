package com.smallfish.zhiwei.client;

import cn.hutool.core.util.StrUtil;
import com.smallfish.zhiwei.common.constant.MilvusConstants;
import com.smallfish.zhiwei.config.MilvusProperties;
import com.smallfish.zhiwei.model.BizKnowledge;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


/*
*  Milvus 客户端工厂类
*  初始化和创建 Milvus 客户端连接
* */
@Slf4j
@Component
public class MilvusClientFactory {

    @Resource
    private MilvusProperties  milvusProperties;

    /**
     * 创建客户端（带重试机制）
     * 如果连接失败，会重试 3 次，每次间隔 2 秒
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public MilvusServiceClient createClient() {
        MilvusServiceClient client = null;
        try {
            // 1. 建立连接
            log.info("正在连接 Milvus (Host: {}, Port: {})...", milvusProperties.getHost(), milvusProperties.getPort());
            ConnectParam.Builder builder = ConnectParam.newBuilder()
                   .withHost(milvusProperties.getHost())
                   .withPort(milvusProperties.getPort())
                   .withConnectTimeout(milvusProperties.getTimeout(), TimeUnit.MILLISECONDS);
            // 开启了鉴权信息就要 加入账号和密码
            if(!StrUtil.hasBlank(milvusProperties.getUsername())) {
                builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
            }
            ConnectParam connectParam = builder.build();

            client = new MilvusServiceClient(connectParam);

            // 2. 验证是否存活
            R<Boolean> health = client.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .build());
            if(health.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Milvus 连接握手失败: " + health.getMessage());
            }
            log.info("Milvus 连接成功！");

            // 3. 初始化
            initializeCollection(client);
            return client;
        } catch (Exception e) {
            log.error("Milvus 连接初始化失败", e);
            if (client != null) {
                client.close();
            }
            throw e; // 抛出异常以触发 @Retryable
        }
    }

    /*
    * 初始化向量数据库方法
    * */
    private void initializeCollection(MilvusServiceClient client) {
        String collectionName = MilvusConstants.MILVUS_COLLECTION_NAME;
        // 检查是否存在
        final R<Boolean> hasCollection = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collectionName).build());

        if(hasCollection.getData()) {
            log.info("Collection '{}' 已存在，正在校验 Schema...", collectionName);
            validateSchema(client, collectionName);
        } else {
            log.info("Collection '{}' 不存在，开始创建...", collectionName);
            // 创建数据库
            createBizCollection(client);
            // 创建索引
            createIndexes(client);
            log.info("Collection '{}' 创建并初始化完成。", collectionName);
        }
    }
    /*
    *  校验现有 Schema 是否符合预期
    *  防止表结构变更导致程序崩溃
    * */
    private void validateSchema(MilvusServiceClient client, String collectionName) {
        final R<DescribeCollectionResponse> response = client.describeCollection(DescribeCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());

        if(response.getStatus() != R.Status.Success.getCode()) {
            log.warn("无法获取 Collection Schema，跳过校验");
            return;
        }

        final DescribeCollectionResponse collectionInfo  = response.getData();
        // 简单校验：检查核心字段是否存在
        boolean hasVectorField = collectionInfo.getSchema().getFieldsList().stream()
                .anyMatch(field -> field.getName().equals(BizKnowledge.FIELD_VECTOR));

        if (!hasVectorField) {
            throw new RuntimeException("严重错误：现有 Collection 缺少 'vector' 字段！请检查数据库或执行迁移。");
        }

        log.info("Schema 校验通过。");
    }


    /*
    *  创建 model collection
    * */
    private void createBizCollection(MilvusServiceClient client) {
        // 定义字段
        final FieldType id = FieldType.newBuilder()
                .withName(BizKnowledge.FIELD_ID)
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.ID_MAX_LENGTH)
                .withPrimaryKey(true)
                .build();
        final FieldType vector = FieldType.newBuilder()
                .withName(BizKnowledge.FIELD_VECTOR)
                .withDataType(DataType.FloatVector)
                .withDimension(MilvusConstants.VECTOR_DIM) // 向量字段的维度
                .withDescription("相似度计算")
                .build();
        final FieldType content = FieldType.newBuilder()
                .withName(BizKnowledge.FIELD_CONTENT)
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                .withDescription("用户输入的原文")
                .build();
        final FieldType metadata = FieldType.newBuilder()
                .withName(BizKnowledge.FIELD_METADATA)
                .withDataType(DataType.JSON)
                .withDescription("过滤与溯源")
                .build();

        final FieldType source = FieldType.newBuilder()
                .withName(BizKnowledge.FIELD_SOURCE)
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.SOURCE_MAX_LENGTH) // 路径长度
                .withDescription("源文件标识，用于幂等性删除")
                .build();
        // 创建 collection schema
        final CollectionSchemaParam schemaParam = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .addFieldType(id)
                .addFieldType(vector)
                .addFieldType(content)
                .addFieldType(metadata)
                .addFieldType(source)
                .build();

        // 创建 collection
        final CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withSchema(schemaParam)
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .build();

        final R<RpcStatus> response = client.createCollection(createCollectionParam);

        if(response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建 collection 失败: " + response.getMessage());
        }

    }

    /*
     *  创建数据库索引
     * */
    private void createIndexes(MilvusServiceClient client) {
        // 为 vector 字段创建索引（FloatVector 使用 IVF_FLAT 和 L2 距离）
        final CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFieldName(BizKnowledge.FIELD_VECTOR)
                .withIndexType(IndexType.IVF_FLAT) // 倒排文件索引 整个向量空间切分成许多个“聚类单元
                .withMetricType(MetricType.L2) // l2 欧式距离  计算两个点在多维空间中的直线距离。距离越小，相似度越高。
                .withExtraParam("{\"nlist\":1024}") // nlist 将数据分成多少个桶 nlist ≈ 4 * sqrt(N)
                .withSyncMode(Boolean.FALSE)
                .build();
        client.createIndex(vectorIndexParam);
        // 4. 【新增】为 source 字段创建倒排索引 (Inverted Index)
        // 这对于字符串的精确匹配（source == "xxx"）是必须的优化
        final CreateIndexParam sourceIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFieldName(BizKnowledge.FIELD_SOURCE            )
                .withIndexName("idx_source") // 给索引取个名
                .withIndexType(IndexType.INVERTED) // 倒排索引类型
                .build();
        final R<RpcStatus> response = client.createIndex(sourceIndexParam);
        if(response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建 source 索引失败: " + response.getMessage());
        }
        log.info("成功创建索引: vector(IVF_FLAT), source(INVERTED)");
    }
}
