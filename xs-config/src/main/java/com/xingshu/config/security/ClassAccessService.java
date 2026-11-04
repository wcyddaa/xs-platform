package com.xingshu.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教学班归属校验：调用 xs-course 获取教师可见教学班、学生选班关系与教学班信息。
 * 管理员（含内部服务）不受限制；教师只能操作自己创建/被分配的教学班。
 */
@Service
public class ClassAccessService {

    private final RestTemplate restTemplate;
    private final String courseBaseUrl;

    public ClassAccessService(RestTemplate restTemplate,
                              @Value("${xingshu.course.base-url:http://localhost:8082}") String courseBaseUrl) {
        this.restTemplate = restTemplate;
        this.courseBaseUrl = courseBaseUrl;
    }

    /** 教师创建或被分配的教学班 ID 集合 */
    public Set<Long> teacherClassIds(Long teacherId) {
        Map<?, ?> body = get(courseBaseUrl + "/api/classes/teacher/" + teacherId + "/class-ids");
        List<?> data = body == null ? null : (List<?>) body.get("data");
        if (data == null) {
            return Set.of();
        }
        return data.stream()
                .filter(item -> item != null)
                .map(item -> Long.valueOf(item.toString()))
                .collect(Collectors.toSet());
    }

    /** 学生是否已选该教学班 */
    public boolean studentEnrolled(Long studentId, Long classId) {
        Map<?, ?> body = get(courseBaseUrl + "/api/classes/student/" + studentId);
        List<?> data = body == null ? null : (List<?>) body.get("data");
        if (data == null) {
            return false;
        }
        return data.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> ((Map<?, ?>) item).get("id"))
                .anyMatch(id -> id != null && Long.valueOf(id.toString()).equals(classId));
    }

    /** 管理员视角：所有教学班（含草稿/待审核） */
    public List<ClassSummary> listAllClasses() {
        Map<?, ?> body = get(courseBaseUrl + "/api/classes?pageNum=1&pageSize=500");
        Object dataObj = body == null ? null : body.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return List.of();
        }
        Object listObj = data.get("list");
        if (!(listObj instanceof List<?> list)) {
            return List.of();
        }
        List<ClassSummary> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map) || map.get("id") == null) {
                continue;
            }
            result.add(new ClassSummary(
                    Long.valueOf(map.get("id").toString()),
                    text(map.get("name")),
                    map.get("teacherId") == null ? null : Long.valueOf(map.get("teacherId").toString()),
                    text(map.get("teacherName")),
                    text(map.get("status"))));
        }
        return result;
    }

    /** 教学班 ID → 教学班信息（用于管理员查看全部实例） */
    public Map<Long, ClassSummary> classSummaryMap() {
        Map<Long, ClassSummary> map = new HashMap<>();
        listAllClasses().forEach(item -> map.put(item.id(), item));
        return map;
    }

    /** 教师可管理该教学班的智能体实例并调整额度 */
    public void assertCanManageClass(AuthUser user, Long classId) {
        if (user.isAdmin()) {
            return;
        }
        if (classId != null && user.isTeacher() && teacherClassIds(user.userId()).contains(classId)) {
            return;
        }
        throw AuthException.forbidden("只能管理自己教学班的智能体实例与额度");
    }

    /** 可查看该教学班的智能体配置（教师限本人教学班，学生限已选班级） */
    public void assertCanViewClass(AuthUser user, Long classId) {
        if (user.isAdmin()) {
            return;
        }
        if (classId == null) {
            throw AuthException.forbidden("教学班不能为空");
        }
        if (user.isTeacher() && teacherClassIds(user.userId()).contains(classId)) {
            return;
        }
        if (user.isStudent() && studentEnrolled(user.userId(), classId)) {
            return;
        }
        throw AuthException.forbidden("无权查看该教学班的智能体配置");
    }

    /** 教师只能查看自己的配置；管理员不受限 */
    public void assertSelfOrAdmin(AuthUser user, Long teacherId) {
        if (user.isAdmin() || (user.isTeacher() && user.userId().equals(teacherId))) {
            return;
        }
        throw AuthException.forbidden("只能查看自己的智能体配置");
    }

    private Map<?, ?> get(String url) {
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (RestClientException e) {
            throw AuthException.unavailable("教学班服务暂不可用，请稍后重试");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public record ClassSummary(Long id, String name, Long teacherId, String teacherName, String status) {
    }
}
