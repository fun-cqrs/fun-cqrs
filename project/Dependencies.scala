import sbt.Keys._
import sbt._

//@formatter:off
object Dependencies {  

  //------------------------------------------------------------------------------------------------------------
  // io.strongtyped.funcqrs core
  val scalaLogging          =  "com.typesafe.scala-logging" %% "scala-logging"      % "3.4.0"
  val scalaTest             =  "org.scalatest"              %% "scalatest"          % "3.0.0" % "test"
  val rxScala               =  "io.reactivex"               %% "rxscala"            % "0.26.2"


  val mainDeps = Seq(scalaLogging, scalaTest, rxScala)
  //------------------------------------------------------------------------------------------------------------



  //------------------------------------------------------------------------------------------------------------
  // Akka Module
  val akkaDeps = {
    val akkaVersion               =   "2.4.8"
    
      Seq(
        "com.typesafe.akka"           %%  "akka-actor"        % akkaVersion,
        "com.typesafe.akka"           %%  "akka-persistence"  % akkaVersion,
        "com.typesafe.akka"           %%  "akka-slf4j"        % akkaVersion,
        "com.typesafe.akka"           %%  "akka-stream"       % akkaVersion,
        // experimental
        "com.typesafe.akka"           %%  "akka-persistence-query-experimental" % akkaVersion,
        // test scope
        "com.typesafe.akka"           %%  "akka-testkit"                        % akkaVersion   % "test",
        "com.github.dnvriend"         %%  "akka-persistence-inmemory"           % "1.3.5"       % "test"
      )
  }
  //------------------------------------------------------------------------------------------------------------

  
  //------------------------------------------------------------------------------------------------------------
  // Play Json support
  val playJson              =    "com.typesafe.play"          %% "play-json"          %  "2.4.8"
  val play25Json            =    "com.typesafe.play"          %% "play-json"          %  "2.5.4"
  //------------------------------------------------------------------------------------------------------------

  val levelDb           =   "org.iq80.leveldb"            %   "leveldb"           % "0.7"
  val levelDbJNI        =   "org.fusesource.leveldbjni"   %   "leveldbjni-all"    % "1.8"

  val sampleDeps = Seq(levelDb, levelDbJNI) ++ mainDeps ++ akkaDeps
}
// /@formatter:on
