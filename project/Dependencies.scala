import sbt.Keys._
import sbt._

//@formatter:off
object Dependencies {  

  //------------------------------------------------------------------------------------------------------------
  // io.strongtyped.funcqrs core
  val scalaLogging          =  "com.typesafe.scala-logging" %% "scala-logging"      % "3.5.0"
  val scalaTest             =  "org.scalatest"              %% "scalatest"          % "3.0.0" % "test"
  val rxScala               =  "io.reactivex"               %% "rxscala"       	% "0.26.4"


  val mainDeps = Seq(scalaLogging, scalaTest)
  //------------------------------------------------------------------------------------------------------------



  //------------------------------------------------------------------------------------------------------------
  // Akka Module
  val akkaDeps = {
    val akkaVersion               =   "2.4.12"
    
      Seq(
        "com.typesafe.akka"           %%  "akka-actor"        % akkaVersion,
        "com.typesafe.akka"           %%  "akka-persistence"  % akkaVersion,
        "com.typesafe.akka"           %%  "akka-slf4j"        % akkaVersion,
        "com.typesafe.akka"           %%  "akka-stream"       % akkaVersion,
        // experimental
        "com.typesafe.akka"           %%  "akka-persistence-query-experimental" % akkaVersion,
        // test scope
        "com.typesafe.akka"           %%  "akka-testkit"                        % akkaVersion   % "test",
        "com.github.dnvriend"         %%  "akka-persistence-inmemory"           % "1.3.14"      % "test"
      )
  }
  //------------------------------------------------------------------------------------------------------------

  
  //------------------------------------------------------------------------------------------------------------
  val levelDb           =   "org.iq80.leveldb"            %   "leveldb"           % "0.7"
  val levelDbJNI        =   "org.fusesource.leveldbjni"   %   "leveldbjni-all"    % "1.8"

  val sampleDeps = Seq(levelDb, levelDbJNI) ++ mainDeps ++ akkaDeps
}
// /@formatter:on
