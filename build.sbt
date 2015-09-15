//@formatter:off

import Dependencies._

name := "fun-cqrs"
organization in ThisBuild := "io.strongtyped"
scalaVersion in ThisBuild := "2.11.7"

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-Xlint:-infer-any", "-Xfatal-warnings")
crossScalaVersions := Seq("2.10.5", "2.11.7")

// dependencies
lazy val root = Project(
  id = "fun-cqrs",
  base = file("."),
  settings = Seq(
    publishArtifact := false
  )
) aggregate(funCqrs, funCqrsAkka, invoiceSample, playApp)


// Core ==========================================
lazy val funCqrs = Project(
  id = "fun-cqrs-core",
  base = file("modules/core"),
  settings = mainDeps
)
//================================================



// Akka integration ==============================
lazy val funCqrsAkka = Project(
  id = "fun-cqrs-core",
  base = file("modules/akka"),
  settings = mainDeps ++ akkaDeps
) dependsOn (funCqrs % "compile->compile;test->test")
//================================================




// #####################################################
// #                     SAMPLES                      #
// #####################################################

// contains Play / Akka / Macwire sample
lazy val playApp = Project(
  id = "fun-cqrs-akka-play-sample",
  base = file("modules/samples/cqrs-akka-play"),
   settings = Seq(
    publishArtifact := false,
    routesGenerator := InjectedRoutesGenerator
  ) ++ mainDeps ++ akkaDeps ++ macwireDeps
).enablePlugins(PlayScala)
 .disablePlugins(PlayLayoutPlugin)
 .dependsOn (funCqrs % "compile->compile;test->test")
 .dependsOn(funCqrsAkka % "compile->compile;test->test")
//================================================



// contains examples used on the docs, not intended to be released
lazy val invoiceSample = Project(
  id = "fun-cqrs-invoices-sample",
  base = file("modules/samples/invoices"),
  settings = Seq(
    publishArtifact := false
  ) ++ mainDeps 
) dependsOn (funCqrs % "compile->compile;test->test")
//================================================


//@formatter:on