package com.xingshu.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.course.domain.po.Course;
import com.xingshu.course.domain.po.CourseClass;

import java.util.List;
import java.util.Map;

public interface ICourseService extends IService<Course> {

    Page<Course> pageCourses(Integer pageNum, Integer pageSize, String keyword);

    /** 获取创建者的课程列表 */
    Page<Course> pageByCreator(Integer pageNum, Integer pageSize, Long creatorId);

    void approveCourse(Long courseId);

    /** 获取课程的教学班列表 */
    List<CourseClass> getClasses(Long courseId);

    /** 绑定教学班到课程 */
    void addClass(Long courseId, Long classId, String className);

    /** 移除教学班 */
    void removeClass(Long courseId, Long classId);

    /**
     * 删除课程（仅当所有关联数据已清空时才允许删除）
     * @throws IllegalStateException 如果存在关联数据，异常消息中列出需要手动删除的内容
     */
    void deleteCourse(Long courseId);

    /** 删除前预检查：返回绑定的数据概览 */
    Map<String, Object> preCheckDelete(Long courseId);

    /** 按创建者删除所有课程（仅删除无关联数据的课程） */
    void deleteCoursesByCreatorId(Long creatorId);

    /** 清除指定教材ID的课程绑定 */
    void clearTextbookBinding(String textbookId);
}