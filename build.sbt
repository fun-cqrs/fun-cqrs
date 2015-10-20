//@formatter:off

import Dependencies._

name := "fun-cqrs"
organization in ThisBuild := "io.strongtyped"
scalaVersion in ThisBuild := "2.11.7"

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-Xlint:-infer-any", "-Xfatal-warnings")

// dependencies
lazy val root = Project(
  id = "fun-cqrs",
  base = file("."),
  settings = Seq(
    publishArtifact := false
  )
) aggregate(funCqrs, funCqrsAkka, funCqrsLevelDb, playApp)


// Core ==========================================
lazy val funCqrs = Project(
  id = "fun-cqrs-core",
  base = file("modules/core"),
  settings = mainDeps
)
//================================================

// Akka integration ==============================
lazy val funCqrsAkka = Project(
  id = "fun-cqrs-akka",
  base = file("modules/akka"),
  settings = mainDeps ++ akkaDeps
) dependsOn (funCqrs % "compile->compile;test->test")
//================================================

// LevelDB integration ===========================
lazy val funCqrsLevelDb = Project(
  id = "fun-cqrs-leveldb",
  base = file("modules/leveldb"),
  settings = levelDbDeps
).dependsOn(funCqrs % "compile->compile;test->test")
  .dependsOn(funCqrsAkka % "compile->compile;test->test")
//================================================

// #####################################################
// #                     SAMPLES                      #
// #####################################################

// contains Play / Akka / Macwire sample
lazy val playApp = Project(
  id = "fun-cqrs-akka-play-sample",
  base = file("samples/cqrs-akka-play"),
  settings = Seq(
    publishArtifact := false,
    routesGenerator := InjectedRoutesGenerator
  ) ++ playSampleDeps
).enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(funCqrs % "compile->compile;test->test")
  .dependsOn(funCqrsAkka % "compile->compile;test->test")
  .dependsOn(funCqrsLevelDb % "compile->compile;test->test")
//================================================

addCommandAlias("runPlaySample", "fun-cqrs-akka-play-sample/run")


//@formatter:on