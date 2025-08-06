import sbt.*

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-30"
  val hmrcMongoVersion = "2.7.0"
  var bootstrapVersion = "9.18.0"

  val compile: Seq[ModuleID] = Seq(
    s"$hmrc.mongo" %% s"hmrc-mongo-$playVersion"    % hmrcMongoVersion,
    "uk.gov.hmrc"  %% s"play-hmrc-api-$playVersion" % "8.2.0",
    hmrc           %% s"play-hal-$playVersion"      % "4.1.0",
    hmrc           %% s"crypto-json-$playVersion"   % "8.2.0"
  )

  def test(scope: String = "test, it"): Seq[ModuleID] = Seq(
    hmrc                     %% s"bootstrap-test-$playVersion"  % bootstrapVersion    % scope,
    s"$hmrc.mongo"           %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion    % scope,
    "org.scalatestplus"      %% "scalacheck-1-17"               % "3.2.18.0"          % scope,
    "com.codacy"                   %% "scalaj-http"              % "2.5.0"             % scope
  )
}
