package com.xingshu.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.course.domain.po.Chapter;
import com.xingshu.course.mapper.ChapterMapper;
import com.xingshu.course.service.IChapterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChapterServiceImpl extends ServiceImpl<ChapterMapper, Chapter> implements IChapterService {

    @Override
    public List<Chapter> getByCourseId(Long courseId) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getCourseId, courseId)
                .orderByAsc(Chapter::getSortOrder);
        return list(wrapper);
    }

    @Override
    @Transactional
    public void batchCreate(Long courseId, List<Chapter> chapters) {
        for (Chapter chapter : chapters) {
            chapter.setCourseId(courseId);
            save(chapter);
        }
    }
}