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
    final case class User(id: Long, first_name: Option[String], last_name: Option[String], register_date: Timestamp)
    final case class Account(id: Long, user_id: Long, email: String, password:Option[String], lasttime:Timestamp)

    /** 2）生成相应的表结构描述 */
    final class Users(tag: Tag) extends Table[User](tag, "USERS") {
        def id = column[Long]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column
        def first_name = column[Option[String]]("FIRST_NAME")
        def last_name = column[Option[String]]("LAST_NAME")
        def register_date = column[Timestamp]("REGISTER_DATE")
        // * 代表结果集字段,所有的 Table 类都需要定义一个 * 来映射字段
        def * = (id, first_name, last_name, register_date) <> (User.tupled, User.unapply)
    }
    /**
      * 3) 定义指令模版 DDL，这很重要，下面的指令，包括 filter, drop, sortBy 等都是基于这个模版来生成最终的指令
      * */
    val users = TableQuery[Users]

    final class Accounts(tag: Tag) extends Table[Account](tag, "ACCOUNTS") {
        def id = column[Long]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column
        def user_id = column[Long]("USER_ID")
        def email = column[String]("EMAIL")
        def password = column[Option[String]]("PASSWORD")
        def lasttime = column[Timestamp]("LAST_TIME",O.Default(new java.sql.Timestamp(new Date().getTime())))
        // * 代表结果集字段,所有的 Table 类都需要定义一个 * 来映射字段
        def * = (id, user_id, email, password, lasttime) <> (Account.tupled, Account.unapply)
        /** 3-1）定义外键，需要用到对应表的模板 */
        def user = foreignKey("USER_FK", user_id, users)(_.id)
    }
    val accounts = TableQuery[Accounts]
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
              * 5) 插入: INSERT(user) -> SELECT(user_id) -> INSERT account(user_id)
              * */

            /**
              * 5-1) 定义 INSERT 的数据集(插入两条新用户)
              * */
            val add_user = DBIO.seq(   // 得到 DBIOAction
                /** Account 的第一个参数只给 0，因为是自增长 ID。 Timestamp 设为 null，会取得缺省值。*/
                users += User(0, Option("First_1"), Option("Last_1"), null),
                users += User(0, Option("First_2"), None, null )
            )

            val createAccountByUser =
                /**
                  * 5-3) 执行 users 的结果集的默认行为是 SELECT * (注意！会返回全部用户，包括数据库中已存在的)
                  * */
                conn.run(users.result)
                    /**
                      * run 返回的是一个 Vector,包含了所有返回值, 然后再从 Vector 中 map 取出每个值
                      * */
                    .map(_.map {
                    case User(user_id, first_name, last_name, register_date) =>
                        println(s"($user_id):$first_name $last_name: register at: $register_date")
                        /**
                          * 5-4) 根据 SELECT 的结果，构建新的 INSERT
                          *
                          * 根据 Vector[ProfileAction] 获得下一步的 DBIOAction
                          * */
                        // 生成下一步用于 INSERT Account 的 Vector[ProfileAction]
                        accounts += Account(
                            0,
                            user_id,
                            s"first_$user_id.last_$user_id@test.com",
                            Option(BCrypt.hashpw("password", BCrypt.gensalt())),
                            null)
                })
                    // 根据 Vector[ProfileAction] 得到 DBIOAction
                    .map(DBIO.sequence(_))
                    /**
                      * 5-5) 执行 INSERT Account(将 DBIOAction 直接 map 到 conn.run)
                      * */
                    .map(conn.run(_))
                    .recover{
                        case t => logger.error(t.getMessage, t)
                    }

            /** 等待 "插入 -> 查询 -> 插入 -> 查询" 结果 */
            val result = Await.result(
                for{
                    /** 5-2) 执行由 users 导出的序列集的默认行为是 INSERT */
                    _ <- conn.run(add_user)          // 插入新用户
                    _ <- createAccountByUser         // 返回 user_id + 插入新 account
                    r <- conn.run(accounts.result)   // 返回全部 account
                }yield(r)
                , timeout.value
            )

            /** 打印结果*/
            result.foreach{
                case Account(id, user_id, email, password, last_time) =>
                    println(s"($id):$user_id $email/($password): last login time: $last_time")
            }


            /***********************************
              * 6) 查询
              *
              * 6-1) filter 相当于定义 Where, 返回 WrappingQuery
              * */
            //val hashed: String = BCrypt.hashpw("password", BCrypt.gensalt)
            val search = accounts.filter(_.email === "sample@gmail.com")   // 注意 “===” 而不是 "=="，类型也必须匹配

            /** 6-2-1) WrappingQuery 的 result 表示结果集（全部返回的字段），它的 map 函数将结果映射到一个输出 */
            val query1 = search.result.map(_.headOption.map(a => Account(a.id, a.user_id, a.email, Option(null), a.lasttime)))
            val account1 = conn.run(query1)                        // run 得到一个异步结果(Future)
            val result1 = Await.result(account1, Duration.Inf)     // 等待结束

            /** 6-2-2) 如果在 result 之前先调用 Query 的 map，相当于定义 SELECT 的参数，选择输出到 result 的字段 */
            val query2 = search.map(a => (a.id, a.user_id, a.email, a.lasttime))
                    .result.map(_.headOption.map {              // 然后对输出的字段再定义 map, 相对于 5-1) 这样可以减小输出的流量
                case (id, userId, email, lastLoginTime) => Account(id, userId, email, Option(null), lastLoginTime)
            })
            val result2 = Await.result(conn.run(query2), Duration.Inf)

            assert(result1 == result2)
        }
    }
}
