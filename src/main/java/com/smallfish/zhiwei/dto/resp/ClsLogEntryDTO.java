package com.smallfish.zhiwei.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;


/*
*  日志结果实体
* */
@Data
public class ClsLogEntryDTO {


    /*
    *  格式化后的时间，如 "2023-10-01 12:00:00"
    * */
    @JsonProperty("time_str")
    private String time;

    /*
    *  具体的日志内容 key-value
    * */
    @JsonProperty("content")
    private Map<String, Object> content;
}