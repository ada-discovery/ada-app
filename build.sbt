import com.github.play2war.plugin._
import PlayKeys._

organization := "org.adada"

name := "ada-web-ncer"

version := "0.8.1.RC.9"

scalaVersion := "2.11.12"

//Play2WarPlugin.play2WarSettings
//Play2WarKeys.servletVersion := "3.1"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

libraryDependencies ++= Seq(cache, ws, filters)

PlayKeys.devSettings := Seq(
  "play.server.netty.maxInitialLineLength" -> "16384",
  "play.server.netty.transport" -> "jdk"
)

resolvers ++= Seq(
  Resolver.mavenLocal
)

routesImport ++= Seq(
  "reactivemongo.bson.BSONObjectID",
  "org.ada.web.controllers.PathBindables._",
  "org.ada.web.controllers.QueryStringBinders._",
  "org.ada.web.controllers.pdchallenge.QueryStringBinders._",
  "controllers.QueryStringBinders._"
)

val playVersion = "2.5.9"

libraryDependencies ++= Seq(
  "org.adada" %% "ada-web" % "0.8.1.RC.9",
  "org.adada" %% "ada-web" % "0.8.1.RC.9" classifier "assets",
  "org.adada" %% "ada-dream-pd-challenge" % "0.0.6",
  "org.in-cal" %% "incal-dl4j" % "0.2.2",   // DL4J
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.apache.pdfbox" % "pdfbox" % "2.0.1",
  "org.irods.jargon" % "jargon-core" % "4.3.0.2-RELEASE"  // iRODS stuff - installed locally from https://github.com/DICE-UNC/jargon/releases/tag/4.3.0.2-RELEASE
).map(_.exclude("org.slf4j", "slf4j-log4j12"))

// excludeDependencies += SbtExclusionRule(organization = "com.typesafe.akka") // "com.typesafe.akka" %% "akka-stream"

//libraryDependencies ++= Seq(
//  "ch.qos.logback" % "logback-classic" % "1.2.3"
//)

val jacksonVersion = "2.8.8"

// Because of Spark
dependencyOverrides ++= Set(
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,       
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion
  //  "io.netty" % "netty-transport-native-epoll" % "4.1.17.Final",
  //  "io.netty" % "netty-codec-http" % "4.1.17.Final", // 4.1.17.Final
  //  "io.netty" % "netty-handler" % "4.1.17.Final", // 4.0.56.Final
  //  "io.netty" % "netty-buffer" % "4.1.17.Final",
  //  "io.netty" % "netty-common" % "4.1.17.Final",
  //  "io.netty" % "netty-transport" % "4.1.17.Final"
)

// RequireJS
// pipelineStages := Seq(rjs)
// pipelineStages := Seq(rjs, uglify, digest, gzip)
// pipelineStages in Assets := Seq(uglify, digest,gzip)
// unmanagedResourceDirectories in Assets += baseDirectory.value / "images"

pipelineStages in Assets := Seq(closure, cssCompress, digest, gzip)

excludeFilter in gzip := (excludeFilter in gzip).value || new SimpleFileFilter(file => new File(file.getAbsolutePath + ".gz").exists)

includeFilter in digest := (includeFilter in digest).value && new SimpleFileFilter(f => f.getPath.contains("public/"))

includeFilter in closure := (includeFilter in closure).value && new SimpleFileFilter(f => f.getPath.contains("public/javascripts"))

includeFilter in cssCompress := (includeFilter in cssCompress).value && new SimpleFileFilter(f => f.getPath.contains("public/stylesheets"))

//includeFilter in uglify := GlobFilter("javascripts/*.js")

