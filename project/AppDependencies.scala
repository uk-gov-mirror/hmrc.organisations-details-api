import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val hmrcMongo = "uk.gov.hmrc.mongo"

  val compile = Seq(
    hmrc                  %% "bootstrap-backend-play-27"  % "4.1.0",
    hmrcMongo             %% "hmrc-mongo-play-27"         % "0.49.0",
    hmrc                  %% "mongo-caching"              % "7.0.0-play-27",
    hmrc                  %% "json-encryption"            % "4.10.0-play-27"
  )

  def test(scope: String = "test, it") = Seq(
    hmrc                      %% "bootstrap-test-play-27"   % "4.1.0"             % scope,
    "org.scalatest"           %% "scalatest"                % "3.2.5"             % scope,
    "com.typesafe.play"       %% "play-test"                % PlayVersion.current % scope,
    "com.vladsch.flexmark"    %  "flexmark-all"             % "0.36.8"            % scope,
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "4.0.3"             % scope,
    hmrcMongo                 %% "hmrc-mongo-test-play-27"  % "0.49.0"            % scope,
    hmrc                      %% "service-integration-test" % "1.1.0-play-27"     % scope
  )
}
