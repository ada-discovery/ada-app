import com.github.play2war.plugin._

// val conf = play.api.Configuration.load(new File("."))

organization := "org.ada"

name := "ada-web"

version := "0.6.0"

scalaVersion := "2.11.11"

Play2WarPlugin.play2WarSettings

Play2WarKeys.servletVersion := "3.1"

lazy val dataaccess = project in file("data-access")

lazy val root = (project in file(".")).enablePlugins(PlayScala,SbtWeb) // .aggregate(dataaccess).dependsOn(dataaccess)

libraryDependencies ++= Seq(cache, ws, filters)

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "JCenter" at "http://jcenter.bintray.com/",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  Resolver.mavenLocal
//  "Ivylocal" at "file://home/peter/.ivy2/local/" // " + Path.userHome.absolutePath + "
)

routesImport ++= Seq(
  "reactivemongo.bson.BSONObjectID",
  "models.PathBindables._",
  "models.QueryStringBinders._"
)

libraryDependencies ++= Seq(
  "org.ada" % "ada-dataaccess_2.11" % "0.6.0",
//  "nioc-bot" % "nioc-bot_2.11" % "0.2.3",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.6-play25" exclude("com.typesafe.play", "play_2.11") exclude("com.typesafe.play", "play-json_2.11") exclude("com.typesafe.play", "play-iteratees_2.11") exclude("com.typesafe.play", "play-server_2.11") exclude("com.typesafe.play", "play-netty-server_2.11"), // "0.11.14-play24", // "0.12.6-play24", // "0.11.14-play24", // "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-SNAPSHOT", "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24", "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-play24",
  "org.reactivemongo" %% "reactivemongo-akkastream" % "0.12.6",
  "com.evojam" %% "play-elastic4s" % "0.3.1" exclude("com.typesafe.play", "play_2.11") exclude("com.typesafe.play", "play-json_2.11"),
  "com.sksamuel.elastic4s" %% "elastic4s-streams" % "2.3.0",
  "org.webjars" %% "webjars-play" % "2.5.0",
  "org.webjars" % "bootstrap" % "3.3.5",
  "org.webjars" % "bootswatch-united" % "3.3.4+1",
  "org.webjars" % "typeaheadjs" % "0.11.1",
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "highcharts" % "4.2.7",
//  "org.webjars.npm" % "fractalis" % "0.1.9",
  "org.webjars.bower" % "plotly.js" % "1.5.1",
  "org.webjars.bower" % "d3" % "3.5.16",
  "org.webjars.bower" % "Autolinker.js" % "0.25.0", // to convert links to a-href elements
  "org.webjars" % "jquery-ui" % "1.11.1",
  "net.codingwell" %% "scala-guice" % "4.0.1",
//  "com.google.inject.extensions" % "guice-spring" % "4.0", // so we can initialize spring container in Guice
  "org.clapper" % "classutil_2.11" % "1.0.6",
  "org.scalaz" % "scalaz-core_2.11" % "7.2.1",
  "org.apache.spark" % "spark-core_2.11" % "2.2.0" exclude("com.fasterxml.jackson.core", "jackson-databind"), // exclude("asm", "asm")
  "org.apache.spark" % "spark-sql_2.11" % "2.2.0" exclude("com.fasterxml.jackson.core", "jackson-databind"), // exclude("asm", "asm") exclude("com.fasterxml.jackson.module", "jackson-module-scala_2.11")
  "org.apache.spark" % "spark-mllib_2.11" % "2.2.0" exclude("com.fasterxml.jackson.core", "jackson-databind"),
//    "com.stratio.datasource" % "spark-mongodb_2.11" % "0.11.2", // exclude("asm", "asm")
  "commons-net" % "commons-net" % "3.5",   // for ftp access
  "com.typesafe.play" % "play-java-ws_2.11" % "2.5.6",
  "be.objectify" % "deadbolt-scala_2.11" % "2.5.1",
  "jp.t2v" %% "play2-auth" % "0.14.1",
  "com.unboundid" % "unboundid-ldapsdk" % "2.3.8",
  "com.typesafe.play" %% "play-mailer" % "4.0.0",
  "org.apache.ignite" % "ignite-spark" % "1.6.0",
  "com.banda" % "incal" % "0.1.6" exclude("org.springframework", "spring-context") exclude("org.springframework", "spring-test") exclude("org.springframework", "spring-web") exclude("org.springframework", "spring-webmvc")
//  "org.scalatra.scalate" %% "scalate-core" % "1.8.0"
).map(_.exclude("org.slf4j", "slf4j-log4j12" ))

//  "com.typesafe.play" % "play-logback_2.11" % "2.5.1"
// Following overrides are needed since Spark 1.6 uses jackson-databind 2.4.4
// Note that deadbolt's dependency jackson-datatype-jsr310 has to be overriden as well because of transitivity
// dependencyOverrides ++= Set(
//  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4",
//  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.4.4",
// "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.4.4"
// )

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.7.6"
)

// TODO: could be removed in Play 2.5 (since it's considered by default)
routesGenerator := InjectedRoutesGenerator

// RequireJS
// pipelineStages := Seq(rjs)
// pipelineStages := Seq(rjs, uglify, digest, gzip)
// pipelineStages in Assets := Seq(uglify, digest,gzip)
pipelineStages in Assets := Seq(closure, cssCompress, digest, gzip)

excludeFilter in gzip := (excludeFilter in gzip).value || new SimpleFileFilter(file => new File(file.getAbsolutePath + ".gz").exists)

includeFilter in closure := (includeFilter in closure).value && new SimpleFileFilter(f => f.getPath.contains("javascripts"))

includeFilter in cssCompress := (includeFilter in cssCompress).value && new SimpleFileFilter(f => f.getPath.contains("stylesheets"))

//includeFilter in uglify := GlobFilter("javascripts/*.js")

