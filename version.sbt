val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.5" + snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
