val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.12" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
