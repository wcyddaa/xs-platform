package com.xingshu.config.constant;

import java.util.List;

/**
 * 后端内置智能体模板。
 * 模板决定了智能体的能力边界（对应 FastAPI 中已注册的智能体），管理员不可新增或删除；
 * 教师只能在某个教学班下基于这些模板创建实例，并配置提示词与已发布的模型。
 */
public final class AgentTemplates {

    public record Template(String code, String name, String description, String defaultPrompt) {
    }

    public static final String CHAT = "chat";
    public static final String HOMEWORK = "homework";
    public static final String KNOWLEDGE_GRAPH = "knowledge_graph";
    public static final String STUDENT_INSIGHT = "student_insight";

    private static final List<Template> TEMPLATES = List.of(
            new Template(CHAT, "问答智能体",
                    "面向学生的课程答疑，可结合教材检索与联网搜索回答提问。",
                    "你是本课程的助教。回答学生问题时，优先依据课程教材与课件内容，"
                            + "给出清晰、循序渐进的解释；不确定的内容要明确说明，不编造教材页码。"),
            new Template(HOMEWORK, "作业智能体",
                    "根据教材、章节范围与知识点生成作业题目、参考答案与解析。",
                    "你是本课程的作业生成助手。请围绕指定章节与知识点生成难度适中的作业，"
                            + "包含选择题、简答题与应用题，并给出参考答案与解析；题目不得超出教材范围。"),
            new Template(KNOWLEDGE_GRAPH, "知识图谱智能体",
                    "从课件、学生高频提问与作业错误中抽取知识点及关系，形成课程知识图谱。",
                    "你是课程知识图谱构建智能体。请根据课件、学生提问主题与作业错误点，"
                            + "抽取核心知识点并建立先修、包含、关联等关系，输出严格合法的 JSON。"),
            new Template(STUDENT_INSIGHT, "报告生成智能体",
                    "基于提问分析、能力图谱与作业数据，为学生生成个性化的学情报告。",
                    "你是学情报告生成助手。请基于学生的提问记录、能力图谱与作业表现，"
                            + "生成结构清晰的 Markdown 学情报告，指出优势、薄弱点与可执行的改进建议。")
    );

    private AgentTemplates() {
    }

    public static List<Template> all() {
        return TEMPLATES;
    }

    public static Template find(String code) {
        if (code == null) {
            return null;
        }
        return TEMPLATES.stream().filter(t -> t.code().equals(code)).findFirst().orElse(null);
    }
}
