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

import java.util.HashMap;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.session.Configuration;

/**
 * Static SqlSource. It is faster than {@link DynamicSqlSource} because mappings are
 * calculated during startup.
 *
 * @since 3.2.0
 * @author Eduardo Macarron
 *
 * 静态sql脚本容器
 * 尽管不包含“${”和“}”，但是可能包含“#{”和“}”
 */
public class RawSqlSource implements SqlSource {

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
     * HashMap里面放的是参数，但是这里HashMap是空的，不是真的要进行参数注入
     */
    sqlSource = sqlSourceParser.parse(sql, clazz, new HashMap<>());
  }

  /**
   * 将sql脚本片段（SqlNode）拼接成完整的sql脚本
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
