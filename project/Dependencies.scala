import sbt.Keys._
import sbt._

//@formatter:off
object Dependencies {

  val macwireVersion    =   "1.0.7"
  val macwireMacros     =   "com.softwaremill.macwire"    %% "macros"                           % macwireVersion
  val macwireRuntime    =   "com.softwaremill.macwire"    %% "runtime"                          % macwireVersion

  val akkaVersion       =   "2.3.13"
  
  val akkaPersistence   =   "com.typesafe.akka"           %%  "akka-persistence-experimental"   % akkaVersion
  val akkaTestKit       =   "com.typesafe.akka"           %%  "akka-testkit"                    % akkaVersion     % "test"

  val scalaTest         =   "org.scalatest"               %% "scalatest"                        % "2.2.1"         % "test"


  val mainDeps = Seq(
    libraryDependencies ++= Seq(scalaTest)
  )

  val akkaDeps = Seq(
    libraryDependencies ++= Seq(akkaPersistence, akkaTestKit)
  )

  val macwireDeps = Seq (
    libraryDependencies ++= Seq(macwireRuntime, macwireMacros)
  )

}
// /@formatter:on