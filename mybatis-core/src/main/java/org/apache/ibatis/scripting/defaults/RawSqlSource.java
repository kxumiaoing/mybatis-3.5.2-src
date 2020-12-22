/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.defaults;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.session.Configuration;

import java.util.HashMap;

/**
 * Static SqlSource. It is faster than {@link DynamicSqlSource} because mappings are
 * calculated during startup.
 *
 * @since 3.2.0
 * @author Eduardo Macarron
 *
 * 静态sql脚本（不包含“${”和“}”）容器
 * 代理StaticSqlSource，StaticSqlSource在构造器中创建，因此是静态的
 */
public class RawSqlSource implements SqlSource {

  /**
   * sqlSource其实是StaticSqlSource
   * 此处持有SqlSource的引用是因为这个对象中的sql语句是静态的，并且已经解析完成，不需要重复解析，用的时候直接注入参数就可以
   */
  private final SqlSource sqlSource;

  public RawSqlSource(Configuration configuration, SqlNode rootSqlNode, Class<?> parameterType) {
    /**
     * getSql将sql脚本片段（SqlNode）拼接成完整的sql语句
     */
    this(configuration, getSql(configuration, rootSqlNode), parameterType);
  }

  /**
   * sql就是sql语句，语句中包含“#{”和“}”，需要进行变量替换
   */
  public RawSqlSource(Configuration configuration, String sql, Class<?> parameterType) {
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    /**
     * parameterType是参数类型，通过parameterType对象对sql语句进行动态注入参数
     */
    Class<?> clazz = parameterType == null ? Object.class : parameterType;
    /**
     * 构建（静态生成）StaticSqlSource：将sql语句中的参数替换成“?”，并且解析成ParameterMapping
     */
    sqlSource = sqlSourceParser.parse(sql, clazz, new HashMap<>());
  }

  /**
   * 将sql脚本片段（SqlNode）拼接成完整的sql脚本
   * 这个方法在构造方法中调用，正好印证了这个对象里面包含的是静态sql，
   * 即源sql语句中不包含“${”和“}”，是不会随着参数变化而变化的
   */
  private static String getSql(Configuration configuration, SqlNode rootSqlNode) {
    DynamicContext context = new DynamicContext(configuration, null);
    /**
     * rootSqlNode是MixedSqlNode，MixedSqlNode是SqlNode的集合
     * MixedSqlNode的apply方法会迭代依次调用子SqlNode（StaticTextNode）的apply方法
     * StaticTextNode的apply方法会将sql脚本片段添加到DynamicContext中
     * DynamicContext中使用StringJoiner将sql脚本片段拼接到一起
     */
    rootSqlNode.apply(context);
    return context.getSql();
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    return sqlSource.getBoundSql(parameterObject);
  }

}
