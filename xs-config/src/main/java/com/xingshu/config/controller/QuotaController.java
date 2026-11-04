package com.xingshu.config.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.config.entity.ClassQuota;
import com.xingshu.config.security.AuthContext;
import com.xingshu.config.security.AuthUser;
import com.xingshu.config.security.ClassAccessService;
import com.xingshu.config.service.IQuotaService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 额度管理：管理员设置平台总额度，教师设置自己教学班的额度，班内智能体共享。
 *
 * 鉴权规则：平台总额度仅管理员可查看/调整；教学班额度仅该班教师或管理员可查看/调整。
 */
@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final IQuotaService quotaService;
    private final ClassAccessService classAccessService;

    public QuotaController(IQuotaService quotaService, ClassAccessService classAccessService) {
        this.quotaService = quotaService;
        this.classAccessService = classAccessService;
    }

    /** 平台额度总览（含各班明细，仅管理员） */
    @GetMapping("/platform")
    public ApiResponse<Map<String, Object>> platformStats() {
        AuthContext.requireAdmin();
        return ApiResponse.success(quotaService.platformStats());
    }

    /** 平台额度概览（不含班级明细，教师调整班级额度时参考） */
    @GetMapping("/platform/overview")
    public ApiResponse<Map<String, Object>> platformOverview() {
        AuthContext.requireAdminOrTeacher();
        return ApiResponse.success(quotaService.platformOverview());
    }

    /** 管理员调整平台总额度 */
    @PutMapping("/platform")
    public ApiResponse<Map<String, Object>> updatePlatformTotal(@RequestBody Map<String, Object> body) {
        AuthContext.requireAdmin();
        try {
            Long total = toLong(body.get("totalTokenLimit"));
            return ApiResponse.success("已更新平台总额度", quotaService.updatePlatformTotal(total));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 查询教学班额度（该班教师或管理员） */
    @GetMapping("/class/{classId}")
    public ApiResponse<Map<String, Object>> getClassQuota(@PathVariable Long classId) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertCanViewClass(user, classId);
        return ApiResponse.success(quotaService.getClassQuota(classId));
    }

    /** 教师调整本教学班额度（不能超过平台剩余可分配额度） */
    @PutMapping("/class/{classId}")
    public ApiResponse<ClassQuota> updateClassQuota(@PathVariable Long classId,
                                                    @RequestBody Map<String, Object> body) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertCanManageClass(user, classId);
        try {
            Long total = toLong(body.get("totalTokenLimit"));
            // 操作人取自登录身份，忽略前端传入
            return ApiResponse.success("已更新教学班额度",
                    quotaService.upsertClassQuota(classId, total, user.userId()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    private Long toLong(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("额度必须是数字");
        }
    }
}
