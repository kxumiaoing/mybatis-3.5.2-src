package org.xumiao.test.vo;

import lombok.Data;
import org.xumiao.test.entity.Person;

import java.util.List;

@Data
public class People {
    private Long id;
    private List<Person> personList;
}
