val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.1.0" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
