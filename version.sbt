val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.0.1" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
