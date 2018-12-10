name := "ScalaDatabase"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    "org.apache.derby" % "derby" % "10.14.2.0",
    "org.apache.derby" % "derbyclient" % "10.14.2.0",
    "com.typesafe" % "config" % "1.3.3",
    "org.apache.commons" % "commons-dbcp2" % "2.5.0"
)