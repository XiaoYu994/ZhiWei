package com.smallfish.zhiwei.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 腾讯云 CLS 日志主题 (Topic) 的数据传输对象。
 * 该类用于将腾讯云 SDK 返回的复杂对象简化为 AI 易于理解的 JSON 结构。
 * AI 模型将通过 {@code topic_name} 来模糊匹配用户口语中的服务名称，
 * 并提取对应的 {@code topic_id} 用于后续的日志查询。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClsTopicDTO {

    /*
    *  日志主题名称
    * */
    @JsonProperty("topic_name")
    private String topicName;

    /*
    *  日志主题的唯一标识 ID
    * */
    @JsonProperty("topic_id")
    private String topicId;

    /*
    *  所属日志集 ID
    * */
    @JsonProperty("logset_id")
    private String logsetId;

    /*
    *  主题描述信息
    * */
    @JsonProperty("description")
    private String description;
}
