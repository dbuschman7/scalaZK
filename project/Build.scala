import sbt._
import Keys._
import sbt.Keys._
import java.io.PrintWriter
import java.io.File
import sys.process.stringSeqToProcess
import sbtbuildinfo.Plugin._

import com.typesafe.sbt.packager.Keys._

object ApplicationBuild extends Build {

  val appName = "scalaZK"

  val branch = ""; // "git rev-parse --abbrev-ref HEAD".!!.trim
  val commit = ""; // "git rev-parse --short HEAD".!!.trim
  val buildTime = (new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")).format(new java.util.Date())

  val major = 0
  val minor = 1
  val patch = 0
  val appVersion = s"$major.$minor.$patch-$commit"
  val buildTag = scala.util.Properties.envOrElse("BUILD_TAG", "jenkins-Developer-0000.")

  val scalaVersion = scala.util.Properties.versionString.substring(8)

  println()
  println(s"App Name      => ${appName}")
  println(s"App Version   => ${appVersion}")
  println(s"Git Branch    => ${branch}")
  println(s"Git Commit    => ${commit}")
  println(s"Scala Version => ${scalaVersion}")
  println()

  val scalaBuildOptions = Seq("-unchecked", "-feature", "-language:reflectiveCalls", "-deprecation",
    "-language:implicitConversions", "-language:postfixOps", "-language:dynamics", "-language:higherKinds",
    "-language:existentials", "-language:experimental.macros", "-Xmax-classfile-name", "140")

  val appDependencies = Seq(
    // Logging Helper
    "org.slf4j" % "log4j-over-slf4j" % "1.7.10",

    // Logic Deps
    "org.apache.curator" % "curator-framework" % "2.7.1"
      excludeAll (ExclusionRule(organization = "log4j", name = "log4j")), // never in a play app
    "org.apache.curator" % "curator-recipes" % "2.7.1",

    //
    // Test Deps
    "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test",
    "junit" % "junit" % "4.8.1" % "test"
    )

  val repos = Seq(
    )

  val geoIpService = Project("scalaZK", file("."))
    .settings(scalacOptions ++= scalaBuildOptions)
    .settings(
      version := appVersion,
      libraryDependencies ++= appDependencies,
      sourceGenerators in Compile <+= buildInfo,
      resolvers ++= repos)
    .settings(buildInfoSettings: _*)
    .settings(
      buildInfoPackage := "me.lightspeed7.scalazk",
      buildInfoKeys ++= Seq[BuildInfoKey](
        "builtAt" -> {
          val dtf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
          dtf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
          dtf.format(new java.util.Date())
        },
        "builtAtMillis" -> { System.currentTimeMillis() },
        "appName" -> { appName },
        "branch" -> { branch },
        "commit" -> { commit },
        "jenkins" -> { buildTag },
        "major" -> { major },
        "minor" -> { minor },
        "patch" -> { patch }) //
        )
    
}



