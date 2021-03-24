package org.xumiao.test.vfs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class TestVFS {
    public static void main(String[] args) throws Exception {
        String path = "org/apache/commons/lang3";
        Enumeration<URL> enumer = TestVFS.class.getClassLoader().getResources(path);
        List<URL> urls = Collections.list(enumer);

        urls.stream()
                .forEach(u -> {
                    //URL = protocol + path(file)
                    //jar:file:/home/xumiao/.m2/repository/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar!/org/apache/commons/lang3
                    System.out.println("=====================================");
                    //-1
                    System.out.println("port = " + u.getPort());
                    //file:/home/xumiao/.m2/repository/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar!/org/apache/commons/lang3
                    System.out.println("path = " + u.getPath());
                    //file:/home/xumiao/.m2/repository/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar!/org/apache/commons/lang3
                    System.out.println("file = " + u.getFile());

                    {
                        URL uu = u;

                        try {
                            for (;;) {
                                //jar:file:/xxx/yyy/zzz.jar!/aaa/bbb/ccc
                                //  -> file:/xxx/yyy/zzz.jar!/aaa/bbb/ccc
                                //  -> /xxx/yyy/zzz.jar!/aaa/bbb/ccc(not a URL)
                                uu = new URL(uu.getFile());
                            }
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                    //jar:file:/home/xumiao/.m2/repository/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar!/org/apache/commons/lang3
                    StringBuilder sb = new StringBuilder(u.toExternalForm());
                    System.out.println("externalForm = " + sb);
                    //jar:file:/home/xumiao/.m2/repository/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar
                    sb.setLength(sb.lastIndexOf(".jar") + 4);
                    System.out.println("externalForm = " + sb);

                    URL url = null;

                    try {
                        url = new URL(sb.toString());
                    } catch (MalformedURLException e) {
                        //Exception: no !/
                        try {
                            url =  new URL("file:/home/xumiao/.m2/repository/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar");
                        } catch (MalformedURLException malformedURLException) {
                            malformedURLException.printStackTrace();
                        }
                    }

                    String myDir = "org/apache/commons/lang3/event", name;
                    try (JarInputStream in = new JarInputStream(url.openStream())) {
                        for (JarEntry entry = null; (entry = in.getNextJarEntry()) != null;) {
                           if ((name = entry.getName()).startsWith(myDir)) {
                               System.out.println("<<<<<<<<<" + name);
                           }
                        }

                        //<<<<<<<<<org/apache/commons/lang3/event/
                        //<<<<<<<<<org/apache/commons/lang3/event/EventUtils.class
                        //<<<<<<<<<org/apache/commons/lang3/event/EventListenerSupport.class
                        //<<<<<<<<<org/apache/commons/lang3/event/EventListenerSupport$ProxyInvocationHandler.class
                        //<<<<<<<<<org/apache/commons/lang3/event/EventUtils$EventBindingInvocationHandler.class
                    } catch (Exception e) {}
                    /*
                    URI uri = null;
                    URL url = null;

                    try {
                        uri = u.toURI();
                        url = new URL("http://www.baidu.com");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                     */
                    int a = 0;
                });
        int i = 0;


        path = "org/xumiao/test";
        enumer = TestVFS.class.getClassLoader().getResources(path);
        urls = Collections.list(enumer);

        urls.stream()
                .forEach(u -> {
                    //file:/home/xumiao/Documents/idea-workspace/src-learning/mybatis-3.5.2-src/mybatis-test/target/classes/org/xumiao/test
                    System.out.println("=====================================");
                    //-1
                    System.out.println("port = " + u.getPort());
                    ///home/xumiao/Documents/idea-workspace/src-learning/mybatis-3.5.2-src/mybatis-test/target/classes/org/xumiao/test
                    System.out.println("path = " + u.getPath());
                    ///home/xumiao/Documents/idea-workspace/src-learning/mybatis-3.5.2-src/mybatis-test/target/classes/org/xumiao/test
                    System.out.println("file = " + u.getFile());

                    String line;

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(u.openStream()))) {
                        if (null != (line = reader.readLine())) {
                            //>>>entity
                            System.out.println(">>>" + line);
                        }
                    } catch (Exception e) {}
                });

        try {
            TestVFS.class.getClassLoader().getResources("org/xumiao/test/vfs/a line");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
