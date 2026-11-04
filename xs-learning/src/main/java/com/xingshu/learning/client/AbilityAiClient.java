package com.xingshu.learning.client;

import com.xingshu.learning.domain.po.AbilityDimension;
import com.xingshu.learning.domain.po.StudentQuestionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力评分智能体客户端：调用 FastAPI 获取各维度评分与评语。
 */
@Component
public class AbilityAiClient {

    private static final Logger log = LoggerFactory.getLogger(AbilityAiClient.class);

    private final RestTemplate restTemplate;

    @Value("${ai.ability-score-url:http://localhost:8000/api/student-insight/score}")
    private String scoreUrl;

    public AbilityAiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 返回 null 表示智能体不可用，由调用方走本地规则兜底。
     */
    @SuppressWarnings("unchecked")
    public ScoreResult score(Long studentId,
                             Long classId,
                             String studentName,
                             List<AbilityDimension> dimensions,
                             List<StudentQuestionLog> questions,
                             List<ExamHomeworkClient.HomeworkInfo> homework) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("student_id", studentId);
            payload.put("class_id", classId);
            payload.put("student_name", studentName);

            List<Map<String, Object>> dims = new ArrayList<>();
            for (AbilityDimension dim : dimensions) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", dim.getId());
                item.put("name", dim.getDimensionName());
                item.put("description", dim.getDescription() == null ? "" : dim.getDescription());
                item.put("weight", dim.getWeight());
                dims.add(item);
            }
            payload.put("dimensions", dims);

            List<Map<String, Object>> qs = new ArrayList<>();
            for (StudentQuestionLog q : questions) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("question", q.getQuestion());
                item.put("topics", q.getTopics());
                qs.add(item);
            }
            payload.put("questions", qs);

            List<Map<String, Object>> hws = new ArrayList<>();
            for (ExamHomeworkClient.HomeworkInfo hw : homework) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", hw.getTitle());
                item.put("score", hw.getScore());
                item.put("status", hw.getStatus());
                item.put("comment", hw.getComment());
                item.put("answer", hw.getAnswer());
                hws.add(item);
            }
            payload.put("homework", hws);

            Map<String, Object> body = restTemplate.postForObject(scoreUrl, payload, Map.class);
            if (body == null || Boolean.FALSE.equals(body.get("success"))) {
                log.warn("能力评分智能体返回失败: {}", body == null ? "empty" : body.get("error"));
                return null;
            }

            ScoreResult result = new ScoreResult();
            result.setSummary(asString(body.get("summary")));
            result.setSource(asString(body.get("source")));
            result.setOverall(asDecimal(body.get("overall")));

            Object scoresObj = body.get("scores");
            if (scoresObj instanceof List) {
                for (Object raw : (List<Object>) scoresObj) {
                    if (!(raw instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> map = (Map<String, Object>) raw;
                    Item item = new Item();
                    item.setDimensionId(asLong(map.get("dimension_id")));
                    item.setScore(asDecimal(map.get("score")));
                    item.setComment(asString(map.get("comment")));
                    if (item.getDimensionId() != null) {
                        result.getScores().add(item);
                    }
                }
            }
            if (result.getScores().isEmpty()) {
                return null;
            }
            return result;
        } catch (Exception e) {
            log.warn("能力评分智能体不可用，使用规则兜底: {}", e.getMessage());
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @lombok.Data
    public static class ScoreResult {
        private String summary;
        private String source;
        private BigDecimal overall;
        private List<Item> scores = new ArrayList<>();
    }

    @lombok.Data
    public static class Item {
        private Long dimensionId;
        private BigDecimal score;
        private String comment;
    }
}
