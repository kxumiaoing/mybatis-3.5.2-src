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

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Frank D. Martinez [mnesarco]
 *
 * 解析<include>标签
 * 最终使用文本节点替换<include>节点
 */
public class XMLIncludeTransformer {

  private final Configuration configuration;
  private final MapperBuilderAssistant builderAssistant;

  public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
    this.configuration = configuration;
    this.builderAssistant = builderAssistant;
  }

  public void applyIncludes(Node source) {
    /**
     * 用来替换变量的属性
     */
    Properties variablesContext = new Properties();
    Properties configurationVariables = configuration.getVariables();
    Optional.ofNullable(configurationVariables).ifPresent(variablesContext::putAll);
    /**
     * 开始解析<include>标签
     */
    applyIncludes(source, variablesContext, false);
  }

  /**
   * Recursively apply includes through all SQL fragments.
   * @param source Include node in DOM tree
   * @param variablesContext Current context for static variables with values
   * @param included source是否属于include的内容
   *
   * 使用<include>标签指向的<sql>标签内容替换<include>标签，并且对所有标签中的变量进行值替换
   *
   */
  private void applyIncludes(Node source, final Properties variablesContext, boolean included) {
    /**
     * <include>节点
     */
    if (source.getNodeName().equals("include")) {
      /**
       * 从configuration中查找sqlFragment，即<sql>对应的节点，并且克隆的一份
        */
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"), variablesContext);
      /**
       * 解析<property>标签，合并属性
       */
      Properties toIncludeContext = getVariablesContext(source, variablesContext);
      /**
       * 对<sql>节点进行如下操作：
       *    1、对<sql>节点中所有属性节点进行属性替换
       *    2、对<sql>节点中所有文本节点进行属性替换
       *    3、继续挖掘<sql>节点中的<include>节点
       */
      applyIncludes(toInclude, toIncludeContext, true);

      /**
       * 更新<sql>节点的拥有者文档
       */
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      /**
       * 用<sql>节点直接替换<include>节点
       */
      source.getParentNode().replaceChild(toInclude, source);
      /**
       * 将<sql>节点的所有子节点都插入到<sql>节点的前面
       */
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      /**
       * 删除<sql>节点
       */
      toInclude.getParentNode().removeChild(toInclude);
    } else if (source.getNodeType() == Node.ELEMENT_NODE) {
      /**
       * 只有当是<sql>节点时，included才为true
       * 对节点中的属性进行变量值替换
       */
      if (included && !variablesContext.isEmpty()) {
        // replace variables in attribute values
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
          Node attr = attributes.item(i);
          attr.setNodeValue(PropertyParser.parse(attr.getNodeValue(), variablesContext));
        }
      }
      /**
       * 查找子节点，递归解析<include>子节点
       */
      NodeList children = source.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        applyIncludes(children.item(i), variablesContext, included);
      }
    } else if (included && source.getNodeType() == Node.TEXT_NODE
        && !variablesContext.isEmpty()) {
      // replace variables in text node
      /**
       * 只有当是<sql>节点时，included才为true
       * 对文本节点进行变量值替换，比如sql中的alias
       */
      source.setNodeValue(PropertyParser.parse(source.getNodeValue(), variablesContext));
    }
  }

  /**
   * 从configuration缓存（sqlFragments）中查找相同id的<sql>标签节点，并且需要复制一份（不能修改原始内容）
   */
  private Node findSqlFragment(String refid, Properties variables) {
    /**
     * refid也可能包含属性，需要进行属性替换
     */
    refid = PropertyParser.parse(refid, variables);
    refid = builderAssistant.applyCurrentNamespace(refid, true);
    try {
      //查找
      XNode nodeToInclude = configuration.getSqlFragments().get(refid);
      /**
       * cloneNode复制一个新的节点，需要属性替换
       */
      return nodeToInclude.getNode().cloneNode(true);
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
    }
  }

  private String getStringAttribute(Node node, String name) {
    /**
     * Node类型节点查找属性
     */
    return node.getAttributes().getNamedItem(name).getNodeValue();
  }

  /**
   * Read placeholders and their values from include node definition.
   * @param node Include node instance
   * @param inheritedVariablesContext Current context used for replace variables in new variables values
   * @return variables context from include instance (no inherited values)
   */
  private Properties getVariablesContext(Node node, Properties inheritedVariablesContext) {
    Map<String, String> declaredProperties = null;
    NodeList children = node.getChildNodes();
    /**
     * <property>节点设置的属性值
     */
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        /**
         * 获取属性值
         */
        String name = getStringAttribute(n, "name");
        // Replace variables inside
        String value = PropertyParser.parse(getStringAttribute(n, "value"), inheritedVariablesContext);
        if (declaredProperties == null) {
          declaredProperties = new HashMap<>();
        }
        /**
         * 不能重复定义属性
         */
        if (declaredProperties.put(name, value) != null) {
          throw new BuilderException("Variable " + name + " defined twice in the same include definition");
        }
      }
    }
    /**
     * 合并属性
     */
    if (declaredProperties == null) {
      return inheritedVariablesContext;
    } else {
      /**
       * 加上Configuration收集到的所有属性（定义位置越近，优先级越高）
       */
      Properties newProperties = new Properties();
      newProperties.putAll(inheritedVariablesContext);
      newProperties.putAll(declaredProperties);
      return newProperties;
    }
  }
}
