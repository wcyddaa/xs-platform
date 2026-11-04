package com.xingshu.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.common.base.PageResult;
import com.xingshu.learning.domain.dto.QuestionLogCreateDTO;
import com.xingshu.learning.domain.dto.QuestionLogQueryDTO;
import com.xingshu.learning.domain.po.StudentQuestionLog;
import com.xingshu.learning.domain.vo.ClassQuestionStatsVO;
import com.xingshu.learning.domain.vo.StudentQuestionStatsVO;
import com.xingshu.learning.domain.vo.QuestionTendencyVO;
import com.xingshu.learning.mapper.StudentQuestionLogMapper;
import com.xingshu.learning.service.IStudentQuestionLogService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentQuestionLogServiceImpl extends ServiceImpl<StudentQuestionLogMapper, StudentQuestionLog> implements IStudentQuestionLogService {

    @Override
    public StudentQuestionLog recordQuestion(QuestionLogCreateDTO dto) {
        StudentQuestionLog log = new StudentQuestionLog();
        log.setClassId(dto.getClassId());
        log.setStudentId(dto.getStudentId());
        log.setStudentName(dto.getStudentName());
        log.setQuestion(dto.getQuestion());
        log.setAnswer(dto.getAnswer());
        log.setTopics(dto.getTopics());
        log.setTokensUsed(dto.getTokensUsed());
        save(log);
        return log;
    }

    @Override
    public PageResult<StudentQuestionLog> queryPage(QuestionLogQueryDTO dto) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        if (dto.getClassId() != null) {
            wrapper.eq(StudentQuestionLog::getClassId, dto.getClassId());
        }
        if (dto.getStudentId() != null) {
            wrapper.eq(StudentQuestionLog::getStudentId, dto.getStudentId());
        }
        wrapper.orderByDesc(StudentQuestionLog::getCreateTime);

        Page<StudentQuestionLog> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<StudentQuestionLog> result = page(page, wrapper);

        return PageResult.of(result.getRecords(), result.getTotal(), dto.getPageNum(), dto.getPageSize());
    }

    @Override
    public ClassQuestionStatsVO getClassStats(Long classId) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentQuestionLog::getClassId, classId);
        List<StudentQuestionLog> logs = list(wrapper);

        ClassQuestionStatsVO vo = new ClassQuestionStatsVO();
        vo.setClassId(classId);
        vo.setTotalQuestions((long) logs.size());
        vo.setStudentCount(logs.stream().map(StudentQuestionLog::getStudentId).distinct().count());

        // 统计提问倾向
        Map<String, Long> topicCount = new HashMap<>();
        for (StudentQuestionLog log : logs) {
            if (log.getTopics() != null && !log.getTopics().isEmpty()) {
                for (String topic : log.getTopics().split(",")) {
                    topic = topic.trim();
                    if (!topic.isEmpty()) {
                        topicCount.merge(topic, 1L, Long::sum);
                    }
                }
            }
        }
        String topTopics = topicCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
        vo.setTopTopics(topTopics);

        return vo;
    }

    @Override
    public List<StudentQuestionStatsVO> getStudentStats(Long classId) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentQuestionLog::getClassId, classId);
        List<StudentQuestionLog> logs = list(wrapper);

        Map<Long, List<StudentQuestionLog>> studentMap = logs.stream()
                .collect(Collectors.groupingBy(StudentQuestionLog::getStudentId));

        List<StudentQuestionStatsVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<StudentQuestionLog>> entry : studentMap.entrySet()) {
            StudentQuestionStatsVO vo = new StudentQuestionStatsVO();
            vo.setStudentId(entry.getKey());
            vo.setStudentName(entry.getValue().get(0).getStudentName());
            vo.setQuestionCount((long) entry.getValue().size());

            // 统计该学生的提问倾向
            Map<String, Long> topicCount = new HashMap<>();
            for (StudentQuestionLog log : entry.getValue()) {
                if (log.getTopics() != null && !log.getTopics().isEmpty()) {
                    for (String topic : log.getTopics().split(",")) {
                        topic = topic.trim();
                        if (!topic.isEmpty()) {
                            topicCount.merge(topic, 1L, Long::sum);
                        }
                    }
                }
            }
            String topTopics = topicCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));
            vo.setTopTopics(topTopics);

            result.add(vo);
        }
        result.sort(Comparator.comparing(StudentQuestionStatsVO::getQuestionCount).reversed());
        return result;
    }

    @Override
    public List<QuestionTendencyVO> getTendencies(Long classId) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentQuestionLog::getClassId, classId);
        List<StudentQuestionLog> logs = list(wrapper);

        Map<String, Long> topicCount = new HashMap<>();
        for (StudentQuestionLog log : logs) {
            if (log.getTopics() != null && !log.getTopics().isEmpty()) {
                for (String topic : log.getTopics().split(",")) {
                    topic = topic.trim();
                    if (!topic.isEmpty()) {
                        topicCount.merge(topic, 1L, Long::sum);
                    }
                }
            }
        }

        return topicCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    QuestionTendencyVO vo = new QuestionTendencyVO();
                    vo.setTopic(e.getKey());
                    vo.setCount(e.getValue());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByClassId(Long classId) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentQuestionLog::getClassId, classId);
        remove(wrapper);
    }

    @Override
    public void deleteByStudentId(Long studentId) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentQuestionLog::getStudentId, studentId);
        remove(wrapper);
    }

    @Override
    public List<StudentQuestionLog> getByStudentId(Long studentId) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentQuestionLog::getStudentId, studentId)
                .orderByDesc(StudentQuestionLog::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<StudentQuestionLog> getByStudentAndClass(Long classId, Long studentId) {
        LambdaQueryWrapper<StudentQuestionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentQuestionLog::getClassId, classId)
                .eq(StudentQuestionLog::getStudentId, studentId)
                .orderByDesc(StudentQuestionLog::getCreateTime);
        return list(wrapper);
    }
}