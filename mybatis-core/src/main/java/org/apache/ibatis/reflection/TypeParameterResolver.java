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
package org.apache.ibatis.reflection;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  /**
   * 解析属性的类型并尽可能缩小“范围”
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    Type fieldType = field.getGenericType();
    Class<?> declaringClass = field.getDeclaringClass();
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  /**
   * 解析方法返回值类型并尽可能缩小“范围”
   * @param method 方法
   * @param srcType 声明method的类或者其子类
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    Type returnType = method.getGenericReturnType();
    Class<?> declaringClass = method.getDeclaringClass();
    /**
     * declaringClass已经擦除了returnType的泛型信息（如果有的话）
     */
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  /**
   * 解析方法参数类型并尽可能缩小“范围”
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    Type[] paramTypes = method.getGenericParameterTypes();
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  /**
   * @param type 需要解析的类型
   * @param srcType declaringClass或者declaringClass的子类
   * @param declaringClass “最早”使用type的类
   *
   * 缩小type的范围
   */
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    if (type instanceof TypeVariable) {
      /**
       * 类型变量
       */
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      /**
       * 参数类型
       */
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      /**
       * 泛型数组类型
       */
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
      /**
       * class的实例
       */
      return type;
    }
  }

  /**
   * @param genericArrayType 需要推断的类型
   * @param srcType declaringClass或者declaringClass的子类
   * @param declaringClass “最早”使用genericArrayType的类
   *
   * 缩小genericArrayType的范围
   */
  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    //分不同的类型处理
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    //基本类型是Class的对象
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      //自己实现的数组类型（保存组件类型而已）
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /**
   * @param parameterizedType 需要推测的类型
   * @param srcType declaringClass或者declaringClass的子类
   * @param declaringClass 使用parameterizedType的类
   * 缩小参数化类型参数的范围
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
    Type[] args = new Type[typeArgs.length];

    /**
     * 依次缩小参数化类型参数的范围
     */
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        args[i] = typeArgs[i];
      }
    }
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  /**
   * @param wildcardType 需要缩小范围的通用类型
   * @param srcType declaringClass或declaringClass的子类
   * @param declaringClass “最早”使用wildcardType的类
   */
  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  /**
   * @param bounds 需要缩小范围的边界类型
   * @param srcType declaringClass或declaringClass的子类
   * @param declaringClass “最早”使用bounds的类
   */
  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * @param typeVar 需要推测的类型
   * @param srcType declaringClass或者declaringClass的子类
   * @param declaringClass 使用typeVar的类
   *
   * 缩小变量类型的“范围”
   * 1、直到declaringClass都没能进行缩小，那么就是使用变量类型的边界
   * 2、从父类中推断，直到declaringClass
   * 3、从父接口中推断，直到declaringClass
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result;
    Class<?> clazz;

    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      /**
       * 使用变量类型的类必须是参数化类型（或者是泛型擦除后的Class实例）
       */
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    /**
     * declaringClass已经擦除了泛型信息，对推断变量类型的“范围”起不了任何作用
     * srcType就是declaringClass
     */
    if (clazz == declaringClass) {
      /**
       * 使用变量类型的边界（如果存在的话）
       */
      Type[] bounds = typeVar.getBounds();
      if (bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    /**
     * 从父类中推断，有结果就返回
     */
    Type superclass = clazz.getGenericSuperclass();
    /**
     * typeVar是要推测的类型
     * srcType是使用typeVar的类型（Type的实例）
     * clazz是srcType擦除泛型后的类
     * declaringClass是clazz或者clazz的父类（Class实例）
     * superClass是clazz的直接父类（Type实例）
     *
     * 从父类中推断typeVar更小的“范围”
     */
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

    /**
     * typeVar是要推测的类型
     * srcType是使用typeVar的类型（Type的实例）
     * clazz是srcType擦除泛型后的类
     * declaringClass是clazz或者clazz的父类（Class实例）
     * superInterface是clazz的直接接口（Type实例）
     *
     * 从父接口中推断typeVar更小的“范围”
     */
    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }
    return Object.class;
  }

  /**
   * @param typeVar 需要推断的类型
   * @param srcType 使用typeVar的类（Type实例）
   * @param clazz srcType擦除泛型后的类（Class实例）
   * @param declaringClass 是clazz或者clazz的父类（Class实例）
   * @param superclass clazz的直接父类（Type实例）
   *
   * 从srcType父类中缩小typeVar的范围，直到父类是declaringClass
   * 1、父类就是declaringClass，结果就是typeVar在父类参数化类型中对应的类型
   * 2、继续从父类（Type实例）找
   * 3、继续从父类（Class实例）找
   * 4、找不到
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    if (superclass instanceof ParameterizedType) {
      ParameterizedType parentAsType = (ParameterizedType) superclass;
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
      TypeVariable<?>[] parentTypeVars = parentAsClass.getTypeParameters();
      /**
       * 通过子类来缩小父类中参数化类型的范围
       */
      if (srcType instanceof ParameterizedType) {
        parentAsType = translateParentTypeVars((ParameterizedType) srcType, clazz, parentAsType);
      }
      /**
       * 找到了declaringClass对应的Type实例，然后设法找到typeVar对应的通配符类型（这是理想情况，也有可能还是变量类型）
       */
      if (declaringClass == parentAsClass) {
        for (int i = 0; i < parentTypeVars.length; i++) {
          if (typeVar == parentTypeVars[i]) {
            return parentAsType.getActualTypeArguments()[i];
          }
        }
      }
      /**
       * 继续往父类（Type实例）找，直到父类是declaringClass（最早使用typeVar的类）
       */
      if (declaringClass.isAssignableFrom(parentAsClass)) {
        return resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
      /**
       * 继续往父类（Class实例）找，直到父类是declaringClass（最早使用typeVar的类）
       */
    } else if (superclass instanceof Class && declaringClass.isAssignableFrom((Class<?>) superclass)) {
      return resolveTypeVar(typeVar, superclass, declaringClass);
    }
    return null;
  }

  /**
   * @param srcType 原始的参数化类型（Type实例）
   * @param srcClass srcType进行泛型擦除后的类（Class实例）
   * @param parentType srcType/srcClass父类型（Type实例）
   *
   *
   * 基于子类，缩小父类中参数化类型的范围
   */
  private static ParameterizedType translateParentTypeVars(ParameterizedType srcType, Class<?> srcClass, ParameterizedType parentType) {
    /**
     * 父类（Type实例）的类型参数
     */
    Type[] parentTypeArgs = parentType.getActualTypeArguments();
    /**
     * 子类（Type实例）的类型参数
     */
    Type[] srcTypeArgs = srcType.getActualTypeArguments();
    /**
     * 子类（Class实例）的类型参数（变量类型）
     */
    TypeVariable<?>[] srcTypeVars = srcClass.getTypeParameters();
    Type[] newParentArgs = new Type[parentTypeArgs.length];
    boolean noChange = true;
    for (int i = 0; i < parentTypeArgs.length; i++) {
        /**
         * 是变量类型
         */
      if (parentTypeArgs[i] instanceof TypeVariable) {
        for (int j = 0; j < srcTypeVars.length; j++) {
          if (srcTypeVars[j] == parentTypeArgs[i]) {
            noChange = false;
            newParentArgs[i] = srcTypeArgs[j];
          }
        }
      } else {
        newParentArgs[i] = parentTypeArgs[i];
      }
    }
    return noChange ? parentType : new ParameterizedTypeImpl((Class<?>)parentType.getRawType(), null, newParentArgs);
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
