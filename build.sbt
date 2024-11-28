ThisBuild / organization := "com.tubitv"

ThisBuild / description := "A plugin for authenticating with AWS CodeArtifact"
ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / homepage := Some(url("https://github.com/adRise/sbt-codeartifact"))

ThisBuild / developers := List(
  Developer(
    "douglasthomsen",
    "Douglas Thomsen",
    "douglas.thomsen@iterable.com",
    url("https://github.com/douglasthomsen")
  )
)

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/adRise/sbt-codeartifact"),
    "scm:git@github.com:adRise/sbt-codeartifact.git"
  )
)
lazy val testSettings: Seq[Setting[_]] = Seq(
  scriptedLaunchOpts := {
    scriptedLaunchOpts.value ++ Seq(
      "-Xmx1024M",
      "-Dplugin.version=" + version.value
    )
  },
  scriptedBufferLog := false
)

lazy val core = project
  .in(file("core"))
  .settings(testSettings)

lazy val `sbt-codeartifact` = project
  .in(file("sbt-codeartifact"))
  .dependsOn(core)
  .settings(testSettings)

lazy val root = project
  .in(file("."))
  .aggregate(core, `sbt-codeartifact`)
  .settings(
    publish / skip := true
  )
