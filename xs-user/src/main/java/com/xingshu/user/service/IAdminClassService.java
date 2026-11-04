package com.xingshu.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.user.domain.po.AdminClass;
import com.xingshu.user.domain.po.AdminClassStudent;

import java.util.List;

public interface IAdminClassService extends IService<AdminClass> {

    /** 获取所有行政班 */
    List<AdminClass> listAll();

    /** 绑定学生到行政班 */
    void bindStudent(Long classId, Long studentId);

    /** 解除学生行政班绑定 */
    void unbindStudent(Long studentId);
}