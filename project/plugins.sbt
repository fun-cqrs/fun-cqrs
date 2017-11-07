logLevel := Level.Warn

addSbtPlugin("com.typesafe.play" % "sbt-plugin"   % "2.4.3")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "1.0.0")
addSbtPlugin("com.timushev.sbt"  % "sbt-updates"  % "0.1.10")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.3")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.5.6")
addSbtPlugin("com.fortysevendeg" % "sbt-microsites" % "0.3.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "1.2.0")