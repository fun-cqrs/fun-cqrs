import sbt.Keys._
import sbt._

//@formatter:off
object Dependencies {  

  //------------------------------------------------------------------------------------------------------------
  // io.strongtyped.funcqrs core
  val scalaLogging          =  "com.typesafe.scala-logging" %% "scala-logging"      % "3.1.0"
  val scalaTest             =  "org.scalatest"              %% "scalatest"          % "3.0.0-M10" % "test"
  val rxScala               =  "io.reactivex"               %% "rxscala"            % "0.26.0"


  val mainDeps = Seq(
    libraryDependencies ++= Seq(scalaLogging, scalaTest, rxScala)
  )
  //------------------------------------------------------------------------------------------------------------



  //------------------------------------------------------------------------------------------------------------
  // Akka Module
  val akkaVersion               =   "2.4.2"
  val akkaActor                 =   "com.typesafe.akka"           %%  "akka-actor"        % akkaVersion
  
  val akkaPersistence           =   "com.typesafe.akka"           %%  "akka-persistence"  % akkaVersion
  val akkaSlf4j                 =   "com.typesafe.akka"           %%  "akka-slf4j"        % akkaVersion
  val akkaStreams               =   "com.typesafe.akka"           %%  "akka-stream"       % akkaVersion
  val akkaTestKit               =   "com.typesafe.akka"           %%  "akka-testkit"      % akkaVersion     % "test"
  val akkaPersistenceQuery      =   "com.typesafe.akka"           %%  "akka-persistence-query-experimental" % akkaVersion

  val akkaPersistenceInMemory   =   "com.github.dnvriend"         %%  "akka-persistence-inmemory"           % "1.2.2"     % "test"

  val akkaDeps = Seq(
    libraryDependencies ++= Seq(akkaActor, akkaPersistence, akkaStreams, akkaSlf4j),
    // experimental
    libraryDependencies ++= Seq(akkaPersistenceQuery),
    // test scope
    libraryDependencies ++= Seq(akkaTestKit, akkaPersistenceInMemory)
  )
  //------------------------------------------------------------------------------------------------------------

  
  //------------------------------------------------------------------------------------------------------------
  // Play Json support
  val playJson              =    "com.typesafe.play"          %% "play-json"          %  "2.4.4"
  val play25Json            =    "com.typesafe.play"          %% "play-json"          %  "2.5.3"
  //------------------------------------------------------------------------------------------------------------

  val levelDb           =   "org.iq80.leveldb"            %   "leveldb"           % "0.7"
  val levelDbJNI        =   "org.fusesource.leveldbjni"   %   "leveldbjni-all"    % "1.8"

  val sampleDeps = Seq(
    libraryDependencies ++= Seq(levelDb, levelDbJNI)
  ) ++ mainDeps ++ akkaDeps
}
// /@formatter:on
