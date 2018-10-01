import com.github.play2war.plugin._
import PlayKeys._
import com.typesafe.config._

name := "ada-web"

// load version from the app config
val conf = ConfigFactory.parseFile(new java.io.File("conf/application.conf")).resolve()
version := conf.getString("app.version")

scalaVersion := "2.11.12"

Play2WarPlugin.play2WarSettings

Play2WarKeys.servletVersion := "3.1"

lazy val dataaccess = project in file("data-access")

lazy val root = (project in file(".")).enablePlugins(PlayScala,SbtWeb) // .aggregate(dataaccess).dependsOn(dataaccess)

libraryDependencies ++= Seq(cache, ws, filters)

PlayKeys.devSettings := Seq("play.server.netty.maxInitialLineLength" -> "16384")

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "JCenter" at "http://jcenter.bintray.com/",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  "jitpack.io" at "https://jitpack.io",   // for t-SNE (Java impl)
  "Local Maven Repository" at "file:///"+ Path.userHome.absolutePath + "/.m2/repository",
  "bnd libs" at "https://peterbanda.net/maven2"
//  "Ivylocal" at "file:///"++Path.userHome.absolutePath+"/.ivy2/local/"
)

routesImport ++= Seq(
  "reactivemongo.bson.BSONObjectID",
  "controllers.PathBindables._",
  "controllers.QueryStringBinders._"
)

libraryDependencies ++= Seq(
  "org.ada" % "ada-dataaccess_2.11" % "0.7.0",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.6-play25" exclude("com.typesafe.play", "play_2.11") exclude("com.typesafe.play", "play-json_2.11") exclude("com.typesafe.play", "play-iteratees_2.11") exclude("com.typesafe.play", "play-server_2.11") exclude("com.typesafe.play", "play-netty-server_2.11"), // "0.11.14-play24", // "0.12.6-play24", // "0.11.14-play24", // "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-SNAPSHOT", "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24", "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-play24",
  "org.reactivemongo" %% "reactivemongo-akkastream" % "0.12.6",
  "com.evojam" %% "play-elastic4s" % "0.3.1" exclude("com.typesafe.play", "play_2.11") exclude("com.typesafe.play", "play-json_2.11"),
  "com.sksamuel.elastic4s" %% "elastic4s-streams" % "2.3.0",
  "org.in-cal" %% "incal-play" % "0.0.20",
  "com.typesafe.play" % "play-java-ws_2.11" % "2.5.6",
  "jp.t2v" %% "play2-auth" % "0.14.1",
  "org.webjars" % "bootstrap" % "3.3.5",
  "org.webjars" % "bootswatch-united" % "3.3.4+1",
  "org.webjars" % "typeaheadjs" % "0.11.1",
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "highcharts" % "5.0.14",  // "4.2.7",
//  "org.webjars.npm" % "fractalis" % "0.1.9",
  "org.webjars.bower" % "plotly.js" % "1.5.1",
  "org.webjars.bower" % "d3" % "3.5.16",
  "org.webjars.bower" % "Autolinker.js" % "0.25.0", // to convert links to a-href elements
//  "org.webjars.bower" % "vis" % "4.19.1" exclude("org.webjars" % "jquery"),  // to create graphs visualizations
  "org.webjars" % "visjs" % "4.21.0", // to create graphs visualizations
  "org.webjars" % "jquery-ui" % "1.11.1",
  "org.clapper" % "classutil_2.11" % "1.0.6",
  "org.scalaz" % "scalaz-core_2.11" % "7.2.1",
//  "org.apache.spark" %% "spark-core" % "2.3.1", // exclude("io.netty", "netty-all"),  // uses netty 4.1.17.Final incompatible with Play's netty 4.0.39.Final (which was upgraded to 4.0.56.Final)
//  "org.apache.spark" %% "spark-sql" % "2.3.1",
//  "org.apache.spark" %% "spark-mllib" % "2.3.1",
  "org.apache.spark" % "spark-core_2.11" % "2.2.0" exclude("com.fasterxml.jackson.core", "jackson-databind"), // exclude("asm", "asm")
  "org.apache.spark" % "spark-sql_2.11" % "2.2.0" exclude("com.fasterxml.jackson.core", "jackson-databind"), // exclude("asm", "asm") exclude("com.fasterxml.jackson.module", "jackson-module-scala_2.11")
  "org.apache.spark" % "spark-mllib_2.11" % "2.2.0" exclude("com.fasterxml.jackson.core", "jackson-databind"),
  //  "io.netty" % "netty-all" % "4.0.56.Final",
  "commons-net" % "commons-net" % "3.5",   // for ftp access
  "com.unboundid" % "unboundid-ldapsdk" % "2.3.8",
  "com.typesafe.play" %% "play-mailer" % "4.0.0",
  "org.apache.ignite" % "ignite-spark" % "1.6.0",
  "com.github.lejon.T-SNE-Java" % "tsne" % "v2.5.0"	,// t-SNE Java
  "org.scalanlp" %% "breeze" % "0.13.2",        // linear algebra and stuff
  "org.scalanlp" %% "breeze-natives" % "0.13.2",  // linear algebra and stuff (native)
//  "org.scalanlp" %% "breeze-viz" % "0.13.2",    // breeze visualization
  "com.banda.network" % "banda-network-business" % "0.5.6.1",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
).map(_.exclude("org.slf4j", "slf4j-log4j12" ))

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
pipelineStages in Assets := Seq(closure, cssCompress, digest, gzip)

excludeFilter in gzip := (excludeFilter in gzip).value || new SimpleFileFilter(file => new File(file.getAbsolutePath + ".gz").exists)

includeFilter in closure := (includeFilter in closure).value && new SimpleFileFilter(f => f.getPath.contains("javascripts"))

includeFilter in cssCompress := (includeFilter in cssCompress).value && new SimpleFileFilter(f => f.getPath.contains("stylesheets"))

//includeFilter in uglify := GlobFilter("javascripts/*.js")

