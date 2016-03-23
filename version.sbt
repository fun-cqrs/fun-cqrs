val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.2" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
