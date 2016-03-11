

import com.github.play2war.plugin._

// val conf = play.api.Configuration.load(new File("."))

name := "ncer-pd"

version := "0.2.0"

Play2WarPlugin.play2WarSettings

Play2WarKeys.servletVersion := "3.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(cache, ws)

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "JCenter" at "http://jcenter.bintray.com/"
)

routesImport ++= Seq("reactivemongo.bson.BSONObjectID", "models.PathBindables._", "util.FilterSpec.FilterSpecQueryStringBinder")

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24",
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "3.3.5",
  "org.webjars" % "bootswatch-united" % "3.3.4+1",
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "highcharts" % "4.2.2",
  "org.webjars.bower" % "plotly.js" % "1.5.1",
  "org.webjars.bower" % "d3" % "3.5.16",
  "org.webjars" % "jquery-ui" % "1.11.1",
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "org.clapper" % "classutil_2.11" % "1.0.6",
  "org.scalaz" % "scalaz-core_2.11" % "7.2.1",
  "org.apache.spark" % "spark-core_2.11" % "1.6.0" exclude("asm", "asm"),
  "org.apache.spark" % "spark-sql_2.11" % "1.6.0" exclude("asm", "asm"),
  "com.stratio.datasource" % "spark-mongodb_2.11" % "0.11.0" exclude("asm", "asm"),
  "com.typesafe.play" % "play-java-ws_2.11" % "2.4.6",
  "be.objectify" % "deadbolt-scala_2.11" % "2.4.3",
  "be.objectify" % "deadbolt-java_2.11" % "2.4.3",
  "jp.t2v" %% "play2-auth"        % "0.14.1",
  "jp.t2v" %% "play2-auth-social" % "0.14.1",
  "jp.t2v" %% "play2-auth-test"   % "0.14.1" % "test",
  "com.unboundid" % "unboundid-ldapsdk" % "2.3.8"
)

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.4"
)

routesGenerator := InjectedRoutesGenerator

// RequireJS
// pipelineStages := Seq(rjs)

