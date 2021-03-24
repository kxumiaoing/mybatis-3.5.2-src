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
package org.apache.ibatis.session;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * Builds {@link SqlSession} instances.
 *
 * @author Clinton Begin
 */

/**
 * 解析配置文件（xml），创建SqlSessionFacotry实例，可以看作是SqlSessionFactory的构造类
 */
public class SqlSessionFactoryBuilder {

  /**
   * 构造SqlSessionFactory的参数
   *   文件流：配置文件的文件流
   *   environment: 环境标示（个性化选择配置信息）
   *   properties: 额外的属性（个性化选择配置信息）
   *
   * 构造过程：
   *  1、使用XmlConfigBuilder解析配置文件，生成Configuration实例
   *  2、使用Configuration实例创建SqlSessionFactory对象
  */

  /**
   * 文件流为字符流（Reader）
  */
  public SqlSessionFactory build(Reader reader) {
    return build(reader, null, null);
  }

  public SqlSessionFactory build(Reader reader, String environment) {
    return build(reader, environment, null);
  }

  public SqlSessionFactory build(Reader reader, Properties properties) {
    return build(reader, null, properties);
  }

  public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
    try {
      XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
      /**
       * 1、解析xml配置文件，生成Configuration对象
       * 2、根据Configuration对象构建DefaultSqlSessionFactory
       */
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        reader.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /**
   * 文件流为字符流（InputStream）
   */
  public SqlSessionFactory build(InputStream inputStream) {
    return build(inputStream, null, null);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment) {
    return build(inputStream, environment, null);
  }

  public SqlSessionFactory build(InputStream inputStream, Properties properties) {
    return build(inputStream, null, properties);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
      /**
       * 1、解析xml配置文件，生成Configuration对象
       * 2、根据Configuration对象构建DefaultSqlSessionFactory
       */
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        inputStream.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /**
   * 使用Configuration对象（包含了所有的配置信息）来创建SqlSessionFactory
   */
  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }

}
