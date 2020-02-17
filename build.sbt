name := "ada-app"

organization in ThisBuild := "org.adada"
scalaVersion in ThisBuild := "2.11.12"
version in ThisBuild := "0.9.0-SNAPSHOT"
isSnapshot in ThisBuild := true

lazy val server = project

lazy val web = project
  .enablePlugins(PlayScala, SbtWeb)
  .dependsOn(server)
  .aggregate(server)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val ncer = project
  .enablePlugins(PlayScala, SbtWeb)

fork in Test := true

// POM settings for Sonatype
homepage in ThisBuild := Some(url("https://ada-discovery.github.io"))
publishMavenStyle in ThisBuild := true
scmInfo in ThisBuild := Some(ScmInfo(url("https://github.com/ada-discovery/ada-server"), "scm:git@github.com:ada-discovery/ada-server.git"))
developers in ThisBuild := List(
  Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net")),
  Developer("sherzinger", "Sascha Herzinger", "sascha.herzinger@uni.lu", url("https://wwwfr.uni.lu/lcsb/people/sascha_herzinger"))
)
publishTo in ThisBuild := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)