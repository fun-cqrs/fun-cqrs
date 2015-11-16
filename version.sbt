val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.0.3" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
