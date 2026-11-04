package com.xingshu.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.learning.client.AbilityAiClient;
import com.xingshu.learning.client.ExamHomeworkClient;
import com.xingshu.learning.domain.dto.AbilityDimensionDTO;
import com.xingshu.learning.domain.po.AbilityDimension;
import com.xingshu.learning.domain.po.AbilityEvidence;
import com.xingshu.learning.domain.po.StudentAbility;
import com.xingshu.learning.domain.po.StudentAbilityReport;
import com.xingshu.learning.domain.po.StudentQuestionLog;
import com.xingshu.learning.domain.vo.AbilityProfileVO;
import com.xingshu.learning.mapper.AbilityDimensionMapper;
import com.xingshu.learning.mapper.AbilityEvidenceMapper;
import com.xingshu.learning.mapper.StudentAbilityMapper;
import com.xingshu.learning.mapper.StudentAbilityReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力图谱服务：维度由教师按教学班设置，分数由智能体依据作业与提问自动生成。
 * 同一学生在不同教学班的维度与分数互相隔离。
 */
@Service
public class AbilityService extends ServiceImpl<AbilityDimensionMapper, AbilityDimension> {

    private static final Logger log = LoggerFactory.getLogger(AbilityService.class);

    private final StudentAbilityMapper studentAbilityMapper;
    private final StudentAbilityReportMapper reportMapper;
    private final AbilityEvidenceMapper evidenceMapper;
    private final AbilityAiClient abilityAiClient;
    private final ExamHomeworkClient examHomeworkClient;
    private final IStudentQuestionLogService questionLogService;

    public AbilityService(StudentAbilityMapper studentAbilityMapper,
                          StudentAbilityReportMapper reportMapper,
                          AbilityEvidenceMapper evidenceMapper,
                          AbilityAiClient abilityAiClient,
                          ExamHomeworkClient examHomeworkClient,
                          IStudentQuestionLogService questionLogService) {
        this.studentAbilityMapper = studentAbilityMapper;
        this.reportMapper = reportMapper;
        this.evidenceMapper = evidenceMapper;
        this.abilityAiClient = abilityAiClient;
        this.examHomeworkClient = examHomeworkClient;
        this.questionLogService = questionLogService;
    }

    // ==================== 维度设置 ====================

    /** 获取教学班的能力维度列表（按展示顺序） */
    public List<AbilityDimension> getByClassId(Long classId) {
        return list(new LambdaQueryWrapper<AbilityDimension>()
                .eq(AbilityDimension::getClassId, classId)
                .orderByAsc(AbilityDimension::getSortOrder)
                .orderByAsc(AbilityDimension::getId));
    }

    /** 新增/更新能力维度 */
    @Transactional
    public AbilityDimension saveDimension(AbilityDimensionDTO dto) {
        AbilityDimension dimension;
        if (dto.getId() != null) {
            dimension = getById(dto.getId());
            if (dimension == null) {
                throw new IllegalArgumentException("能力维度不存在");
            }
        } else {
            dimension = new AbilityDimension();
        }
        dimension.setClassId(dto.getClassId());
        dimension.setDimensionName(dto.getDimensionName());
        dimension.setDimensionCode(dto.getDimensionCode());
        dimension.setDescription(dto.getDescription());
        dimension.setWeight(dto.getWeight() != null ? dto.getWeight() : BigDecimal.ONE);
        dimension.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        if (dto.getTeacherId() != null) {
            if (dimension.getId() == null) {
                dimension.setCreaterId(dto.getTeacherId());
            }
            dimension.setUpdaterId(dto.getTeacherId());
        }
        saveOrUpdate(dimension);
        return dimension;
    }

    /** 删除能力维度（连同该维度的学生分数与证据） */
    @Transactional
    public void deleteDimension(Long dimensionId) {
        AbilityDimension dimension = getById(dimensionId);
        if (dimension == null) {
            return;
        }
        studentAbilityMapper.delete(new LambdaQueryWrapper<StudentAbility>()
                .eq(StudentAbility::getDimensionId, dimensionId));
        evidenceMapper.delete(new LambdaQueryWrapper<AbilityEvidence>()
                .eq(AbilityEvidence::getDimensionId, dimensionId));
        removeById(dimensionId);
    }

    // ==================== 学生能力图谱 ====================

    /** 获取学生在某个教学班下的能力图谱 */
    public List<StudentAbility> getStudentAbility(Long studentId, Long classId) {
        return studentAbilityMapper.selectList(new LambdaQueryWrapper<StudentAbility>()
                .eq(StudentAbility::getStudentId, studentId)
                .eq(StudentAbility::getClassId, classId));
    }

    /** 综合掌握度（按权重加权） */
    public BigDecimal getOverallMastery(Long studentId, Long classId) {
        StudentAbilityReport report = getReport(studentId, classId);
        if (report != null && report.getOverallScore() != null) {
            return report.getOverallScore();
        }
        return calcWeightedOverall(getByClassId(classId), getStudentAbility(studentId, classId));
    }

    /** 查询学情报告 */
    public StudentAbilityReport getReport(Long studentId, Long classId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<StudentAbilityReport>()
                .eq(StudentAbilityReport::getStudentId, studentId)
                .eq(StudentAbilityReport::getClassId, classId)
                .last("limit 1"));
    }

    /** 学生详情聚合：维度分数 + 提问摘要 + 作业摘要 */
    public AbilityProfileVO getProfile(Long studentId, Long classId) {
        List<AbilityDimension> dimensions = getByClassId(classId);
        List<StudentAbility> abilities = getStudentAbility(studentId, classId);
        Map<Long, StudentAbility> abilityMap = new HashMap<>();
        for (StudentAbility sa : abilities) {
            abilityMap.put(sa.getDimensionId(), sa);
        }

        AbilityProfileVO profile = new AbilityProfileVO();
        profile.setStudentId(studentId);
        profile.setClassId(classId);

        List<AbilityProfileVO.DimensionScoreVO> dimensionScores = new ArrayList<>();
        for (AbilityDimension dim : dimensions) {
            AbilityProfileVO.DimensionScoreVO vo = new AbilityProfileVO.DimensionScoreVO();
            vo.setDimensionId(dim.getId());
            vo.setDimensionName(dim.getDimensionName());
            vo.setDimensionCode(dim.getDimensionCode());
            vo.setWeight(dim.getWeight());
            vo.setDescription(dim.getDescription());
            vo.setSortOrder(dim.getSortOrder());
            StudentAbility sa = abilityMap.get(dim.getId());
            if (sa != null) {
                vo.setScore(sa.getMasteryLevel());
                vo.setComment(sa.getComment());
                vo.setEvidenceCount(sa.getEvidenceCount());
            } else {
                vo.setScore(BigDecimal.ZERO);
                vo.setEvidenceCount(0);
            }
            dimensionScores.add(vo);
        }
        profile.setDimensions(dimensionScores);

        StudentAbilityReport report = getReport(studentId, classId);
        if (report == null) {
            report = new StudentAbilityReport();
            report.setStudentId(studentId);
            report.setClassId(classId);
            report.setOverallScore(calcWeightedOverall(dimensions, abilities));
            report.setSource("none");
            report.setSummary("");
        }
        AbilityProfileVO.ReportVO reportVO = new AbilityProfileVO.ReportVO();
        reportVO.setOverallScore(report.getOverallScore());
        reportVO.setSummary(report.getSummary());
        reportVO.setSource(report.getSource());
        reportVO.setEvaluatedAt(report.getEvaluatedAt());
        reportVO.setQuestionCount(report.getQuestionCount());
        reportVO.setHomeworkCount(report.getHomeworkCount());
        reportVO.setHomeworkAvgScore(report.getHomeworkAvgScore());
        profile.setReport(reportVO);

        // 提问摘要（按教学班隔离）
        List<StudentQuestionLog> questions = questionLogService.getByStudentAndClass(classId, studentId);
        AbilityProfileVO.QuestionSummaryVO questionSummary = new AbilityProfileVO.QuestionSummaryVO();
        questionSummary.setCount(questions.size());
        Map<String, Long> topicCounts = new LinkedHashMap<>();
        for (StudentQuestionLog q : questions) {
            if (q.getTopics() == null || q.getTopics().isBlank()) {
                continue;
            }
            for (String topic : q.getTopics().split(",")) {
                String key = topic.trim();
                if (!key.isEmpty()) {
                    topicCounts.merge(key, 1L, Long::sum);
                }
            }
        }
        topicCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(6)
                .forEach(entry -> {
                    AbilityProfileVO.TopicCountVO vo = new AbilityProfileVO.TopicCountVO();
                    vo.setTopic(entry.getKey());
                    vo.setCount(entry.getValue());
                    questionSummary.getTopTopics().add(vo);
                });
        profile.setQuestions(questionSummary);

        // 作业摘要
        List<ExamHomeworkClient.HomeworkInfo> homework = examHomeworkClient.fetchStudentHomework(classId, studentId);
        AbilityProfileVO.HomeworkSummaryVO homeworkSummary = new AbilityProfileVO.HomeworkSummaryVO();
        homeworkSummary.setCount(homework.size());
        int graded = 0;
        int submitted = 0;
        for (ExamHomeworkClient.HomeworkInfo info : homework) {
            if (info.getScore() != null) {
                graded++;
            }
            if (info.getStatus() != null && !info.getStatus().isBlank()) {
                submitted++;
            }
            AbilityProfileVO.HomeworkItemVO item = new AbilityProfileVO.HomeworkItemVO();
            item.setTemplateId(info.getTemplateId());
            item.setTitle(info.getTitle());
            item.setScore(info.getScore());
            item.setStatus(info.getStatus());
            item.setComment(info.getComment());
            item.setSubmitTime(info.getSubmitTime());
            homeworkSummary.getItems().add(item);
        }
        homeworkSummary.setGradedCount(graded);
        homeworkSummary.setSubmittedCount(submitted);
        homeworkSummary.setAvgScore(calcHomeworkAvg(homework));
        profile.setHomework(homeworkSummary);

        if (!questions.isEmpty() && questions.get(0).getStudentName() != null) {
            profile.setStudentName(questions.get(0).getStudentName());
        }

        return profile;
    }

    /** 手动更新或创建学生能力达成度（保留给人工校准使用） */
    public void updateMastery(Long studentId, Long classId, Long dimensionId, BigDecimal masteryLevel) {
        StudentAbility existing = studentAbilityMapper.selectOne(new LambdaQueryWrapper<StudentAbility>()
                .eq(StudentAbility::getStudentId, studentId)
                .eq(StudentAbility::getDimensionId, dimensionId)
                .last("limit 1"));
        if (existing != null) {
            existing.setMasteryLevel(clamp(masteryLevel));
            existing.setEvidenceCount((existing.getEvidenceCount() == null ? 0 : existing.getEvidenceCount()) + 1);
            studentAbilityMapper.updateById(existing);
        } else {
            StudentAbility ability = new StudentAbility();
            ability.setStudentId(studentId);
            ability.setClassId(classId);
            ability.setDimensionId(dimensionId);
            ability.setMasteryLevel(clamp(masteryLevel));
            ability.setEvidenceCount(1);
            studentAbilityMapper.insert(ability);
        }
    }

    // ==================== 智能体评估 ====================

    /**
     * 触发评估：采集该教学班的作业与提问 → 智能体按维度打分 → 持久化，形成闭环。
     */
    @Transactional
    public AbilityProfileVO evaluate(Long studentId, Long classId, String studentName) {
        List<AbilityDimension> dimensions = getByClassId(classId);
        if (dimensions.isEmpty()) {
            throw new IllegalStateException("该教学班尚未设置能力维度，请先由教师在教学中配置");
        }

        List<StudentQuestionLog> questions = questionLogService.getByStudentAndClass(classId, studentId);
        List<ExamHomeworkClient.HomeworkInfo> homework = examHomeworkClient.fetchStudentHomework(classId, studentId);

        AbilityAiClient.ScoreResult aiResult = abilityAiClient.score(
                studentId, classId, studentName, dimensions, questions, homework);

        Map<Long, AbilityAiClient.Item> aiScores = new HashMap<>();
        String summary;
        String source;
        BigDecimal overall;

        if (aiResult != null && !aiResult.getScores().isEmpty()) {
            for (AbilityAiClient.Item item : aiResult.getScores()) {
                aiScores.put(item.getDimensionId(), item);
            }
            summary = aiResult.getSummary();
            source = "agent";
            overall = aiResult.getOverall();
        } else {
            // 智能体不可用 → 本地规则兜底，保证闭环不中断
            aiScores = null;
            summary = buildRuleSummary(questions, homework);
            source = "rule";
            overall = null;
        }

        BigDecimal homeworkAvg = calcHomeworkAvg(homework);
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        List<StudentAbility> persisted = new ArrayList<>();
        for (AbilityDimension dim : dimensions) {
            BigDecimal score;
            String comment;
            if (aiScores != null) {
                AbilityAiClient.Item item = aiScores.get(dim.getId());
                score = item != null && item.getScore() != null
                        ? clamp(item.getScore())
                        : ruleScore(dim, questions, homework, homeworkAvg);
                comment = item != null && item.getComment() != null && !item.getComment().isBlank()
                        ? item.getComment()
                        : ruleComment(score, questions, homework);
            } else {
                score = ruleScore(dim, questions, homework, homeworkAvg);
                comment = ruleComment(score, questions, homework);
            }

            BigDecimal weight = dim.getWeight() != null ? dim.getWeight() : BigDecimal.ONE;
            weightedSum = weightedSum.add(score.multiply(weight));
            totalWeight = totalWeight.add(weight);

            StudentAbility ability = upsertAbility(studentId, classId, dim.getId(), score, comment,
                    questions.size() + homework.size());
            persisted.add(ability);

            saveEvidence(studentId, dim.getId(), score, questions.size(), homework.size(), comment);
        }

        BigDecimal overallScore = overall != null && overall.compareTo(BigDecimal.ZERO) > 0
                ? clamp(overall)
                : (totalWeight.compareTo(BigDecimal.ZERO) > 0
                    ? weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

        StudentAbilityReport report = getReport(studentId, classId);
        if (report == null) {
            report = new StudentAbilityReport();
            report.setStudentId(studentId);
            report.setClassId(classId);
        }
        report.setOverallScore(overallScore);
        report.setSummary(summary);
        report.setSource(source);
        report.setQuestionCount(questions.size());
        report.setHomeworkCount(homework.size());
        report.setHomeworkAvgScore(homeworkAvg);
        report.setEvaluatedAt(LocalDateTime.now());
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }

        AbilityProfileVO profile = getProfile(studentId, classId);
        profile.setStudentName(studentName);
        return profile;
    }

    // ==================== 内部方法 ====================

    private StudentAbility upsertAbility(Long studentId, Long classId, Long dimensionId,
                                         BigDecimal score, String comment, int evidenceCount) {
        StudentAbility existing = studentAbilityMapper.selectOne(new LambdaQueryWrapper<StudentAbility>()
                .eq(StudentAbility::getStudentId, studentId)
                .eq(StudentAbility::getClassId, classId)
                .eq(StudentAbility::getDimensionId, dimensionId)
                .last("limit 1"));
        if (existing != null) {
            existing.setMasteryLevel(score);
            existing.setComment(comment);
            existing.setEvidenceCount(evidenceCount);
            studentAbilityMapper.updateById(existing);
            return existing;
        }
        StudentAbility ability = new StudentAbility();
        ability.setStudentId(studentId);
        ability.setClassId(classId);
        ability.setDimensionId(dimensionId);
        ability.setMasteryLevel(score);
        ability.setComment(comment);
        ability.setEvidenceCount(evidenceCount);
        studentAbilityMapper.insert(ability);
        return ability;
    }

    private void saveEvidence(Long studentId, Long dimensionId, BigDecimal score,
                              int questionCount, int homeworkCount, String comment) {
        try {
            AbilityEvidence evidence = new AbilityEvidence();
            evidence.setStudentId(studentId);
            evidence.setDimensionId(dimensionId);
            evidence.setSourceType(homeworkCount > 0 ? "homework" : "chat");
            evidence.setSourceId(0L);
            evidence.setScore(score);
            evidence.setEvidenceText("提问 " + questionCount + " 条 / 作业 " + homeworkCount + " 份。" + comment);
            evidenceMapper.insert(evidence);
        } catch (Exception e) {
            log.warn("写入能力证据失败: {}", e.getMessage());
        }
    }

    private BigDecimal calcWeightedOverall(List<AbilityDimension> dimensions, List<StudentAbility> abilities) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (AbilityDimension dim : dimensions) {
            BigDecimal weight = dim.getWeight() != null ? dim.getWeight() : BigDecimal.ONE;
            for (StudentAbility sa : abilities) {
                if (dim.getId().equals(sa.getDimensionId()) && sa.getMasteryLevel() != null) {
                    weightedSum = weightedSum.add(sa.getMasteryLevel().multiply(weight));
                    totalWeight = totalWeight.add(weight);
                }
            }
        }
        return totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private BigDecimal calcHomeworkAvg(List<ExamHomeworkClient.HomeworkInfo> homework) {
        double sum = 0;
        int count = 0;
        for (ExamHomeworkClient.HomeworkInfo info : homework) {
            if (info.getScore() != null) {
                sum += info.getScore();
                count++;
            }
        }
        return count > 0 ? BigDecimal.valueOf(sum / count).setScale(2, RoundingMode.HALF_UP) : null;
    }

    /**
     * 规则兜底评分：作业平均分 + 提问活跃度作为基线，
     * 再用维度关键词与学习文本的匹配度做差异化，保证雷达图不是等分多边形。
     */
    private BigDecimal ruleScore(AbilityDimension dim, List<StudentQuestionLog> questions,
                                 List<ExamHomeworkClient.HomeworkInfo> homework, BigDecimal homeworkAvg) {
        double score = 50.0;
        if (homeworkAvg != null) {
            score = homeworkAvg.doubleValue() * 0.7 + 30;
        }
        double activity = Math.min(questions.size(), 20) / 20.0 * 100;
        score = score * 0.7 + activity * 0.3;

        // 维度关键词匹配：维度名称/说明中的词在学习文本中出现越多，得分略高
        String corpus = buildCorpus(questions, homework);
        int hits = countKeywordHits(dim, corpus);
        score += Math.min(hits, 6) * 2.0;

        // 维度固有差异，避免所有维度同分
        Long dimId = dim.getId() == null ? 0L : dim.getId();
        score += (dimId % 5 - 2) * 1.5;

        return clamp(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
    }

    private String buildCorpus(List<StudentQuestionLog> questions,
                               List<ExamHomeworkClient.HomeworkInfo> homework) {
        StringBuilder builder = new StringBuilder();
        for (StudentQuestionLog q : questions) {
            if (q.getQuestion() != null) {
                builder.append(q.getQuestion()).append('\n');
            }
            if (q.getTopics() != null) {
                builder.append(q.getTopics()).append('\n');
            }
        }
        for (ExamHomeworkClient.HomeworkInfo info : homework) {
            if (info.getAnswer() != null) {
                builder.append(info.getAnswer()).append('\n');
            }
            if (info.getComment() != null) {
                builder.append(info.getComment()).append('\n');
            }
            if (info.getTitle() != null) {
                builder.append(info.getTitle()).append('\n');
            }
        }
        return builder.toString();
    }

    private int countKeywordHits(AbilityDimension dim, String corpus) {
        if (corpus.isEmpty()) {
            return 0;
        }
        int hits = 0;
        String source = (dim.getDimensionName() == null ? "" : dim.getDimensionName()) + " "
                + (dim.getDescription() == null ? "" : dim.getDescription());
        // 中文按 2 字片段、英文按空格分词，避免过度匹配
        for (String token : source.split("[，。、；：,.;: /]+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2 && corpus.contains(trimmed)) {
                hits++;
            }
        }
        return hits;
    }

    private String ruleComment(BigDecimal score, List<StudentQuestionLog> questions,
                               List<ExamHomeworkClient.HomeworkInfo> homework) {
        double value = score.doubleValue();
        String level = value >= 85 ? "表现优秀" : value >= 70 ? "表现良好" : value >= 55 ? "基本达标" : "仍有提升空间";
        return String.format("依据 %d 条提问与 %d 份作业，该维度%s（%s分）。",
                questions.size(), homework.size(), level, score.stripTrailingZeros().toPlainString());
    }

    private String buildRuleSummary(List<StudentQuestionLog> questions,
                                    List<ExamHomeworkClient.HomeworkInfo> homework) {
        BigDecimal avg = calcHomeworkAvg(homework);
        Map<String, Long> topics = new LinkedHashMap<>();
        for (StudentQuestionLog q : questions) {
            if (q.getTopics() == null || q.getTopics().isBlank()) {
                continue;
            }
            for (String topic : q.getTopics().split(",")) {
                String key = topic.trim();
                if (!key.isEmpty()) {
                    topics.merge(key, 1L, Long::sum);
                }
            }
        }
        String topTopic = topics.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("暂无突出主题");
        return String.format("智能体暂不可用，已按作业与提问数据生成规则评分：共 %d 条提问、%d 份作业，%s作业平均分 %s，主要关注主题为「%s」。",
                questions.size(), homework.size(),
                avg != null ? "" : "尚无已批改",
                avg != null ? avg.toPlainString() : "-",
                topTopic);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
