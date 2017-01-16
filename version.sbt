val snapshotSuffix = "-SNAPSHOT"

version in ThisBuild := "0.4.10" //+ snapshotSuffix

isSnapshot := version.value.endsWith(snapshotSuffix)
