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
) aggregate(
  funCqrs,
  funCqrsAkka,
  funPlayJsonSupport,
  funCqrsTestKit,
//  shopApp,
  lotteryApp
  )


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



// Play Json support ==============================
lazy val funPlayJsonSupport = Project(
  id = "fun-cqrs-play-json",
  base = file("modules/play-json"),
  settings = mainDeps ++ Seq(libraryDependencies += playJson)
) dependsOn (funCqrs % "compile->compile;test->test")
//================================================



// Play Json support ==============================
lazy val funCqrsTestKit = Project(
  id = "fun-cqrs-test-kit",
  base = file("modules/tests"),
  settings = mainDeps
) dependsOn (funCqrs % "compile->compile;test->test")
//================================================


// #####################################################
// #                     SAMPLES                      #
// #####################################################

lazy val lotteryApp = Project(
  id = "sample-lottery",
  base = file("samples/lottery"),
  settings = Seq(
    publishArtifact := false,
    routesGenerator := InjectedRoutesGenerator
  ) ++ sampleDeps
).dependsOn(funCqrs)
 .dependsOn(funCqrsTestKit)
 .dependsOn(funCqrsAkka)

addCommandAlias("runLottery", "sample-lottery/runMain lottery.app.Main")
addCommandAlias("runLotteryView", "sample-lottery/runMain lottery.app.MainView")

//@formatter:on