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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */

/**
 * 类型处理器：用于数据库字段类型和领域属性类型之间转换
 */
public interface TypeHandler<T> {

  /**
   * 根据JdbcType往PreparedStatement里面设置参数值
   */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * @param columnName Colunm name, when configuration <code>useColumnLabel</code> is <code>false</code>
   */
  /**
   * 根据字段名从ResultSet中获取值，并且转换成对应的java类型
   */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  /**
   * 根据字段索引从ResultSet中获取值，并且转换成对应的java类型
   */
  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  /**
   * 根据字段索引从CallableStatement中获取值，并且转换成对应的java类型
   */
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
