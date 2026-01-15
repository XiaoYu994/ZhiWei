package com.smallfish.zhiwei.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/*
*  日志主题返回实体
* */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClsTopicListRespDTO {
    private boolean success;
    private String message;
    private List<ClsTopicDTO> topics;
}
