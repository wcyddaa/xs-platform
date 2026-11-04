package com.xingshu.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.exam.domain.po.StudentHomework;

import java.util.List;

public interface IStudentHomeworkService extends IService<StudentHomework> {

    Page<StudentHomework> pageByStudent(Integer pageNum, Integer pageSize, Long studentId, Long templateId);

    /** 学生提交作业（创建实例） */
    void submitHomework(Long templateId, Long studentId, String studentName, String answer);

    void gradeHomework(Long id, Double score, String comment);

    /** 获取模板作业的所有学生提交 */
    List<StudentHomework> getByTemplateId(Long templateId);

    /** 按学生ID删除作业提交 */
    void deleteByStudentId(Long studentId);

    /** 按模板作业ID列表批量删除实例作业 */
    void deleteByTemplateIds(List<Long> templateIds);
}