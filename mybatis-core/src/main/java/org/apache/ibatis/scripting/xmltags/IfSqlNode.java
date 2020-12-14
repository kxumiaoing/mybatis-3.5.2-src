/**
 *    Copyright 2009-2017 the original author or authors.
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

/**
 * @author Clinton Begin
 *
 *
 * if节点对应脚本的容器
 *
 * 装饰TextSqlNode
 */
public class IfSqlNode implements SqlNode {
  private final ExpressionEvaluator evaluator;
  private final String test;
  private final SqlNode contents;

  public IfSqlNode(SqlNode contents, String test) {
    //测试条件表达式
    this.test = test;
    //实际的SqlNode
    this.contents = contents;
    //表达式解析器
    this.evaluator = new ExpressionEvaluator();
  }

  @Override
  /**
   * 使用Ognl求解表达式的值，这个值会影响sql脚本片段
   */
  public boolean apply(DynamicContext context) {
    /**
     * context.getBindings()是ContextMap，它包裹了实际入参参数
     */
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
      contents.apply(context);
      return true;
    }
    return false;
  }

}
