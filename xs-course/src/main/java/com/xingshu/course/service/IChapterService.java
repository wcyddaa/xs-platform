package com.xingshu.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.course.domain.po.Chapter;

import java.util.List;

public interface IChapterService extends IService<Chapter> {

    List<Chapter> getByCourseId(Long courseId);

    void batchCreate(Long courseId, List<Chapter> chapters);
}