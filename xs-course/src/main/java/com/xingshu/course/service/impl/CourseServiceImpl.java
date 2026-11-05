package com.xingshu.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.constant.CommonConstants;
import com.xingshu.course.domain.po.Chapter;
import com.xingshu.course.domain.po.Course;
import com.xingshu.course.domain.po.CourseClass;
import com.xingshu.course.mapper.ChapterMapper;
import com.xingshu.course.mapper.CourseClassMapper;
import com.xingshu.course.mapper.CourseMapper;
import com.xingshu.course.service.ICourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);
    private static final String BOOK_API_BASE = "http://localhost:8083";
    private static final String EXAM_API_BASE = "http://localhost:8084";

    private final CourseClassMapper courseClassMapper;
    private final ChapterMapper chapterMapper;
    private final RestTemplate restTemplate;

    public CourseServiceImpl(CourseClassMapper courseClassMapper,
                             ChapterMapper chapterMapper,
                             RestTemplate restTemplate) {
        this.courseClassMapper = courseClassMapper;
        this.chapterMapper = chapterMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public Page<Course> pageCourses(Integer pageNum, Integer pageSize, String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Course::getName, keyword);
        }
        wrapper.orderByDesc(Course::getCreateTime);
        Page<Course> page = page(new Page<>(pageNum, pageSize), wrapper);
        // 校验教材是否存在，失效则清除绑定
        validateTextbookBindings(page.getRecords());
        return page;
    }

    @Override
    public Page<Course> pageByCreator(Integer pageNum, Integer pageSize, Long creatorId) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getCreaterId, creatorId);
        wrapper.orderByDesc(Course::getCreateTime);
        Page<Course> page = page(new Page<>(pageNum, pageSize), wrapper);
        // 校验教材是否存在，失效则清除绑定
        validateTextbookBindings(page.getRecords());
        return page;
    }

    /**
     * 校验课程绑定的教材ID是否仍然存在，不存在则自动清除绑定
     */
    private void validateTextbookBindings(List<Course> courses) {
        for (Course course : courses) {
            String textbookId = course.getTextbookId();
            if (textbookId == null || textbookId.isBlank()) {
                continue;
            }
            try {
                var response = restTemplate.exchange(
                        BOOK_API_BASE + "/api/textbooks/" + textbookId,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<ApiResponse<Object>>() {}
                );
                ApiResponse<Object> apiResponse = response.getBody();
                if (apiResponse == null || apiResponse.getCode() != 200) {
                    clearSingleTextbookBinding(course);
                }
            } catch (Exception e) {
                log.warn("校验教材失败(textbookId={}, courseId={}): {}", textbookId, course.getId(), e.getMessage());
                clearSingleTextbookBinding(course);
            }
        }
    }

    private void clearSingleTextbookBinding(Course course) {
        Course update = new Course();
        update.setId(course.getId());
        update.setTextbookId("");
        update.setTextbookTitle("");
        updateById(update);
        course.setTextbookId("");
        course.setTextbookTitle("");
        log.info("已自动清除课程 {} 的失效教材绑定: textbookId={}", course.getId(), course.getTextbookId());
    }

    @Override
    public void approveCourse(Long courseId) {
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CommonConstants.COURSE_STATUS_APPROVED);
        updateById(course);
    }

    // ==================== 课程-教学班关联 ====================

    @Override
    public List<CourseClass> getClasses(Long courseId) {
        LambdaQueryWrapper<CourseClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseClass::getCourseId, courseId);
        return courseClassMapper.selectList(wrapper);
    }

    @Override
    public void addClass(Long courseId, Long classId, String className) {
        LambdaQueryWrapper<CourseClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseClass::getCourseId, courseId)
                .eq(CourseClass::getClassId, classId);
        if (courseClassMapper.selectCount(wrapper) > 0) {
            return;
        }
        CourseClass cc = new CourseClass();
        cc.setCourseId(courseId);
        cc.setClassId(classId);
        cc.setClassName(className);
        courseClassMapper.insert(cc);
    }

    @Override
    public void removeClass(Long courseId, Long classId) {
        LambdaQueryWrapper<CourseClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseClass::getCourseId, courseId)
                .eq(CourseClass::getClassId, classId);
        courseClassMapper.delete(wrapper);
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId) {
        Map<String, Object> checkResult = preCheckDelete(courseId);
        if (!Boolean.TRUE.equals(checkResult.get("exists"))) {
            throw new IllegalStateException("课程不存在");
        }

        long classCount = ((Number) checkResult.getOrDefault("classCount", 0L)).longValue();
        if (classCount > 0) {
            log.info("课程 {} 仍绑定 {} 个教学班，将删除绑定关系（教学班保留）", courseId, classCount);
        }

        // 多对多：只删除课程-教学班绑定关系
        LambdaQueryWrapper<CourseClass> classWrapper = new LambdaQueryWrapper<>();
        classWrapper.eq(CourseClass::getCourseId, courseId);
        courseClassMapper.delete(classWrapper);

        // 级联删除：课件（含 ES 元数据、切片与存储文件）
        try {
            restTemplate.delete(BOOK_API_BASE + "/api/courseware/internal/by-course/" + courseId);
            log.info("已级联删除课程 {} 的课件", courseId);
        } catch (Exception e) {
            log.warn("级联删除课件失败(courseId={}): {}", courseId, e.getMessage());
        }

        // 级联删除：该课程的模板作业（含学生作业与附件）
        try {
            restTemplate.delete(EXAM_API_BASE + "/api/homework/templates/course/" + courseId);
            log.info("已级联删除课程 {} 的作业", courseId);
        } catch (Exception e) {
            log.warn("级联删除作业失败(courseId={}): {}", courseId, e.getMessage());
        }

        // 自动级联删除：章节
        LambdaQueryWrapper<com.xingshu.course.domain.po.Chapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(com.xingshu.course.domain.po.Chapter::getCourseId, courseId);
        long chapterCount = chapterMapper.selectCount(chapterWrapper);
        if (chapterCount > 0) {
            chapterMapper.delete(chapterWrapper);
            log.info("已级联删除课程 {} 的 {} 个章节", courseId, chapterCount);
        }

        // 删除课程本身
        removeById(courseId);
        log.info("课程已删除: courseId={}, courseName={}", courseId, checkResult.get("courseName"));
    }

    @Override
    @Transactional
    public void deleteCoursesByCreatorId(Long creatorId) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getCreaterId, creatorId);
        List<Course> courses = list(wrapper);
        for (Course course : courses) {
            try {
                deleteCourse(course.getId());
            } catch (IllegalStateException e) {
                log.warn("跳过删除课程(id={}, name={}): {}", course.getId(), course.getName(), e.getMessage());
            }
        }
    }

    @Override
    public void clearTextbookBinding(String textbookId) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getTextbookId, textbookId);
        List<Course> courses = list(wrapper);
        for (Course course : courses) {
            Course update = new Course();
            update.setId(course.getId());
            update.setTextbookId("");
            update.setTextbookTitle("");
            updateById(update);
            log.info("已清除课程 {} 的教材绑定: textbookId={}", course.getId(), textbookId);
        }
    }

    @Override
    public Map<String, Object> preCheckDelete(Long courseId) {
        Map<String, Object> result = new java.util.HashMap<>();

        Course course = getById(courseId);
        if (course == null) {
            result.put("exists", false);
            return result;
        }
        result.put("exists", true);
        result.put("courseName", course.getName());

        // 统计教学班绑定
        LambdaQueryWrapper<CourseClass> ccWrapper = new LambdaQueryWrapper<>();
        ccWrapper.eq(CourseClass::getCourseId, courseId);
        long classCount = courseClassMapper.selectCount(ccWrapper);
        result.put("classCount", classCount);

        // 检查教材绑定
        boolean hasTextbook = course.getTextbookId() != null && !course.getTextbookId().isBlank();
        result.put("hasTextbook", hasTextbook);
        if (hasTextbook) {
            result.put("textbookTitle", course.getTextbookTitle() != null ? course.getTextbookTitle() : course.getTextbookId());
        }

        // 统计章节（自动级联删除，不阻止）
        long chapterCount = chapterMapper.selectCount(
                new LambdaQueryWrapper<com.xingshu.course.domain.po.Chapter>()
                        .eq(com.xingshu.course.domain.po.Chapter::getCourseId, courseId));
        result.put("chapterCount", chapterCount);

        // 判断是否可删除（仅教学班绑定和教材绑定阻止删除）
        boolean canDelete = classCount == 0 && !hasTextbook;
        result.put("canDelete", canDelete);

        if (!canDelete) {
            List<String> blockers = new ArrayList<>();
            if (classCount > 0) blockers.add("仍有 " + classCount + " 个教学班绑定，请先到「分配教学班」中解除绑定");
            if (hasTextbook) blockers.add("仍有教材（" + course.getTextbookTitle() + "）绑定，请先清除教材绑定");
            result.put("blockers", blockers);
        }

        return result;
    }
}