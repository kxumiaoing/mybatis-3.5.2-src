package com.xumiao.test;

import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.List;

public class TestType {
    public static void main(String[] args) {
        Type t = AbstractList.class.getGenericSuperclass();

        t = List.class.getGenericSuperclass();

        int a = 0;
    }
}
