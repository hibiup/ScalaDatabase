package com.hibiup.database

import java.sql.Timestamp
import java.util.Date

import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.slf4j.LoggerFactory
import slick.lifted.Tag

import scala.concurrent.Await

// Derby database JDBC Driver
import slick.jdbc.H2Profile.api._

object Example_2_Slick_Test{
    private val logger = LoggerFactory.getLogger(this.getClass)

    /** 1）定义表字段模版 */
    final case class Account(id: Long, email: String, password:Option[String], lasttime:Timestamp)

    /** 2）生成相应的表结构描述 */
    final class Accounts(tag: Tag) extends Table[Account](tag, "ACCOUNTS") {
        def id = column[Long]("ID", O.PrimaryKey) // This is the primary key column
        def email = column[String]("EMAIL")
        def password = column[Option[String]]("PASSWORD")
        def lasttime = column[Timestamp]("LAST_TIME",O.Default(new java.sql.Timestamp(new Date().getTime())))
        // * 代表结果集字段,所有的 Table 类都需要定义一个 * 来映射字段
        def * = (id, email, password, lasttime) <> (Account.tupled, Account.unapply)
    }
}

class Example_2_Slick_Test extends Init{
    import Example_2_Slick_Test._    // 引入 1, 2
    import scala.concurrent.duration._

    val timeout = Timeout(10 seconds)

    "Slick" should "" in {
        /** 3）用 Slick 提供的 jdbc Database object 来连接数据库. 参数是 Scala 的 Config */
        //val conn = Database.forConfig("database.connection")
        //try {
        withResource(Database.forConfig("database.connection")){ conn =>
            /**
              * 4) 定义指令模版 DDL，这很重要，下面的指令，包括 filter, drop, sortBy 等都是基于这个模版来生成最终的指令
              * */
            val users = TableQuery[Accounts]

            /*************************************
              * 6) 插入指令
              *
              * 定义 INSERT 的数据集
              * */
            val add_user = DBIO.seq(
                users += Account(101, "user2", Option("P@55W0rd"), new java.sql.Timestamp(new Date().getTime())),
                users += Account(102, "user3", None, new java.sql.Timestamp(new Date().getTime()) )
            )

            /** 6-2) 执行由 users 导出的序列集的默认行为是 INSERT */
            Await.result(conn.run(add_user), Duration.Inf)

            /** 6-2) 执行 users 的结果集的默认行为是 SELECT */
            val fetchUser = conn.run(users.result).map(_.foreach {
                case Account(id, email, password, lasttime) =>
                    println(s"$email($id)/$password: Last log in time: $lasttime")
            })
            fetchUser.recover{
                case t => logger.error(t.getMessage, t)
            }

            Await.result(fetchUser, timeout.value)


            /***********************************
              * 5) 查询指令
              *
              * 5-1) filter 相当于定义 Where, 返回 WrappingQuery
              * */
            val search = users.filter(_.id === 1L)           // 注意 “===” 而不是 "=="，类型也必须匹配

            /** 5-2-1) WrappingQuery 的 result 表示结果集（全部返回的字段），它的 map 函数将结果映射到一个输出 */
            val query1 = search.result.map(_.headOption.map(u => Account(u.id, u.email, Option(null), u.lasttime)))
            val user1 = conn.run(query1)                      // run 得到一个异步结果(Future)
        val result1 = Await.result(user1, Duration.Inf)   // 等待结束

            /** 5-2-2) 如果在 result 之前先调用 Query 的 map，相当于定义 SELECT 的参数，选择输出到 result 的字段 */
            val query2 = search.map(u => (u.id, u.email, u.lasttime))
                    .result.map(_.headOption.map {                // 然后对输出的字段再定义 map, 相对于 5-1) 这样可以减小输出的流量
                case (userId, email, lastLoginTime) => Account(userId, email, Option(null), lastLoginTime)
            })
            val result2 = Await.result(conn.run(query2), Duration.Inf)

            assert(result1 == result2)

        } //finally {conn.close()}
    }
}
