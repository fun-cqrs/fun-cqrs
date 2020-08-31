logLevel := Level.Warn

addSbtPlugin("com.typesafe.play" % "sbt-plugin"   % "2.7.2")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "2.0.1")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.9.4")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.15")
addSbtPlugin("com.lucidchart"    % "sbt-scalafmt"        % "1.15")
addSbtPlugin("com.47deg"  % "sbt-microsites" % "1.2.1")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"       % "0.7.0")
