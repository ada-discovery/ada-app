import com.github.play2war.plugin._
import PlayKeys._
import com.typesafe.config._

organization := "org.adada"

name := "ada-web-ncer"

// load version from the app config
val conf = ConfigFactory.parseFile(new java.io.File("conf/application.conf")).resolve()
version := conf.getString("app.version")

scalaVersion := "2.11.12"

Play2WarPlugin.play2WarSettings

Play2WarKeys.servletVersion := "3.1"

lazy val root = (project in file(".")).enablePlugins(PlayScala,SbtWeb)

libraryDependencies ++= Seq(cache, ws, filters)

PlayKeys.devSettings := Seq("play.server.netty.maxInitialLineLength" -> "16384")

resolvers ++= Seq(
//  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
//  "JCenter" at "http://jcenter.bintray.com/",
//  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
//  "jitpack.io" at "https://jitpack.io",   // for t-SNE (Java impl)
//  "Local Maven Repository" at "file:///"+ Path.userHome.absolutePath + "/.m2/repository",
  "bnd libs" at "https://peterbanda.net/maven2"
//  "Ivylocal" at "file:///"++Path.userHome.absolutePath+"/.ivy2/local/"
)

routesImport ++= Seq(
  "reactivemongo.bson.BSONObjectID",
  "org.ada.web.controllers.PathBindables._",
  "org.ada.web.controllers.QueryStringBinders._",
  "org.ada.web.controllers.pdchallenge.QueryStringBinders._"
)

val playVersion = "2.5.9"

libraryDependencies ++= Seq(
  "org.adada" %% "ada-web" % "0.7.3.RC.7.SNAPSHOT.3",
  "org.adada" %% "ada-dream-pd-challenge" % "0.0.2",
  "org.deeplearning4j" %% "scalnet" % "1.0.0-beta3",
// "org.deeplearning4j" % "deeplearning4j-core" % "1.0.0-beta3",  
  "org.nd4j" % "nd4j-native-platform" % "1.0.0-beta3",
  "org.deeplearning4j" % "deeplearning4j-nlp" % "1.0.0-beta3",
//  "org.deeplearning4j" %% "deeplearning4j-ui" % "1.0.0-beta3",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
).map(_.exclude("org.slf4j", "slf4j-log4j12"))

// excludeDependencies += SbtExclusionRule(organization = "com.typesafe.akka") // "com.typesafe.akka" %% "akka-stream"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.7.6"
//  "io.netty" % "netty-transport-native-epoll" % "4.1.17.Final",
//  "io.netty" % "netty-codec-http" % "4.1.17.Final", // 4.1.17.Final
//  "io.netty" % "netty-handler" % "4.1.17.Final", // 4.0.56.Final
//  "io.netty" % "netty-buffer" % "4.1.17.Final",
//  "io.netty" % "netty-common" % "4.1.17.Final",
//  "io.netty" % "netty-transport" % "4.1.17.Final"
)

// TODO: could be removed in Play 2.5 (since it's considered by default)
routesGenerator := InjectedRoutesGenerator

// RequireJS
// pipelineStages := Seq(rjs)
// pipelineStages := Seq(rjs, uglify, digest, gzip)
// pipelineStages in Assets := Seq(uglify, digest,gzip)
// unmanagedResourceDirectories in Assets += baseDirectory.value / "images"

pipelineStages in Assets := Seq(closure, cssCompress, digest, gzip)

excludeFilter in gzip := (excludeFilter in gzip).value || new SimpleFileFilter(file => new File(file.getAbsolutePath + ".gz").exists)

includeFilter in closure := (includeFilter in closure).value && new SimpleFileFilter(f => f.getPath.contains("javascripts"))

includeFilter in cssCompress := (includeFilter in cssCompress).value && new SimpleFileFilter(f => f.getPath.contains("stylesheets"))

//includeFilter in uglify := GlobFilter("javascripts/*.js")

