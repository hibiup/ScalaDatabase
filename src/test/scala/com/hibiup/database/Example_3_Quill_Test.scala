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
    import java.sql.Timestamp
    // case class 的名称就对应 Table 名，参数对应字段名。
    final case class Users(id: Long, first_name: String, last_name: Option[String], register_date: Timestamp)
    final case class Accounts(id: Long, user_id: Long, email: String, password:Option[String], last_time:Timestamp)

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
          * 正如上例所示，由于编译时优化，导致 quotation 不能直接使用外部变量，为了解决这个问题 Quill 使用了另外一个类 DynamicQuery
          * 来实现动态数据适配：
          * */
        withResource(new H2JdbcContext(SnakeCase, "database.ctx")) { ctx =>
            import ctx._

            dynamicQuery[Users]
            dynamicQuerySchema[Users]("users", alias(_.first_name, "fname"))

            def users(f_name:String) = quote {
                query[Users]
            }.dynamic.filter{p =>
                p.first_name == f_name
            }

            /*def users(f_name:String) = dynamicQuery[Users].filter{u =>
                quote(u.first_name == f_name)
            }*/
            println(ctx.run(users("John")))
        }
    }
}
