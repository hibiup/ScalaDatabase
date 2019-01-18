package com.hibiup.database

import java.sql.Timestamp
import java.util.Date

import org.mindrot.jbcrypt.BCrypt
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
    final case class User(id: Long, account_id: Long, first_name: Option[String], last_name:Option[String])

    /** 2）生成相应的表结构描述 */
    final class Accounts(tag: Tag) extends Table[Account](tag, "ACCOUNTS") {
        def id = column[Long]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column
        def email = column[String]("EMAIL")
        def password = column[Option[String]]("PASSWORD")
        def lasttime = column[Timestamp]("LAST_TIME",O.Default(new java.sql.Timestamp(new Date().getTime())))
        // * 代表结果集字段,所有的 Table 类都需要定义一个 * 来映射字段
        def * = (id, email, password, lasttime) <> (Account.tupled, Account.unapply)
    }
    /**
      * 3) 定义指令模版 DDL，这很重要，下面的指令，包括 filter, drop, sortBy 等都是基于这个模版来生成最终的指令
      * */
    val accounts = TableQuery[Accounts]

    final class Users(tag: Tag) extends Table[User](tag, "USERS") {
        def id = column[Long]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column
        def account_id = column[Long]("ACCOUNT_ID")
        def first_name = column[Option[String]]("FIRST_NAME")
        def last_name = column[Option[String]]("LAST_NAME")
        // * 代表结果集字段,所有的 Table 类都需要定义一个 * 来映射字段
        def * = (id, account_id, first_name, last_name) <> (User.tupled, User.unapply)
        /** 3-1）定义外键，需要用到对应表的模板 */
        def account = foreignKey("ACCOUNT_FK", account_id, accounts)(_.id)
    }
    val users = TableQuery[Users]
}

class Example_2_Slick_Test extends Init{
    import Example_2_Slick_Test._    // 引入 1, 2
    import scala.concurrent.duration._

    val timeout = Timeout(10 seconds)

    "Slick" should "" in {
        /**
          * 4）用 Slick 提供的 jdbc Database object 来连接数据库. 参数是 Scala 的 Config
          * */
        withResource(Database.forConfig("database.connection")){ conn =>
            /*************************************
              * 5) 插入
              *
              * 定义 INSERT 的数据集
              * */
            val hashed: String = BCrypt.hashpw("P@55W0rd", BCrypt.gensalt)
            val add_account = DBIO.seq(
                /** Account 的第一个参数只给 0，因为是自增长 ID。 Timestamp 设为 null，会取得缺省值。*/
                accounts += Account(0, "username2", Option(hashed), null),
                accounts += Account(0, "username3", None, null )
            )

            /** 5-2) 执行由 users 导出的序列集的默认行为是 INSERT */
            Await.result(conn.run(add_account), Duration.Inf)

            /** 5-2) 执行 users 的结果集的默认行为是 SELECT */
            val fetchUser = conn.run(accounts.result).map(_.foreach {
                case Account(id, email, password, lasttime) =>
                    println(s"$email($id)/$password: Last log in time: $lasttime")
            })
            fetchUser.recover{
                case t => logger.error(t.getMessage, t)
            }
            /** 等待查询结果 */
            Await.result(fetchUser, timeout.value)


            /***********************************
              * 6) 查询
              *
              * 6-1) filter 相当于定义 Where, 返回 WrappingQuery
              * */
            val search = accounts.filter(_.id === 1L)           // 注意 “===” 而不是 "=="，类型也必须匹配

            /** 6-2-1) WrappingQuery 的 result 表示结果集（全部返回的字段），它的 map 函数将结果映射到一个输出 */
            val query1 = search.result.map(_.headOption.map(u => Account(u.id, u.email, Option(null), u.lasttime)))
            val user1 = conn.run(query1)                        // run 得到一个异步结果(Future)
            val result1 = Await.result(user1, Duration.Inf)     // 等待结束

            /** 6-2-2) 如果在 result 之前先调用 Query 的 map，相当于定义 SELECT 的参数，选择输出到 result 的字段 */
            val query2 = search.map(u => (u.id, u.email, u.lasttime))
                    .result.map(_.headOption.map {              // 然后对输出的字段再定义 map, 相对于 5-1) 这样可以减小输出的流量
                case (userId, email, lastLoginTime) => Account(userId, email, Option(null), lastLoginTime)
            })
            val result2 = Await.result(conn.run(query2), Duration.Inf)

            assert(result1 == result2)
        }
    }
}
