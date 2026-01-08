package com.smallfish.zhiwei.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/*
*  agent 获取当前时间的工具
* */
@Component
public class DateTimeTools implements AgentTools {

    @Tool(description = "获取当前系统的日期和时间，当用户询问'现在几点了'或需要基于当前时间进行计算时使用。")
    public String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
