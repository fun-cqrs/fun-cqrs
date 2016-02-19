val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.1.2" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
