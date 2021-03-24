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
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.util.List;

/**
 * @author Clinton Begin
 * 对象包裹器，提供对象的通用访问
 *
 * ObjectWrapper -> MetaClass -> Reflector
 *
 * 操作委托给MetaClass和MetaObject
 *
 */
public interface ObjectWrapper {

  /**
   * 通过属性名字获取属性
   */
  Object get(PropertyTokenizer prop);

  /**
   * 通过属性名设置属性
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 查找属性名
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 所有getter方法的名字
   */
  String[] getGetterNames();

  /**
   * 所有setter方法的名字
   */
  String[] getSetterNames();

  /**
   * 根据属性名查找setter的参数类型
   */
  Class<?> getSetterType(String name);

  /**
   * 根据属性名查找getter的返回值类型
   */
  Class<?> getGetterType(String name);

  /**
   * 根据属性名判断是否有对应的setter
   */
  boolean hasSetter(String name);

  /**
   * 根据属性名判断是否有对应的getter
   */
  boolean hasGetter(String name);

  /**
   * 获取属性对象对应的MetaObject
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  boolean isCollection();

  void add(Object element);

  <E> void addAll(List<E> element);

}
