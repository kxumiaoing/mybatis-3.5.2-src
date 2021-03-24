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
 * 在编译时解析完的sql语句
 *
 */
public class RawSqlSource implements SqlSource {

  /**
   * sqlSource其实是StaticSqlSource
   * 此处持有SqlSource的引用是因为这个对象中的sql语句是静态的，并且已经解析完成，不需要重复解析，用的时候直接注入参数就可以
   */
  private final SqlSource sqlSource;

  public RawSqlSource(Configuration configuration, SqlNode rootSqlNode, Class<?> parameterType) {
    this(configuration, getSql(configuration, rootSqlNode), parameterType);
  }

  /**
   * @param sql 包含#{}参数的sql语句（需要将参数解析出来）
   * @param parameterType 运行时外部传递参数的类型
   */
  public RawSqlSource(Configuration configuration, String sql, Class<?> parameterType) {
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> clazz = parameterType == null ? Object.class : parameterType;
    /**
     * 编译时解析sql语句中的参数信息
     */
    sqlSource = sqlSourceParser.parse(sql, clazz, new HashMap<>());
  }

  private static String getSql(Configuration configuration, SqlNode rootSqlNode) {
    DynamicContext context = new DynamicContext(configuration, null);
    /**
     * DynamicContext在此只是简单地将多个sql片段拼接成完整的sql语句（语句中带有#{}参数的）
     */
    rootSqlNode.apply(context);
    return context.getSql();
  }

  /**
   * @param parameterObject 运行时外部传递的参数
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    return sqlSource.getBoundSql(parameterObject);
  }

}
