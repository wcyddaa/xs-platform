package com.xingshu.log.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.log.domain.po.OperationLog;
import com.xingshu.log.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/log")
@CrossOrigin(origins = "*", maxAge = 3600)
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /** 记录操作日志 */
    @PostMapping("/record")
    public ApiResponse<Void> record(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        OperationLog log = new OperationLog();
        log.setOperatorId(body.get("operatorId") != null ? Long.valueOf(body.get("operatorId").toString()) : 0L);
        log.setOperatorName(body.get("operatorName") != null ? body.get("operatorName").toString() : "");
        log.setOperatorIp(getClientIp(request));
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setOperationType(body.get("operationType") != null ? body.get("operationType").toString() : "");
        log.setTargetType(body.get("targetType") != null ? body.get("targetType").toString() : "");
        log.setTargetId(body.get("targetId") != null ? body.get("targetId").toString() : "");
        log.setTargetName(body.get("targetName") != null ? body.get("targetName").toString() : "");
        log.setStatus(body.get("status") != null ? body.get("status").toString() : "SUCCESS");
        log.setErrorMsg(body.get("errorMsg") != null ? body.get("errorMsg").toString() : "");
        log.setDurationMs(body.get("durationMs") != null ? Long.valueOf(body.get("durationMs").toString()) : 0L);
        operationLogService.save(log);
        return ApiResponse.success("记录成功", null);
    }

    /** 分页查询操作日志 */
    @GetMapping
    public ApiResponse<PageResult<OperationLog>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String operationType) {
        Page<OperationLog> page = operationLogService.pageByType(operationType, pageNum, pageSize);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 查询某操作者的日志 */
    @GetMapping("/operator/{operatorId}")
    public ApiResponse<java.util.List<OperationLog>> findByOperator(
            @PathVariable Long operatorId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(operationLogService.findByOperator(operatorId, limit));
    }

    /** 查询某目标的操作日志 */
    @GetMapping("/target")
    public ApiResponse<java.util.List<OperationLog>> findByTarget(
            @RequestParam String targetType,
            @RequestParam String targetId) {
        return ApiResponse.success(operationLogService.findByTarget(targetType, targetId));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}