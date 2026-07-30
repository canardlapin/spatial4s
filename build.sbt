import org.scalajs.linker.interface.ModuleKind
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import org.typelevel.sbt.gha.JavaSpec
import sbtcrossproject.{CrossProject, CrossType}
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*

val Scala3 = "3.7.4"
val munitV = "1.3.4"
val munitCheckV = "1.3.0"

ThisBuild / tlBaseVersion := "0.1"
ThisBuild / organization := "io.github.canardlapin"
ThisBuild / organizationName := "Bradley Buchsbaum"
ThisBuild / startYear := Some(2026)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("canardlapin", "Bradley Buchsbaum")
)
ThisBuild / homepage := Some(url("https://github.com/canardlapin/spatial4s"))

ThisBuild / scalaVersion := Scala3
ThisBuild / crossScalaVersions := Seq(Scala3)
ThisBuild / tlJdkRelease := Some(11)
ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.temurin("17"),
  JavaSpec.temurin("21")
)
ThisBuild / versionPolicyIntention := Compatibility.None
ThisBuild / tlFatalWarnings := true

lazy val commonSettings = Seq(
  scalacOptions +=
    "-Wconf:msg=package scala contains object and package with same name.*caps:silent",
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit" % munitV % Test,
    "org.scalameta" %%% "munit-scalacheck" % munitCheckV % Test
  ),
  Test / parallelExecution := false
)

def spatialProject(artifact: String): CrossProject =
  CrossProject(artifact, file(s"modules/$artifact"))(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .settings(commonSettings)
    .settings(name := artifact)
    .jsSettings(
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
    )

lazy val spatial4sCore =
  spatialProject("spatial4s-core")

lazy val spatial4sLaws =
  spatialProject("spatial4s-laws")
    .dependsOn(spatial4sCore)

lazy val docs =
  project
    .in(file("site"))
    .dependsOn(spatial4sCore.jvm)
    .enablePlugins(TypelevelSitePlugin)
    .settings(
      name := "spatial4s-docs",
      description := "Executable guides and reference documentation for spatial4s.",
      publish / skip := true,
      mdocExtraArguments += "--no-link-hygiene"
    )

lazy val root =
  project
    .in(file("."))
    .aggregate(
      spatial4sCore.jvm,
      spatial4sCore.js,
      spatial4sLaws.jvm,
      spatial4sLaws.js,
      docs
    )
    .settings(
      name := "spatial4s-root",
      publish / skip := true
    )

addCommandAlias("compileAll", ";root/compile")
addCommandAlias("testAll", ";root/test")
addCommandAlias(
  "testFullOptJS",
  ";set Global / scalaJSStage := FullOptStage;" +
    "spatial4s-coreJS/test;" +
    "spatial4s-lawsJS/test"
)
addCommandAlias(
  "checkAll",
  ";scalafmtCheckAll;scalafmtSbtCheck;compileAll;testAll"
)
addCommandAlias(
  "docsCheck",
  ";spatial4s-coreJVM/doc;" +
    "spatial4s-lawsJVM/doc;" +
    "docs/tlSite"
)
