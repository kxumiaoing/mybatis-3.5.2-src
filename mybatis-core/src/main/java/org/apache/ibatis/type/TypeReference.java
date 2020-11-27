/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * References a generic type.
 *
 * @param <T> the referenced type
 * @since 3.1.0
 * @author Simone Tripodi
 */

/**
 * 获取参数类型中的类型参数
 */
public abstract class TypeReference<T> {

  private final Type rawType;

  protected TypeReference() {
    rawType = getSuperclassTypeParameter(getClass());
  }

  //获取TypeReference的参数类型
  Type getSuperclassTypeParameter(Class<?> clazz) {
    //获取直接父类
    Type genericSuperclass = clazz.getGenericSuperclass();
    //是Class的实例
    if (genericSuperclass instanceof Class) {
      // try to climb up the hierarchy until meet something useful
      //如果是TypeReference，继续往父类找
      if (TypeReference.class != genericSuperclass) {
        return getSuperclassTypeParameter(clazz.getSuperclass());
      }

      //如果直接继承TypeReference，但是没有参数类型就抛出异常
      throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
        + "Remove the extension or add a type parameter to it.");
    }

    //一定是ParameterizedType的子类，获取第一个类型参数
    Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
    // TODO remove this when Reflector is fixed to return Types
    //如果类型参数是ParameterizedType的子类，去除类型参数
    if (rawType instanceof ParameterizedType) {
      rawType = ((ParameterizedType) rawType).getRawType();
    }

    return rawType;
  }

  public final Type getRawType() {
    return rawType;
  }

  @Override
  public String toString() {
    return rawType.toString();
  }

}
