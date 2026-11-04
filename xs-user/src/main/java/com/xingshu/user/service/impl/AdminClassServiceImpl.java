package com.xingshu.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.user.domain.po.AdminClass;
import com.xingshu.user.domain.po.AdminClassStudent;
import com.xingshu.user.mapper.AdminClassMapper;
import com.xingshu.user.mapper.AdminClassStudentMapper;
import com.xingshu.user.service.IAdminClassService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminClassServiceImpl extends ServiceImpl<AdminClassMapper, AdminClass>
        implements IAdminClassService {

    private final AdminClassStudentMapper studentMapper;

    public AdminClassServiceImpl(AdminClassStudentMapper studentMapper) {
        this.studentMapper = studentMapper;
    }

    @Override
    public List<AdminClass> listAll() {
        return list();
    }

    @Override
    public void bindStudent(Long classId, Long studentId) {
        // 先删除旧绑定
        unbindStudent(studentId);
        // 创建新绑定
        AdminClassStudent acs = new AdminClassStudent();
        acs.setClassId(classId);
        acs.setStudentId(studentId);
        studentMapper.insert(acs);
    }

    @Override
    public void unbindStudent(Long studentId) {
        LambdaQueryWrapper<AdminClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminClassStudent::getStudentId, studentId);
        studentMapper.delete(wrapper);
    }
}