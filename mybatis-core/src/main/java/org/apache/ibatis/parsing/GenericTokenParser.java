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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
public class GenericTokenParser {

  /**
   * 起始token
   */
  private final String openToken;
  /**
   * 结束token
   */
  private final String closeToken;
  /**
   * 组合了TokenHandler对象来解析变量
  */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 解析字符串中变量表达式（不支持嵌套）
   */
  public String parse(String text) {
    //空值
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    /**
     * 没有找到open token
     */
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }

    char[] src = text.toCharArray();
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    while (start > -1) {
      /**
       * 有转移字符，计算的start不正确，需要重新计算
       */
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        /**
         * 起始token被转义，当作一般字符来直接缓存
         */
        builder.append(src, offset, start - offset - 1).append(openToken);
        /**
         * 调整offset
         */
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        /**
         * 清空expression，以备缓存变量表达式
         */
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        /**
         * 缓存一般字符串
         */
        builder.append(src, offset, start - offset);
        /**
         * 调整offset
         */
        offset = start + openToken.length();

        /**
         * 结束token索引位置
         */
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          /**
           * 结束token被转义，当作一般字符来处理
           */
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            /**
             * 寻找下一个结束token位置
             */
            end = text.indexOf(closeToken, offset);
          } else {
            /**
             * expression抠出变量表达式部分
             */
            expression.append(src, offset, end - offset);
            break;
          }
        }
        /**
         * 没有找到结束token，起始token不是一个有效的边界token，被当作一般字符来处理
         */
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          /**
           * 使用TokenHandler解析变量表达式的值
           */
          builder.append(handler.handleToken(expression.toString()));
          /**
           * offset跳过结束token，为下一次循环做准备
           */
          offset = end + closeToken.length();
        }
      }

      /**
       * 继续寻找下一个变量表达式
       */
      start = text.indexOf(openToken, offset);
    }

    /**
     * 结束token后面的尾巴
     */
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
