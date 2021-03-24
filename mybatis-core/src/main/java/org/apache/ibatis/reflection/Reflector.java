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

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 * getDeclaredConstructors/getConstructors, getDeclaredMethods/getMethods的区别：
 *      带有Declared的返回自身定义的所有对象（private、default、protected和public），不包括从父类继承的
 *      不带有Declared的只返会public对象，包括从父类继承的
 *
 *
 * getCenericReturnType/getReturnType, getGenericSuperClass/getSuperClass, getGenericInterfaces/getInterfaces的区别：
 *      带有Generic的返回的是Type的实例，带有泛型信息
 *      没有Generic的返回的是Class的实例，不带泛型信息（泛型被擦除了）
 *
 *
 *
 * ##########################################
 * ###       难点是类型的精确性推断       ###
 * ##########################################
 *
 *
 */
public class Reflector {
  /**
   * 反射器对应的类
   */
  private final Class<?> type;
  /**
   * 可读属性
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性
   */
  private final String[] writablePropertyNames;
  /**
   * setter方法
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * getter方法
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * setter方法参数类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * getter方法返回类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 默认的构造器
   */
  private Constructor<?> defaultConstructor;

  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    /**
     * 1、解析构造器
     */
    addDefaultConstructor(clazz);
    /**
     * 2、解析getter
     */
    addGetMethods(clazz);
    /**
     * 3、解析setter
     */
    addSetMethods(clazz);
    /**
     * 4、解析属性
     */
    addFields(clazz);
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 解析默认构造器
   */
  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    /**
     * 选择无参构造器
     */
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  private void addGetMethods(Class<?> clazz) {
    /**
     * 一个属性可能有多个getter方法
     */
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    /**
     * 获取所有方法（private、default、protected和public）
     * 按照签名（包含返回类型）区分不同方法
     */
    Method[] methods = getClassMethods(clazz);
    /**
     * 所有的getter方法
     */
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    /**
     * 解决冲突的方法
     */
    resolveGetterConflicts(conflictingGetters);
  }

    /**
     * 一个属性（推断）对应多个getter方法，需要解决冲突
     */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        /**
         * 返回值类型相等
         */
        if (candidateType.equals(winnerType)) {
          /**
           * 只有boolean属性才存在两种不同形式的getter方法：isXXX和getXXX
           */
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {
            /**
             * 首选is开头的getter方法
             */
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          /**
           * 返回值类型取“范围”更小的那一个
           * 方法的返回值如果是泛型（擦除后变成Object），重写之后变成实际类型
           */
          winner = candidate;
        } else {
          /**
           * 两个方法的返回值类型不存在继承关系，违反了重写规则
           */
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      addGetMethod(propName, winner);
    }
  }

  /**
   * 添加getter方法，以及getter的返回值类型
   */
  private void addGetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      /**
       * 属性名（推断出来的），getter方法对应的Invoker
       */
      getMethods.put(name, new MethodInvoker(method));
      /**
       * 缩小返回值类型的“范围”
       */
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      /**
       * 属性名（推断出来的），getter方法的返回值类型
       */
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> clazz) {
    /**
     * 一个属性可能有多个setter方法
     */
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    /**
     * 获取所有方法（private、default、protected和public）
     * 按照签名（包含返回类型）区分不同方法
     */
    Method[] methods = getClassMethods(clazz);
    /**
     * 所有的setter方法
     */
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    /**
     * 解决冲突的方法
     */
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
    list.add(method);
  }

  /**
   * 一个属性（推断）对应多个setter方法，需要解决冲突
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        /**
         * 通过getter方法的返回值类型类确定对应的setter方法
         */
        if (setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        /**
         * 在没有报错的情况，寻找最好的那个setter方法
         */
        if (exception == null) {
          try {
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * @param setter1 目前最有的setter
   * @param setter2 最有setter的候选
   * @param property 属性的名字（推断）
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    /**
     * 选取参数类型范围最小的那个版本
     */
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    /**
     * 多个setter方法的参数类型之间没有继承关系，违反覆写规则
     */
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  /**
   * 添加setter方法，以及setter方法的参数类型
   */
  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  /**
   * 擦除泛型
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    /**
     * clazz声明的全部属性
     */
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        /**
         * 常量的值不可以在运行时设置
         */
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    /**
     * 递归父类
     */
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      /**
       * 反射优先setter？ 使得setter中的特殊逻辑失效
       */
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      /**
       * 反射优先getter？ 使得getter中的特殊逻辑失效
       */
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 检查属性名的有效性
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   *
   * 获取类的全部方法（private、default、protected和public）
   */
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    /**
     * 递归获取类申明的所有方法（private、default、protected和public）
     */
    while (currentClass != null && currentClass != Object.class) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
        /**
         * 对应接口的所有方法
         */
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      /**
       * 过滤掉桥接方法
       */
      if (!currentMethod.isBridge()) {
        /**
         * 方法签名
         */
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 本方法的签名：
   *    java.long.String#getSignature:java.lang.reflect.Method
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    /**
     * 1、方法返回值类型
     */
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    /**
     * 2、方法名称
     */
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    /**
     * 3、方法参数类型
     */
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
