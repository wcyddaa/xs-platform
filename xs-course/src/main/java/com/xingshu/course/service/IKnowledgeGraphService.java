package com.xingshu.course.service;

import java.util.Map;

public interface IKnowledgeGraphService {

    /** 获取课程知识图谱（节点 + 关系） */
    Map<String, Object> getGraph(Long courseId);

    /** 基于课件、学生提问与作业错误重新生成课程知识图谱 */
    Map<String, Object> extractGraph(Long courseId);

    /** 删除课程知识图谱（课程删除时级联调用） */
    void deleteByCourseId(Long courseId);
}
