import Dependencies._
import BuildSettings._

name := "fun-cqrs"
organization in ThisBuild := "io.strongtyped"
scalaVersion in ThisBuild := "2.11.8"

crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.1")

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-Xlint:-infer-any", "-Xfatal-warnings")

// dependencies
lazy val root = Project(
    id   = "fun-cqrs",
    base = file("."),
    settings = Seq(
      publishArtifact := false
    )
  ) aggregate (
    funCqrs,
    funCqrsAkka,
    funCqrsTestKit,
    lotteryApp
  )

// Core ==========================================
lazy val funCqrs = Project(
  id       = "fun-cqrs-core",
  base     = file("modules/core"),
  settings = defaultSettings
).settings(libraryDependencies ++= mainDeps)
//================================================

// Akka integration ==============================
lazy val funCqrsAkka = Project(
  id       = "fun-cqrs-akka",
  base     = file("modules/akka"),
  settings = defaultSettings
).settings(libraryDependencies ++= mainDeps ++ akkaDeps)
  .dependsOn(funCqrs % "compile->compile;test->test")
//================================================

//Test kit =======================================
lazy val funCqrsTestKit = Project(
  id       = "fun-cqrs-test-kit",
  base     = file("modules/tests"),
  settings = defaultSettings
).settings(libraryDependencies ++= mainDeps ++ Seq(rxScala))
  .dependsOn(funCqrs % "compile->compile;test->test")
//================================================

// #####################################################
// #                     SAMPLES                      #
// #####################################################
lazy val lotteryApp = Project(
  id       = "sample-lottery",
  base     = file("samples/lottery"),
  settings = defaultSettings
).settings(libraryDependencies ++= sampleDeps)
  .settings(publishArtifact := false)
  .dependsOn(funCqrs)
  .dependsOn(funCqrsTestKit)
//  .dependsOn(funCqrsAkka)

addCommandAlias("runLotteryAkka", "sample-lottery/runMain lottery.app.MainAkka")
addCommandAlias("runLotteryInMemory", "sample-lottery/runMain lottery.app.MainInMemory")
