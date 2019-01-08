package com.hibiup.database

class Example_1_WithResource_Test extends Init {
    "withResource " should "closes connection automatically" in {
        withResource(dataSource.getConnection) {conn =>
            transactional(conn) { conn =>
                val stmt = conn.prepareStatement("SELECT * FROM users")
                val resultSet = stmt.executeQuery
                while (resultSet.next()) {
                    println(s"email: ${resultSet.getString("email")}")
                }
            }
        }
    }
}
