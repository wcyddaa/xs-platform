package com.xingshu.config.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.config.constant.AgentTemplates;
import com.xingshu.config.entity.AgentClassConfig;
import com.xingshu.config.security.AuthContext;
import com.xingshu.config.security.AuthException;
import com.xingshu.config.security.AuthUser;
import com.xingshu.config.security.ClassAccessService;
import com.xingshu.config.service.IAgentClassConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学班智能体实例管理 Controller。
 * 模板由后端内置，管理员不可新增；教师只能在教学班下基于模板创建实例，
 * 模型只能选用管理员测试通过并发布的模型，密钥由服务端注入，教师不可见。
 *
 * 鉴权规则：
 * - 管理员：可查看全部实例（含所属教学班）、管理任意实例；
 * - 教师：仅可管理自己创建/被分配教学班的实例；
 * - 学生：仅可查看已选教学班的公开智能体信息；
 * - 内部服务（FastAPI/xs-course/xs-user）：携带内部令牌访问运行时与级联接口。
 */
@RestController
@RequestMapping("/api/agent-class-config")
public class AgentClassConfigController {

    private final IAgentClassConfigService agentClassConfigService;
    private final ClassAccessService classAccessService;

    public AgentClassConfigController(IAgentClassConfigService agentClassConfigService,
                                      ClassAccessService classAccessService) {
        this.agentClassConfigService = agentClassConfigService;
        this.classAccessService = classAccessService;
    }

    /** 平台内置智能体模板（固定四种，管理员不可新增） */
    @GetMapping("/templates")
    public ApiResponse<List<AgentTemplates.Template>> templates() {
        AuthContext.requireAdminOrTeacher();
        return ApiResponse.success(AgentTemplates.all());
    }

    /** 根据教学班ID获取第一个启用的智能体（兼容旧接口，密钥脱敏） */
    @GetMapping("/class/{classId}")
    public ApiResponse<AgentClassConfig> getByClassId(@PathVariable Long classId) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertCanViewClass(user, classId);
        AgentClassConfig config = agentClassConfigService.getByClassId(classId);
        if (config == null) {
            return ApiResponse.error(404, "该教学班未配置智能体");
        }
        hideSecret(config);
        return ApiResponse.success(config);
    }

    /** 获取教学班配置的全部智能体（密钥与地址不返回） */
    @GetMapping("/class/{classId}/agents")
    public ApiResponse<List<AgentClassConfig>> listByClassId(@PathVariable Long classId) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertCanViewClass(user, classId);
        List<AgentClassConfig> configs = agentClassConfigService.listByClassId(classId);
        configs.forEach(this::hideSecret);
        return ApiResponse.success(configs);
    }

    /** 获取教学班可用智能体的公开信息（供学生端选择） */
    @GetMapping("/class/{classId}/agents/public")
    public ApiResponse<List<Map<String, Object>>> listPublicAgents(@PathVariable Long classId) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertCanViewClass(user, classId);
        List<Map<String, Object>> result = agentClassConfigService.listByClassId(classId).stream()
                .filter(c -> "active".equals(c.getStatus()))
                .map(c -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", c.getId());
                    item.put("agentName", c.getAgentName());
                    item.put("agentType", c.getAgentType());
                    item.put("modelName", c.getModelName());
                    item.put("status", c.getStatus());
                    return item;
                })
                .toList();
        return ApiResponse.success(result);
    }

    /** 获取教学班智能体用量汇总（含班级共享额度） */
    @GetMapping("/class/{classId}/usage")
    public ApiResponse<Map<String, Object>> getClassUsage(@PathVariable Long classId) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertCanViewClass(user, classId);
        return ApiResponse.success(agentClassConfigService.getClassUsage(classId));
    }

    /** 供 FastAPI 获取运行时配置（内部接口，含真实密钥与额度校验） */
    @GetMapping("/runtime/{classId}")
    public ApiResponse<AgentClassConfig> getRuntime(@PathVariable Long classId,
                                                    @RequestParam(required = false) String agentName) {
        AuthContext.requireInternal();
        try {
            AgentClassConfig config = agentClassConfigService.getRuntimeConfig(classId, agentName);
            if (config == null) {
                return ApiResponse.error(404, "该教学班未配置可用智能体");
            }
            return ApiResponse.success(config);
        } catch (IllegalStateException e) {
            return ApiResponse.error(429, e.getMessage());
        }
    }

    /** 根据教师ID获取所有配置（仅本人或管理员） */
    @GetMapping("/teacher/{teacherId}")
    public ApiResponse<List<AgentClassConfig>> listByTeacherId(@PathVariable Long teacherId) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertSelfOrAdmin(user, teacherId);
        List<AgentClassConfig> configs = agentClassConfigService.listByTeacherId(teacherId);
        configs.forEach(this::hideSecret);
        return ApiResponse.success(configs);
    }

    /** 管理员查看全部实例，返回所属教学班与教师信息 */
    @GetMapping("/all")
    public ApiResponse<List<Map<String, Object>>> listAll() {
        AuthContext.requireAdmin();
        Map<Long, ClassAccessService.ClassSummary> classes = classAccessService.classSummaryMap();
        List<Map<String, Object>> result = agentClassConfigService.listAll().stream()
                .map(config -> {
                    ClassAccessService.ClassSummary summary = classes.get(config.getClassId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", config.getId());
                    item.put("classId", config.getClassId());
                    item.put("className", summary == null ? "教学班#" + config.getClassId() : summary.name());
                    item.put("classStatus", summary == null ? "" : summary.status());
                    item.put("teacherId", config.getTeacherId());
                    item.put("teacherName", summary == null ? "" : summary.teacherName());
                    item.put("agentName", config.getAgentName());
                    item.put("agentType", config.getAgentType());
                    item.put("modelConfigId", config.getModelConfigId());
                    item.put("modelName", config.getModelName());
                    item.put("status", config.getStatus());
                    item.put("tokenUsed", config.getTokenUsed());
                    item.put("requestUsed", config.getRequestUsed());
                    item.put("requestDate", config.getRequestDate());
                    item.put("updateTime", config.getUpdateTime());
                    return item;
                })
                .toList();
        return ApiResponse.success(result);
    }

    /** 测试已保存实例（服务端使用已发布模型凭据，教师无需提交密钥） */
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testInstance(@PathVariable Long id) {
        AuthUser user = AuthContext.requireLogin();
        AgentClassConfig config = agentClassConfigService.getById(id);
        if (config == null) {
            return ApiResponse.error(404, "实例不存在");
        }
        classAccessService.assertCanManageClass(user, config.getClassId());
        try {
            Map<String, Object> result = agentClassConfigService.testInstance(id);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            String message = String.valueOf(result.get("message"));
            return success ? ApiResponse.success(message, result) : ApiResponse.error(502, message);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 保存或更新配置（按 id 或 classId + agentName 唯一） */
    @PostMapping
    public ApiResponse<AgentClassConfig> saveOrUpdate(@RequestBody AgentClassConfig config) {
        return doSave(config);
    }

    /** 新增/更新单个智能体（语义化接口） */
    @PostMapping("/agent")
    public ApiResponse<AgentClassConfig> saveAgent(@RequestBody AgentClassConfig config) {
        return doSave(config);
    }

    private ApiResponse<AgentClassConfig> doSave(AgentClassConfig config) {
        try {
            applyOwner(config);
            AgentClassConfig saved = agentClassConfigService.saveOrUpdateConfig(config);
            hideSecret(saved);
            return ApiResponse.success("保存成功", saved);
        } catch (AuthException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "保存失败：" + e.getMessage());
        }
    }

    /** 教师保存时强制绑定为本人，并校验教学班归属；管理员可保留指定教师 */
    private void applyOwner(AgentClassConfig config) {
        AuthUser user = AuthContext.requireLogin();
        if (user.isTeacher()) {
            classAccessService.assertCanManageClass(user, config.getClassId());
            config.setTeacherId(user.userId());
            return;
        }
        if (!user.isAdmin()) {
            throw AuthException.forbidden("仅管理员或教师可配置智能体实例");
        }
        if (config.getTeacherId() == null && config.getId() != null) {
            AgentClassConfig existing = agentClassConfigService.getById(config.getId());
            if (existing != null) {
                config.setTeacherId(existing.getTeacherId());
            }
        }
    }

    /** 上报智能体用量（内部接口，FastAPI 回写） */
    @PostMapping("/{id}/usage")
    public ApiResponse<Void> reportUsage(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        AuthContext.requireInternal();
        int tokens = body.get("tokens") != null ? Integer.parseInt(body.get("tokens").toString()) : 0;
        agentClassConfigService.reportUsage(id, tokens);
        return ApiResponse.success("已记录用量", null);
    }

    /** 删除配置 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        AuthUser user = AuthContext.requireLogin();
        AgentClassConfig config = agentClassConfigService.getById(id);
        if (config == null) {
            return ApiResponse.error(404, "实例不存在");
        }
        classAccessService.assertCanManageClass(user, config.getClassId());
        agentClassConfigService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /** 按教学班ID删除配置（级联删除调用，内部服务或管理员/班级教师） */
    @DeleteMapping("/class/{classId}")
    public ApiResponse<Void> deleteByClassId(@PathVariable Long classId) {
        AuthUser user = AuthContext.requireLogin();
        classAccessService.assertCanManageClass(user, classId);
        agentClassConfigService.deleteByClassId(classId);
        return ApiResponse.success("删除成功", null);
    }

    /** 按教师ID删除配置（级联删除调用，仅管理员/内部服务） */
    @DeleteMapping("/teacher/{teacherId}")
    public ApiResponse<Void> deleteByTeacherId(@PathVariable Long teacherId) {
        AuthContext.requireAdmin();
        agentClassConfigService.deleteByTeacherId(teacherId);
        return ApiResponse.success("删除成功", null);
    }

    private void hideSecret(AgentClassConfig config) {
        config.setApiKey(null);
        config.setBaseUrl(null);
    }
}
