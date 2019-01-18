package com.hibiup.database

class Example_1_WithResource_Test extends Init {
    "withResource " should "closes connection automatically" in {
        withResource(dataSource.getConnection) {conn =>
            transactional(conn) { conn =>
                    /** 未知的情况是所有的 table name 和字段名都需要转变成大写 */
                val stmt = conn.prepareStatement(
                    """SELECT *
                      |FROM "USERS" AS "u"
                      |JOIN "ACCOUNTS" AS "a"
                      |ON "u"."ACCOUNT_ID" = "a"."ID" AND "a"."ID" = 1""".stripMargin)
                val resultSet = stmt.executeQuery
                while (resultSet.next()) {
                    println(
                        s"""Email: ${resultSet.getString("EMAIL")}
                        |First name: ${resultSet.getString("FIRST_NAME")}
                        |Last name: ${resultSet.getString("LAST_NAME")}""".stripMargin)
                }
            }
        }
    }
}
