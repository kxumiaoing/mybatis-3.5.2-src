/**
 *    Copyright 2009-2018 the original author or authors.
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
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Clinton Begin
 * 如果是级联属性，按照级联属性处理
 * 如果不是级联属性，按照map的key处理
 */
public class MapWrapper extends BaseWrapper {

  //Map的原始对象
  private final Map<String, Object> map;

  //metaObject是宿主对象的元信息对象
  public MapWrapper(MetaObject metaObject, Map<String, Object> map) {
    super(metaObject);
    this.map = map;
  }

  /**
   * 获取属性值
   */
  @Override
  public Object get(PropertyTokenizer prop) {
    //如果有索引
    if (prop.getIndex() != null) {
        //属性对象或者“当前”对象 ()
      Object collection = resolveCollection(prop, map);
      //按照索引获取值
      return getCollectionValue(prop, collection);
    } else {
      //如果没有索引，属性名字就是key
      return map.get(prop.getName());
    }
  }

  /**
   * 设置属性值
   */
  @Override
  public void set(PropertyTokenizer prop, Object value) {
      //如果有索引
    if (prop.getIndex() != null) {
      Object collection = resolveCollection(prop, map);
      //按照索引设置值
      setCollectionValue(prop, collection, value);
    } else {
      //属性名就是key，设置值
      map.put(prop.getName(), value);
    }
  }

  @Override
  public String findProperty(String name, boolean useCamelCaseMapping) {
    //简单返回名字就是了
    return name;
  }

  @Override
  public String[] getGetterNames() {
    //所有key
    return map.keySet().toArray(new String[map.keySet().size()]);
  }

  @Override
  public String[] getSetterNames() {
    //所有的key
    return map.keySet().toArray(new String[map.keySet().size()]);
  }

  @Override
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);

    //如果是级联属性
    if (prop.hasNext()) {
      //获取属性对象的元数据对象
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      //属性对象的元数据对象如果为null，返回Object.class
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return Object.class;
      } else {
          //从属性对象上继续往下找
        return metaValue.getSetterType(prop.getChildren());
      }
    } else {
      //如果不是级联属性，属性名就是key，返回key对应value的类型
      if (map.get(name) != null) {
        return map.get(name).getClass();
      } else {
        return Object.class;
      }
    }
  }

  /**
   * 和getSetterType一样的逻辑
   */
  @Override
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return Object.class;
      } else {
        return metaValue.getGetterType(prop.getChildren());
      }
    } else {
      if (map.get(name) != null) {
        return map.get(name).getClass();
      } else {
        return Object.class;
      }
    }
  }

  @Override
  public boolean hasSetter(String name) {
    return true;
  }

  @Override
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //级联属性
    if (prop.hasNext()) {
      //和getGetterType相似，不过首先判断map中是否有这个属性
      if (map.containsKey(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return true;
        } else {
          return metaValue.hasGetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      return map.containsKey(prop.getName());
    }
  }

  /**
   * 初始化属性的元数据对象
   */
  @Override
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
    HashMap<String, Object> map = new HashMap<>();
    set(prop, map);
    return MetaObject.forObject(map, metaObject.getObjectFactory(), metaObject.getObjectWrapperFactory(), metaObject.getReflectorFactory());
  }

  @Override
  public boolean isCollection() {
    return false;
  }

  @Override
  public void add(Object element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> void addAll(List<E> element) {
    throw new UnsupportedOperationException();
  }

}
