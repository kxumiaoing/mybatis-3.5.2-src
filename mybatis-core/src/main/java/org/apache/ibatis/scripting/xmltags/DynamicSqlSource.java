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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * 动态sql语句（包含“${”和“}”包裹的变量）容器
 * 最终的sql需要根据运行时传递的参数，解析根SqlNode来确定（运行时生成StaticSqlNode）
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    /**
     * 动态sql的原因所在：sql是根据parameterObject动态变化的
     */
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    /**
     * SqlNode依次执行apply方法，解析sql片段中的动态部分（“${”和“}”包裹的内容），生成静态sql，并且添加到DynamicContex中
     */
    rootSqlNode.apply(context);
    /**
     * 分割线以上部分是拼接sql语句
     * ==========================================================
     * 分割线以下部分是构建SqlSource
     */
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    /**
     * parameterType根据实际参数来动态获取类型
     */
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    /**
     * 构建（动态生成）StaticSqlSource：将sql语句中的参数替换成“?”，并且解析成ParameterMapping
     */
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    /**
     * =================================================
     * 分割线一下创建BoundSql
     */
    /**
     * 构建（动态生成）BoundSql
     */
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    /**
     * 将附加参数放入BoundSql中
     */
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
