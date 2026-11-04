package com.xingshu.config.service;

import com.xingshu.config.entity.ClassQuota;

import java.util.Map;

/**
 * 智能体额度服务：平台总额度 -> 教学班额度（班内智能体共享）
 */
public interface IQuotaService {

    /** 平台额度总览：总额、已分配、可分配、已消耗、班级列表 */
    Map<String, Object> platformStats();

    /** 平台额度概览（不含各班级明细，管理员与教师均可查看） */
    Map<String, Object> platformOverview();

    /** 管理员调整平台总额度（不得低于已分配额度） */
    Map<String, Object> updatePlatformTotal(Long totalTokenLimit);

    /** 查询某教学班额度（未分配时返回 configured=false） */
    Map<String, Object> getClassQuota(Long classId);

    /** 教师调整教学班额度（不得低于已用，且不超过平台剩余可分配额度） */
    ClassQuota upsertClassQuota(Long classId, Long totalTokenLimit, Long operatorId);

    /** 运行结束后累计消耗（班级额度与平台已用同步增加） */
    void consume(Long classId, int tokens);

    /** 教学班额度是否已耗尽（未分配额度视为不可用） */
    boolean isExhausted(Long classId);
}
