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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.*;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.io.InputStream;
import java.io.Reader;
import java.util.*;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 *
 * 解析Mapper对应的xml文件
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  private final MapperBuilderAssistant builderAssistant;
  private final Map<String, XNode> sqlFragments;
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  /**
   * 字节流
   */
  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    //每一个XMLMapperBuilder对应一个MapperBuilderAssistant
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    /**
     * 由Configuration传递进来，sqlFragments缓存了所有<sql>的XNode
     */
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 这个解析不算是正规流程，bindMapperForNamespace方法解析注解配置
   * 正规流程在MapperAnnotationBuilder中
   */
  public void parse() {
    /**
     * 判断之前是否加载
     */
    if (!configuration.isResourceLoaded(resource)) {
      /**
       * 正式解析mapper文件
       */
      configurationElement(parser.evalNode("/mapper"));
      /**
       * 已经解析的mapper文件地址加入缓存
       */
      configuration.addLoadedResource(resource);
        /**
         * 为命名空间（接口的全限定名字）创建MapperProxyFactory
         * 并且解析命名空间所对应的类中的注解
         */
      bindMapperForNamespace();
    }

    /**
     * 完成未完成的解析操作
     * （最后一个XMLMapperBuilder一定能够完成所有未完成的解析操作）
     */
    parsePendingResultMaps();
    parsePendingCacheRefs();
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

    /**
     * <mapper>标签下的直接子标签：
     *    1、<cache>标签
     *    2、<cache-ref>标签
     *    3、<resultMap>标签
     *    4、<sql>标签
     *    5、<select>标签
     *    6、<update>标签
     *    7、<delete>标签
     *    8、<insert>标签
     */
  private void configurationElement(XNode context) {
    try {
      /**
       * namespace必须有
       */
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      /**
       * cache-ref节点
       * 主要是检查参考的cache是否存在，如果不存在，就加入未完成列表
       */
      cacheRefElement(context.evalNode("cache-ref"));
      /**
       * cache节点
       */
      cacheElement(context.evalNode("cache"));
      //parameterMap废弃
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      /**
       * resultMap节点
       */
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      /**
       * sql节点（只是将XNode存储器来，并没有进行解析）
       */
      sqlElement(context.evalNodes("/mapper/sql"));
      /**
       * 重点：sql语句解析，使用XMLStatementBuilder来解析sql语句
       */
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  private void buildStatementFromContext(List<XNode> list) {
    //也考虑databaseId
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  /**
   * 解析<select>、<insert>、<update>和<delete>标签
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      /**
       * 每一个标签对应一个XMLStatementBuilder对象
       */
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * <cache-ref>标签
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      /**
       * namespace到namespace的映射
       */
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));


      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        /**
         * 寻找依赖的缓存，如果没有找到，就加入未完成列表，等解析完所有mapper文件后再找
         */
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        /**
         * 加入到未完成列表
         */
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * <cache>标签
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      /**
       * 缓存类型（被装饰类型）
       */
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      /**
       * 过期策略（装饰类型）
       */
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);

      /**
       * 一些属性
       */
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      Properties props = context.getChildrenAsProperties();
      /**
       * 实例化缓存
       */
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  /**
   *
   * 解析resultMap标签和类resultMap标签（association、collection和case标签）
   *
   * <resultMap>标签子标签：
   * 1、<constructor>子标签
   * 2、<id>子标签
   * 3、<result>子标签
   * 4、<association>子标签
   * 5、<collection>子标签
   * 6、<discriminator>子标签
   *
   * <association>、<colleciton>、<case>标签和<resultMap>标签内部子标签是一样的
   *
   * resultMapping与属性/数据列一一对应
   *
   * @param additionalResultMappings 已经解析的ResultMapping（resultMap标签下嵌套了association或collection标签）
   * @param enclosingType 父标签代表的实例类型
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) throws Exception {
    /**
     * 错误日志
     */
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    /**
     * 获取resultMap对应的实例类型
     */
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    Class<?> typeClass = resolveClass(type);
    /**
     * 如果实例类型为空，从继承父标签那里继承
     */
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<>();
    resultMappings.addAll(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();

    /**
     * 子标签
     */
    for (XNode resultChild : resultChildren) {
      /**
       * <constructor>子标签
       */
      if ("constructor".equals(resultChild.getName())) {
        /**
         * 为<arg>子标签和<idArg>子标签分别创建ResultMapping实例
         * 等同于<id>和<result>标签
         */
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        /**
         * <discriminator>子标签，实例化一个Discriminator实例
         */
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        /**
         * <id>、<result>、<association>和<collection>子标签
         */
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    /**
     * 类resultMap（比如association、colleciton和case标签）没有id属性
     */
    String id = resultMapNode.getStringAttribute("id",
            resultMapNode.getValueBasedIdentifier());
    String extend = resultMapNode.getStringAttribute("extends");
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      /**
       * 使用MapperBuilderAssistant来创建一个ResultMap
       */
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      /**
       * 加入未完成列表
       */
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * 如果没有指定实例类型，继承父标签的实例类型
   */
  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    /**
     * <association>标签，并且resultMap为空
     */
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      /**
       * 使用MetaClass通过属性名字获取setter的参数类型
       * */
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      /**
       * <case>标签，如果没有resultMap属性，那么最终实例的类型为enclosingType
       */
      return enclosingType;
    }
    return null;
  }

  /**
   * <constructor>标签
   *
   * 为<arg>子标签和<idArg>子标签生成对应的ResultMapping实例
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    /**
     * 构造器节点下只有idArg和arg节点
     */
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      /**
       * <arg>或<idArg>标签
       */
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * Discriminator对象中包含对应的ResultMaping，以及每个case值对应一个ResultMap的id
   *
   * case的形式：
   *    1、通过resultMap属性指向另外的一个resultMap
   *        （另外一个resultMap与当前resultMap有关系[extend属性]或者没有关系）
   *    2、内嵌<result>子标签
   *        （情况1两个有关系的resultMap的内嵌版本）
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<>();

    /**
     * <case>子标签
     */
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      /**
       * 每个<case>子标签都对应一个resultMap（包含完整的ResultMapping实例）
       */
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    /**
     * 创建Discriminator实例
     */
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  private void sqlElement(List<XNode> list) {
    /**
     * 根绝databaseId来解析sql标签
     */
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  /**
   * 处理<sql>标签
   *
   * 将<sql>标签对应的XNode缓存起来，以备后用
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      String databaseId = context.getStringAttribute("databaseId");
      //id
      String id = context.getStringAttribute("id");
      //添加上namespace的id
      id = builderAssistant.applyCurrentNamespace(id, false);
      /**
       * 将XNode节点缓存到sqlFragment中（Configuration对象中）
       */
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * @param id sql标签中语句的id
   * @param databaseId sql标签中实际的databaseId
   * @param requiredDatabaseId 配置文件中配置的databaseId
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    //如果存在，都需存在并且相等，如果不存在，都需要不存在
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    XNode context = this.sqlFragments.get(id);
    //如果当前sql没有databaseId，那么替换原来没有databaseId，但是不替换有databaseId的
    return context.getStringAttribute("databaseId") == null;
  }

  /**
   * @param context 对应的<id>、<result>、<arg>、<idArg>、<association>、<collection>标签等
   * @param resultType 实例类型
   * @param flags result的标示
   *
   * association的四种形式：
   *              1、直接内嵌<id>、<result>或者<constructor>子标签
   *              2、通过select属性指向另外一个<select>标签 ———— nested select for association
   *                 （column属性可以向select传递多列的值，比如 column="{prop1=col1,prop2=col2}"）
   *              3、通过resultMap属性指向另外一个<resultMap>标签 ———— nested results for association
   *                 （columnPrefix属性可以指定映射哪些列）
   *              4、通过resultSet属性指向多个数据集中的其中一个数据集，形式同1 ———— multiple resultsets for association
   *                 （select标签执行一个存储过程，返回多个数据集，求取关联数据集是通过column和foreignColumn来传递值）
   *
   * collection的三种形式：
   *              1、直接包裹<id>和<result>子标签
   *              2、通过select属性指向另外一个<select>标签 ———— nested select for collection
   *                 （column属性可以向select传递多列的值，比如 column="{prop1=col1,prop2=col2}"）
   *              3、通过resultMap属性指向另外一个<resultMap>标签 ———— nested results for collecition
   *                 （columnPrefix属性可以指定映射哪些列）
   *              4、通过resultSet属性指向多个数据集中的其中一个数据集，形式同1 ———— multiple resultsets for collection
   *                 （select标签执行一个存储过程，返回多个数据集，求取关联数据集是通过column和foreignColumn来传递值）
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    /**
     * 属性名字
     */
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");

    /**
     * 指向一个<select>标签
     */
    String nestedSelect = context.getStringAttribute("select");
    /**
     * 符合标签（<association>、<collection>或<case>），它们可能指向其他resultMap标签，也可能内嵌resultMap，也可能指向一个select
     *
     * 要么指向一个已有的resultMap，要么解析内嵌的resultMap
     *
     */
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    /**
     * 实例化一个ResultMapping对象
      */
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 调用resultMapElement()方法处理<association>标签、<collection>标签、<case>标签内嵌的resultMap
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      if (context.getStringAttribute("select") == null) {
        validateCollection(context, enclosingType);
        ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
        return resultMap.getId();
      }
    }
    return null;
  }

  /**
   * <collection>标签既没有resultMap属性，也没有javaType属性，并且没有对应的setter方法，必须报错，不能推断出实例的类型
   */
  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 给命名空间绑定MapperProxyFactory，并且解析命名空间对应的类
   */
  private void bindMapperForNamespace() {
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          configuration.addLoadedResource("namespace:" + namespace);
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
