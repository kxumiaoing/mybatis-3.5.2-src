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

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.util.*;

/**
 * @author Clinton Begin
 * 协助解析Mapper配置文件，主要用来实例化Mappper中的对象
 *
 * 一个XMLMapperBuilder对象对应一个MapperBuilderAssistant对象
 *
 */
public class MapperBuilderAssistant extends BaseBuilder {
  /**
   * 命名空间
   */
  private String currentNamespace;
  /**
   * mapper文件的地址
   */
  private final String resource;
  /**
   * 缓存
   */
  private Cache currentCache;
  private boolean unresolvedCacheRef; // issue #676

  public MapperBuilderAssistant(Configuration configuration, String resource) {
    super(configuration);
    ErrorContext.instance().resource(resource);
    /**
     * 当前Mapper xml文件的地址（resource地址或者url地址）
     */
    this.resource = resource;
  }

  public String getCurrentNamespace() {
    return currentNamespace;
  }

  public void setCurrentNamespace(String currentNamespace) {
    if (currentNamespace == null) {
      throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
    }

    /**
     * 重复设置的话，不能设置不同的
     */
    if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
      throw new BuilderException("Wrong namespace. Expected '"
          + this.currentNamespace + "' but found '" + currentNamespace + "'.");
    }

    this.currentNamespace = currentNamespace;
  }

  /**
   * 在base上加上namespace前缀
   */
  public String applyCurrentNamespace(String base, boolean isReference) {
    if (base == null) {
      return null;
    }

    if (isReference) {
      /**
       * 支持引用其他namespace的id
       */
      // is it qualified with any namespace yet?
      if (base.contains(".")) {
        return base;
      }
    } else {
      /**
       * 已经包含了namespace，直接返回
       */
      // is it qualified with this namespace yet?
      if (base.startsWith(currentNamespace + ".")) {
        return base;
      }
      /**
       * 不支持引用的id如果包含点号就报错
       */
      if (base.contains(".")) {
        throw new BuilderException("Dots are not allowed in element names, please remove it from " + base);
      }
    }
    /**
     * 加上namespace
     */
    return currentNamespace + "." + base;
  }

  /**
   * 引用其他namespace的Cache
   *
   * namespace: 被参考的命名空间
   */
  public Cache useCacheRef(String namespace) {
    if (namespace == null) {
      throw new BuilderException("cache-ref element requires a namespace attribute.");
    }
    try {
      unresolvedCacheRef = true;
      /**
       * 被参考的缓存
       */
      Cache cache = configuration.getCache(namespace);
      if (cache == null) {
        throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.");
      }
      currentCache = cache;
      unresolvedCacheRef = false;
      return cache;
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
    }
  }

  /**
   * 创建Cache
   */
  public Cache useNewCache(Class<? extends Cache> typeClass,
      Class<? extends Cache> evictionClass,
      Long flushInterval,
      Integer size,
      boolean readWrite,
      boolean blocking,
      Properties props) {
    Cache cache = new CacheBuilder(currentNamespace)
        .implementation(valueOrDefault(typeClass, PerpetualCache.class))
        .addDecorator(valueOrDefault(evictionClass, LruCache.class))
        .clearInterval(flushInterval)
        .size(size)
        .readWrite(readWrite)
        .blocking(blocking)
        .properties(props)
        .build();
    configuration.addCache(cache);
    currentCache = cache;
    return cache;
  }

  /**
   * parameterMap废弃，不用管
   */
  public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
    id = applyCurrentNamespace(id, false);
    ParameterMap parameterMap = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings).build();
    configuration.addParameterMap(parameterMap);
    return parameterMap;
  }

  /**
   * parameterMap废弃，不用管
   */
  public ParameterMapping buildParameterMapping(
      Class<?> parameterType,
      String property,
      Class<?> javaType,
      JdbcType jdbcType,
      String resultMap,
      ParameterMode parameterMode,
      Class<? extends TypeHandler<?>> typeHandler,
      Integer numericScale) {
    resultMap = applyCurrentNamespace(resultMap, true);

    // Class parameterType = parameterMapBuilder.type();
    Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

    return new ParameterMapping.Builder(configuration, property, javaTypeClass)
        .jdbcType(jdbcType)
        .resultMapId(resultMap)
        .mode(parameterMode)
        .numericScale(numericScale)
        .typeHandler(typeHandlerInstance)
        .build();
  }

  /**
   * 创建一个ResultMap
   */
  public ResultMap addResultMap(
      String id,//resultMap对应的id
      Class<?> type,//resultMap对应的实例类型
      String extend,//从哪个resultMap继承（resultMap对应的id）
      Discriminator discriminator,//discriminator
      List<ResultMapping> resultMappings,//resultMap下的所有ResultMapping实例
      Boolean autoMapping) {//是否是自动映射
    /**
     * 加上命名空间信息
     */
    id = applyCurrentNamespace(id, false);
    extend = applyCurrentNamespace(extend, true);

    if (extend != null) {
      /**
       * 扩展的resultMap还没解析，先报异常，加入未完成列表
       */
      if (!configuration.hasResultMap(extend)) {
        throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
      }
      ResultMap resultMap = configuration.getResultMap(extend);
      List<ResultMapping> extendedResultMappings = new ArrayList<>(resultMap.getResultMappings());
      extendedResultMappings.removeAll(resultMappings);
      // Remove parent constructor if this resultMap declares a constructor.
      boolean declaresConstructor = false;
      for (ResultMapping resultMapping : resultMappings) {
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          declaresConstructor = true;
          break;
        }
      }
      /**
       * 删除父类定义的构造器属性
       */
      if (declaresConstructor) {
        extendedResultMappings.removeIf(resultMapping -> resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR));
      }
      resultMappings.addAll(extendedResultMappings);
    }
    /**
     * 创建一个ResultMap实例
     */
    ResultMap resultMap = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping)
        .discriminator(discriminator)
        .build();
    configuration.addResultMap(resultMap);
    return resultMap;
  }

  /**
   * 创建一个discriminator对应的ResultMapping
   */
  public Discriminator buildDiscriminator(
      Class<?> resultType,//discriminator字标签对应的实体类型或实体类型父类
      String column,//数据库列名称
      Class<?> javaType,//数据对应的类型
      JdbcType jdbcType,//数据库列对应的jdbc类型
      Class<? extends TypeHandler<?>> typeHandler,//typehandler属性指定的类型处理器
      Map<String, String> discriminatorMap) {//不同case对应的resultMap
    /**
     * 创建discriminator对应的ResultMapping实例
     */
    ResultMapping resultMapping = buildResultMapping(
        resultType,
        null,
        column,
        javaType,
        jdbcType,
        null,
        null,
        null,
        null,
        typeHandler,
        new ArrayList<>(),
        null,
        null,
        false);
    /**
     * resultMap对应的id加上命令空间信息
     */
    Map<String, String> namespaceDiscriminatorMap = new HashMap<>();
    for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
      String resultMap = e.getValue();

      resultMap = applyCurrentNamespace(resultMap, true);
      namespaceDiscriminatorMap.put(e.getKey(), resultMap);
    }
    /**
     * 创建一个Discriminator实例
     */
    return new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap).build();
  }

  /**
   *  根据sql语句（SqlSource）常见对应的MapperedStatement
   */
  public MappedStatement addMappedStatement(
      String id,//sql语句对应的id
      SqlSource sqlSource,//sql语句对应的SqlSource
      StatementType statementType,//sql语句执行类型
      SqlCommandType sqlCommandType,//sql语句类型
      Integer fetchSize,//select类型语句属性
      Integer timeout,//sql语句执行超时时间
      String parameterMap,//废弃
      Class<?> parameterType,//sql语句执行时的入参类型
      String resultMap,//select语句对应的执行结果集名称
      Class<?> resultType,//select语句返回结果的类型（简单）
      ResultSetType resultSetType,//游标结果集类型
      boolean flushCache,//是否刷新缓存
      boolean useCache,//是否使用缓存
      boolean resultOrdered,//结果集是否排序
      KeyGenerator keyGenerator,//key生成器
      String keyProperty,//key对应的属性
      String keyColumn,//key对应的列名
      String databaseId,//数据库id
      LanguageDriver lang,//语言驱动
      String resultSets) {//多个结果集的名字列表

      /**
       * 需要引用cache对象
       */
    if (unresolvedCacheRef) {
      throw new IncompleteElementException("Cache-ref not yet resolved");
    }

    /**
     * id加上namespace
     */
    id = applyCurrentNamespace(id, false);
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

    MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType)
        .resource(resource)
        .fetchSize(fetchSize)
        .timeout(timeout)
        .statementType(statementType)
        .keyGenerator(keyGenerator)
        .keyProperty(keyProperty)
        .keyColumn(keyColumn)
        .databaseId(databaseId)
        .lang(lang)
        .resultOrdered(resultOrdered)
        .resultSets(resultSets)
        .resultMaps(getStatementResultMaps(resultMap, resultType, id))//获取对应的ResultMap对象
        .resultSetType(resultSetType)
        .flushCacheRequired(valueOrDefault(flushCache, !isSelect))
        .useCache(valueOrDefault(useCache, isSelect))
        .cache(currentCache);

    ParameterMap statementParameterMap = getStatementParameterMap(parameterMap, parameterType, id);
    if (statementParameterMap != null) {
      statementBuilder.parameterMap(statementParameterMap);
    }

    MappedStatement statement = statementBuilder.build();
    /**
     * 缓存到Configuration对象中
     */
    configuration.addMappedStatement(statement);
    return statement;
  }

  private <T> T valueOrDefault(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }

  /**
   * 废弃
   */
  private ParameterMap getStatementParameterMap(
      String parameterMapName,
      Class<?> parameterTypeClass,
      String statementId) {
    parameterMapName = applyCurrentNamespace(parameterMapName, true);
    ParameterMap parameterMap = null;
    if (parameterMapName != null) {
      try {
        parameterMap = configuration.getParameterMap(parameterMapName);
      } catch (IllegalArgumentException e) {
        throw new IncompleteElementException("Could not find parameter map " + parameterMapName, e);
      }
    } else if (parameterTypeClass != null) {
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      parameterMap = new ParameterMap.Builder(
          configuration,
          statementId + "-Inline",
          parameterTypeClass,
          parameterMappings).build();
    }
    return parameterMap;
  }

  /**
   * 通过resultMap和resultType属性获取对应的ResultMap对象
   */
  private List<ResultMap> getStatementResultMaps(
      String resultMap,//resultMap的id
      Class<?> resultType,//
      String statementId) {//<insert>、<update>、<delete>、<select>标签对应的id
    resultMap = applyCurrentNamespace(resultMap, true);

    List<ResultMap> resultMaps = new ArrayList<>();
    if (resultMap != null) {
      /**
       * 应该是没有“，”的
       */
      String[] resultMapNames = resultMap.split(",");
      for (String resultMapName : resultMapNames) {
        try {
          resultMaps.add(configuration.getResultMap(resultMapName.trim()));
        } catch (IllegalArgumentException e) {
          throw new IncompleteElementException("Could not find result map '" + resultMapName + "' referenced from '" + statementId + "'", e);
        }
      }
    } else if (resultType != null) {
      ResultMap inlineResultMap = new ResultMap.Builder(
          configuration,
          statementId + "-Inline",
          resultType,
          new ArrayList<>(),
          null).build();
      resultMaps.add(inlineResultMap);
    }
    return resultMaps;
  }

  /**
   * 创建一个ResultMapping:属性到列的映射
   */
  public ResultMapping buildResultMapping(
      Class<?> resultType,//属性宿主实例类型
      String property,//属性名称
      String column,//列名称
      Class<?> javaType,//属性对应的类型
      JdbcType jdbcType,//列对应的jdbc类型
      String nestedSelect,//集合属性对应的<select>标签
      String nestedResultMap,//集合属性组件类型对应的ResultMap
      String notNullColumn,//属性不能为空标示
      String columnPrefix,//列前缀
      Class<? extends TypeHandler<?>> typeHandler,//属性值的类型处理器
      List<ResultFlag> flags,//对应的标签类型（主要是id属性或者构造器中的id属性）
      String resultSet,//复合属性对应的结果集名字（存储过程可以返回多个结果集）
      String foreignColumn,//外键(求多个数据集时用来传递参数的)
      boolean lazy) {
    /**
     * 属性类型
     */
    Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
    /**
     * 根据TypeHandler类型获取对应的实例
     */
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
    List<ResultMapping> composites;

    /**
     * foreignColumn是查询多个数据集时用来传递中间参数的
     */
    if ((nestedSelect == null || nestedSelect.isEmpty()) && (foreignColumn == null || foreignColumn.isEmpty())) {
      composites = Collections.emptyList();
    } else {
      /**
       * column属性给指定的<select>标签传递了多个属性
       */
      composites = parseCompositeColumnName(column);
    }
    /**
     * 实例化一个ResultMapping对象
     */
    return new ResultMapping.Builder(configuration, property, column, javaTypeClass)
        .jdbcType(jdbcType)
        .nestedQueryId(applyCurrentNamespace(nestedSelect, true))
        .nestedResultMapId(applyCurrentNamespace(nestedResultMap, true))
        .resultSet(resultSet)
        .typeHandler(typeHandlerInstance)
        .flags(flags == null ? new ArrayList<>() : flags)
        .composites(composites)
        .notNullColumns(parseMultipleColumnNames(notNullColumn))
        .columnPrefix(columnPrefix)
        .foreignColumn(foreignColumn)
        .lazy(lazy)
        .build();
  }

  /**
   * 将notNullColumn="{col1,col2}"类型的notNullColumn解析成一个set集合
   */
  private Set<String> parseMultipleColumnNames(String columnName) {
    Set<String> columns = new HashSet<>();
    if (columnName != null) {
      if (columnName.indexOf(',') > -1) {
        StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
        while (parser.hasMoreTokens()) {
          String column = parser.nextToken();
          columns.add(column);
        }
      } else {
        columns.add(columnName);
      }
    }
    return columns;
  }

  /**
   * 将column="{prop1=col1,prop2=col2}"类型的column解析成多个ResultMapping对象
   */
  private List<ResultMapping> parseCompositeColumnName(String columnName) {
    List<ResultMapping> composites = new ArrayList<>();
    if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
      StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
      while (parser.hasMoreTokens()) {
        String property = parser.nextToken();
        String column = parser.nextToken();
        ResultMapping complexResultMapping = new ResultMapping.Builder(
            configuration, property, column, configuration.getTypeHandlerRegistry().getUnknownTypeHandler()).build();
        composites.add(complexResultMapping);
      }
    }
    return composites;
  }

  /**
   * 推断属性的类型
   */
  private Class<?> resolveResultJavaType(Class<?> resultType, String property, Class<?> javaType) {
    /**
     * 通过MetaClass类推断
     */
    if (javaType == null && property != null) {
      try {
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        javaType = metaResultType.getSetterType(property);
      } catch (Exception e) {
        //ignore, following null check statement will deal with the situation
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

  private Class<?> resolveParameterJavaType(Class<?> resultType, String property, Class<?> javaType, JdbcType jdbcType) {
    if (javaType == null) {
      if (JdbcType.CURSOR.equals(jdbcType)) {
        javaType = java.sql.ResultSet.class;
      } else if (Map.class.isAssignableFrom(resultType)) {
        javaType = Object.class;
      } else {
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        javaType = metaResultType.getGetterType(property);
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

  /** Backward compatibility signature. */
  public ResultMapping buildResultMapping(Class<?> resultType, String property, String column, Class<?> javaType,
      JdbcType jdbcType, String nestedSelect, String nestedResultMap, String notNullColumn, String columnPrefix,
      Class<? extends TypeHandler<?>> typeHandler, List<ResultFlag> flags) {
    return buildResultMapping(
      resultType, property, column, javaType, jdbcType, nestedSelect,
      nestedResultMap, notNullColumn, columnPrefix, typeHandler, flags, null, null, configuration.isLazyLoadingEnabled());
  }

  /**
   * @deprecated Use {@link Configuration#getLanguageDriver(Class)}
   */
  @Deprecated
  public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
    return configuration.getLanguageDriver(langClass);
  }

  /** Backward compatibility signature. */
  public MappedStatement addMappedStatement(String id, SqlSource sqlSource, StatementType statementType,
      SqlCommandType sqlCommandType, Integer fetchSize, Integer timeout, String parameterMap, Class<?> parameterType,
      String resultMap, Class<?> resultType, ResultSetType resultSetType, boolean flushCache, boolean useCache,
      boolean resultOrdered, KeyGenerator keyGenerator, String keyProperty, String keyColumn, String databaseId,
      LanguageDriver lang) {
    return addMappedStatement(
      id, sqlSource, statementType, sqlCommandType, fetchSize, timeout,
      parameterMap, parameterType, resultMap, resultType, resultSetType,
      flushCache, useCache, resultOrdered, keyGenerator, keyProperty,
      keyColumn, databaseId, lang, null);
  }

}
