val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.0.6.2" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
