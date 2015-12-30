val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.1.1" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
