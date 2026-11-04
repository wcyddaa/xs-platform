package com.xingshu.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.common.base.PageResult;
import com.xingshu.learning.domain.dto.QuestionLogCreateDTO;
import com.xingshu.learning.domain.dto.QuestionLogQueryDTO;
import com.xingshu.learning.domain.po.StudentQuestionLog;
import com.xingshu.learning.domain.vo.ClassQuestionStatsVO;
import com.xingshu.learning.domain.vo.StudentQuestionStatsVO;
import com.xingshu.learning.domain.vo.QuestionTendencyVO;

import java.util.List;

/**
 * 学生提问记录服务接口
 */
public interface IStudentQuestionLogService extends IService<StudentQuestionLog> {

    /** 记录学生提问 */
    StudentQuestionLog recordQuestion(QuestionLogCreateDTO dto);

    /** 分页查询提问记录 */
    PageResult<StudentQuestionLog> queryPage(QuestionLogQueryDTO dto);

    /** 教学班提问统计 */
    ClassQuestionStatsVO getClassStats(Long classId);

    /** 教学班学生提问次数统计 */
    List<StudentQuestionStatsVO> getStudentStats(Long classId);

    /** 教学班提问倾向分析 */
    List<QuestionTendencyVO> getTendencies(Long classId);

    /** 按教学班删除提问记录 */
    void deleteByClassId(Long classId);

    /** 按学生删除提问记录 */
    void deleteByStudentId(Long studentId);

    /** 按学生ID查询提问记录 */
    List<StudentQuestionLog> getByStudentId(Long studentId);

    /** 按教学班 + 学生查询提问记录 */
    List<StudentQuestionLog> getByStudentAndClass(Long classId, Long studentId);
}