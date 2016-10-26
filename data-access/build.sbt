organization := "ada"

name := "ada-dataaccess"

version := "0.3.1-alpha"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "JCenter" at "http://jcenter.bintray.com/",
  Resolver.mavenLocal
  //  "Ivylocal" at "file://home/peter/.ivy2/local/" // " + Path.userHome.absolutePath + "
)

libraryDependencies ++= Seq(
  "com.typesafe.play" % "play_2.11" % "2.4.6",
  "com.typesafe.play" % "play-json_2.11" % "2.4.6",
  "com.typesafe.play" % "play-iteratees_2.11" % "2.4.6",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.14-play24", // "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-SNAPSHOT", "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24", "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-play24",
  "org.apache.ignite" % "ignite-core" % "1.6.0",
  "org.apache.ignite" % "ignite-spring" % "1.6.0",
  "org.apache.ignite" % "ignite-indexing" % "1.6.0",
  "org.apache.ignite" % "ignite-scalar" % "1.6.0",
  "org.scalactic" %% "scalactic" % "3.0.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)