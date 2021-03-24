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

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

import java.util.regex.Pattern;

/**
 * @author Clinton Begin
 *
 * sql语句段（不完整的）容器
 */
public class TextSqlNode implements SqlNode {
  /**
   * sql片段
   */
  private final String text;
  /**
   * 注入值验证模式
   */
  private final Pattern injectionFilter;

  public TextSqlNode(String text) {
    this(text, null);
  }

  public TextSqlNode(String text, Pattern injectionFilter) {
    this.text = text;
    this.injectionFilter = injectionFilter;
  }

  /**
   * sql语句中包含“${”和“}”就是动态sql
   */
  public boolean isDynamic() {
    DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
    GenericTokenParser parser = createParser(checker);
    /**
     * 需要处理变量，就表明包含“${”和“}”
     */
    parser.parse(text);
    return checker.isDynamic();
  }

  @Override
  public boolean apply(DynamicContext context) {
    GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter));
    /**
     * 使用OGNL来进行变量替换
     */
    context.appendSql(parser.parse(text));
    return true;
  }

  private GenericTokenParser createParser(TokenHandler handler) {
    return new GenericTokenParser("${", "}", handler);
  }

  /**
   * 通过OGNL（上下文是ContextMap）来求取变量的值
   */
  private static class BindingTokenParser implements TokenHandler {

    private DynamicContext context;
    /**
     * 注入值验证模式
     */
    private Pattern injectionFilter;

    public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
      this.context = context;
      this.injectionFilter = injectionFilter;
    }

    @Override
    public String handleToken(String content) {
      /**
       * 原始参数
       */
      Object parameter = context.getBindings().get("_parameter");
      /**
       * 简单参数以及null，设置到以value为key的值中
       */
      if (parameter == null) {
        context.getBindings().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
        context.getBindings().put("value", parameter);
      }
      /**
       * 通过OGNL求值
       */
      Object value = OgnlCache.getValue(content, context.getBindings());
      /**
       * 一定是字符串
       */
      String srtValue = value == null ? "" : String.valueOf(value); // issue #274 return "" instead of "null"
      /**
       * 验证注入值是否有安全问题
       */
      checkInjection(srtValue);
      return srtValue;
    }

    private void checkInjection(String value) {
      if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
        throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
      }
    }
  }

  private static class DynamicCheckerTokenParser implements TokenHandler {

    private boolean isDynamic;

    public DynamicCheckerTokenParser() {
      // Prevent Synthetic Access
    }

    public boolean isDynamic() {
      return isDynamic;
    }

    @Override
    public String handleToken(String content) {
      this.isDynamic = true;
      return null;
    }
  }

}
