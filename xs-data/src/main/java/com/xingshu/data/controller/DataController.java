package com.xingshu.data.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.data.service.OnlineService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DataController {

    private final OnlineService onlineService;

    public DataController(OnlineService onlineService) {
        this.onlineService = onlineService;
    }

    /** 心跳上报（教师上报不会计入在线人数） */
    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        String userName = body.get("userName") != null ? body.get("userName").toString() : "";
        if (userId == null) {
            return ApiResponse.error(400, "用户ID不能为空");
        }
        // 未传 classIds 时传 null：表示“本次不上报教学班”，避免误清空已有班级在线记录
        List<Long> classIds = body.containsKey("classIds") ? toLongList(body.get("classIds")) : null;
        onlineService.heartbeat(userId, userName, toInt(body.get("roleId")), classIds);
        return ApiResponse.success("心跳上报成功", null);
    }

    /** 获取全局在线学生人数（不含教师） */
    @GetMapping("/online/count")
    public ApiResponse<Long> getOnlineCount() {
        return ApiResponse.success(onlineService.getOnlineCount());
    }

    /** 获取全局在线学生列表 */
    @GetMapping("/online/users")
    public ApiResponse<List<String>> getOnlineUsers() {
        return ApiResponse.success(onlineService.getOnlineUsers());
    }

    /** 记录今日登录（教师不计入人数） */
    @PostMapping("/login/record")
    public ApiResponse<Void> recordLogin(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        String userName = body.get("userName") != null ? body.get("userName").toString() : "";
        if (userId == null) {
            return ApiResponse.error(400, "用户ID不能为空");
        }
        onlineService.recordLogin(userId, userName, toInt(body.get("roleId")), toLongList(body.get("classIds")));
        return ApiResponse.success("登录记录成功", null);
    }

    /** 获取今日登录学生人数（不含教师） */
    @GetMapping("/login/today/count")
    public ApiResponse<Long> getTodayLoginCount() {
        return ApiResponse.success(onlineService.getTodayLoginCount());
    }

    /** 教师视角统计：仅统计其可见教学班的学生（classIds 逗号分隔） */
    @GetMapping("/teacher/stats")
    public ApiResponse<Map<String, Object>> getTeacherStats(@RequestParam(required = false) String classIds) {
        return ApiResponse.success(onlineService.getTeacherStats(parseIds(classIds)));
    }

    /** 单个教学班统计 */
    @GetMapping("/class/{classId}/stats")
    public ApiResponse<Map<String, Object>> getClassStats(@PathVariable Long classId) {
        return ApiResponse.success(onlineService.getClassStats(classId));
    }

    /** 获取今日 API 调用次数 */
    @GetMapping("/api-call/today/count")
    public ApiResponse<Long> getTodayApiCallCount() {
        return ApiResponse.success(onlineService.getTodayApiCallCount());
    }

    /** 获取全局统计数据汇总 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.success(Map.of(
                "onlineCount", onlineService.getOnlineCount(),
                "todayLoginCount", onlineService.getTodayLoginCount(),
                "apiCallCount", onlineService.getTodayApiCallCount()
        ));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> toLongList(Object value) {
        List<Long> ids = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Long id = toLong(item);
                if (id != null) ids.add(id);
            }
        } else if (value != null) {
            ids.addAll(parseIds(value.toString()));
        }
        return ids;
    }

    private List<Long> parseIds(String value) {
        List<Long> ids = new ArrayList<>();
        if (value == null || value.isBlank()) return ids;
        for (String part : value.split(",")) {
            try {
                ids.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法ID
            }
        }
        return ids;
    }
}
