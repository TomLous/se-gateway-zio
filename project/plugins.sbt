// Format Scala code https://github.com/scalameta/scalafmt / doesn't need to align with scalafmt version
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")

// Automated release process https://github.com/sbt/sbt-release
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")

// Generate scala from build definitions https://github.com/sbt/sbt-buildinfo
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo"  % "0.10.0")

// Generate scala from avro https://github.com/julianpeeters/sbt-avrohugger#changing-settings
addSbtPlugin("com.julianpeeters" % "sbt-avrohugger" % "2.0.0")

// Native Image on local machine https://github.com/scalameta/sbt-native-image
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.2")

// Package code natively. Used for Graal \w Docker https://github.com/sbt/sbt-native-packager
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")

// Automated release process https://github.com/sbt/sbt-release
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
