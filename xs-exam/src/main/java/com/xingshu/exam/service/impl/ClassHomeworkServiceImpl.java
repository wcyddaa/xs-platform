package com.xingshu.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.exam.domain.po.ClassHomework;
import com.xingshu.exam.mapper.ClassHomeworkMapper;
import com.xingshu.exam.service.IClassHomeworkService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClassHomeworkServiceImpl extends ServiceImpl<ClassHomeworkMapper, ClassHomework>
        implements IClassHomeworkService {

    @Override
    public List<ClassHomework> getByClassId(Long classId) {
        LambdaQueryWrapper<ClassHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassHomework::getClassId, classId);
        return list(wrapper);
    }

    @Override
    public List<ClassHomework> getByTemplateId(Long templateId) {
        LambdaQueryWrapper<ClassHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassHomework::getTemplateId, templateId);
        return list(wrapper);
    }

    @Override
    public void bind(Long classId, String className, Long templateId) {
        LambdaQueryWrapper<ClassHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassHomework::getClassId, classId)
                .eq(ClassHomework::getTemplateId, templateId);
        if (count(wrapper) > 0) {
            return;
        }
        ClassHomework ch = new ClassHomework();
        ch.setClassId(classId);
        ch.setClassName(className);
        ch.setTemplateId(templateId);
        ch.setAssignTime(LocalDateTime.now());
        save(ch);
    }

    @Override
    public void unbind(Long classId, Long templateId) {
        LambdaQueryWrapper<ClassHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassHomework::getClassId, classId)
                .eq(ClassHomework::getTemplateId, templateId);
        remove(wrapper);
    }

    @Override
    public void deleteByTemplateIds(List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) return;
        LambdaQueryWrapper<ClassHomework> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ClassHomework::getTemplateId, templateIds);
        remove(wrapper);
    }
}