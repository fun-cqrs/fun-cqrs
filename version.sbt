val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.9" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
