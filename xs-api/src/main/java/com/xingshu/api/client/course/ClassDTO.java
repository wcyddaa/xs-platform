package com.xingshu.api.client.course;

import lombok.Data;

/**
 * 班级信息 DTO
 */
@Data
public class ClassDTO {
    private Long id;
    private String name;
    private Long courseId;
    private String courseName;
    private String college;
}