import sbt.Keys._
import sbt._

object BuildSettings {

  val scalacBuildOptions = Seq("-unchecked", "-deprecation", "-feature", "-Xlint:-infer-any")

  lazy val defaultSettings = Seq(
    scalacOptions := scalacBuildOptions
  )

  def projSettings(dependencies: Seq[ModuleID] = Seq()) = {
    defaultSettings ++ Seq(
      libraryDependencies ++= dependencies
    )
  }
}
