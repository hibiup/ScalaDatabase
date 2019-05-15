package com.hibiup.database

import io.getquill._

import concurrent.duration._

object Example_3_Quill_Test {
    // pagination parameters
    val page=2
    val limit = 2

    /**
      * Quill 可能需要一些定制的 Encoder/Decoder 来做字段类型的编解码。
      *
      * 例如下面的 "SELECT Query Example 1" 中，Quill 缺省使用 Java.time.LocalDateTime 来解释数据库的 Timestamp 字段，因此如
      * 果对应的 case class 不是使用该类型，那么就需要定制隐式编解码器。*/
    // case class 的名称就对应 Table 名，参数对应字段名。
    import java.sql.Timestamp
    final case class Users(id: Long, first_name: String, last_name: Option[String], register_date: Option[Timestamp] )
    final case class Accounts(id: Long, user_id: Long, email: String, password:Option[String], last_time:Option[Timestamp] )

    import java.time.LocalDateTime
    implicit val timestampEncoder = MappedEncoding[LocalDateTime, Timestamp]{ Timestamp.valueOf }
    implicit val timestampDecoder = MappedEncoding[Timestamp, LocalDateTime]{ _.toLocalDateTime }
}


class Example_3_Quill_Test extends Init {
    import Example_3_Quill_Test._

    val timeout = Duration.Inf

    "Quotation" should "" in {
        /** Quotation is a quoted block of code，是 Quill 的一个执行单元，在运行时被 Quill 转换成抽象语法树
          * Abstract Syntax Tree (AST). Quotation 可以被引用嵌套。 */

        /** 1) 首先要获得数据库的 context。Quill 提供了一个 Mirror Context，它并不产生真的数据库联接，只用于检查语法。例如： */
        val ctx = new MirrorContext(MirrorSqlDialect, Literal)

        /** 2）然后引入这个镜像的 context 下的 quote 来构建执行单元 */
        import ctx._
        val pi = quote(3.14159)

        /** 3）构件支持组建，如业务所需的 case class */
        case class Circle(radius: Float)

        val areas = quote {
            /**
              * 4-1）query 生成 Circle 的 Query statement
              *
              * 也就是生成 SELECT ... FROM 查询子句。
              * */
            query[Circle]
                    /**
                      * 4-2) 过滤结果.
                      *
                      * 这实际上是设置查询条件和过滤结果的地方，也就是设置 WHERE，GROUP，等查询条件。可用的查询子句参考：
                      * https://getquill.io/#quotation-queries
                      * */
                    .filter(c => c.radius > 1)
                    /**
                      * 4-3）结果映射
                      *
                      * 注意 pi 是之前定义的 quota，多个 quota 可以 monadic (Free)
                      * */
                    .map(c => pi * c.radius * c.radius)
        }

        /**
          * 5) 执行。
          *
          * Quill 通过 ctx.run 来同步执行查询（Quill 提供了 IO Monad 来实现异步执行，或可以自己利用 Future 来实现），以上输出：
          *
          *   QueryMirror(SELECT (3.14159 * c.radius) * c.radius FROM Circle c WHERE c.radius > 1,Row(List())
          *
          * 因为没有实际联接数据库，所以得到的结果 Row(List()) 是空的。
          * */
        println(ctx.run(areas))
    }

    "SELECT Query Example 1" should "" in {

        /**
          * 如果是对接一个真正的数据库，例如 H2，需要在配置文件 application.conf 中配置连接参数，然后获得 context:
          *
          * lazy val ctx = new H2JdbcContext(SnakeCase, "database.ctx")
          * ctx.close()
          *
          * */
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            val users = quote {
                query[Users].filter { u =>
                    u.first_name == "John"
                }
            }

            println(ctx.run(users))
        }
    }

    "SELECT Query with NONE-able field" should "" in {
        /**
          * 如果数据库字段是非 NO NULL 的，那么将返回 Some 或 None，比如 Accounts 表格中的 password 可以为空。
          * */
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            val users = quote {
                query[Accounts].filter { a =>
                    a.user_id == 1
                }
            }

            println(ctx.run(users))
        }
    }

    "Join SELECT" should "" in {
        /**
          * 可以有两种语法来实现关联查询：
          */
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

              /**
                * 1）Applicative Join:
                * */
            val joinQuery1 = quote {
                query[Users].join(query[Accounts]).on(_.id == _.user_id)
                        /**
                          * 实现 pagination：
                          *
                          *   val page = 2
                          *   val limit = 2
                          *
                          * 注意：不能直接使用（定义在 quota block 之外的）外部变量，例如定义在伴随 object 中。这是因为 quotation
                          * 同时是编译时和运行时变量，在编译时 Quill 使用类型细化(type refinement)来将 quotation AST 转化成注释，
                          * 而在运行时用 q.ast 来重新获取它，也就是说，Quill 在编译时就完成了SQL 语句的预编译以加快运行时的速度。因此
                          * 在运行时就不能再动态植入变量了。
                          *
                          * 也由于这个原因导致 quota 的类型在编译时丢失了，因此要避免显式指定 quota 的返回值类型。
                          *
                          * 如果一定要实现“动态”变量的植入，参考：https://getquill.io/#dynamic-queries
                          */
                        .drop((2-1)*2).take(2)
            }
            val result1 = ctx.run(joinQuery1)
            println(result1)

            /**
              * 2）Flat Join:
              * */
            val joinQuery2 = quote {
                (for {
                    u <- query[Users]
                    a <- query[Accounts] if (u.id == a.user_id)
                    // 或： a <- query[Accounts].join(_.user_id == u.id)
                } yield (u, a))
                        // pagination，不能使用外部变量，理由同上。
                        .drop((2-1)*2).take(2)
            }

            val result2 = ctx.run(joinQuery2)
            assert(result1 == result2)
        }
    }

    "Dynamic query" should "" in {
        /**
          * 正如上例所示，由于编译时优化，导致 quotation 不能直接使用外部变量，为了解决这个问题 Quill 使用了另外一个函数 dynamicQuery
          * 来实现动态数据适配. dynamicQuery 的 filter 是一个 Transformer, 接受一个 Quoted[T] 返回 Quoted[Boolean]
          * */
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            // 获得动态参数
            def users1(fname:Option[String]) = dynamicQuery[Users]
                    /** filterOpt 将 Option 类型数据植入 quotation. */
                    .filterOpt(fname){(u, fn) =>
                        quote(u.first_name == fn)
                    }
            val result1 = ctx.run(users1(Option("John")))
            println(result1)

            // 等价于
            def users2(fname:Option[String]) = quote {
                query[Users]
            }.dynamic.filterOpt(fname){(p,f_name) =>
                p.first_name == f_name
            }
            assert(ctx.run(users2(Option("John"))) === result1)

            /** 带有 Opt 后缀的方法 which apply the transformation only if the option is defined, 例如:
              * 动态分页 */
            def accounts(userId:Option[Long], page:Option[Int], limit:Option[Int]) = dynamicQuery[Users]
                    .join(query[Accounts]).on(_.id == _.user_id)         // Join
                    .filterOpt(userId)((r, id) => r._1.id == id)         // Where
                    .dropOpt(page).takeOpt(limit)                        // <- Opt 后缀方法
            val p = ctx.run(accounts(Option(1L), Option((page-1)*limit),Option(limit)))
            println(p)
            assert(p.size === 1)

            /** method with `If` suffix, for better chaining */
            def accountList(accountIds: Seq[Long]) = dynamicQuery[Accounts]
                    .filterIf(accountIds.nonEmpty) { account =>
                        quote(liftQuery(accountIds).contains(account.id))
                    }
            println(ctx.run(accountList(Seq(1,2,3))))
        }
    }

    "Execute RAW SQL" should "" in {
        /**
          * Quill 可以直接执行 SQL
          * */
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            val account = quote{ user_id: Long =>
                /** 由于静态优化的原因，不能插入非本地变量。但是可以使用本地动态变量（见下一个例子）*/
                infix"""SELECT * FROM ACCOUNTS WHERE user_id=$user_id""".as[Query[Accounts]]
            }

            // 不能传入变量作为 id
            println(s"Results: ${ctx.run(account(1L))}")
        }
    }

    "INSERT single record" should "" in {
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            /** 1) 定义一个隐式来获取 users.id 的数据库 meta，以告知 Quill 这个 id 是否具有 auto 或 default 等属性。 */
            implicit val userIdInsertMeta = insertMeta[Users](_.id)
            //implicit val userRegisterDateInsertMeta = insertMeta[Users](_.register_date)

            val user = quote(query[Users].insert(lift(Users(
                0, "Jane", Option(null), Option(null)
            )))
                    /** returning 返回 id */
                    .returning(_.id))
            val id = ctx.transaction {
                ctx.run(user)   // returning id
            }

            println(s"Result: $id")
            /**
              * 根据返回的 id 检查记录
              *
              * infix 可以执行 raw sql 语句，可以使用 #$ 插入本地动态变量（不支持非本地动态变量）
              * */
            println(ctx.run(infix"SELECT * FROM USERS WHERE id=#$id".as[Query[Users]]))
        }
    }

    "Quote combination for INSERT" should "" in {
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            /** !!! 貌似 Quill API 暂时不支持在一个 transaction 中支持 insert from selection，所以无法
              * map(_.id)，如果要实现这个功能，请考虑用 infix 直接执行 raw sql */
            val user = quote(query[Users].filter(_.first_name == "John")/*.map(_.id)*/.size)

            val id = ctx.run(quote(
                query[Accounts].insert(Accounts(0, user, "New", Option(null), Option(null))).returning(_.id)
            ))
            println(ctx.run(infix"SELECT * FROM ACCOUNTS WHERE id=#$id".as[Query[Accounts]]))
        }
    }

    "Batch INSERT" should "" in {
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            val insertQuery = quote {
                /** 1) 将 query lift 成 monad */
                liftQuery(
                    List(
                        Users(0, "Jane", Option(null), Option(null)),
                        Users(0, "Jack", Option(null), Option(null)),
                        Users(0, "Jessica", Option("Miller"), Option(null))
                    )
                )
                        /** 2）逐条插入并返回 id.
                          *
                          * 注意 Quill liftQuery 的 foreach 不是 monad 的带有副作用的 foreach，而且它可以有返回值。*/
                        .foreach(u => query[Users].insert(u).returning(_.id))
            }

            /** 3）执行并打印出结果 */
            ctx.transaction(
                ctx.run(insertQuery)
                        /** 将返回的 ID 然后 map 到新的查询(或插入) */
                        .map(id => ctx.run(infix"SELECT * FROM USERS WHERE id=#$id".as[Query[Users]])
                ).foreach(println)  // 打印出查询结果。
            )
        }
    }

    "UPDATE specific column by condition" should "" in {
        import org.mindrot.jbcrypt.BCrypt
        import java.sql.Timestamp
        import java.util.Date
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            def updatePassword(id:Option[Long], new_pass:String) = {
                val secret = BCrypt.hashpw(new_pass, BCrypt.gensalt())
                dynamicQuery[Accounts].filterOpt(id)((a,id) => a.id == id).update(set(
                    _.password, lift(Option(s"$secret"))
                ))/*.update(lift(
                    Accounts(1, 1,
                        "john", Option(BCrypt.hashpw("new_pass", BCrypt.gensalt())),
                        Option(new Timestamp((new Date).getTime)) )
                ))*/
            }

            val id = 1L
            ctx.run(updatePassword(Option(id), "new_pass"))
        }
    }
}
