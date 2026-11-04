package com.xingshu.learning.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * xs-exam 作业服务客户端：按教学班收集该学生的作业提交情况。
 */
@Component
public class ExamHomeworkClient {

    private static final Logger log = LoggerFactory.getLogger(ExamHomeworkClient.class);

    private final RestTemplate restTemplate;

    @Value("${xs.exam.base-url:http://localhost:8084}")
    private String examBaseUrl;

    public ExamHomeworkClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<HomeworkInfo> fetchStudentHomework(Long classId, Long studentId) {
        List<Long> templateIds = fetchClassTemplateIds(classId);
        List<HomeworkInfo> result = new ArrayList<>();
        for (Long templateId : templateIds) {
            Map<String, Object> template = getData("/api/homework/templates/" + templateId);
            List<Map<String, Object>> submissions = getListData("/api/homework/templates/" + templateId + "/submissions");
            if (submissions == null) {
                continue;
            }
            for (Map<String, Object> submission : submissions) {
                if (!studentId.equals(asLong(submission.get("studentId")))) {
                    continue;
                }
                HomeworkInfo info = new HomeworkInfo();
                info.setTemplateId(templateId);
                info.setTitle(template != null ? asString(template.get("title")) : "作业 " + templateId);
                info.setScore(asDouble(submission.get("score")));
                info.setStatus(asString(submission.get("status")));
                info.setComment(asString(submission.get("comment")));
                info.setAnswer(trim(asString(submission.get("answer")), 600));
                info.setSubmitTime(parseTime(asString(submission.get("submitTime"))));
                result.add(info);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Long> fetchClassTemplateIds(Long classId) {
        List<Map<String, Object>> bindings = getListData("/api/homework/classes/" + classId);
        if (bindings == null) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> binding : bindings) {
            Long templateId = asLong(binding.get("templateId"));
            if (templateId != null && !ids.contains(templateId)) {
                ids.add(templateId);
            }
        }
        return ids;
    }

    private Map<String, Object> getData(String path) {
        try {
            Map<String, Object> body = exchange(path);
            Object data = body != null ? body.get("data") : null;
            if (data instanceof Map) {
                return castMap(data);
            }
        } catch (Exception e) {
            log.warn("调用作业服务失败 {}: {}", path, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getListData(String path) {
        try {
            Map<String, Object> body = exchange(path);
            Object data = body != null ? body.get("data") : null;
            if (data instanceof List) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Object item : (List<Object>) data) {
                    if (item instanceof Map) {
                        list.add(castMap(item));
                    }
                }
                return list;
            }
            // 分页结果 {list:[...]}
            if (data instanceof Map) {
                Object inner = ((Map<String, Object>) data).get("list");
                if (inner instanceof List) {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (Object item : (List<Object>) inner) {
                        if (item instanceof Map) {
                            list.add(castMap(item));
                        }
                    }
                    return list;
                }
            }
        } catch (Exception e) {
            log.warn("调用作业服务失败 {}: {}", path, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> exchange(String path) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                examBaseUrl + path,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
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

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank() || "null".equals(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }

    /** 作业提交信息 */
    @lombok.Data
    public static class HomeworkInfo {
        private Long templateId;
        private String title;
        private Double score;
        private String status;
        private String comment;
        private String answer;
        private LocalDateTime submitTime;
    }
}
