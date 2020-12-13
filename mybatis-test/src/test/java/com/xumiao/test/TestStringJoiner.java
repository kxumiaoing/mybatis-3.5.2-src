package com.xumiao.test;

import java.util.*;
import java.util.stream.IntStream;

public class TestStringJoiner {
    public static void main(String[] args) {
        StringJoiner joiner = new StringJoiner(",");
        StringJoiner joiner1 = new StringJoiner(",", "start", "end");

        IntStream.range(1, 5).forEach(i -> {
            joiner.add(i + "");
            joiner1.add(i + "");
        });

        System.out.println("joiner = " + joiner.toString());
        System.out.println("joiner1 = " + joiner1.toString());
    }
}
