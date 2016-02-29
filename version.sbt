val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.1" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
