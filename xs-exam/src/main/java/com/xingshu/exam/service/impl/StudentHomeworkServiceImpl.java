package com.xingshu.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.common.constant.CommonConstants;
import com.xingshu.exam.domain.po.StudentHomework;
import com.xingshu.exam.domain.po.TemplateHomework;
import com.xingshu.exam.mapper.StudentHomeworkMapper;
import com.xingshu.exam.mapper.TemplateHomeworkMapper;
import com.xingshu.exam.service.IStudentHomeworkService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudentHomeworkServiceImpl extends ServiceImpl<StudentHomeworkMapper, StudentHomework>
        implements IStudentHomeworkService {

    private final TemplateHomeworkMapper templateHomeworkMapper;

    public StudentHomeworkServiceImpl(TemplateHomeworkMapper templateHomeworkMapper) {
        this.templateHomeworkMapper = templateHomeworkMapper;
    }

    @Override
    public Page<StudentHomework> pageByStudent(Integer pageNum, Integer pageSize, Long studentId, Long templateId) {
        LambdaQueryWrapper<StudentHomework> wrapper = new LambdaQueryWrapper<>();
        if (studentId != null) {
            wrapper.eq(StudentHomework::getStudentId, studentId);
        }
        if (templateId != null) {
            wrapper.eq(StudentHomework::getTemplateId, templateId);
        }
        wrapper.orderByDesc(StudentHomework::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void submitHomework(Long templateId, Long studentId, String studentName, String answer) {
        // 检查是否已提交过
        LambdaQueryWrapper<StudentHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentHomework::getTemplateId, templateId)
                .eq(StudentHomework::getStudentId, studentId);
        StudentHomework exist = getOne(wrapper);
        boolean isNew = (exist == null);

        if (exist != null) {
            exist.setAnswer(answer);
            exist.setStatus(CommonConstants.HOMEWORK_STATUS_SUBMITTED);
            exist.setSubmitTime(LocalDateTime.now());
            updateById(exist);
        } else {
            StudentHomework hw = new StudentHomework();
            hw.setTemplateId(templateId);
            hw.setStudentId(studentId);
            hw.setStudentName(studentName);
            hw.setAnswer(answer);
            hw.setStatus(CommonConstants.HOMEWORK_STATUS_SUBMITTED);
            hw.setSubmitTime(LocalDateTime.now());
            save(hw);
        }

        // 更新模板作业的提交人数
        if (isNew) {
            TemplateHomework template = templateHomeworkMapper.selectById(templateId);
            if (template != null) {
                TemplateHomework update = new TemplateHomework();
                update.setId(templateId);
                update.setSubmittedCount(
                    (template.getSubmittedCount() == null ? 0 : template.getSubmittedCount()) + 1
                );
                templateHomeworkMapper.updateById(update);
            }
        }
    }

    @Override
    public void gradeHomework(Long id, Double score, String comment) {
        StudentHomework hw = getById(id);
        if (hw == null) return;

        boolean wasGraded = CommonConstants.HOMEWORK_STATUS_GRADED.equals(hw.getStatus());

        hw.setScore(score);
        hw.setComment(comment);
        hw.setStatus(CommonConstants.HOMEWORK_STATUS_GRADED);
        updateById(hw);

        // 更新模板作业的已批改人数（首次批改才+1）
        if (!wasGraded) {
            TemplateHomework template = templateHomeworkMapper.selectById(hw.getTemplateId());
            if (template != null) {
                TemplateHomework update = new TemplateHomework();
                update.setId(hw.getTemplateId());
                update.setGradedCount(
                    (template.getGradedCount() == null ? 0 : template.getGradedCount()) + 1
                );
                templateHomeworkMapper.updateById(update);
            }
        }
    }

    @Override
    public List<StudentHomework> getByTemplateId(Long templateId) {
        LambdaQueryWrapper<StudentHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentHomework::getTemplateId, templateId);
        wrapper.orderByAsc(StudentHomework::getCreateTime);
        return list(wrapper);
    }

    @Override
    public void deleteByStudentId(Long studentId) {
        LambdaQueryWrapper<StudentHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentHomework::getStudentId, studentId);
        remove(wrapper);
    }

    @Override
    public void deleteByTemplateIds(List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<StudentHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(StudentHomework::getTemplateId, templateIds);
        remove(wrapper);
    }
}