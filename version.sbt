val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.7" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
