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
    final case class User(id: Long, first_name: String, last_name: Option[String], register_date: Timestamp)
    final case class Account(id: Long, user_id: Long, email: String, password:Option[String], lasttime:Timestamp)

    /** 2）生成相应的表结构描述 */
    final class Users(tag: Tag) extends Table[User](tag, "USERS") {
        def id = column[Long]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column
        def first_name = column[String]("FIRST_NAME")
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

    /** ***********************************
      * 单条插入
      * */
    "Slick insert one with returning id" should "" in {
        /**
          * 用 Slick 提供的 jdbc Database object 来连接数据库. 参数是 Scala 的 Config
          **/
        withResource(Database.forConfig("database.connection")) { conn =>
            /** 4-1) 定义插入后的返回动作 */
            val insertUserQuery = users returning users
                    .map(_.id) into((user, user_id) => user.copy(id=user_id))

            val insertAccountQuery = accounts returning accounts
                    .map(_.id) into((account, account_id) => account.copy(id=account_id))

            /** 4-2) 定义插入函数 */
            def add_user:User => DBIO[User] = user => insertUserQuery += user
            def add_account:(User, String, String) => DBIO[Account] = (user, email, password) => {
                insertAccountQuery += Account(
                    0,
                    user.id,
                    email,
                    Option(BCrypt.hashpw(password, BCrypt.gensalt())),
                    null)
            }

            /** 4-3）插入过程 */
            val createAction = for{
                user <- add_user(User(0, "First_2", Option("Last_2"), null))
                account <- add_account(user, s"first_${user.id}.last_${user.id}@test.com", "password")
            }yield(user, account)

            /** 检查 */
            val (new_user, new_account) = Await.result(conn.run(createAction.transactionally), timeout.value)
            println(new_user, new_account)
        }
    }


    /** ***********************************
      * 批量插入
      * */
    "Slick insert batch" should "" in {
        withResource(Database.forConfig("database.connection")) { conn =>
            /**
              * 4-1) 定义 INSERT user 后返回 user id 回填到 user 中
              **/
            val insertUserQuery = users returning users
                    .map(_.id) into ((user, user_id) => user.copy(id = user_id))

            /** 4-2) 定义 INSERT 的数据集(插入多条新用户) */
            val add_user = DBIO.seq( // 得到 DBIOAction
                // 每次插入一条的语法（Account 的第一个参数只给 0，因为是自增长 ID。 Timestamp 设为 null，会取得缺省值。）
                insertUserQuery += User(0, "First_1", Option("Last_1"), null),
                // 批量插入的语法
                insertUserQuery ++= Seq(
                    User(0, "First_2", None, null),
                    User(0, "First_3", Option("Last_3"), null)
                )
            )

            /** 4-3）执行批量插入 */
            Await.result(conn.run(add_user), timeout.value)
            /** TODO: 批量插入返回空值 Unit，因此要用其他途径插入 Accounts */

            /** 检查 */
            Await.result(conn.run(users.result).map(_.foreach {
                case User(user_id, first_name, last_name, register_date) =>
                    println(s"($user_id):$first_name $last_name: register at: $register_date")
            }), timeout.value)
        }
    }


    /** *********************************
      * 直接查询
      */
    "Directly SELECT record from a table" should "" in {
        withResource(Database.forConfig("database.connection")) { conn =>
              /**
              * 4-1) filter 相当于定义 Where, 返回 WrappingQuery
              * */
            val search_condition = accounts.filter(_.user_id === 2L)   // 注意 “===” 而不是 "=="，类型也必须匹配

            /** 4-2-1) WrappingQuery 的 result 表示结果集（全部返回的字段），它的 map 函数将结果映射到一个输出 */
            val query1 = search_condition.result.map(_.toList.map(a =>
                Account(a.id, a.user_id, a.email, Option(null), a.lasttime)
            ))
            val account1 = conn.run(query1)                        // run 得到一个异步结果(Future)
            val result1 = Await.result(account1, Duration.Inf)     // 等待结束

            /** 4-2-2) 如果在 result 之前先调用 Query 的 map，相当于定义 SELECT 的参数，选择输出到 result 的字段 */
            val query2 = search_condition.map(a => (a.id, a.user_id, a.email, a.lasttime))
                    .result.map(_.toList.map {              // 然后对输出的字段再定义 map, 相对于 5-1) 这样可以减小输出的流量
                case (id, userId, email, lastLoginTime) => Account(id, userId, email, Option(null), lastLoginTime)
            })
            val result2 = Await.result(conn.run(query2), Duration.Inf)

            assert(result1 == result2)
        }
    }

    /** *********************************
      * 关联查询
      */
    "Select record due to relative condition" should "" in {
        val limit = 2   // 每次取得多少条记录
        val page = 2    // 选取哪一页

        withResource(Database.forConfig("database.connection")) { conn =>
            /**
              * 4-1) 构造关系
              **/
            val joinTable = accounts.joinLeft(users).on(_.user_id === _.id)

            /** 4-2) 设置关联查询条件 */
            val query = for {
                (account, user) <- joinTable.filter(_._2.map {x =>
                    // 直接比较 native 类型
                    x.first_name === "John" &&
                            // 比较 Option
                            x.last_name.isDefined &&
                            x.last_name.getOrElse("") === "Smith"
                })
            } yield(account, user)

            /** 映射结果 */
            val result = query.drop((page-1)*limit).take(limit).result   // 选取第2页，因为每页限制2条，所以等于选取了第 3 条 account 记录
            result.statements.foreach(println)  // 打印 SQL

            val _query = result.map(_.toList.map {
                case (a, Some(u)) => Account(a.id, u.id, a.email, Option(null), a.lasttime)
            })

            /** 4-3) 执行 */
            val result1 = Await.result(conn.run(_query), timeout.value)
            println(result1)
            assert(result1(0).email == "sample@dummy.com")

            /** 4-2-Alternative) 以上两步也可以写成: */
            val _query2 = joinTable.filter{
                case (account, user) =>
                    user.map{ x =>
                        x.first_name === "John" &&
                                x.last_name.isDefined &&
                                x.last_name.getOrElse("") === "Smith"
                    }
            }.drop((page-1)*limit).take(limit).result.map(_.toList.map {
                case (a, Some(u)) => Account(a.id, u.id, a.email, Option(null), a.lasttime)
            })

            /** 检验 4-2-Alternative */
            assert(result1 == Await.result(conn.run(_query2), timeout.value))
        }
    }

    "Execution plain SQL SELECT" should "" in {
        import slick.jdbc.GetResult

        /** 为 “as” 定义隐式转换 */
        implicit val getUserResult = GetResult(r => User(r.<<, r.<<, r.<<, r.<<))
        implicit val getAccountResult = GetResult(r => Account(r.<<, r.<<, r.<<, r.<<, r.<<))

        val limit = 2   // 每次取得多少条记录
        val page = 2    // 选取哪一页
        withResource(Database.forConfig("database.connection")) { conn =>
            val query_users:Int => DBIO[Seq[(Account, User)]] = id =>
                sql"""SELECT *
                      | FROM "ACCOUNTS" as "A"
                      | LEFT OUTER JOIN "USERS" as "U"
                      | ON "A"."USER_ID" = "U"."ID"
                      | WHERE "A"."USER_ID" = $id
                      | LIMIT ${limit} offset ${(page-1)*limit}
                """.stripMargin.as[(Account, User)]     // Limit 和 offset 用于 pagination

            val result = Await.result(conn.run(query_users(1)), timeout.value)
            println(result)
        }
    }
}
