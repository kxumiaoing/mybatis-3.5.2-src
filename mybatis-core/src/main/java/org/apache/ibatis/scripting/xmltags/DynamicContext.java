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

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 *
 * 解析sql语句时使用的上下文
 *
 * 缓存了提供参数的对象以及这个对象对应的元对象（提供对象属性的访问）
 *
 */
public class DynamicContext {

  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  private final ContextMap bindings;
  /**
   * 内部使用StringBuilder拼接字符串
   */
  private final StringJoiner sqlBuilder = new StringJoiner(" ");
  private int uniqueNumber = 0;

  /**
   * @param configuration Configuration对象
   * @param parameterObject 提供参数的对象，这个是动态sql语句的关键：sql语句的动态部分由这个对象决定
   */
  public DynamicContext(Configuration configuration, Object parameterObject) {
    /**
     * 如果如果参数是Map，没必要通过MetaObject来访问它的“属性”（key）
     */
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      /**
       * 有TypeHandler表示提供参数的对象是一个“简单”的对象，不是一个参数值的集合
       */
      boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
      bindings = new ContextMap(metaObject, existsTypeHandler);
    } else {
      bindings = new ContextMap(null, false);
    }
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }

  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  /**
   * 使用StringJoiner将sql片段拼接成完整的sql语句
   */
  public void appendSql(String sql) {
    sqlBuilder.add(sql);
  }

  /**
   * 返回完整sql语句
   */
  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;
    private final MetaObject parameterMetaObject;
    private final boolean fallbackParameterObject;

    public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
      this.parameterMetaObject = parameterMetaObject;
      this.fallbackParameterObject = fallbackParameterObject;
    }

    @Override
    public Object get(Object key) {
      String strKey = (String) key;
      /**
       * 这里已经有的值是绑定到上下文中的值
       */
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }

      if (parameterMetaObject == null) {
        return null;
      }

      /**
       * 使用MetaObject求值了
       */
      if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
          //没有getter就使用原始对象
        return parameterMetaObject.getOriginalObject();
      } else {
        // issue #61 do not modify the context when reading
        //有getter，就根据getter获取属性的值
        return parameterMetaObject.getValue(strKey);
      }
    }
  }

  static class ContextAccessor implements PropertyAccessor {

    @Override
    /**
     * 从target上获取属性值
     * 求值顺序：
     * 1、ContextMap对应的Map本身（可能是缓存）
     * 2、入参对象的MetaObject对象
     * 3、对参对象本身（此处入参对象是一Map）
     */
    public Object getProperty(Map context, Object target, Object name) {
      Map map = (Map) target;

      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }

      /**
       * 在实际入参对象（它为null或者是一个Map）上获取属性值
       * 与DynamicContext的构造方法遥相呼应（互补），战线拉得有点长
       */
      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    /**
     * 往target上设置值
     */
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}