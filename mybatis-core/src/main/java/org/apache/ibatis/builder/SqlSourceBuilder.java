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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 *
 * 解析sql语句中的参数信息，构建StaticSqlSource
 *
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * @param originalSql 包含#{}参数的sql语句
   * @param parameterType 运行时外部传递参数的类型
   * @param additionalParameters 运行时外部传递的参数
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);

    /**
     * 将参数替换成？的sql语句
     */
    String sql = parser.parse(originalSql);
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  /**
   * 解析出sql中的参数信息
   */
  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {
    /**
     * 参数描述信息
     */
    private List<ParameterMapping> parameterMappings = new ArrayList<>();
    /**
     * 运行时外部传递参数的类型
     */
    private Class<?> parameterType;
    /**
     * 运行时外部传递参数对应的MetaObject
     */
    private MetaObject metaParameters;

    /**
     * @param configuration
     * @param parameterType 运行时外部传递参数的类型
     * @param additionalParameters 运行时外部传递的参数
     */
    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    @Override
    /**
     * content是参数的名字
     */
    public String handleToken(String content) {
      /**
       * 将sql中参数的信息收集起来
       */
      parameterMappings.add(buildParameterMapping(content));
      /**
       * 将参数替换成？，这是jdbc的参数注入方式
       */
      return "?";
    }

    private ParameterMapping buildParameterMapping(String content) {
      /**
       * 参数信息解析结果（Map）
       */
      Map<String, String> propertiesMap = parseParameterMapping(content);
      String property = propertiesMap.get("property");
      /**
       * 推断属性的类型
       */
      Class<?> propertyType;
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        /**
         * getter的返回值类型
         */
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        /**
         * 运行时传递的参数是基本类型（有对应的类型处理器），那么激进地认为它就是参数类型
         */
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        /**
         * jdbcType指定的类型如果是游标，那么属性类型就是ResultSet
         */
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        /**
         * 不知道属性的类型
         */
        propertyType = Object.class;
      } else {
        /**
         * 根据参数类型，通过getter来确定属性的类型
         */
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }

      /**
       * 使用参数的信息实例化一个ParameterMapping对象
       */
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      /**
       * 先推断，后补充（用户明确指定）
       */
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          /**
           * sql语句中的参数暂时不支持表达式
           */
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        /**
         * 参数表达式解析结果（Map）
         */
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
