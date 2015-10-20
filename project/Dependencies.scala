import sbt.Keys._
import sbt._

//@formatter:off
object Dependencies {  

  //------------------------------------------------------------------------------------------------------------
  // io.strongtyped.funcqrs core
  val scalaLogging          =  "com.typesafe.scala-logging" %%  "scala-logging"    % "3.1.0"
  
  val mainDeps = Seq(
    libraryDependencies ++= Seq(scalaLogging)
  )
  //------------------------------------------------------------------------------------------------------------

  //------------------------------------------------------------------------------------------------------------
  // Akka Module
  val akkaVersion           =   "2.4.0"
  val akkaActor             =   "com.typesafe.akka"           %%  "akka-actor"        % akkaVersion
  val akkaPersistence       =   "com.typesafe.akka"           %%  "akka-persistence"  % akkaVersion
  val akkaSlf4j             =   "com.typesafe.akka"           %%  "akka-slf4j"        % akkaVersion
  val akkaTestKit           =   "com.typesafe.akka"           %%  "akka-testkit"      % akkaVersion     % "test"

  val akkaStreams           =   "com.typesafe.akka"           %%  "akka-stream-experimental"            % "1.0"
  val akkaPersistenceQuery  =   "com.typesafe.akka"           %%  "akka-persistence-query-experimental" % akkaVersion

  val akkaDeps = Seq(
    libraryDependencies ++= Seq(akkaActor, akkaPersistence, akkaSlf4j, akkaTestKit),
    // experimental
    libraryDependencies ++= Seq(akkaPersistenceQuery, akkaStreams)
  )
  //------------------------------------------------------------------------------------------------------------

  //------------------------------------------------------------------------------------------------------------
  // LevelDb Module
  val levelDb               =   "org.iq80.leveldb"            %   "leveldb"           % "0.7"
  val levelDbJNI            =   "org.fusesource.leveldbjni"   %   "leveldbjni-all"    % "1.8"

  val levelDbDeps = Seq(
    libraryDependencies ++= Seq(levelDb, levelDbJNI)
  )
  //------------------------------------------------------------------------------------------------------------

  //------------------------------------------------------------------------------------------------------------
  //  PLAY Sample
  val macwireVersion    =   "1.0.7"
  val macwireMacros     =   "com.softwaremill.macwire" %% "macros"    % macwireVersion
  val macwireRuntime    =   "com.softwaremill.macwire" %% "runtime"   % macwireVersion
  val scalaTest         =   "org.scalatest"            %% "scalatest" % "2.2.1"         % "test"

  val playSampleDeps = Seq(
    libraryDependencies += macwireRuntime,
    libraryDependencies += macwireMacros,
    libraryDependencies += scalaTest
  )
  //------------------------------------------------------------------------------------------------------------

}
// /@formatter:on