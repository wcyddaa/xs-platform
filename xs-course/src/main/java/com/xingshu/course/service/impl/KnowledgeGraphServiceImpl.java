package com.xingshu.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xingshu.course.domain.po.Chapter;
import com.xingshu.course.domain.po.Course;
import com.xingshu.course.domain.po.CourseClass;
import com.xingshu.course.domain.po.KnowledgeEdge;
import com.xingshu.course.domain.po.KnowledgeNode;
import com.xingshu.course.mapper.ChapterMapper;
import com.xingshu.course.mapper.CourseClassMapper;
import com.xingshu.course.mapper.CourseMapper;
import com.xingshu.course.mapper.KnowledgeEdgeMapper;
import com.xingshu.course.mapper.KnowledgeNodeMapper;
import com.xingshu.course.service.IKnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 课程知识图谱服务
 *
 * 数据来源：
 * 1. 课件（xs-book，课件绑定课程章节）
 * 2. 学生提问（xs-learning，按课程关联教学班的提问日志）
 * 3. 作业错误（xs-exam，按课程关联的作业模板及低分作答）
 *
 * 生成方式：调用 FastAPI 知识图谱智能体抽取节点与关系；智能体不可用时回退到规则图谱，
 * 保证教师与学生始终能看到可用的图谱。
 */
@Service
public class KnowledgeGraphServiceImpl implements IKnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphServiceImpl.class);
    private static final String BOOK_API_BASE = "http://localhost:8083";
    private static final String LEARNING_API_BASE = "http://localhost:8087";
    private static final String EXAM_API_BASE = "http://localhost:8084";
    private static final String AI_API_BASE = "http://localhost:8000";

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final CourseClassMapper courseClassMapper;
    private final RestTemplate restTemplate;

    public KnowledgeGraphServiceImpl(KnowledgeNodeMapper nodeMapper,
                                     KnowledgeEdgeMapper edgeMapper,
                                     CourseMapper courseMapper,
                                     ChapterMapper chapterMapper,
                                     CourseClassMapper courseClassMapper,
                                     RestTemplate restTemplate) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.courseClassMapper = courseClassMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public Map<String, Object> getGraph(Long courseId) {
        List<KnowledgeNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getCourseId, courseId)
                        .orderByAsc(KnowledgeNode::getId));
        List<KnowledgeEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEdge>()
                        .eq(KnowledgeEdge::getCourseId, courseId)
                        .orderByAsc(KnowledgeEdge::getId));

        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("generatedAt", nodes.stream()
                .map(KnowledgeNode::getUpdateTime)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .map(LocalDateTime::toString)
                .orElse(null));
        result.put("empty", nodes.isEmpty());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> extractGraph(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new IllegalStateException("课程不存在");
        }
        List<Chapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getCourseId, courseId)
                        .orderByAsc(Chapter::getSortOrder));
        List<CourseClass> courseClasses = courseClassMapper.selectList(
                new LambdaQueryWrapper<CourseClass>().eq(CourseClass::getCourseId, courseId));

        List<Map<String, Object>> coursewares = fetchCoursewares(courseId);
        List<String> questionTopics = fetchQuestionTopics(courseClasses);
        List<String> homeworkErrors = fetchHomeworkErrors(courseId, courseClasses);

        Map<String, Object> payload = new HashMap<>();
        payload.put("courseId", courseId);
        payload.put("courseName", course.getName());
        List<Map<String, Object>> chapterPayload = new ArrayList<>();
        for (Chapter ch : chapters) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", ch.getId());
            item.put("title", ch.getTitle());
            item.put("description", ch.getDescription());
            chapterPayload.add(item);
        }
        payload.put("chapters", chapterPayload);
        payload.put("coursewares", coursewares);
        payload.put("questionTopics", questionTopics);
        payload.put("homeworkErrors", homeworkErrors);

        ExtractedGraph graph;
        try {
            graph = callAgent(payload);
            if (graph.nodes.isEmpty()) {
                log.warn("知识图谱智能体未返回节点，课程 {} 使用规则图谱", courseId);
                graph = buildFallbackGraph(courseId, chapters, coursewares, questionTopics);
            }
        } catch (Exception e) {
            log.warn("知识图谱智能体调用失败，课程 {} 使用规则图谱: {}", courseId, e.getMessage());
            graph = buildFallbackGraph(courseId, chapters, coursewares, questionTopics);
        }

        persist(courseId, graph);
        Map<String, Object> result = getGraph(courseId);
        result.put("source", graph.source);
        return result;
    }

    @Override
    public void deleteByCourseId(Long courseId) {
        nodeMapper.delete(new LambdaQueryWrapper<KnowledgeNode>()
                .eq(KnowledgeNode::getCourseId, courseId));
        edgeMapper.delete(new LambdaQueryWrapper<KnowledgeEdge>()
                .eq(KnowledgeEdge::getCourseId, courseId));
    }

    // ==================== 数据采集 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCoursewares(Long courseId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Map<String, Object> body = restTemplate.exchange(
                    BOOK_API_BASE + "/api/courseware?courseId=" + courseId + "&page=1&size=100",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            if (body == null || !isOk(body)) {
                return result;
            }
            Object dataObj = body.get("data");
            if (dataObj instanceof Map<?, ?> data) {
                Object listObj = data.get("list");
                if (listObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> item) {
                            Map<String, Object> cw = new LinkedHashMap<>();
                            cw.put("title", item.get("title"));
                            cw.put("chapterId", item.get("chapterId"));
                            cw.put("coursewareId", item.get("id"));
                            result.add(cw);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取课程 {} 课件失败: {}", courseId, e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchQuestionTopics(List<CourseClass> courseClasses) {
        Set<String> topics = new LinkedHashSet<>();
        for (CourseClass cc : courseClasses) {
            try {
                Map<String, Object> body = restTemplate.exchange(
                        LEARNING_API_BASE + "/api/question-log?classId=" + cc.getClassId() + "&pageNum=1&pageSize=30",
                        HttpMethod.GET, null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
                if (body == null || !isOk(body)) continue;
                Object dataObj = body.get("data");
                if (dataObj instanceof Map<?, ?> data && data.get("list") instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> item) {
                            String question = str(item.get("question"));
                            String topic = str(item.get("topics"));
                            if (!topic.isBlank()) {
                                topics.add(topic);
                            } else if (!question.isBlank()) {
                                topics.add(question.length() > 60 ? question.substring(0, 60) : question);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取教学班 {} 提问记录失败: {}", cc.getClassId(), e.getMessage());
            }
            if (topics.size() >= 60) break;
        }
        return new ArrayList<>(topics);
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchHomeworkErrors(Long courseId, List<CourseClass> courseClasses) {
        List<String> errors = new ArrayList<>();
        for (CourseClass cc : courseClasses) {
            try {
                Map<String, Object> body = restTemplate.exchange(
                        EXAM_API_BASE + "/api/homework/classes/" + cc.getClassId(),
                        HttpMethod.GET, null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
                if (body == null || !isOk(body) || !(body.get("data") instanceof List<?> links)) continue;
                for (Object linkObj : links) {
                    if (!(linkObj instanceof Map<?, ?> link)) continue;
                    Object templateId = link.get("templateId");
                    if (templateId == null) continue;
                    appendHomeworkError(courseId, ((Number) templateId).longValue(), errors);
                    if (errors.size() >= 20) return errors;
                }
            } catch (Exception e) {
                log.warn("获取教学班 {} 作业失败: {}", cc.getClassId(), e.getMessage());
            }
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private void appendHomeworkError(Long courseId, Long templateId, List<String> errors) {
        try {
            Map<String, Object> templateBody = restTemplate.exchange(
                    EXAM_API_BASE + "/api/homework/templates/" + templateId,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            if (templateBody == null || !isOk(templateBody) || !(templateBody.get("data") instanceof Map<?, ?> tpl)) {
                return;
            }
            Object tplCourseId = tpl.get("courseId");
            if (tplCourseId != null && !courseId.equals(((Number) tplCourseId).longValue())) {
                return;
            }
            Map<String, Object> subBody = restTemplate.exchange(
                    EXAM_API_BASE + "/api/homework/templates/" + templateId + "/submissions",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            if (subBody == null || !isOk(subBody) || !(subBody.get("data") instanceof List<?> subs)) {
                return;
            }
            String title = str(tpl.get("title"));
            for (Object o : subs) {
                if (!(o instanceof Map<?, ?> sub)) continue;
                Object scoreObj = sub.get("score");
                double score = scoreObj instanceof Number n ? n.doubleValue() : Double.NaN;
                String comment = str(sub.get("comment"));
                String answer = str(sub.get("answer"));
                if (!Double.isNaN(score) && score < 60) {
                    errors.add("《" + title + "》得分 " + score + (comment.isBlank() ? "" : "，评语：" + trunc(comment, 80))
                            + (answer.isBlank() ? "" : "，作答摘要：" + trunc(answer, 120)));
                } else if (!comment.isBlank() && (answer.isBlank() || answer.length() < 10)) {
                    errors.add("《" + title + "》" + trunc(comment, 120));
                }
                if (errors.size() >= 20) return;
            }
        } catch (Exception e) {
            log.warn("获取作业 {} 提交失败: {}", templateId, e.getMessage());
        }
    }

    private boolean isOk(Map<String, Object> body) {
        Object code = body.get("code");
        return code instanceof Number n && n.intValue() == 200;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String trunc(String text, int max) {
        if (text == null) return "";
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    // ==================== 智能体调用 ====================

    @SuppressWarnings("unchecked")
    private ExtractedGraph callAgent(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = restTemplate.exchange(
                AI_API_BASE + "/api/knowledge-graph/extract",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        if (body == null) {
            throw new IllegalStateException("知识图谱智能体无响应");
        }
        boolean success = Boolean.TRUE.equals(body.get("success"));
        if (!success) {
            throw new IllegalStateException(str(body.get("error")));
        }
        ExtractedGraph graph = new ExtractedGraph();
        graph.source = "agent";
        Object nodesObj = body.get("nodes");
        if (nodesObj instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> item)) continue;
                String key = str(item.get("key")).trim();
                String name = str(item.get("name")).trim();
                if (key.isBlank() || name.isBlank()) continue;
                ExtractedNode node = new ExtractedNode();
                node.key = key;
                node.name = name;
                node.category = defaultIfBlank(str(item.get("category")), "concept");
                node.description = trunc(str(item.get("description")), 480);
                node.chapterId = item.get("chapterId") instanceof Number n ? n.longValue() : null;
                node.weight = item.get("weight") instanceof Number n ? clamp(n.doubleValue()) : 1.0;
                node.source = defaultIfBlank(str(item.get("source")), "courseware");
                graph.nodes.add(node);
            }
        }
        Object edgesObj = body.get("edges");
        if (edgesObj instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> item)) continue;
                String source = str(item.get("source")).trim();
                String target = str(item.get("target")).trim();
                if (source.isBlank() || target.isBlank() || source.equals(target)) continue;
                ExtractedEdge edge = new ExtractedEdge();
                edge.source = source;
                edge.target = target;
                edge.relation = defaultIfBlank(str(item.get("relation")), "related");
                edge.weight = item.get("weight") instanceof Number n ? clamp(n.doubleValue()) : 1.0;
                graph.edges.add(edge);
            }
        }
        // 智能体返回的关键节点键去重
        Set<String> keys = new LinkedHashSet<>();
        graph.nodes.removeIf(n -> !keys.add(n.key));
        return graph;
    }

    private double clamp(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.5;
        return Math.max(0.0, Math.min(1.0, v));
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    // ==================== 规则图谱（智能体不可用时的兜底） ====================

    private ExtractedGraph buildFallbackGraph(Long courseId, List<Chapter> chapters,
                                              List<Map<String, Object>> coursewares,
                                              List<String> questionTopics) {
        ExtractedGraph graph = new ExtractedGraph();
        graph.source = "rule";

        String courseKey = "course_" + courseId;
        ExtractedNode courseNode = new ExtractedNode();
        courseNode.key = courseKey;
        courseNode.name = courseMapper.selectById(courseId).getName();
        courseNode.category = "course";
        courseNode.description = "课程总览";
        courseNode.weight = 1.0;
        courseNode.source = "chapter";
        graph.nodes.add(courseNode);

        for (Chapter ch : chapters) {
            ExtractedNode node = new ExtractedNode();
            node.key = "ch_" + ch.getId();
            node.name = ch.getTitle();
            node.category = "chapter";
            node.description = trunc(ch.getDescription(), 480);
            node.chapterId = ch.getId();
            node.weight = 1.0;
            node.source = "chapter";
            graph.nodes.add(node);

            ExtractedEdge edge = new ExtractedEdge();
            edge.source = courseKey;
            edge.target = node.key;
            edge.relation = "contains";
            edge.weight = 1.0;
            graph.edges.add(edge);
        }

        int index = 0;
        for (Map<String, Object> cw : coursewares) {
            ExtractedNode node = new ExtractedNode();
            node.key = "cw_" + index++;
            node.name = defaultIfBlank(str(cw.get("title")), "课件");
            node.category = "courseware";
            node.description = "课程课件";
            node.chapterId = cw.get("chapterId") instanceof Number n ? n.longValue() : null;
            node.weight = 0.9;
            node.source = "courseware";
            graph.nodes.add(node);

            ExtractedEdge edge = new ExtractedEdge();
            edge.source = node.chapterId != null ? "ch_" + node.chapterId : courseKey;
            edge.target = node.key;
            edge.relation = "contains";
            edge.weight = 0.9;
            graph.edges.add(edge);
        }

        int topicIndex = 0;
        for (String topic : questionTopics) {
            if (topic == null || topic.isBlank()) continue;
            ExtractedNode node = new ExtractedNode();
            node.key = "q_" + topicIndex++;
            node.name = trunc(topic, 60);
            node.category = "question";
            node.description = "学生高频提问主题";
            node.weight = 0.7;
            node.source = "question";
            graph.nodes.add(node);

            ExtractedEdge edge = new ExtractedEdge();
            edge.source = courseKey;
            edge.target = node.key;
            edge.relation = "related";
            edge.weight = 0.7;
            graph.edges.add(edge);
        }
        return graph;
    }

    // ==================== 持久化 ====================

    private void persist(Long courseId, ExtractedGraph graph) {
        nodeMapper.delete(new LambdaQueryWrapper<KnowledgeNode>()
                .eq(KnowledgeNode::getCourseId, courseId));
        edgeMapper.delete(new LambdaQueryWrapper<KnowledgeEdge>()
                .eq(KnowledgeEdge::getCourseId, courseId));

        Map<String, String> savedKeys = new HashMap<>();
        for (ExtractedNode n : graph.nodes) {
            KnowledgeNode node = new KnowledgeNode();
            node.setCourseId(courseId);
            node.setNodeKey(n.key);
            node.setName(n.name);
            node.setCategory(n.category);
            node.setDescription(n.description);
            node.setChapterId(n.chapterId);
            node.setWeight(BigDecimal.valueOf(n.weight).setScale(2, RoundingMode.HALF_UP));
            node.setSource(n.source != null && n.source.length() > 30 ? n.source.substring(0, 30) : n.source);
            node.setCreateTime(LocalDateTime.now());
            node.setUpdateTime(LocalDateTime.now());
            nodeMapper.insert(node);
            savedKeys.put(n.key, n.key);
        }

        for (ExtractedEdge e : graph.edges) {
            if (!savedKeys.containsKey(e.source) || !savedKeys.containsKey(e.target)) continue;
            KnowledgeEdge edge = new KnowledgeEdge();
            edge.setCourseId(courseId);
            edge.setSourceKey(e.source);
            edge.setTargetKey(e.target);
            edge.setRelation(e.relation);
            edge.setWeight(BigDecimal.valueOf(e.weight).setScale(2, RoundingMode.HALF_UP));
            edge.setCreateTime(LocalDateTime.now());
            edgeMapper.insert(edge);
        }
        log.info("课程 {} 知识图谱已更新：{} 个节点，{} 条关系，来源={}",
                courseId, graph.nodes.size(), graph.edges.size(), graph.source);
    }

    // ==================== 内部结构 ====================

    private static class ExtractedGraph {
        String source = "rule";
        List<ExtractedNode> nodes = new ArrayList<>();
        List<ExtractedEdge> edges = new ArrayList<>();
    }

    private static class ExtractedNode {
        String key;
        String name;
        String category;
        String description;
        Long chapterId;
        double weight;
        String source;
    }

    private static class ExtractedEdge {
        String source;
        String target;
        String relation;
        double weight;
    }
}
