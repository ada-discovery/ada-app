organization := "org.ada"

name := "ada-dataaccess"

version := "0.7.0"

scalaVersion := "2.11.11"

isSnapshot := true

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "JCenter" at "http://jcenter.bintray.com/",
  Resolver.mavenLocal
  //  "Ivylocal" at "file://home/peter/.ivy2/local/" // " + Path.userHome.absolutePath + "
)

libraryDependencies ++= Seq(
  "com.typesafe.play" % "play_2.11" % "2.5.6",
  "com.typesafe.play" % "play-json_2.11" % "2.5.6",
  "com.typesafe.play" % "play-iteratees_2.11" % "2.5.6",
  "org.reactivemongo" %% "play2-reactivemongo" %  "0.12.6-play25" exclude("com.typesafe.play", "play_2.11") exclude("com.typesafe.play", "play-json_2.11") exclude("com.typesafe.play", "play-iteratees_2.11") exclude("com.typesafe.play", "play-server_2.11") exclude("com.typesafe.play", "play-netty-server_2.11"), // "0.11.14-play24", // "0.12.6-play24", // "0.11.14-play24", // "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-SNAPSHOT", "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24", "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-play24",
  "org.reactivemongo" %% "reactivemongo-akkastream" % "0.12.6",
  "com.evojam" %% "play-elastic4s" % "0.3.1" exclude("com.typesafe.play", "play_2.11") exclude("com.typesafe.play", "play-json_2.11"),
  "com.sksamuel.elastic4s" %% "elastic4s-streams" % "2.3.0",
  "org.apache.ignite" % "ignite-core" % "1.6.0",
  "org.apache.ignite" % "ignite-spring" % "1.6.0",
  "org.apache.ignite" % "ignite-indexing" % "1.6.0",
  "org.apache.ignite" % "ignite-scalar" % "1.6.0",
  "org.scalactic" %% "scalactic" % "3.0.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)