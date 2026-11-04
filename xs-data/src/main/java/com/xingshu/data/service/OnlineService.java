package com.xingshu.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线/登录统计服务。
 * 统计口径：仅统计学生（roleId=3），教师登录不计入人数；
 * 同时按教学班维度记录，用于教师查看自己教学班的学生情况。
 */
@Service
public class OnlineService {

    private static final Logger log = LoggerFactory.getLogger(OnlineService.class);

    private static final int ROLE_STUDENT = 3;

    /** 全局在线学生 hash：userId -> "userId:userName" */
    private static final String ONLINE_KEY = "data:online:students";
    /** 单个学生心跳标记 key：data:online:heartbeat:{userId} */
    private static final String HEARTBEAT_PREFIX = "data:online:heartbeat:";
    /** 教学班在线 hash：data:online:class:{classId} -> userId -> "userId:userName" */
    private static final String CLASS_ONLINE_PREFIX = "data:online:class:";
    /** 全局今日登录学生 hash：data:login:today:students:{date} */
    private static final String LOGIN_KEY_PREFIX = "data:login:today:students:";
    /** 教学班今日登录 set：data:login:class:{date}:{classId} */
    private static final String CLASS_LOGIN_PREFIX = "data:login:class:";
    /** 学生当前所属教学班集合：data:online:user-classes:{userId} */
    private static final String USER_CLASSES_PREFIX = "data:online:user-classes:";
    private static final String API_COUNT_KEY_PREFIX = "api:count:daily:";

    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(35); // 30s心跳 + 5s缓冲
    private static final Duration LOGIN_TTL = Duration.ofHours(25);

    private final StringRedisTemplate redisTemplate;

    public OnlineService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 心跳上报：仅学生计入在线人数，并按教学班维度记录 */
    public void heartbeat(Long userId, String userName, Integer roleId, List<Long> classIds) {
        if (userId == null) return;
        String value = userId + ":" + (userName != null ? userName : "");
        String userClassKey = USER_CLASSES_PREFIX + userId;

        if (!isStudent(roleId)) {
            // 非学生（教师/管理员）不计入在线人数，同时清理可能残留的班级在线记录
            removePresence(userId, userClassKey);
            return;
        }

        redisTemplate.opsForHash().put(ONLINE_KEY, String.valueOf(userId), value);
        redisTemplate.opsForValue().set(HEARTBEAT_PREFIX + userId, value, HEARTBEAT_TTL);

        if (classIds != null) {
            Set<String> current = new LinkedHashSet<>();
            for (Long classId : classIds) {
                if (classId != null) current.add(String.valueOf(classId));
            }
            // 学生退课/换班后，清理不再属于的教学班在线记录
            Set<String> previous = redisTemplate.opsForSet().members(userClassKey);
            if (previous != null) {
                for (String oldClassId : previous) {
                    if (!current.contains(oldClassId)) {
                        redisTemplate.opsForHash().delete(CLASS_ONLINE_PREFIX + oldClassId, String.valueOf(userId));
                    }
                }
            }
            redisTemplate.delete(userClassKey);
            for (String classId : current) {
                redisTemplate.opsForSet().add(userClassKey, classId);
                redisTemplate.opsForHash().put(CLASS_ONLINE_PREFIX + classId, String.valueOf(userId), value);
            }
            if (!current.isEmpty()) {
                redisTemplate.expire(userClassKey, HEARTBEAT_TTL);
            }
        }
    }

    /** 清理用户在线痕迹（全局与所有教学班） */
    private void removePresence(Long userId, String userClassKey) {
        redisTemplate.opsForHash().delete(ONLINE_KEY, String.valueOf(userId));
        redisTemplate.delete(HEARTBEAT_PREFIX + userId);
        Set<String> classIds = redisTemplate.opsForSet().members(userClassKey);
        if (classIds != null) {
            for (String classId : classIds) {
                redisTemplate.opsForHash().delete(CLASS_ONLINE_PREFIX + classId, String.valueOf(userId));
            }
        }
        redisTemplate.delete(userClassKey);
    }

    /** 记录今日登录：仅学生计入，并按教学班维度记录 */
    public void recordLogin(Long userId, String userName, Integer roleId, List<Long> classIds) {
        if (userId == null || !isStudent(roleId)) return;

        String todayKey = LOGIN_KEY_PREFIX + today();
        redisTemplate.opsForHash().put(todayKey, String.valueOf(userId), userName != null ? userName : "");
        redisTemplate.expire(todayKey, LOGIN_TTL);

        if (classIds != null) {
            for (Long classId : classIds) {
                if (classId == null) continue;
                String classKey = CLASS_LOGIN_PREFIX + today() + ":" + classId;
                redisTemplate.opsForSet().add(classKey, String.valueOf(userId));
                redisTemplate.expire(classKey, LOGIN_TTL);
            }
        }
    }

    /** 清理过期心跳（35秒无心跳即为离线） */
    public void cleanExpired() {
        cleanHashByHeartbeat(ONLINE_KEY);
        Set<String> classKeys = redisTemplate.keys(CLASS_ONLINE_PREFIX + "*");
        if (classKeys != null) {
            for (String key : classKeys) {
                cleanHashByHeartbeat(key);
            }
        }
    }

    private void cleanHashByHeartbeat(String hashKey) {
        Set<Object> userIds = redisTemplate.opsForHash().keys(hashKey);
        if (userIds == null || userIds.isEmpty()) return;
        for (Object uid : userIds) {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(HEARTBEAT_PREFIX + uid))) {
                redisTemplate.opsForHash().delete(hashKey, String.valueOf(uid));
            }
        }
    }

    /** 获取全局在线学生人数 */
    public long getOnlineCount() {
        cleanExpired();
        return redisTemplate.opsForHash().size(ONLINE_KEY);
    }

    /** 获取全局在线学生列表，格式 "userId:userName" */
    public List<String> getOnlineUsers() {
        cleanExpired();
        return redisTemplate.opsForHash().values(ONLINE_KEY).stream()
                .map(String::valueOf).toList();
    }

    /** 获取今日登录学生人数（不含教师） */
    public long getTodayLoginCount() {
        String todayKey = LOGIN_KEY_PREFIX + today();
        Long size = redisTemplate.opsForHash().size(todayKey);
        return size != null ? size : 0L;
    }

    /** 获取指定教学班集合的在线学生人数（去重） */
    public long getClassOnlineCount(List<Long> classIds) {
        return getClassOnlineUsers(classIds).size();
    }

    /** 获取指定教学班集合的在线学生列表（去重，格式 "userId:userName"） */
    public List<String> getClassOnlineUsers(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) return new ArrayList<>();
        cleanExpired();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Long classId : classIds) {
            if (classId == null) continue;
            values.addAll(redisTemplate.opsForHash().values(CLASS_ONLINE_PREFIX + classId).stream()
                    .map(String::valueOf).toList());
        }
        return new ArrayList<>(values);
    }

    /** 获取指定教学班集合的今日登录学生人数（去重） */
    public long getClassTodayLoginCount(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) return 0L;
        LinkedHashSet<String> members = new LinkedHashSet<>();
        for (Long classId : classIds) {
            if (classId == null) continue;
            Set<String> ids = redisTemplate.opsForSet().members(CLASS_LOGIN_PREFIX + today() + ":" + classId);
            if (ids != null) members.addAll(ids);
        }
        return members.size();
    }

    /** 教师视角统计：仅包含其可见教学班的学生 */
    public Map<String, Object> getTeacherStats(List<Long> classIds) {
        List<Long> ids = classIds != null ? classIds : List.of();
        return Map.of(
                "classCount", ids.size(),
                "onlineCount", getClassOnlineCount(ids),
                "todayLoginCount", getClassTodayLoginCount(ids),
                "onlineStudents", getClassOnlineUsers(ids)
        );
    }

    /** 单个教学班统计 */
    public Map<String, Object> getClassStats(Long classId) {
        return getTeacherStats(List.of(classId));
    }

    /** 获取今日 API 调用次数 */
    public long getTodayApiCallCount() {
        String todayKey = API_COUNT_KEY_PREFIX + today();
        String value = redisTemplate.opsForValue().get(todayKey);
        return value != null ? Long.parseLong(value) : 0L;
    }

    private boolean isStudent(Integer roleId) {
        // 未传角色时按学生处理，保持旧接口兼容
        return roleId == null || roleId == ROLE_STUDENT;
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    }
}
