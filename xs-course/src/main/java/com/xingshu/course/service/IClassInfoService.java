package com.xingshu.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.course.domain.po.ClassInfo;
import com.xingshu.course.domain.po.ClassStudent;
import com.xingshu.course.domain.po.ClassTeacher;
import com.xingshu.course.domain.po.Course;

import java.util.List;
import java.util.Map;

public interface IClassInfoService extends IService<ClassInfo> {

    Page<ClassInfo> pageClasses(Integer pageNum, Integer pageSize, String keyword, String status);

    /** 获取教师可见的教学班：管理员分配的 + 自己创建的 */
    Page<ClassInfo> pageByTeacher(Integer pageNum, Integer pageSize, Long teacherId);

    /** 获取教师可见教学班ID集合 */
    List<Long> getTeacherClassIds(Long teacherId);

    /** 获取教学班绑定的教师列表 */
    List<ClassTeacher> getTeachers(Long classId);

    /** 为教学班添加教师（可多对多） */
    void addTeacher(Long classId, Long teacherId, String teacherName);

    /** 从教学班移除指定教师 */
    void removeTeacher(Long classId, Long teacherId);

    /**
     * 删除教学班
     * @param classId 教学班ID
     * @param deleterRole 删除者角色：admin/teacher
     * @param deleterId 删除者用户ID（教师删除时需验证是否为本班教师）
     * @throws IllegalStateException 如果存在不可级联删除的关联数据
     */
    void deleteClass(Long classId, String deleterRole, Long deleterId);

    /** 删除前预检查：返回绑定的数据概览 */
    Map<String, Object> preCheckDelete(Long classId);

    /** 审核通过教学班 */
    void approveClass(Long classId);

    /** 绑定教师到教学班 */
    void bindTeacher(Long classId, Long teacherId, String teacherName);

    /** 解除教师绑定 */
    void unbindTeacher(Long classId);

    /** 自动填充教师姓名（从用户服务查询） */
    void fillTeacherName(ClassInfo classInfo);

    /** 学生自主选班（选上即绑定） */
    void enrollStudent(Long classId, Long studentId, String studentName);

    void addStudent(Long classId, Long studentId, String studentName);

    void removeStudent(Long classId, Long studentId);

    /** 获取教学班学生列表（含姓名） */
    List<ClassStudent> getStudents(Long classId);

    /** 获取学生所属的教学班列表 */
    List<ClassInfo> getClassesByStudent(Long studentId);

    /** 获取教师的教学班统计（教学班数+学生数） */
    java.util.Map<String, Long> getTeacherStats(Long teacherId);

    /** 获取教学班关联的课程列表 */
    List<Course> getCoursesByClassId(Long classId);

    /** 删除学生的所有班级绑定关系 */
    void deleteStudentBindings(Long studentId);

    /** 级联删除教师的所有教学班 */
    void deleteClassesByTeacherId(Long teacherId);
}