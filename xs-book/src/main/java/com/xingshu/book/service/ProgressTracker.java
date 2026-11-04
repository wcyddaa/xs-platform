package com.xingshu.book.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度追踪服务 - 追踪教材上传/删除的进度
 * 
 * 前端通过 GET /api/textbooks/progress/{taskId} 轮询进度。
 * 支持两阶段进度：cloud（云端存储）→ database（数据库向量化）。
 */
@Service
public class ProgressTracker {

    private static final Logger log = LoggerFactory.getLogger(ProgressTracker.class);

    /** 任务进度缓存 */
    private final Map<String, TaskProgress> progressMap = new ConcurrentHashMap<>();

    /** 任务进度 */
    public static class TaskProgress {
        private String taskId;
        private String type;          // upload / delete
        private String textbookId;
        private String title;
        private String phase;         // 当前阶段：cloud / database
        private int totalSteps;       // 当前阶段总步骤数
        private int completedSteps;   // 当前阶段已完成步骤数
        private String currentStep;   // 当前步骤描述
        private String status;        // processing / completed / failed
        private String error;

        public TaskProgress() {}

        public TaskProgress(String taskId, String type, String textbookId, String title, int totalSteps) {
            this.taskId = taskId;
            this.type = type;
            this.textbookId = textbookId;
            this.title = title;
            this.phase = "cloud";
            this.totalSteps = totalSteps;
            this.completedSteps = 0;
            this.status = "processing";
            this.currentStep = "初始化中...";
        }

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTextbookId() { return textbookId; }
        public void setTextbookId(String textbookId) { this.textbookId = textbookId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getPhase() { return phase; }
        public void setPhase(String phase) { this.phase = phase; }
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
        public int getCompletedSteps() { return completedSteps; }
        public void setCompletedSteps(int completedSteps) { this.completedSteps = completedSteps; }
        public String getCurrentStep() { return currentStep; }
        public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    /** 创建进度记录 */
    public TaskProgress create(String taskId, String type, String textbookId, String title, int totalSteps) {
        TaskProgress progress = new TaskProgress(taskId, type, textbookId, title, totalSteps);
        progressMap.put(taskId, progress);
        log.info("创建进度追踪: taskId={}, type={}, textbookId={}, totalSteps={}", taskId, type, textbookId, totalSteps);
        return progress;
    }

    /** 更新进度 */
    public void update(String taskId, int completedSteps, String currentStep) {
        TaskProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setCompletedSteps(completedSteps);
            progress.setCurrentStep(currentStep);
        }
    }

    /**
     * 切换到下一阶段（云端 → 数据库）
     * @param taskId 任务ID
     * @param phase 新阶段标识：cloud / database
     * @param totalSteps 新阶段的总步骤数
     * @param message 新阶段的初始描述
     */
    public void switchPhase(String taskId, String phase, int totalSteps, String message) {
        TaskProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setPhase(phase);
            progress.setTotalSteps(totalSteps);
            progress.setCompletedSteps(0);
            progress.setCurrentStep(message);
            log.info("进度切换阶段: taskId={}, phase={}, totalSteps={}", taskId, phase, totalSteps);
        }
    }

    /** 标记完成 */
    public void complete(String taskId) {
        TaskProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setStatus("completed");
            progress.setCompletedSteps(progress.getTotalSteps());
            progress.setCurrentStep("完成");
        }
    }

    /** 标记失败 */
    public void fail(String taskId, String error) {
        TaskProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setStatus("failed");
            progress.setError(error);
        }
    }

    /** 获取进度 */
    public TaskProgress get(String taskId) {
        return progressMap.get(taskId);
    }

    /** 清理过期进度（完成/失败后清理） */
    public void cleanup() {
        progressMap.entrySet().removeIf(entry -> {
            TaskProgress p = entry.getValue();
            return "completed".equals(p.getStatus()) || "failed".equals(p.getStatus());
        });
    }
}