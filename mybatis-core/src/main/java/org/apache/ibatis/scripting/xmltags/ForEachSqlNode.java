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
import org.apache.ibatis.session.Configuration;

import java.util.Map;

/**
 * @author Clinton Begin
 *
 * foreach节点脚本容器
 *
 * 通过一些条件来控制子节点脚本生成
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  private final ExpressionEvaluator evaluator;
  private final String collectionExpression;
  private final SqlNode contents;
  private final String open;
  private final String close;
  private final String separator;
  private final String item;
  private final String index;
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  /**
   * context中包含了实际入参
   */
  public boolean apply(DynamicContext context) {
    /**
     * context.getBindings()是ContextMap，不仅包含实际入参，而且包含<bind>标签绑定的参数
     */
    Map<String, Object> bindings = context.getBindings();
    /**
     * 通过ognl求值（集合），将值转换成iterable对象
     */
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    /**
     * 将前缀添加到sql中
     */
    applyOpen(context);
    int i = 0;
    /**
     * 通过foreach拼接sql片段
     */
    for (Object o : iterable) {
      /**
       * 保存老的Context
       */
      DynamicContext oldContext = context;
      /**
       * 处理分隔符前缀
       * 由于foreach每次迭代都需要将index和item绑定到上下文，最终是绑定到oldContext中
       * 为了处理分隔符前缀（仅仅是为了这个目的，因为绑定最终还是绑到底层的Context上了），每次都创建新的Context，着实有点浪费
       */
      if (first || separator == null) {
        context = new PrefixedContext(context, "");
      } else {
        context = new PrefixedContext(context, separator);
      }
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        /**
         * 将index和item绑定到上下文中
         * 如果集合是Map，那么index为key
         */
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        /**
         * 将index和item绑定到上下文中
         * 如果集合不是Map，那么index为自动增长的数字
         */
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      /**
       * 更新first变量，第一次迭代和后面的迭代在处理分隔符前缀时不一样
       */
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    /**
     * 将后缀添加到sql中
     */
    applyClose(context);
    /**
     * 将index和item从Context中移除，上文的绑定其实都是绑到底层Context上了
     * 但是没有删除那些特殊的index和item
     */
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      /**
       * 将index绑定到DynamicContext中
       * DynamicContext子类使用了装饰模式，但是数据绑定都会绑到底层的ContextMap上，for-each中最终会剥离装饰类的外层，但是
       * 绑定的参数并不会丢失
       */
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    /**
     * 将item绑定到DynamicContext中
     * DynamicContext子类使用了装饰模式，但是数据绑定都会绑到底层的ContextMap上，for-each中最终会剥离装饰类的外层，但是
     * 绑定的参数并不会丢失
     */
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  private static class FilteredDynamicContext extends DynamicContext {
    private final DynamicContext delegate;
    private final int index;
    private final String itemIndex;
    private final String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      /**
       * 此处使用内置的item名字（每次迭代都不同）和内置的index名字（每次迭代都不同）替换foreach迭代过程中的临时变量
       * 是为了将foreach平铺开，最终解析变量值时可以笼统解析
       */
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        /**
         * (?![^.,:\\s])等价于(?=[.,:\\s])，表示后面紧跟着“.”或“,”或“:”或空格的变量名才会被替换
         */
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });

      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }

  private class PrefixedContext extends DynamicContext {
    private final DynamicContext delegate;
    private final String prefix;
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public void appendSql(String sql) {
      /**
       * sql不为空就会添加prefix
       */
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
