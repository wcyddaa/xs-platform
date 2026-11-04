package com.xingshu.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.user.domain.po.User;
import com.xingshu.user.mapper.AdminClassStudentMapper;
import com.xingshu.user.mapper.UserMapper;
import com.xingshu.user.service.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.xingshu.user.domain.po.AdminClassStudent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户表 服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String COURSE_API_BASE = "http://localhost:8082";
    private static final String LEARNING_API_BASE = "http://localhost:8087";
    private static final String EXAM_API_BASE = "http://localhost:8084";
    private static final String CONFIG_API_BASE = "http://localhost:8086";

    /** 内部服务令牌，用于调用 xs-config 的内部接口 */
    @Value("${xingshu.internal-token:xingshu-internal-2026}")
    private String internalToken;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AdminClassStudentMapper adminClassStudentMapper;

    @Override
    public User getUserById(Long id) {
        return getById(id);
    }

    @Override
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return getOne(wrapper);
    }

    @Override
    public User getUserByNumber(String number) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getNumber, number);
        return getOne(wrapper);
    }

    @Override
    public void addUser(User user) {
        // 检查用户名是否重复
        User exist = getUserByUsername(user.getUsername());
        if (exist != null) {
            throw new RuntimeException("账号（工号）已存在，不可重复");
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        save(user);

        // 如果是学生且指定了行政班，创建绑定关系
        if (user.getType() != null && user.getType() == 3 && user.getClassId() != null) {
            AdminClassStudent acs = new AdminClassStudent();
            acs.setClassId(user.getClassId());
            acs.setStudentId(user.getId());
            adminClassStudentMapper.insert(acs);
        }
    }

    @Override
    public void updateUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            // 密码非空：如果不是BCrypt密文，则加密
            if (!user.getPassword().startsWith("$2a$")
                    && !user.getPassword().startsWith("$2b$")
                    && !user.getPassword().startsWith("$2y$")) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            // 已经是BCrypt密文，保持不变（用户未修改密码）
        } else {
            // 密码为空，设为null避免updateById把密码清空
            user.setPassword(null);
        }
        updateById(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = getById(id);
        if (user == null) {
            return;
        }

        Integer userType = user.getType();

        if (userType != null && userType == 3) {
            // 学生：删除行政班绑定、教学班绑定、提问记录、作业提交
            try {
                LambdaQueryWrapper<AdminClassStudent> acsWrapper = new LambdaQueryWrapper<>();
                acsWrapper.eq(AdminClassStudent::getStudentId, id);
                adminClassStudentMapper.delete(acsWrapper);
            } catch (Exception e) {
                log.warn("级联删除学生行政班绑定失败(userId={}): {}", id, e.getMessage());
            }
            try {
                restTemplate.delete(COURSE_API_BASE + "/api/classes/student/" + id + "/bindings");
            } catch (Exception e) {
                log.warn("级联删除学生班级绑定失败(userId={}): {}", id, e.getMessage());
            }
            try {
                restTemplate.delete(LEARNING_API_BASE + "/api/question-log/student/" + id);
            } catch (Exception e) {
                log.warn("级联删除学生提问记录失败(userId={}): {}", id, e.getMessage());
            }
            try {
                restTemplate.delete(EXAM_API_BASE + "/api/homework/students/student/" + id);
            } catch (Exception e) {
                log.warn("级联删除学生作业失败(userId={}): {}", id, e.getMessage());
            }
        } else if (userType != null && userType == 2) {
            // 教师：删除教学班、课程、智能体配置
            try {
                restTemplate.delete(COURSE_API_BASE + "/api/classes/teacher/" + id);
            } catch (Exception e) {
                log.warn("级联删除教师教学班失败(userId={}): {}", id, e.getMessage());
            }
            try {
                restTemplate.delete(COURSE_API_BASE + "/api/courses/creator/" + id);
            } catch (Exception e) {
                log.warn("级联删除教师课程失败(userId={}): {}", id, e.getMessage());
            }
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Token", internalToken);
                restTemplate.exchange(CONFIG_API_BASE + "/api/agent-class-config/teacher/" + id,
                        HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            } catch (Exception e) {
                log.warn("级联删除教师智能体配置失败(userId={}): {}", id, e.getMessage());
            }
        }
        // admin (type=1) 直接删除用户本身

        removeById(id);
    }

    @Override
    public void updatePassword(Long userId, String password) {
        User user = new User();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(password));
        updateById(user);
    }

    @Override
    public void disableUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setStatus(0);
        updateById(user);
    }

    @Override
    public void enableUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setStatus(1);
        updateById(user);
    }

    @Override
    public User login(String username, String password) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
                .eq(User::getStatus, 1);
        User user = getOne(wrapper);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    @Override
    public Page<User> getUserPage(Integer pageNum, Integer pageSize) {
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public Page<User> getUserPageByCondition(Integer pageNum, Integer pageSize,
                                              String keyword, Integer type, Integer status) {
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索：用户名、手机号或姓名
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getNumber, keyword)
                    .or()
                    .like(User::getName, keyword));
        }
        // 身份类型筛选
        if (type != null) {
            wrapper.eq(User::getType, type);
        }
        // 状态筛选
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional
    public int batchImportUsers(List<User> users) {
        if (users == null || users.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (User user : users) {
            // 检查用户名是否已存在
            User exist = getUserByUsername(user.getUsername());
            if (exist != null) {
                continue;
            }
            // 加密密码
            if (user.getPassword() != null && !user.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            save(user);
            count++;
        }
        return count;
    }

    @Override
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("adminCount", count(new LambdaQueryWrapper<User>().eq(User::getType, 1)));
        stats.put("teacherCount", count(new LambdaQueryWrapper<User>().eq(User::getType, 2)));
        stats.put("studentCount", count(new LambdaQueryWrapper<User>().eq(User::getType, 3)));
        stats.put("totalCount", count());
        return stats;
    }

    @Override
    public Map<Long, String> getUsernamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = listByIds(ids);
        Map<Long, String> result = new HashMap<>();
        for (User user : users) {
            result.put(user.getId(), user.getUsername());
        }
        return result;
    }
}