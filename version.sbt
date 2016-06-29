val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.6" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
