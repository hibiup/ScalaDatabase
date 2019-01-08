package com.hibiup.database

import java.sql.Timestamp
import java.util.Date

import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.slf4j.LoggerFactory
import slick.lifted.Tag

import scala.concurrent.Await

// Derby database JDBC Driver
import slick.jdbc.DerbyProfile.api._

object Example_2_Slick_Test{
    private val logger = LoggerFactory.getLogger(this.getClass)

    /** 1）定义表字段模版 */
    final case class User(id: Long, email: String, password:Option[String], lasttime:Timestamp)

    /** 2）生成相应的表结构描述 */
    class Users(tag: Tag) extends Table[User](tag, "users") {
        def id = column[Long]("ID", O.PrimaryKey) // This is the primary key column
        def email = column[String]("email")
        def password = column[Option[String]]("password")
        def lasttime = column[Timestamp]("lasttime",O.Default(new java.sql.Timestamp(new Date().getTime())))
        // * 代表结果集字段,所有的 Table 类都需要定义一个 * 来映射字段
        def * = (id, email, password, lasttime) <> (User.tupled, User.unapply)
    }
}

class Example_2_Slick_Test extends Init{
    import Example_2_Slick_Test._
    import scala.concurrent.duration._

    val timeout = Timeout(10 seconds)

    "Slick" should "" in {
        val users = TableQuery[Users]

        /** 3）用 Slick 提供的 jdbc Database object 来连接数据库. 参数是 Scala 的 Config */
        //val conn = Database.forConfig("database.connection")
        //try {
        withResource(Database.forConfig("database.connection")){ conn =>
            val add_user = DBIO.seq(
                users += User(101, "user2", Option("99 Market Street"), new java.sql.Timestamp(new Date().getTime())),
                users += User(102, "user3", None, new java.sql.Timestamp(new Date().getTime()) ),
            )

            conn.run(add_user)

            val fetchUser = conn.run(users.result).map(_.foreach {
                case User(id, email, password, lasttime) =>
                    println(s"$email($id)/$password: Last log in time: $lasttime")
            })
            fetchUser.recover{
                case t => logger.error(t.getMessage, t)
            }

            Await.result(fetchUser, timeout.value)
        } //finally conn.close
    }
}
