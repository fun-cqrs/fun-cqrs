import Dependencies._
import BuildSettings._

name := "fun-cqrs"
organization in ThisBuild := "io.strongtyped"
scalaVersion in ThisBuild := "2.11.11"

crossScalaVersions in ThisBuild := Seq("2.11.11", "2.12.3")

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}

// replaces dynver + by -
version in ThisBuild ~= (_.replace('+', '-'))

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
  raffleApp
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
).settings(libraryDependencies ++= mainDeps ++ Seq(rxScala, reactiveStreamAdapter))
  .dependsOn(funCqrs % "compile->compile;test->test")
//================================================

// #####################################################
// #                     SAMPLES                      #
// #####################################################
lazy val raffleApp = Project(
  id       = "sample-raffle",
  base     = file("samples/raffle"),
  settings = defaultSettings
).settings(libraryDependencies ++= sampleDeps)
  .settings(publishArtifact := false)
  .dependsOn(funCqrs)
  .dependsOn(funCqrsTestKit)
  .dependsOn(funCqrsAkka)

addCommandAlias("runRaffleAkka", "sample-raffle/runMain raffle.app.MainAkka")
addCommandAlias("runRaffleInMemory", "sample-raffle/runMain raffle.app.MainInMemory")


publishTo in ThisBuild := version { (v: String) =>
  val nexus = "https://collab.mow.vlaanderen.be/artifacts/repository/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("collab snapshots" at nexus + "maven-snapshots")
  else
    Some("collab releases" at nexus + "maven-releases")
}.value