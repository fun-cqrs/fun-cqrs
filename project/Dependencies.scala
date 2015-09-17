import sbt.Keys._
import sbt._

//@formatter:off
object Dependencies {

  val macwireVersion    =   "1.0.7"
  val macwireMacros     =   "com.softwaremill.macwire"    %% "macros"                           % macwireVersion
  val macwireRuntime    =   "com.softwaremill.macwire"    %% "runtime"                          % macwireVersion


  val akkaVersion           =   "2.4.0-RC3" //"2.3.13"
  val akkaPersistence       =   "com.typesafe.akka"           %%  "akka-persistence"                    % akkaVersion
  val akkaPersistenceQuery  =   "com.typesafe.akka"           %%  "akka-persistence-query-experimental" % akkaVersion
  val akkaSlf4j             =   "com.typesafe.akka"           %%  "akka-slf4j"                          % akkaVersion
  val akkaStreams           =   "com.typesafe.akka"           %%  "akka-stream-experimental"            % "1.0"

  val levelDb               =   "org.iq80.leveldb"            %   "leveldb"                             % "0.7"
  val levelDbJNI            =   "org.fusesource.leveldbjni"   %   "leveldbjni-all"                      % "1.8"


  val scalaLogging          =  "com.typesafe.scala-logging"   %%  "scala-logging"                       % "3.1.0"

  val logBack               =  "ch.qos.logback"               %   "logback-classic"                     % "1.1.2"

  val akkaTestKit           =   "com.typesafe.akka"           %%  "akka-testkit"                        % akkaVersion     % "test"
  val scalaTest             =   "org.scalatest"               %%  "scalatest"                           % "2.2.1"         % "test"


  val mainDeps = Seq(
    libraryDependencies ++= Seq(scalaLogging, logBack),
    libraryDependencies ++= Seq(scalaTest)
  )

  val akkaDeps = Seq(
    libraryDependencies ++= Seq(akkaPersistence, akkaTestKit, akkaSlf4j),
    libraryDependencies ++= Seq(levelDb, levelDbJNI),
    // experimental
    libraryDependencies ++= Seq(akkaPersistenceQuery, akkaStreams)
  )

  val playSampleDeps = Seq(
    libraryDependencies += macwireRuntime,
    libraryDependencies += macwireMacros
  ) ++ mainDeps ++ akkaDeps

}
// /@formatter:on