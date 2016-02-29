val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.0" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
