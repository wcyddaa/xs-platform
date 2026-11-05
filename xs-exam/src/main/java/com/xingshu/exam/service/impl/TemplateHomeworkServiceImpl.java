package com.xingshu.exam.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.common.constant.CommonConstants;
import com.xingshu.exam.domain.po.TemplateHomework;
import com.xingshu.exam.mapper.TemplateHomeworkMapper;
import com.xingshu.exam.service.HomeworkFileService;
import com.xingshu.exam.service.IClassHomeworkService;
import com.xingshu.exam.service.IStudentHomeworkService;
import com.xingshu.exam.service.ITemplateHomeworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TemplateHomeworkServiceImpl extends ServiceImpl<TemplateHomeworkMapper, TemplateHomework>
        implements ITemplateHomeworkService {

    private static final Logger log = LoggerFactory.getLogger(TemplateHomeworkServiceImpl.class);
    private static final String COURSE_API_BASE = "http://localhost:8082";

    private final IStudentHomeworkService studentHomeworkService;
    private final HomeworkFileService homeworkFileService;
    private final IClassHomeworkService classHomeworkService;

    public TemplateHomeworkServiceImpl(IStudentHomeworkService studentHomeworkService,
                                       HomeworkFileService homeworkFileService,
                                       IClassHomeworkService classHomeworkService) {
        this.studentHomeworkService = studentHomeworkService;
        this.homeworkFileService = homeworkFileService;
        this.classHomeworkService = classHomeworkService;
    }

    @Override
    public Page<TemplateHomework> pageByCourse(Integer pageNum, Integer pageSize, Long courseId, Long chapterId) {
        LambdaQueryWrapper<TemplateHomework> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(TemplateHomework::getCourseId, courseId);
        }
        if (chapterId != null) {
            wrapper.eq(TemplateHomework::getChapterId, chapterId);
        }
        wrapper.orderByDesc(TemplateHomework::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Page<TemplateHomework> pageByTeacher(Integer pageNum, Integer pageSize, Long teacherId, List<Long> classIds) {
        LambdaQueryWrapper<TemplateHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateHomework::getTeacherId, teacherId);
        if (classIds != null && !classIds.isEmpty()) {
            wrapper.in(TemplateHomework::getClassId, classIds);
        }
        wrapper.orderByDesc(TemplateHomework::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Page<TemplateHomework> pageByStudentClasses(Integer pageNum, Integer pageSize, List<Long> classIds) {
        LambdaQueryWrapper<TemplateHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateHomework::getStatus, CommonConstants.TEMPLATE_STATUS_PUBLISHED);
        if (classIds != null && !classIds.isEmpty()) {
            wrapper.in(TemplateHomework::getClassId, classIds);
        }
        wrapper.orderByDesc(TemplateHomework::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void publish(Long id) {
        TemplateHomework template = getById(id);
        if (template == null) return;

        int totalStudents = 0;
        if (template.getClassId() != null) {
            totalStudents = getClassStudentCount(template.getClassId());
        }

        TemplateHomework hw = new TemplateHomework();
        hw.setId(id);
        hw.setStatus(CommonConstants.TEMPLATE_STATUS_PUBLISHED);
        hw.setTotalStudents(totalStudents);
        updateById(hw);
    }

    /** 调用 xs-course 获取教学班学生人数 */
    private int getClassStudentCount(Long classId) {
        try {
            String url = COURSE_API_BASE + "/api/classes/" + classId + "/students";
            String result = cn.hutool.http.HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(result);
            if (json.getInt("code", -1) == 0) {
                JSONArray data = json.getJSONArray("data");
                return data != null ? data.size() : 0;
            }
        } catch (Exception e) {
            log.warn("获取教学班 {} 学生人数失败: {}", classId, e.getMessage());
        }
        return 0;
    }

    @Override
    public long countPublishedByTeacher(Long teacherId) {
        LambdaQueryWrapper<TemplateHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateHomework::getTeacherId, teacherId)
                .eq(TemplateHomework::getStatus, CommonConstants.TEMPLATE_STATUS_PUBLISHED);
        return count(wrapper);
    }

    @Override
    @Transactional
    public void deleteByClassId(Long classId) {
        LambdaQueryWrapper<TemplateHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateHomework::getClassId, classId);
        List<TemplateHomework> templates = list(wrapper);
        if (!templates.isEmpty()) {
            // 级联删除：附件文件 + 实例作业 + 班级作业关联
            for (TemplateHomework t : templates) {
                homeworkFileService.deleteAttachments(t.getId());
            }
            List<Long> templateIds = templates.stream().map(TemplateHomework::getId).collect(Collectors.toList());
            studentHomeworkService.deleteByTemplateIds(templateIds);
            classHomeworkService.deleteByTemplateIds(templateIds);
        }
        remove(wrapper);
        log.info("已删除教学班 {} 的 {} 份作业", classId, templates.size());
    }

    @Override
    @Transactional
    public void deleteByCourseId(Long courseId) {
        LambdaQueryWrapper<TemplateHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateHomework::getCourseId, courseId);
        List<TemplateHomework> templates = list(wrapper);
        if (!templates.isEmpty()) {
            for (TemplateHomework t : templates) {
                homeworkFileService.deleteAttachments(t.getId());
            }
            List<Long> templateIds = templates.stream().map(TemplateHomework::getId).collect(Collectors.toList());
            studentHomeworkService.deleteByTemplateIds(templateIds);
            classHomeworkService.deleteByTemplateIds(templateIds);
        }
        remove(wrapper);
        log.info("已删除课程 {} 的 {} 份作业", courseId, templates.size());
    }
}