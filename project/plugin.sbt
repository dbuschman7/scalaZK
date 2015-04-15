resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += Classpaths.sbtPluginReleases

// Zip packaging
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.4")

// Build and version info in a scala case object
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.3.2")

// Code coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.4")

