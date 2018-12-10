package com.hibiup.database

import java.io.{FileInputStream, InputStream}
import java.sql.Connection
import java.util.Scanner

import org.scalatest.{BeforeAndAfter, FlatSpec}

class hibiup extends FlatSpec with BeforeAndAfter{
    before {
        withResource(new FileInputStream("src/test/resources/init.sql")) { file =>
            withResource(dataSource.getConnection) { conn =>
                executeScript(conn, file)
            }
        }
    }

    def executeScript(conn: Connection, in: InputStream): Unit = {
        val s = new Scanner(in)
        s.useDelimiter("/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*|;")

        withResource(conn.createStatement()) { st =>
            while ( {
                s.hasNext
            }) {
                val line = s.next.trim
                if (!line.isEmpty) {
                    st.execute(line)
                }
            }
        }
    }

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
