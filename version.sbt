val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.8" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
