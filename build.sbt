lazy val root = project
  .in(file("."))
  .settings(
    name := "gitlab-for-scala",
    scalaVersion := "2.12.20",
    crossScalaVersions := Seq(scalaVersion.value, "3.3.4"),
    libraryDependencies ++= Seq(
      "io.github.kijuky" %% "diff-for-scala" % "1.0.0",
      "org.gitlab4j" % "gitlab4j-api" % "5.6.0"
    )
  )

inThisBuild(
  Seq(
    organization := "io.github.kijuky",
    homepage := Some(url("https://github.com/kijuky/gitlab-for-scala")),
    licenses := Seq(
      "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "kijuky",
        "Kizuki YASUE",
        "ikuzik@gmail.com",
        url("https://github.com/kijuky")
      )
    ),
    versionScheme := Some("early-semver"),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
  )
)
