// Build del proyecto Scala.
// Scala 2.13 (y no Scala 3) porque Scala 3 eliminó el do-while, que es parte de la tabla de clase.
ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "torneos-scala",
    version := "1.0.0",
    Compile / mainClass := Some("torneos.Main"),
    scalacOptions ++= Seq("-deprecation", "-feature", "-encoding", "UTF-8"),
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    // Las pruebas se ejecutan en un proceso aparte con la carpeta scala/ como directorio de trabajo
    Test / fork := true,
    Test / baseDirectory := baseDirectory.value,
    assembly / assemblyJarName := "torneos-scala.jar",
    assembly / mainClass := Some("torneos.Main")
  )
