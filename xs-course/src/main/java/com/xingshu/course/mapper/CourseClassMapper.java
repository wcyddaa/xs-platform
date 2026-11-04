package com.xingshu.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingshu.course.domain.po.CourseClass;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseClassMapper extends BaseMapper<CourseClass> {
}