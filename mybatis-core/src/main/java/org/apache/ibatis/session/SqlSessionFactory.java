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

import java.sql.Connection;

/**
 * Creates an {@link SqlSession} out of a connection or a DataSource
 *
 * @author Clinton Begin
 */

/**
 * SqlSession生产工厂接口，用来获取SqlSession实例的
 */
public interface SqlSessionFactory {
  /**
   * 获取默认的SqlSession
   * 获取SqlSession，并且设置是否自动提交
   * 通过Connection获取SqlSession
   * 获取SqlSession，并且设置事务级别
   */
  SqlSession openSession();

  SqlSession openSession(boolean autoCommit);

  SqlSession openSession(Connection connection);

  SqlSession openSession(TransactionIsolationLevel level);

  /**
   * 根据Executor的类型，获取默认的SqlSession
   * 根据Executor的类型，获取SqlSession，并且设置是否自动提交
   * 根据Executor的类型，通过Connection获取SqlSession
   * 根据Executor的类型，获取SqlSession，并且设置事务级别
   */
  SqlSession openSession(ExecutorType execType);

  SqlSession openSession(ExecutorType execType, boolean autoCommit);

  SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level);

  SqlSession openSession(ExecutorType execType, Connection connection);

  /**
   * 获取mybatis的整个配置
   */
  Configuration getConfiguration();
}
