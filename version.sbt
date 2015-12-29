val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.0.7" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)