package org.xumiao.test.entity;

import lombok.Data;

@Data
public class Person {
    private Integer id;
    private String name;
//    private List<Teacher> teachers;
//    private List<String> teacherNames;
//    private List<String> childNames;
    private String teacherName;
    private String childName;
}
