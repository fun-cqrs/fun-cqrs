import ReleaseTransformations._

sonatypeProfileName := "org.funcqrs"

releaseCrossBuild := true // true if you cross-build the project for multiple Scala versions

// https://github.com/sbt/sbt-release
// Interactive release:
//  > sbt release
// Pass versions manually:
//  > sbt release release-version 1.0.99 next-version 1.2.0-SNAPSHOT
// Non-interactive release:
//  > sbt release with-defaults
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  // runClean,
  // runTest, // is done by travis
  // setReleaseVersion, // we set versions ourselves
  // commitReleaseVersion,
  // tagRelease,
  // For non cross-build projects, use releaseStepCommand("publishSigned")
  releaseStepCommandAndRemaining("+publishSigned"),
  // setNextVersion,
  // commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll")
  // pushChanges
)
