val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.0.4" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
