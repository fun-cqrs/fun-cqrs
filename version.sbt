val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.11" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
