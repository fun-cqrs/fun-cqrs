val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.3" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
