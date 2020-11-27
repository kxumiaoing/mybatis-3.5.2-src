package com.xumiao.test;

import org.junit.Test;

import java.io.IOException;

public class TestVfs {
    @Test
    public void test() throws IOException {
//        List<String> list = VFS.getInstance().list("test-vfs");
        /*
        List<URL> urls = Collections.list(getClass().getClassLoader().getResources("com/mysql/jdbc"));
        int a  =0;

        for (URL url : urls) {
            String fileName = url.getFile();
            URL u = null;
            try {
                u = new URL(fileName);
            } catch(Exception e) {
                int b = 1;

                b = 2;
            }
        }

         */

        StringBuilder sb = new StringBuilder("shanghai");

        System.out.println(sb.replace(0, sb.length(), "bei"));
        System.out.println(sb.replace(0, sb.length(), "beijingtianjin"));
    }
}
