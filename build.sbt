name := "ada-app"

ThisBuild / organization := "org.adada"
ThisBuild / scalaVersion := "2.11.12"
ThisBuild / version := "0.9.0-SNAPSHOT"
ThisBuild / isSnapshot := true

lazy val server = project

lazy val web = project
  .enablePlugins(PlayScala, SbtWeb)
  .aggregate(server)
  .dependsOn(server)

lazy val ncer = project
  .enablePlugins(PlayScala, SbtWeb)
  .aggregate(web)
  .dependsOn(web)

Test / fork := true

// POM settings for Sonatype
ThisBuild / homepage := Some(url("https://ada-discovery.github.io"))
ThisBuild / publishMavenStyle := true
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/ada-discovery/ada-server"), "scm:git@github.com:ada-discovery/ada-server.git"))
ThisBuild / developers := List(
  Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net")),
  Developer("sherzinger", "Sascha Herzinger", "sascha.herzinger@uni.lu", url("https://wwwfr.uni.lu/lcsb/people/sascha_herzinger"))
)
ThisBuild / publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)