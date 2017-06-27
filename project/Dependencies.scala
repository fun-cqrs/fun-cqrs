import sbt.Keys._
import sbt._

object Dependencies {

  //------------------------------------------------------------------------------------------------------------
  // io.strongtyped.funcqrs core
  val scalaLogging    = "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0"
  val scalaTest       = "org.scalatest"              %% "scalatest"       % "3.0.0" % "test"
  val logback         = "ch.qos.logback"             % "logback-classic"  % "1.1.9"
  val reactiveStreams = "org.reactivestreams"        % "reactive-streams" % "1.0.0"

  val rxScala               = "io.reactivex" %% "rxscala"                % "0.26.5"
  val reactiveStreamAdapter = "io.reactivex" % "rxjava-reactive-streams" % "1.2.1"

  val mainDeps = Seq(scalaLogging, scalaTest, logback, reactiveStreams)
  //------------------------------------------------------------------------------------------------------------

  //------------------------------------------------------------------------------------------------------------
  // Akka Module
  val akkaDeps = {
    val akkaVersion = "2.4.17"

    Seq(
      "com.typesafe.akka" %% "akka-actor"       % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"       % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"      % akkaVersion,
//      "com.typesafe.akka" %% "akka-remote"      % akkaVersion,
      // experimental
      "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
      // test scope
      "com.typesafe.akka"   %% "akka-testkit"              % akkaVersion % "test",
      "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.4.17.3"  % "test"
    )
  }
  //------------------------------------------------------------------------------------------------------------

  //------------------------------------------------------------------------------------------------------------
  val levelDb    = "org.iq80.leveldb"          % "leveldb"        % "0.7"
  val levelDbJNI = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

  val sampleDeps = Seq(levelDb, levelDbJNI) ++ mainDeps ++ akkaDeps
}
