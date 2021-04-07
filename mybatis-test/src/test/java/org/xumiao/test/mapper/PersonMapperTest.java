package org.xumiao.test.mapper;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xumiao.test.entity.Person;
import org.xumiao.test.vo.People;

public class PersonMapperTest {
    private SqlSession sqlSession;

    /*

     */

    @Before
    public void setUp() throws Exception {
        sqlSession = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream("mybatis-cfg.xml")).openSession();
    }


    @Test
    public void queryPersonById4() {
        Person  person = new Person();

        person.setId(1);
        System.out.println(person);

        sqlSession.selectOne("org.xumiao.test.mapper.PersonMapper.queryPersonById", person);

        System.out.println(person);
    }

    @Test
    public void queryPersonById5() {
        People people = new People();

        people.setId(1500L);
        System.out.println(people);

        Object r = sqlSession.selectOne("org.xumiao.test.mapper.PersonMapper.queryPersonById", people);

        System.out.println(r);
        System.out.println(people);
    }

    @After
    public void close() throws Exception {
        if (null != sqlSession) {
            sqlSession.close();
        }
    }
}