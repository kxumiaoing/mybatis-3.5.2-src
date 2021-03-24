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

import java.util.HashMap;

/**
 * Inline parameter expression parser. Supported grammar (simplified):
 *
 * <pre>
 * inline-parameter = (propertyName | expression) oldJdbcType attributes
 * propertyName = /expression language's property navigation path/
 * expression = '(' /expression language's expression/ ')'
 * oldJdbcType = ':' /any valid jdbc type/
 * attributes = (',' attribute)*
 * attribute = name '=' value
 * </pre>
 *
 * @author Frank D. Martinez [mnesarco]
 *
 * sql脚本中参数表达式解析结果，存放在Map中
 */
public class ParameterExpression extends HashMap<String, String> {

  private static final long serialVersionUID = -2417552199605158680L;

  public ParameterExpression(String expression) {
    parse(expression);
  }

  private void parse(String expression) {
    /**
     * 跳过不显示字符
     */
    int p = skipWS(expression, 0);
    if (expression.charAt(p) == '(') {
      /**
       * 参数的值是一个表达式的值
       *
       * 表达式必须使用()包裹起来
       *
       */
      expression(expression, p + 1);
    } else {
      /**
       * 参数的值是对象属性的值
       */
      property(expression, p);
    }
  }

  /**
   * @param expression 表达式字符串
   * @param left 开始解析的位置
   */
  private void expression(String expression, int left) {
    int match = 1;
    int right = left + 1;
    /**
     * 匹配最外层的“(”和“)”
     */
    while (match > 0) {
      if (expression.charAt(right) == ')') {
        match--;
      } else if (expression.charAt(right) == '(') {
        match++;
      }
      right++;
    }
    put("expression", expression.substring(left, right - 1));
    jdbcTypeOpt(expression, right);
  }

  private void property(String expression, int left) {
    if (left < expression.length()) {
      /**
       * 第一个“,”或者末尾作为分隔符，分隔符之前的字符串就是属性名字
       */
      int right = skipUntil(expression, left, ",:");

      /**
       * key为property，value为属性名字
       */
      put("property", trimmedStr(expression, left, right));
      /**
       * 可能存在的jdbcType
       */
      jdbcTypeOpt(expression, right);
    }
  }

  /**
   * 跳过不显示字符
   */
  private int skipWS(String expression, int p) {
    for (int i = p; i < expression.length(); i++) {
      /**
       * 0x20是空格的16进制
       */
      if (expression.charAt(i) > 0x20) {
        return i;
      }
    }
    return expression.length();
  }

  private int skipUntil(String expression, int p, final String endChars) {
    for (int i = p; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (endChars.indexOf(c) > -1) {
        return i;
      }
    }
    return expression.length();
  }

  private void jdbcTypeOpt(String expression, int p) {
    p = skipWS(expression, p);
    if (p < expression.length()) {
      if (expression.charAt(p) == ':') {
        /**
         * 重点：属性名和明确的jdbcType之间使用“:”分割
         */
        jdbcType(expression, p + 1);
      } else if (expression.charAt(p) == ',') {
        /**
         * 多个以“,”分割的key=value中可能存在jdbcType
         */
        option(expression, p + 1);
      } else {
        throw new BuilderException("Parsing error in {" + expression + "} in position " + p);
      }
    }
  }

  /**
   * 使用“:”明确指定jdbcType，jdbcType后面是多个以“,”为分割的key=value
   */
  private void jdbcType(String expression, int p) {
    int left = skipWS(expression, p);
    int right = skipUntil(expression, left, ",");
    if (right > left) {
      put("jdbcType", trimmedStr(expression, left, right));
    } else {
      throw new BuilderException("Parsing error in {" + expression + "} in position " + p);
    }
    option(expression, right + 1);
  }

  /**
   * 提取以“,”为分割的key=value
   */
  private void option(String expression, int p) {
    int left = skipWS(expression, p);
    if (left < expression.length()) {
      int right = skipUntil(expression, left, "=");
      /**
       * "="前面的
       */
      String name = trimmedStr(expression, left, right);
      left = right + 1;
      right = skipUntil(expression, left, ",");
      /**
       * "="后面的
       */
      String value = trimmedStr(expression, left, right);
      /**
       * 放入Map
       */
      put(name, value);
      /**
       * 继续查找key=value，放入Map
       */
      option(expression, right + 1);
    }
  }

  private String trimmedStr(String str, int start, int end) {
    /**
     * 开头跳过不显示的字符
     */
    while (str.charAt(start) <= 0x20) {
      start++;
    }
    /**
     * 结尾跳过不显示的字符
     */
    while (str.charAt(end - 1) <= 0x20) {
      end--;
    }
    return start >= end ? "" : str.substring(start, end);
  }

}
