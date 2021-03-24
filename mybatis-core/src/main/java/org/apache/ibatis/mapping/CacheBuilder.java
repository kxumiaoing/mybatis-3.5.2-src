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
package org.apache.ibatis.mapping;

import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.cache.decorators.*;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Clinton Begin
 */
public class CacheBuilder {
  /**
   * 一般保存用来命令空间
   */
  private final String id;
  /**
   * 被装饰类型
   */
  private Class<? extends Cache> implementation;
  /**
   * 装饰类型
   */
  private final List<Class<? extends Cache>> decorators;
  /**
   * 以下是一些参数
   */
  private Integer size;
  private Long clearInterval;
  private boolean readWrite;
  private Properties properties;
  private boolean blocking;

  public CacheBuilder(String id) {
    this.id = id;
    this.decorators = new ArrayList<>();
  }

  public CacheBuilder implementation(Class<? extends Cache> implementation) {
    this.implementation = implementation;
    return this;
  }

  public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
    if (decorator != null) {
      this.decorators.add(decorator);
    }
    return this;
  }

  public CacheBuilder size(Integer size) {
    this.size = size;
    return this;
  }

  public CacheBuilder clearInterval(Long clearInterval) {
    this.clearInterval = clearInterval;
    return this;
  }

  public CacheBuilder readWrite(boolean readWrite) {
    this.readWrite = readWrite;
    return this;
  }

  public CacheBuilder blocking(boolean blocking) {
    this.blocking = blocking;
    return this;
  }

  public CacheBuilder properties(Properties properties) {
    this.properties = properties;
    return this;
  }

  public Cache build() {
    /**
     * 设置默认值
     */
    setDefaultImplementations();
    /**
     * 被装饰对象
     */
    Cache cache = newBaseCacheInstance(implementation, id);
    /**
     * 设置属性，并且初始化
     */
    setCacheProperties(cache);

    // issue #352, do not apply decorators to custom caches
    if (PerpetualCache.class.equals(cache.getClass())) {
      /**
       * 装饰器，有多少层装饰器，就包装几层
       */
      for (Class<? extends Cache> decorator : decorators) {
        /**
         * 使用有参构造器构造缓存对象
         */
        cache = newCacheDecoratorInstance(decorator, cache);
        setCacheProperties(cache);
      }
      /**
       * 固定属性都是通过装饰器来实现的
       */
      cache = setStandardDecorators(cache);
    } else if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
      /**
       * 如果被装饰对象本来就是一个装饰对象，那么只给它加上命中率统计的装饰器
       */
      cache = new LoggingCache(cache);
    }
    return cache;
  }

  private void setDefaultImplementations() {
    if (implementation == null) {
      implementation = PerpetualCache.class;
      if (decorators.isEmpty()) {
        decorators.add(LruCache.class);
      }
    }
  }

  /**
   * 一些固定属性都是通过特定的装饰器来实现的
   * BlockingCache->SynchronizedCache->LoggingCache->SerializedCache->ScheduledCache->FifoCache/LruCache/SoftCache/WeakCache->PerpetualCache
   */
  private Cache setStandardDecorators(Cache cache) {
    try {
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      /**
       * 设置大小
       */
      if (size != null && metaCache.hasSetter("size")) {
        metaCache.setValue("size", size);
      }
      /**
       * ScheduledCache装饰器来实现定时清理缓存
       */
      if (clearInterval != null) {
        cache = new ScheduledCache(cache);
        ((ScheduledCache) cache).setClearInterval(clearInterval);
      }
      /**
       * SerializedCache装饰器来实现可读写的缓存（系列化）
       */
      if (readWrite) {
        cache = new SerializedCache(cache);
      }
      /**
       * 统计缓存命中率
       */
      cache = new LoggingCache(cache);
      /**
       * 给缓存套上同步的套子
       */
      cache = new SynchronizedCache(cache);
      /**
       * 给缓存套上排它锁的套子（只有命中了才释放锁）
       */
      if (blocking) {
        cache = new BlockingCache(cache);
      }
      return cache;
    } catch (Exception e) {
      throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
    }
  }

  /**
   * 设置属性，并且初始化
   */
  private void setCacheProperties(Cache cache) {
    if (properties != null) {
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      /**
       * 通过MetaObject来设置Cache对象的属性
       */
      for (Map.Entry<Object, Object> entry : properties.entrySet()) {
        String name = (String) entry.getKey();
        String value = (String) entry.getValue();
        //有setter，设置值
        if (metaCache.hasSetter(name)) {
          Class<?> type = metaCache.getSetterType(name);
          if (String.class == type) {
            metaCache.setValue(name, value);
          } else if (int.class == type
              || Integer.class == type) {
            metaCache.setValue(name, Integer.valueOf(value));
          } else if (long.class == type
              || Long.class == type) {
            metaCache.setValue(name, Long.valueOf(value));
          } else if (short.class == type
              || Short.class == type) {
            metaCache.setValue(name, Short.valueOf(value));
          } else if (byte.class == type
              || Byte.class == type) {
            metaCache.setValue(name, Byte.valueOf(value));
          } else if (float.class == type
              || Float.class == type) {
            metaCache.setValue(name, Float.valueOf(value));
          } else if (boolean.class == type
              || Boolean.class == type) {
            metaCache.setValue(name, Boolean.valueOf(value));
          } else if (double.class == type
              || Double.class == type) {
            metaCache.setValue(name, Double.valueOf(value));
          } else {
            throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
          }
        }
      }
    }

    /**
     * 如果实现了InitializingObject接口，那么就地调用initialize方法
     */
    if (InitializingObject.class.isAssignableFrom(cache.getClass())) {
      try {
        ((InitializingObject) cache).initialize();
      } catch (Exception e) {
        throw new CacheException("Failed cache initialization for '"
          + cache.getId() + "' on '" + cache.getClass().getName() + "'", e);
      }
    }
  }

  /**
   * 使用构造方法创建一个实例
   */
  private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
    Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(id);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
    }
  }

  /**
   * 构造方法
   */
  private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(String.class);
    } catch (Exception e) {
      throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  "
        + "Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e, e);
    }
  }

  /**
   * 使用构造方法创建一个实例
   */
  private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
    Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(base);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
    }
  }

  /**
   * 构造方法
   */
  private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(Cache.class);
    } catch (Exception e) {
      throw new CacheException("Invalid cache decorator (" + cacheClass + ").  "
        + "Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
    }
  }
}
