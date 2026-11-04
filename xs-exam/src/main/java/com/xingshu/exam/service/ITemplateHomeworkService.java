package com.xingshu.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.exam.domain.po.TemplateHomework;

import java.util.List;

public interface ITemplateHomeworkService extends IService<TemplateHomework> {

    Page<TemplateHomework> pageByCourse(Integer pageNum, Integer pageSize, Long courseId, Long chapterId);

    /** 教师查询自己教学班的模板作业 */
    Page<TemplateHomework> pageByTeacher(Integer pageNum, Integer pageSize, Long teacherId, List<Long> classIds);

    /** 学生查询自己教学班的已发布模板作业 */
    Page<TemplateHomework> pageByStudentClasses(Integer pageNum, Integer pageSize, List<Long> classIds);

    /** 发布模板作业 */
    void publish(Long id);

    /** 获取教师的已发布作业数 */
    long countPublishedByTeacher(Long teacherId);

    /** 按教学班删除模板作业 */
    void deleteByClassId(Long classId);

    /** 按课程删除模板作业 */
    void deleteByCourseId(Long courseId);
}