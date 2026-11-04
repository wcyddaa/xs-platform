package com.xingshu.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.exam.domain.po.ClassHomework;

import java.util.List;

public interface IClassHomeworkService extends IService<ClassHomework> {

    /** 获取班级绑定的模板作业列表 */
    List<ClassHomework> getByClassId(Long classId);

    /** 获取模板作业绑定的班级列表 */
    List<ClassHomework> getByTemplateId(Long templateId);

    /** 绑定班级到模板作业 */
    void bind(Long classId, String className, Long templateId);

    /** 解绑班级 */
    void unbind(Long classId, Long templateId);

    /** 按模板作业ID批量删除关联 */
    void deleteByTemplateIds(List<Long> templateIds);
}