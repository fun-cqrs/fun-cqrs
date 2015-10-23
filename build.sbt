//@formatter:off

import Dependencies._
import Settings._

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
  ) ++ commonSettings
) aggregate(funCqrs, funCqrsAkka, funPlayJsonSupport, shopApp, lotteryApp)


// Core ==========================================
lazy val funCqrs = Project(
  id = "fun-cqrs-core",
  base = file("modules/core"),
  settings = mainDeps ++ commonSettings
)
//================================================


// Akka integration ==============================
lazy val funCqrsAkka = Project(
  id = "fun-cqrs-akka",
  base = file("modules/akka"),
  settings = mainDeps ++ akkaDeps ++ commonSettings
) dependsOn (funCqrs % "compile->compile;test->test")
//================================================



// Play Json support ==============================
lazy val funPlayJsonSupport = Project(
  id = "fun-cqrs-play-json",
  base = file("modules/play-json"),
  settings = mainDeps ++ Seq(libraryDependencies += playJson) ++ commonSettings
) dependsOn (funCqrs % "compile->compile;test->test")
//================================================

// #####################################################
// #                     SAMPLES                      #
// #####################################################

// contains Play / Akka / Macwire sample
lazy val shopApp = Project(
  id = "sample-shop",
  base = file("samples/shop"),
  settings = Seq(
    publishArtifact := false,
    routesGenerator := InjectedRoutesGenerator
  ) ++ mainDeps ++ playSampleDeps ++ commonSettings
).enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(funCqrs % "compile->compile;test->test")
  .dependsOn(funCqrsAkka % "compile->compile;test->test")
//================================================

lazy val lotteryApp = Project(
  id = "sample-lottery",
  base = file("samples/lottery"),
  settings = Seq(
    publishArtifact := false,
    routesGenerator := InjectedRoutesGenerator
  ) ++ mainDeps ++ playSampleDeps ++ commonSettings
).enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(funCqrs % "compile->compile;test->test")
  .dependsOn(funCqrsAkka % "compile->compile;test->test")

addCommandAlias("runShopSample", "sample-shop/run")
addCommandAlias("runLotterySample", "sample-lottery/run")

addCommandAlias("format", ";scalariformFormat;test:scalariformFormat")


//@formatter:on