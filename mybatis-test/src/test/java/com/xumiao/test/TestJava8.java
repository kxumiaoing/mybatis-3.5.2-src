package com.xumiao.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestJava8 {
    public static void main(String[] args) {
        Map<String, List<String>> map = new HashMap<>();

        map.compute("shanghai", (k, v) -> {
            v = (null == v? new ArrayList<>() : v);
            v.add(k);

            return v;
        });

        int a = 0;
    }
}
