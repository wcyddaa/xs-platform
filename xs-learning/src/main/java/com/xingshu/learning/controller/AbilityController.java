package com.xingshu.learning.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.learning.domain.dto.AbilityDimensionDTO;
import com.xingshu.learning.domain.po.AbilityDimension;
import com.xingshu.learning.domain.po.StudentAbility;
import com.xingshu.learning.domain.po.StudentAbilityReport;
import com.xingshu.learning.domain.vo.AbilityProfileVO;
import com.xingshu.learning.service.AbilityService;
import com.xingshu.learning.service.IStudentQuestionLogService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ability")
public class AbilityController {

    private final AbilityService abilityService;
    private final IStudentQuestionLogService questionLogService;

    public AbilityController(AbilityService abilityService,
                             IStudentQuestionLogService questionLogService) {
        this.abilityService = abilityService;
        this.questionLogService = questionLogService;
    }

    // ==================== 能力维度（教师按教学班配置） ====================

    /** 获取教学班的能力维度 */
    @GetMapping("/dimensions/{classId}")
    public ApiResponse<List<AbilityDimension>> getDimensions(@PathVariable Long classId) {
        return ApiResponse.success(abilityService.getByClassId(classId));
    }

    /** 新增能力维度 */
    @PostMapping("/dimensions")
    public ApiResponse<AbilityDimension> createDimension(@RequestBody AbilityDimensionDTO dto) {
        try {
            if (dto.getClassId() == null || dto.getDimensionName() == null || dto.getDimensionName().isBlank()) {
                return ApiResponse.error(400, "教学班与维度名称不能为空");
            }
            if (dto.getDimensionCode() == null || dto.getDimensionCode().isBlank()) {
                dto.setDimensionCode("dim_" + System.currentTimeMillis());
            }
            return ApiResponse.success("已保存", abilityService.saveDimension(dto));
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /** 更新能力维度 */
    @PutMapping("/dimensions/{id}")
    public ApiResponse<AbilityDimension> updateDimension(@PathVariable Long id,
                                                         @RequestBody AbilityDimensionDTO dto) {
        try {
            dto.setId(id);
            if (dto.getDimensionName() == null || dto.getDimensionName().isBlank()) {
                return ApiResponse.error(400, "维度名称不能为空");
            }
            return ApiResponse.success("已更新", abilityService.saveDimension(dto));
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /** 删除能力维度 */
    @DeleteMapping("/dimensions/{id}")
    public ApiResponse<Void> deleteDimension(@PathVariable Long id) {
        abilityService.deleteDimension(id);
        return ApiResponse.success(null);
    }

    // ==================== 学生能力画像 ====================

    /** 学生详情：教学班画像（无报告时自动触发一次评估） */
    @GetMapping("/student/{studentId}/class/{classId}/profile")
    public ApiResponse<AbilityProfileVO> getProfile(
            @PathVariable Long studentId,
            @PathVariable Long classId,
            @RequestParam(defaultValue = "true") boolean autoEvaluate) {
        try {
            List<AbilityDimension> dimensions = abilityService.getByClassId(classId);
            StudentAbilityReport report = abilityService.getReport(studentId, classId);
            int scoredCount = abilityService.getStudentAbility(studentId, classId).size();
            if (autoEvaluate && !dimensions.isEmpty() && (report == null || scoredCount < dimensions.size())) {
                abilityService.evaluate(studentId, classId, resolveStudentName(studentId, classId));
            }
            AbilityProfileVO profile = abilityService.getProfile(studentId, classId);
            profile.setStudentName(resolveStudentName(studentId, classId));
            return ApiResponse.success(profile);
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /** 手动触发智能体评估 */
    @PostMapping("/student/{studentId}/class/{classId}/evaluate")
    public ApiResponse<AbilityProfileVO> evaluate(
            @PathVariable Long studentId,
            @PathVariable Long classId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String name = body != null && body.get("studentName") != null
                    ? String.valueOf(body.get("studentName"))
                    : resolveStudentName(studentId, classId);
            return ApiResponse.success("评估完成", abilityService.evaluate(studentId, classId, name));
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /** 获取学生能力图谱（原始分数） */
    @GetMapping("/student/{studentId}/class/{classId}")
    public ApiResponse<List<StudentAbility>> getStudentAbility(
            @PathVariable Long studentId, @PathVariable Long classId) {
        return ApiResponse.success(abilityService.getStudentAbility(studentId, classId));
    }

    /** 获取学生综合掌握度 */
    @GetMapping("/student/{studentId}/class/{classId}/overall")
    public ApiResponse<Map<String, BigDecimal>> getOverall(
            @PathVariable Long studentId, @PathVariable Long classId) {
        return ApiResponse.success(Map.of("overallMastery", abilityService.getOverallMastery(studentId, classId)));
    }

    /** 更新学生能力掌握度 */
    @PutMapping("/student/{studentId}/mastery")
    public ApiResponse<Void> updateMastery(
            @PathVariable Long studentId,
            @RequestBody Map<String, Object> body) {
        Long classId = Long.valueOf(body.get("classId").toString());
        Long dimensionId = Long.valueOf(body.get("dimensionId").toString());
        BigDecimal masteryLevel = new BigDecimal(body.get("masteryLevel").toString());
        abilityService.updateMastery(studentId, classId, dimensionId, masteryLevel);
        return ApiResponse.success(null);
    }

    private String resolveStudentName(Long studentId, Long classId) {
        var logs = questionLogService.getByStudentAndClass(classId, studentId);
        if (!logs.isEmpty() && logs.get(0).getStudentName() != null) {
            return logs.get(0).getStudentName();
        }
        return "";
    }
}
