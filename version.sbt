val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.3.1" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
