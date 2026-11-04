package com.xingshu.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.common.constant.CommonConstants;
import com.xingshu.course.domain.po.ClassInfo;
import com.xingshu.course.domain.po.ClassStudent;
import com.xingshu.course.domain.po.ClassTeacher;
import com.xingshu.course.domain.po.Course;
import com.xingshu.course.domain.po.CourseClass;
import com.xingshu.course.mapper.ClassInfoMapper;
import com.xingshu.course.mapper.ClassStudentMapper;
import com.xingshu.course.mapper.ClassTeacherMapper;
import com.xingshu.course.mapper.CourseClassMapper;
import com.xingshu.course.mapper.CourseMapper;
import com.xingshu.course.service.IClassInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClassInfoServiceImpl extends ServiceImpl<ClassInfoMapper, ClassInfo> implements IClassInfoService {

    private static final Logger log = LoggerFactory.getLogger(ClassInfoServiceImpl.class);
    private static final String USER_API_BASE = "http://localhost:8080";
    private static final String EXAM_API_BASE = "http://localhost:8084";
    private static final String CONFIG_API_BASE = "http://localhost:8086";
    private static final String LEARNING_API_BASE = "http://localhost:8087";

    private final ClassStudentMapper classStudentMapper;
    private final ClassTeacherMapper classTeacherMapper;
    private final CourseClassMapper courseClassMapper;
    private final CourseMapper courseMapper;
    private final RestTemplate restTemplate;

    /** 内部服务令牌，用于调用 xs-config 的内部接口 */
    @Value("${xingshu.internal-token:xingshu-internal-2026}")
    private String internalToken;

    public ClassInfoServiceImpl(ClassStudentMapper classStudentMapper,
                                ClassTeacherMapper classTeacherMapper,
                                CourseClassMapper courseClassMapper,
                                CourseMapper courseMapper,
                                RestTemplate restTemplate) {
        this.classStudentMapper = classStudentMapper;
        this.classTeacherMapper = classTeacherMapper;
        this.courseClassMapper = courseClassMapper;
        this.courseMapper = courseMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public Page<ClassInfo> pageClasses(Integer pageNum, Integer pageSize, String keyword, String status) {
        LambdaQueryWrapper<ClassInfo> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ClassInfo::getName, keyword);
        }
        // 学生选课场景只返回已审核通过的教学班；管理端不传 status 可查看全部
        if (status != null && !status.isBlank()) {
            wrapper.eq(ClassInfo::getStatus, status);
        }
        wrapper.orderByDesc(ClassInfo::getCreateTime);
        Page<ClassInfo> page = page(new Page<>(pageNum, pageSize), wrapper);
        enrich(page.getRecords());
        return page;
    }

    @Override
    public Page<ClassInfo> pageByTeacher(Integer pageNum, Integer pageSize, Long teacherId) {
        // 可见范围 = 管理员分配给自己的教学班 ∪ 自己创建的教学班
        List<Long> boundClassIds = boundClassIds(teacherId);
        LambdaQueryWrapper<ClassInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            if (!boundClassIds.isEmpty()) {
                w.in(ClassInfo::getId, boundClassIds).or();
            }
            // 兼容历史数据：class_info.teacher_id 为教师时同样可见
            w.eq(ClassInfo::getCreaterId, teacherId)
                    .or().eq(ClassInfo::getTeacherId, teacherId);
        });
        wrapper.orderByDesc(ClassInfo::getCreateTime);
        Page<ClassInfo> page = page(new Page<>(pageNum, pageSize), wrapper);
        enrich(page.getRecords());
        return page;
    }

    @Override
    public List<Long> getTeacherClassIds(Long teacherId) {
        Set<Long> ids = new LinkedHashSet<>(boundClassIds(teacherId));
        LambdaQueryWrapper<ClassInfo> created = new LambdaQueryWrapper<>();
        created.and(w -> w.eq(ClassInfo::getCreaterId, teacherId)
                        .or().eq(ClassInfo::getTeacherId, teacherId))
                .select(ClassInfo::getId);
        list(created).forEach(c -> ids.add(c.getId()));
        return new ArrayList<>(ids);
    }

    /** 仅在 class_teacher 表中绑定的教学班ID */
    private List<Long> boundClassIds(Long teacherId) {
        LambdaQueryWrapper<ClassTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassTeacher::getTeacherId, teacherId).select(ClassTeacher::getClassId);
        return classTeacherMapper.selectList(wrapper).stream()
                .map(ClassTeacher::getClassId).distinct().toList();
    }

    /** 填充学生人数与教师姓名，供列表展示 */
    private void enrich(List<ClassInfo> classes) {
        if (classes == null || classes.isEmpty()) return;
        List<Long> classIds = classes.stream().map(ClassInfo::getId).toList();
        LambdaQueryWrapper<ClassStudent> studentWrapper = new LambdaQueryWrapper<>();
        studentWrapper.in(ClassStudent::getClassId, classIds);
        List<ClassStudent> students = classStudentMapper.selectList(studentWrapper);
        Map<Long, Long> studentCounts = students.stream()
                .collect(Collectors.groupingBy(ClassStudent::getClassId, Collectors.counting()));

        LambdaQueryWrapper<ClassTeacher> teacherWrapper = new LambdaQueryWrapper<>();
        teacherWrapper.in(ClassTeacher::getClassId, classIds);
        List<ClassTeacher> teachers = classTeacherMapper.selectList(teacherWrapper);
        Map<Long, List<ClassTeacher>> teacherMap = teachers.stream()
                .collect(Collectors.groupingBy(ClassTeacher::getClassId));

        for (ClassInfo c : classes) {
            c.setStudentCount(studentCounts.getOrDefault(c.getId(), 0L));
            List<ClassTeacher> list = teacherMap.get(c.getId());
            if (list != null && !list.isEmpty()) {
                c.setTeacherNames(list.stream().map(ClassTeacher::getTeacherName)
                        .filter(n -> n != null && !n.isBlank())
                        .collect(Collectors.joining("、")));
            } else {
                c.setTeacherNames(c.getTeacherName());
            }
        }
    }

    @Override
    @Transactional
    public void deleteClass(Long classId, String deleterRole, Long deleterId) {
        Map<String, Object> checkResult = preCheckDelete(classId);
        if (!Boolean.TRUE.equals(checkResult.get("exists"))) {
            throw new IllegalStateException("教学班不存在");
        }

        long studentCount = ((Number) checkResult.getOrDefault("studentCount", 0L)).longValue();
        long courseCount = ((Number) checkResult.getOrDefault("courseCount", 0L)).longValue();
        long teacherCount = ((Number) checkResult.getOrDefault("teacherCount", 0L)).longValue();
        boolean hasTeacher = teacherCount > 0;

        // 教师仅可删除自己创建的教学班
        if ("teacher".equals(deleterRole)) {
            Long creatorId = checkResult.get("createrId") instanceof Long
                    ? (Long) checkResult.get("createrId") : null;
            if (deleterId == null || !deleterId.equals(creatorId)) {
                throw new IllegalStateException("无权删除该教学班：只有教学班创建者可以删除");
            }
            // 教师自己删除时，教师绑定不作为阻塞条件；但学生/课程仍必须先清空
            hasTeacher = false;
        }

        // 管理员删除：按原逻辑检查
        StringBuilder sb = new StringBuilder();
        if (hasTeacher) {
            sb.append("仍有教师绑定（").append(checkResult.get("teacherName")).append("），请先解除教师绑定；");
        }
        if (studentCount > 0) {
            sb.append("仍有 ").append(studentCount).append(" 名学生绑定，请先移除所有学生；");
        }
        if (courseCount > 0) {
            sb.append("仍有 ").append(courseCount).append(" 门课程绑定，请先解除课程绑定；");
        }

        if (sb.length() > 0) {
            sb.insert(0, "无法删除教学班「" + checkResult.get("className") + "」：");
            throw new IllegalStateException(sb.toString());
        }

        // 校验通过后再清除教师绑定，避免删除失败却误删其他教师的绑定
        if ("teacher".equals(deleterRole)) {
            removeAllTeachers(classId);
            log.info("教师 {} 删除自己创建的教学班 {}，已清除全部教师绑定", deleterId, classId);
        }

        // 级联删除：模板作业
        try {
            restTemplate.delete(EXAM_API_BASE + "/api/homework/templates/class/" + classId);
            log.info("已级联删除教学班的模板作业: classId={}", classId);
        } catch (Exception e) {
            log.warn("级联删除模板作业失败(classId={}): {}", classId, e.getMessage());
        }

        // 级联删除：提问记录
        try {
            restTemplate.delete(LEARNING_API_BASE + "/api/question-log/class/" + classId);
            log.info("已级联删除教学班的提问记录: classId={}", classId);
        } catch (Exception e) {
            log.warn("级联删除提问记录失败(classId={}): {}", classId, e.getMessage());
        }

        // 级联删除：智能体配置
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            restTemplate.exchange(CONFIG_API_BASE + "/api/agent-class-config/class/" + classId,
                    HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("已级联删除教学班的智能体配置: classId={}", classId);
        } catch (Exception e) {
            log.warn("级联删除智能体配置失败(classId={}): {}", classId, e.getMessage());
        }

        // 删除教学班-教师绑定
        LambdaQueryWrapper<ClassTeacher> teacherWrapper = new LambdaQueryWrapper<>();
        teacherWrapper.eq(ClassTeacher::getClassId, classId);
        classTeacherMapper.delete(teacherWrapper);

        // 删除教学班本身
        removeById(classId);
        log.info("教学班已删除: classId={}, className={}", classId, checkResult.get("className"));
    }

    @Override
    public Map<String, Object> preCheckDelete(Long classId) {
        Map<String, Object> result = new java.util.HashMap<>();

        ClassInfo classInfo = getById(classId);
        if (classInfo == null) {
            result.put("exists", false);
            return result;
        }
        result.put("exists", true);
        result.put("className", classInfo.getName());
        result.put("createrId", classInfo.getCreaterId());

        // 检查教师绑定（多对多）
        List<ClassTeacher> teachers = getTeachers(classId);
        boolean hasTeacher = !teachers.isEmpty();
        result.put("hasTeacher", hasTeacher);
        result.put("teacherCount", (long) teachers.size());
        result.put("teacherId", teachers.isEmpty() ? null : teachers.get(0).getTeacherId());
        String teacherNames = teachers.stream().map(ClassTeacher::getTeacherName)
                .filter(n -> n != null && !n.isBlank()).collect(Collectors.joining("、"));
        if (!hasTeacher && classInfo.getTeacherId() != null) {
            teacherNames = classInfo.getTeacherName();
            result.put("teacherCount", 1L);
            hasTeacher = true;
            result.put("hasTeacher", true);
        }
        result.put("teacherName", teacherNames);

        // 统计学生绑定
        LambdaQueryWrapper<ClassStudent> studentWrapper = new LambdaQueryWrapper<>();
        studentWrapper.eq(ClassStudent::getClassId, classId);
        long studentCount = classStudentMapper.selectCount(studentWrapper);
        result.put("studentCount", studentCount);

        // 统计课程绑定
        LambdaQueryWrapper<CourseClass> courseWrapper = new LambdaQueryWrapper<>();
        courseWrapper.eq(CourseClass::getClassId, classId);
        long courseCount = courseClassMapper.selectCount(courseWrapper);
        result.put("courseCount", courseCount);

        boolean canDelete = !hasTeacher && studentCount == 0 && courseCount == 0;
        result.put("canDelete", canDelete);

        if (!canDelete) {
            List<String> blockers = new java.util.ArrayList<>();
            if (hasTeacher) blockers.add("仍有教师（" + teacherNames + "）绑定，请先解除教师绑定");
            if (studentCount > 0) blockers.add("仍有 " + studentCount + " 名学生绑定，请先到「学生管理」中移除所有学生");
            if (courseCount > 0) blockers.add("仍有 " + courseCount + " 门课程绑定，请先到「分配课程」中解除课程绑定");
            result.put("blockers", blockers);
        }

        return result;
    }

    @Override
    public void approveClass(Long classId) {
        ClassInfo ci = new ClassInfo();
        ci.setId(classId);
        ci.setStatus(CommonConstants.COURSE_STATUS_APPROVED);
        updateById(ci);
    }

    @Override
    public List<ClassTeacher> getTeachers(Long classId) {
        LambdaQueryWrapper<ClassTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassTeacher::getClassId, classId)
                .orderByAsc(ClassTeacher::getJoinTime)
                .orderByAsc(ClassTeacher::getId);
        return classTeacherMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void addTeacher(Long classId, Long teacherId, String teacherName) {
        LambdaQueryWrapper<ClassTeacher> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(ClassTeacher::getClassId, classId)
                .eq(ClassTeacher::getTeacherId, teacherId);
        if (classTeacherMapper.selectCount(existWrapper) > 0) {
            return;
        }

        boolean first = getTeachers(classId).isEmpty();
        String realName = fetchUserName(teacherId);
        if (realName == null || realName.isBlank()) {
            realName = teacherName;
        }
        if (realName == null || realName.isBlank()) {
            realName = "ID:" + teacherId;
        }

        ClassTeacher ct = new ClassTeacher();
        ct.setClassId(classId);
        ct.setTeacherId(teacherId);
        ct.setTeacherName(realName);
        ct.setIsOwner(first ? 1 : 0);
        ct.setJoinTime(LocalDateTime.now());
        classTeacherMapper.insert(ct);

        if (first) {
            updateClassOwner(classId, teacherId, realName);
        }
        log.info("已为教学班 {} 添加教师: teacherId={}, name={}, isOwner={}", classId, teacherId, realName, first);
    }

    @Override
    @Transactional
    public void removeTeacher(Long classId, Long teacherId) {
        LambdaQueryWrapper<ClassTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassTeacher::getClassId, classId)
                .eq(ClassTeacher::getTeacherId, teacherId);
        ClassTeacher target = classTeacherMapper.selectOne(wrapper);
        if (target == null) {
            return;
        }
        classTeacherMapper.deleteById(target.getId());

        List<ClassTeacher> remain = getTeachers(classId);
        if (remain.isEmpty()) {
            clearClassOwner(classId);
        } else if (Integer.valueOf(1).equals(target.getIsOwner())) {
            ClassTeacher next = remain.get(0);
            next.setIsOwner(1);
            classTeacherMapper.updateById(next);
            updateClassOwner(classId, next.getTeacherId(), next.getTeacherName());
        }
        log.info("已从教学班 {} 移除教师: teacherId={}", classId, teacherId);
    }

    @Override
    @Transactional
    public void bindTeacher(Long classId, Long teacherId, String teacherName) {
        addTeacher(classId, teacherId, teacherName);
    }

    @Override
    @Transactional
    public void unbindTeacher(Long classId) {
        removeAllTeachers(classId);
    }

    private void removeAllTeachers(Long classId) {
        LambdaQueryWrapper<ClassTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassTeacher::getClassId, classId);
        classTeacherMapper.delete(wrapper);
        clearClassOwner(classId);
        log.info("已解除教学班 {} 的全部教师绑定", classId);
    }

    private void updateClassOwner(Long classId, Long teacherId, String teacherName) {
        LambdaUpdateWrapper<ClassInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ClassInfo::getId, classId)
                .set(ClassInfo::getTeacherId, teacherId)
                .set(ClassInfo::getTeacherName, teacherName);
        baseMapper.update(null, wrapper);
    }

    private void clearClassOwner(Long classId) {
        LambdaUpdateWrapper<ClassInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ClassInfo::getId, classId)
                .set(ClassInfo::getTeacherId, null)
                .set(ClassInfo::getTeacherName, null);
        baseMapper.update(null, wrapper);
    }

    /** 从用户服务查询真实姓名 */
    private String fetchUserName(Long userId) {
        try {
            var resp = restTemplate.getForObject(USER_API_BASE + "/users/" + userId, java.util.Map.class);
            if (resp != null && resp.get("code") != null && (int) resp.get("code") == 200) {
                @SuppressWarnings("unchecked")
                var data = (Map<String, Object>) resp.get("data");
                if (data != null) {
                    String name = (String) data.get("name");
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                    String username = (String) data.get("username");
                    if (username != null && !username.isBlank()) {
                        return username;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询教师姓名失败(userId={}): {}", userId, e.getMessage());
        }
        return null;
    }

    @Override
    public void fillTeacherName(ClassInfo classInfo) {
        if (classInfo.getTeacherId() == null) return;
        // 始终从用户服务查询真实姓名，不信任前端传入的 teacherName
        try {
            var resp = restTemplate.getForObject(
                    USER_API_BASE + "/users/" + classInfo.getTeacherId(),
                    java.util.Map.class);
            if (resp != null && resp.get("code") != null && (int) resp.get("code") == 200) {
                @SuppressWarnings("unchecked")
                var data = (Map<String, Object>) resp.get("data");
                if (data != null) {
                    String name = (String) data.get("name");
                    if (name != null && !name.isBlank()) {
                        classInfo.setTeacherName(name);
                    } else {
                        // name 为空时使用 username
                        String username = (String) data.get("username");
                        classInfo.setTeacherName(username != null ? username : String.valueOf(classInfo.getTeacherId()));
                    }
                    log.info("已自动填充教师姓名: teacherId={}, name={}", classInfo.getTeacherId(), classInfo.getTeacherName());
                }
            }
        } catch (Exception e) {
            log.warn("查询教师姓名失败(teacherId={}): {}", classInfo.getTeacherId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void enrollStudent(Long classId, Long studentId, String studentName) {
        ClassInfo classInfo = getById(classId);
        if (classInfo == null) {
            throw new RuntimeException("教学班不存在");
        }
        if (!"approved".equals(classInfo.getStatus())) {
            throw new RuntimeException("该教学班尚未通过管理员审核，暂不可选课");
        }
        addStudent(classId, studentId, studentName);
    }

    @Override
    @Transactional
    public void addStudent(Long classId, Long studentId, String studentName) {
        // 多对多：只检查是否已在本班级中，允许学生在多个教学班
        LambdaQueryWrapper<ClassStudent> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(ClassStudent::getClassId, classId)
                .eq(ClassStudent::getStudentId, studentId);
        if (classStudentMapper.selectCount(existWrapper) > 0) {
            return;
        }

        ClassStudent cs = new ClassStudent();
        cs.setClassId(classId);
        cs.setStudentId(studentId);
        cs.setStudentName(studentName);
        cs.setJoinTime(LocalDateTime.now());
        classStudentMapper.insert(cs);
    }

    @Override
    public void removeStudent(Long classId, Long studentId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getClassId, classId)
                .eq(ClassStudent::getStudentId, studentId);
        classStudentMapper.delete(wrapper);
    }

    @Override
    public List<ClassStudent> getStudents(Long classId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getClassId, classId);
        List<ClassStudent> students = classStudentMapper.selectList(wrapper);
        // 批量查询学生用户名（学号）
        if (!students.isEmpty()) {
            List<Long> studentIds = students.stream().map(ClassStudent::getStudentId).toList();
            try {
                String url = USER_API_BASE + "/users/batch?ids=" + studentIds.stream()
                        .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> resp = restTemplate.getForObject(url, java.util.Map.class);
                if (resp != null && resp.get("code") != null && (int) resp.get("code") == 200) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> data = (java.util.Map<String, Object>) resp.get("data");
                    if (data != null) {
                        for (ClassStudent s : students) {
                            Object username = data.get(String.valueOf(s.getStudentId()));
                            if (username != null) {
                                s.setStudentAccount(username.toString());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("批量查询学生用户名失败(classId={}): {}", classId, e.getMessage());
            }
        }
        return students;
    }

    @Override
    public List<ClassInfo> getClassesByStudent(Long studentId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getStudentId, studentId);
        List<ClassStudent> relations = classStudentMapper.selectList(wrapper);
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> classIds = relations.stream().map(ClassStudent::getClassId).toList();
        List<ClassInfo> classes = listByIds(classIds);
        // 草稿教学班不对学生展示，管理员审核通过后才可见
        classes = classes.stream()
                .filter(c -> "approved".equals(c.getStatus()))
                .collect(java.util.stream.Collectors.toList());
        enrich(classes);
        return classes;
    }

    @Override
    public java.util.Map<String, Long> getTeacherStats(Long teacherId) {
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        // 教师可见的教学班：分配的 + 自己创建的
        List<Long> classIds = getTeacherClassIds(teacherId);
        stats.put("classCount", (long) classIds.size());

        long studentCount = 0;
        if (!classIds.isEmpty()) {
            LambdaQueryWrapper<ClassStudent> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.in(ClassStudent::getClassId, classIds);
            studentCount = classStudentMapper.selectCount(studentWrapper);
        }
        stats.put("studentCount", studentCount);
        return stats;
    }

    @Override
    public List<Course> getCoursesByClassId(Long classId) {
        LambdaQueryWrapper<CourseClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseClass::getClassId, classId);
        List<CourseClass> relations = courseClassMapper.selectList(wrapper);
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> courseIds = relations.stream().map(CourseClass::getCourseId).toList();
        return courseMapper.selectBatchIds(courseIds);
    }

    @Override
    public void deleteStudentBindings(Long studentId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getStudentId, studentId);
        classStudentMapper.delete(wrapper);
    }

    @Override
    @Transactional
    public void deleteClassesByTeacherId(Long teacherId) {
        // 该教师创建的教学班：若仍有其他教师，则只解除本人绑定并转移主教师，不删除整个班
        LambdaQueryWrapper<ClassInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassInfo::getCreaterId, teacherId);
        List<ClassInfo> classes = list(wrapper);
        for (ClassInfo cls : classes) {
            LambdaQueryWrapper<ClassTeacher> otherWrapper = new LambdaQueryWrapper<>();
            otherWrapper.eq(ClassTeacher::getClassId, cls.getId())
                    .ne(ClassTeacher::getTeacherId, teacherId);
            long otherTeachers = classTeacherMapper.selectCount(otherWrapper);
            if (otherTeachers > 0) {
                removeTeacher(cls.getId(), teacherId);
                log.info("教学班 {} 仍有其他教师，保留班级并解除教师 {}", cls.getId(), teacherId);
                continue;
            }
            try {
                deleteClass(cls.getId(), "teacher", teacherId);
            } catch (IllegalStateException e) {
                log.warn("跳过删除教学班(id={}, name={}): {}", cls.getId(), cls.getName(), e.getMessage());
            }
        }

        // 最后解除该教师在其他教师教学班中的绑定
        LambdaQueryWrapper<ClassTeacher> teacherWrapper = new LambdaQueryWrapper<>();
        teacherWrapper.eq(ClassTeacher::getTeacherId, teacherId);
        classTeacherMapper.delete(teacherWrapper);
    }
}