package com.hibiup

import java.io.{FileInputStream, InputStream}
import java.sql.Connection
import java.util.Scanner

import com.typesafe.config.ConfigFactory
import org.apache.commons.dbcp2.BasicDataSource
import org.scalatest.{BeforeAndAfter, FlatSpec}

package object database {
    lazy val config = ConfigFactory.parseResources("config.conf");

    lazy val dataSource = {
        val conf = config.getConfig("database.connection")
        val dataSource:BasicDataSource = new BasicDataSource()
        dataSource.setDriverClassName(conf.getString("driver"))
        dataSource.setUrl(conf.getString("url"))
        dataSource.setUsername(conf.getString("username"))
        dataSource.setPassword(conf.getString("password"))
        dataSource.setPoolPreparedStatements(true)
        dataSource.setValidationQuery(conf.getString("pool.validation"))
        dataSource.setMaxOpenPreparedStatements(conf.getInt("pool.maxOpenPreparedStatements"))
        dataSource.setLifo(conf.getBoolean("pool.lifo"))
        dataSource.setMaxTotal(conf.getInt("pool.maxTotal"))
        dataSource.setInitialSize(conf.getInt("pool.initialSize"))
        dataSource
    }

    def withResource[T <: {def close()}, V](r: T)(f: T => V): V = {
        try {
            println(s"With resource ${r}")
            f(r)
        }
        catch {
            case t: Throwable => t.printStackTrace()
                throw t
        } finally {
            r.close()
            println(s"Resource ${r} is closed")
        }
    }

    def transactional[T <: Connection, V](tr:T)(f:(T) => V) = {
        try {
            f(tr)
        }
        catch {
            case t:Throwable =>
                tr.rollback()
                println("Transaction is rollback")
                throw t
        }
        finally {
            tr.commit()
            println("Transaction is committed")
        }
    }

    class Init extends FlatSpec with BeforeAndAfter {
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
    }
}
