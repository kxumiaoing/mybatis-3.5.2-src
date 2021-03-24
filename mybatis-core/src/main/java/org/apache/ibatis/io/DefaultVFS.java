/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.io;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * A default implementation of {@link VFS} that works for most application servers.
 *
 * @author Ben Gunter
 */
public class DefaultVFS extends VFS {
    private static final Log log = LogFactory.getLog(DefaultVFS.class);

    /**
     * The magic header that indicates a JAR (ZIP) file.
     */
    private static final byte[] JAR_MAGIC = {'P', 'K', 3, 4};

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    /**
     * 1、jar文件
     * 2、不是jar文件
     *      2.1、按照jar的方式去读取目录
     *      2.2、按照文件的方式去读目录
     *      2.3、正常读取目录（兜底方案）
     *      2.4、报错
     */
    public List<String> list(URL url, String path) throws IOException {
        //url -> jar:file：/xxx/yyy/zzz.jar!/aaa/bbb/ccc
        InputStream is = null;
        try {
            List<String> resources = new ArrayList<>();

            // First, try to find the URL of a JAR file containing the requested resource. If a JAR
            // file is found, then we'll list child resources by reading the JAR.
            /**
             * 从URL中查找jar的URL
             */
            URL jarUrl = findJarForResource(url);
            if (jarUrl != null) {
                /**
                 * 1、是一个jar文件
                 */
                is = jarUrl.openStream();
                if (log.isDebugEnabled()) {
                    log.debug("Listing " + url);
                }
                /**
                 * 通过JarInputStream来遍历获取
                 */
                resources = listResources(new JarInputStream(is), path);
            } else {
                List<String> children = new ArrayList<>();
                try {
                    /**
                     * 2、是一个假的jar文件（不是jar但是需要按照jar的方式读取）
                     */
                    if (isJar(url)) {
                        // Some versions of JBoss VFS might give a JAR stream even if the resource
                        // referenced by the URL isn't actually a JAR
                        /**
                         * JBoss VFS的一些实现将某些不是jar的文件当作jar来处理
                         * 此时需要按照jar的形式读取文件
                         */
                        is = url.openStream();
                        try (JarInputStream jarInput = new JarInputStream(is)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Listing " + url);
                            }
                            for (JarEntry entry; (entry = jarInput.getNextJarEntry()) != null; ) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Jar entry: " + entry.getName());
                                }
                                children.add(entry.getName());
                            }
                        }
                    } else {
                        /**
                         * 3、将目录当成文件来读取（此步很大概率出现错误，跳到异常处理逻辑：正常读取目录）
                         */
                        /*
                         * Some servlet containers allow reading from directory resources like a
                         * text file, listing the child resources one per line. However, there is no
                         * way to differentiate between directory and file resources just by reading
                         * them. To work around that, as each line is read, try to look it up via
                         * the class loader as a child of the current resource. If any line fails
                         * then we assume the current resource is not a directory.
                         */
                        /**
                         * 一些文件系统允许像读取文本（每一行）一样读取目录，这样没办法区分是文件还是目录
                         * 需要通过类加载器尝试着去加载“每一行”来甄别真正的目录
                         */
                        is = url.openStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                        List<String> lines = new ArrayList<>();
                        for (String line; (line = reader.readLine()) != null; ) {
                            if (log.isDebugEnabled()) {
                                log.debug("Reader entry: " + line);
                            }
                            lines.add(line);
                            /**
                             * 判断对应的条目是否存在（只要有一个不存在，说明这个文件是真的文件，而不是目录）
                             */
                            if (getResources(path + "/" + line).isEmpty()) {
                                lines.clear();
                                break;
                            }
                        }

                        if (!lines.isEmpty()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Listing " + url);
                            }
                            children.addAll(lines);
                        }
                    }
                } catch (FileNotFoundException e) {
                    /*
                     * For file URLs the openStream() call might fail, depending on the servlet
                     * container, because directories can't be opened for reading. If that happens,
                     * then list the directory directly instead.
                     */
                    /**
                     * 4、正常读取目录
                     */
                    if ("file".equals(url.getProtocol())) {
                        File file = new File(url.getFile());
                        if (log.isDebugEnabled()) {
                            log.debug("Listing directory " + file.getAbsolutePath());
                        }
                        if (file.isDirectory()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Listing " + url);
                            }
                            children = Arrays.asList(file.list());
                        }
                    } else {
                        // No idea where the exception came from so rethrow it
                        throw e;
                    }
                }

                // The URL prefix to use when recursively listing child resources
                String prefix = url.toExternalForm();
                if (!prefix.endsWith("/")) {
                    prefix = prefix + "/";
                }

                // Iterate over immediate children, adding files and recursing into directories
                for (String child : children) {
                    String resourcePath = path + "/" + child;
                    resources.add(resourcePath);
                    URL childUrl = new URL(prefix + child);
                    resources.addAll(list(childUrl, resourcePath));
                }
            }

            return resources;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * List the names of the entries in the given {@link JarInputStream} that begin with the
     * specified {@code path}. Entries will match with or without a leading slash.
     *
     * @param jar The JAR input stream
     * @param path The leading path to match
     * @return The names of all the matching entries
     * @throws IOException If I/O errors occur
     */
    /**
     * 从jar中读取所有文件的路径
     */
    protected List<String> listResources(JarInputStream jar, String path) throws IOException {
        // Include the leading and trailing slash when matching names
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }

        // Iterate over the entries and collect those that begin with the requested path
        List<String> resources = new ArrayList<>();
        //从jar中读取文件
        for (JarEntry entry; (entry = jar.getNextJarEntry()) != null; ) {
            //不是目录的都列举出来
            if (!entry.isDirectory()) {
                // Add leading slash if it's missing
                StringBuilder name = new StringBuilder(entry.getName());
                if (name.charAt(0) != '/') {
                    name.insert(0, '/');
                }

                // Check file name
                if (name.indexOf(path) == 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found resource: " + name);
                    }
                    // Trim leading slash
                    resources.add(name.substring(1));
                }
            }
        }
        return resources;
    }

    /**
     * Attempts to deconstruct the given URL to find a JAR file containing the resource referenced
     * by the URL. That is, assuming the URL references a JAR entry, this method will return a URL
     * that references the JAR file containing the entry. If the JAR cannot be located, then this
     * method returns null.
     *
     * @param url The URL of the JAR entry.
     * @return The URL of the JAR file, if one is found. Null if not.
     * @throws MalformedURLException
     *
     * 1、URL地址中是否包含.jar
     *      1.1、使用魔数检验
     *      1.2、文件是否存在
     */
    protected URL findJarForResource(URL url) throws MalformedURLException {
        //url -> jar:file:/xxx/yyy/zzz.jar!/aaa/bbb/ccc
        if (log.isDebugEnabled()) {
            log.debug("Find JAR URL: " + url);
        }

        // If the file part of the URL is itself a URL, then that URL probably points to the JAR
        /**
         * URL实例可以多层包装，此处获取最底层的URL
         * jar:file:/xxx/yyy/zzz.jar!/aaa/bbb/ccc  变成  file:/xxx/yyy/zzz.jar!/aaa/bbb/ccc
         */
        try {
            for (; ; ) {
                url = new URL(url.getFile());
                if (log.isDebugEnabled()) {
                    log.debug("Inner URL: " + url);
                }
            }
        } catch (MalformedURLException e) {
            // This will happen at some point and serves as a break in the loop
        }

        // Look for the .jar extension and chop off everything after that
        StringBuilder jarUrl = new StringBuilder(url.toExternalForm());
        /**
         * 1、判断文件路径中是否包含“.jar”
         */
        int index = jarUrl.lastIndexOf(".jar");
        if (index >= 0) {
            jarUrl.setLength(index + 4);
            if (log.isDebugEnabled()) {
                log.debug("Extracted JAR URL: " + jarUrl);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Not a JAR: " + jarUrl);
            }
            return null;
        }

        /**
         * jarUrl = file:/xxx/yyy/zzz.jar
         */
        // Try to open and test it
        try {
            //jar文件对应的URL
            URL testUrl = new URL(jarUrl.toString());
            /**
             * 1.1、通过读取jar文件的魔数来进一步确认是否是真的jar
             */

            if (isJar(testUrl)) {
                return testUrl;
            } else {
                // WebLogic fix: check if the URL's file exists in the filesystem.
                /**
                 * 1.2、文件存在即可（WebLogic独有）
                 */
                if (log.isDebugEnabled()) {
                    log.debug("Not a JAR: " + jarUrl);
                }
                /**
                 * 去除file:前缀
                 */
                jarUrl.replace(0, jarUrl.length(), testUrl.getFile());
                File file = new File(jarUrl.toString());

                // File name might be URL-encoded
                if (!file.exists()) {
                    try {
                        file = new File(URLEncoder.encode(jarUrl.toString(), "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("Unsupported encoding?  UTF-8?  That's unpossible.");
                    }
                }

                if (file.exists()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Trying real file: " + file.getAbsolutePath());
                    }
                    testUrl = file.toURI().toURL();
                    if (isJar(testUrl)) {
                        return testUrl;
                    }
                }
            }
        } catch (MalformedURLException e) {
            log.warn("Invalid JAR URL: " + jarUrl);
        }

        if (log.isDebugEnabled()) {
            log.debug("Not a JAR: " + jarUrl);
        }
        return null;
    }

    /**
     * Converts a Java package name to a path that can be looked up with a call to
     * {@link ClassLoader#getResources(String)}.
     *
     * @param packageName The Java package name to convert to a path
     */
    protected String getPackagePath(String packageName) {
        return packageName == null ? null : packageName.replace('.', '/');
    }

    /**
     * Returns true if the resource located at the given URL is a JAR file.
     *
     * @param url The URL of the resource to test.
     */
    protected boolean isJar(URL url) {
        return isJar(url, new byte[JAR_MAGIC.length]);
    }

    /**
     * Returns true if the resource located at the given URL is a JAR file.
     *
     * @param url The URL of the resource to test.
     * @param buffer A buffer into which the first few bytes of the resource are read. The buffer
     *            must be at least the size of {@link #JAR_MAGIC}. (The same buffer may be reused
     *            for multiple calls as an optimization.)
     */
    /**
     * 通过检查文件的魔数来判断文件是否是jar文件
     */
    protected boolean isJar(URL url, byte[] buffer) {
        InputStream is = null;
        try {
            is = url.openStream();
            is.read(buffer, 0, JAR_MAGIC.length);
            if (Arrays.equals(buffer, JAR_MAGIC)) {
                if (log.isDebugEnabled()) {
                    log.debug("Found JAR: " + url);
                }
                return true;
            }
        } catch (Exception e) {
            // Failure to read the stream means this is not a JAR
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        return false;
    }
}
