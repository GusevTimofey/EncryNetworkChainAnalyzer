name := "NetworkChainAnalyzer"

version := "0.1"

scalaVersion := "2.12.11"

val fs2Version         = "2.1.0"
val catsVersion        = "2.0.0"
val catsEffectsVersion = "2.0.0"

val kafka = Seq(
  "com.github.fd4s" %% "fs2-kafka" % "1.0.0"
)

val cats: Seq[ModuleID] = Seq(
  "org.typelevel" %% "cats-core"   % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectsVersion,
  "co.fs2"        %% "fs2-core"    % fs2Version,
  "co.fs2"        %% "fs2-io"      % fs2Version
)

libraryDependencies ++= Seq(
  "io.monix"              %% "monix"                  % "3.1.0",
  "ch.qos.logback"        % "logback-classic"         % "1.2.3",
  "org.slf4j"             % "slf4j-api"               % "1.7.25",
  "io.chrisdavenport"     %% "log4cats-slf4j"         % "0.4.0-M2",
  "com.github.pureconfig" %% "pureconfig"             % "0.12.2",
  "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.12.2",
  "com.paulgoldbaum"      %% "scala-influxdb-client"  % "0.6.1"
) ++ kafka ++ cats

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xfatal-warnings",
  "-Ypartial-unification",
  "-unchecked",
  "-feature",
  "-deprecation"
)

addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel"   %% "kind-projector"     % "0.11.0" cross CrossVersion.patch)
addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.1" cross CrossVersion.full)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
