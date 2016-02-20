val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.3.0" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
