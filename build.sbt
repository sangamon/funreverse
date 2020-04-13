import Dependencies._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "de.sangamon"

lazy val root = (project in file("."))
  .settings(
    name := "funreverse",
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "org.scodec" %% "scodec-core" % "1.11.7",
    libraryDependencies += "org.scodec" %% "scodec-bits" % "1.1.14",
    libraryDependencies += "org.scodec" %% "scodec-stream" % "2.0.0",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "2.1.2",
    libraryDependencies += "co.fs2" %% "fs2-core" % "2.2.1",
    libraryDependencies += "co.fs2" %% "fs2-io" % "2.2.1"
  )
