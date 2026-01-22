package com.smallfish.zhiwei.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 集中管理所有的 Prompt 提示词
 */
@Component
public class AiOpsPromptConfig {

    @Value("classpath:prompts/supervisor.st")
    private Resource supervisorResource;

    @Value("classpath:prompts/planner.st")
    private Resource plannerResource;

    @Value("classpath:prompts/executor.st")
    private Resource executorResource;

    @Getter
    @Value("classpath:prompts/alert_analysis.st")
    private Resource alertAnalysisResource;

    @Getter
    @Value("classpath:prompts/ops/format_alert_context.st")
    private Resource formatAlertContext;

    @Getter
    @Value("classpath:prompts/ops/format_query_context.st")
    private Resource formatQueryContext;

    @Getter
    @Value("classpath:prompts/ops/phase_execute.st")
    private Resource phaseExecute;

    @Getter
    @Value("classpath:prompts/ops/phase_plan.st")
    private Resource phasePlan;

    @Getter
    @Value("classpath:prompts/ops/phase_review.st")
    private Resource phaseReview;

    public String getSupervisorPrompt() {
        return loadResource(supervisorResource);
    }

    public String getPlannerPrompt() {
        return loadResource(plannerResource);
    }

    public String getExecutorPrompt() {
        return loadResource(executorResource);
    }

    /**
     * 辅助方法：将 Resource 转为 String
     */
    private String loadResource(Resource resource) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("无法加载 Prompt 文件: " + resource.getFilename(), e);
        }
    }
}