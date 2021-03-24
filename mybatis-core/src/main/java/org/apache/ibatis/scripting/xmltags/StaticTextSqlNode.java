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
 * 静态的sql脚本片段容器
 *
 */
public class StaticTextSqlNode implements SqlNode {
  /**
   * 静态的sql脚本片段
   */
  private final String text;

  public StaticTextSqlNode(String text) {
    this.text = text;
  }

  @Override
  public boolean apply(DynamicContext context) {
    /**
     * 将sql脚本片段拼接到DynamicContext中
     * DynamicContext最终使用StringJoiner将所有的sql脚本片段拼接成完成的sql语句
     */
    context.appendSql(text);
    return true;
  }

}