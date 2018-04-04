import Dependencies._
import BuildSettings._
import sbt._

name := "fun-cqrs"
organization in ThisBuild := "org.funcqrs"
scalaVersion in ThisBuild := "2.11.11"

crossScalaVersions in ThisBuild := Seq("2.11.11", "2.12.3")

val snapshotSuffix = "-SNAPSHOT"
isSnapshot := version.value.endsWith(snapshotSuffix)

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
  funCqrsTestKit,
  raffleApp
)


// Core ==========================================
lazy val funCqrs = Project(
  id = "fun-cqrs-core",
  base = file("modules/core"),
  settings = defaultSettings
).settings(libraryDependencies ++= mainDeps)
//================================================

// Akka integration ==============================
lazy val funCqrsAkka = Project(
  id = "fun-cqrs-akka",
  base = file("modules/akka"),
  settings = defaultSettings
).settings(libraryDependencies ++= mainDeps ++ akkaDeps)
  .dependsOn(funCqrs % "compile->compile;test->test")
//================================================

//Test kit =======================================
lazy val funCqrsTestKit = Project(
  id = "fun-cqrs-test-kit",
  base = file("modules/tests"),
  settings = defaultSettings
).settings(libraryDependencies ++= mainDeps ++ Seq(rxScala, reactiveStreamAdapter))
  .dependsOn(funCqrs % "compile->compile;test->test")
//================================================

// #####################################################
// #                     SAMPLES                       #
// #####################################################
lazy val raffleApp = Project(
  id = "sample-raffle",
  base = file("samples/raffle"),
  settings = defaultSettings
).settings(libraryDependencies ++= sampleDeps)
  .settings(publishArtifact := false)
  .dependsOn(funCqrs)
  .dependsOn(funCqrsTestKit)
  .dependsOn(funCqrsAkka)

addCommandAlias("runRaffleAkka", "sample-raffle/runMain raffle.app.MainAkka")
addCommandAlias("runRaffleInMemory", "sample-raffle/runMain raffle.app.MainInMemory")


// #####################################################
// #                  PUBLISH SETTINGS                 #
// #####################################################

publishMavenStyle := true

pomIncludeRepository := { _ =>
  false
}

credentials ++= publishingCredentials

pomExtra in ThisBuild := pomInfo

pgpPassphrase := Option(System.getenv().get("PGP_PASSPHRASE")).map(_.toCharArray)

pgpSecretRing := file("local.secring.gpg")

pgpPublicRing := file("local.pubring.gpg")

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

lazy val publishingCredentials = (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Seq(Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password))).getOrElse(Seq())

lazy val pomInfo = <url>https://github.com/fun-cqrs/fun-cqrs</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:fun-cqrs/fun-cqrs.git</url>
      <connection>scm:git:git@github.com:fun-cqrs/fun-cqrs.git</connection>
    </scm>
    <developers>
      <developer>
        <id>rcavalcanti</id>
        <name>Renato Cavalcanti</name>
        <url>https://github.com/renatocaval</url>
      </developer>
    </developers>
