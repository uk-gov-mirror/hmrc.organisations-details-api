
TwirlKeys.templateImports := Seq.empty

val appName = "organisations-details-api"

lazy val ItTest = config("it") extend Test
lazy val playSettings: Seq[Setting[?]] = Seq(
  routesImport ++= Seq("uk.gov.hmrc.organisationsdetailsapi.utils.Binders._")
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;" +
      ".*BuildInfo.;uk.gov.hmrc.BuildInfo;.*Routes;.*RoutesPrefix*;" +
      // All after this is due to Early project and getting pipelines up and running. May be removed later.
      "uk.gov.hmrc.organisationsdetailsapi.views;" +
      ".*DocumentationController*;" +
      "uk.gov.hmrc.organisationsdetailsapi.handlers;" +
      ".*definition*;",
    ScoverageKeys.coverageMinimumStmtTotal := 78,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

def intTestFilter(name: String): Boolean = name startsWith "it"
def unitFilter(name: String): Boolean = name startsWith "unit"
def componentFilter(name: String): Boolean = name startsWith "component"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    onLoadMessage := "",
    majorVersion := 0,
    scalaVersion := "3.7.1",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test(),
    scalacOptions += "-Wconf:src=routes/.*:s",
    Test / testOptions := Seq.empty,
    scalacOptions := Seq("-explain"),
  )
  .settings(PlayKeys.playDefaultPort := 9656)
  .settings(playSettings)

  // Integration tests
  .configs(ItTest)
  .settings(inConfig(ItTest)(Defaults.testSettings) *)
  .settings(
    ItTest / Keys.fork := true,
    ItTest / unmanagedSourceDirectories := (ItTest / baseDirectory)(base => Seq(base / "test")).value,
    ItTest / testOptions := Seq(Tests.Filter(intTestFilter)),
    ItTest / parallelExecution := false
  )
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings) *)
  .settings(
    ComponentTest / testOptions := Seq(Tests.Filter(componentFilter)),
    ComponentTest / unmanagedSourceDirectories := (ComponentTest / baseDirectory)(base => Seq(base / "test")).value,
    ComponentTest / parallelExecution := false
  )
  .settings(scoverageSettings *)
  .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "resources")

lazy val ComponentTest = config("component") extend Test
