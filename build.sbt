scalaVersion := "2.12.8"

val derbyVersion = "10.14.2.0"
val slickVersion = "3.2.3"

lazy val ScalaDatabase = (project in file(".")).settings(
    name := "ScalaDatabase",
    version := "0.1",
    libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.0.5" % Test,
        "com.typesafe" % "config" % "1.3.3",
        // Database
        "org.apache.commons" % "commons-dbcp2" % "2.5.0",
        "com.h2database" % "h2" % "1.4.197",
        // Slick
        "com.typesafe.slick" %% "slick" % slickVersion,
        "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
)
