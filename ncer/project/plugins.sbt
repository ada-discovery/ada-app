resolvers ++= Seq(
  // The Typesafe repository
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonata" at "https://oss.sonatype.org/content/repositories/releases/",
  "bintray-sbt-plugin-releases" at "http://dl.bintray.com/scalaz/releases"
)

addSbtPlugin("com.github.play2war" % "play2-war-plugin" % "1.4-beta1")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.9")

// web plugins

// RequireJS
//addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.0.0")

//addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "1.0.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-mocha" % "1.0.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("net.ground5hark.sbt" % "sbt-css-compress" % "0.1.3")

addSbtPlugin("net.ground5hark.sbt" % "sbt-closure" % "0.1.3")

// addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")
