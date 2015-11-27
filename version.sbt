val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.0.5" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
