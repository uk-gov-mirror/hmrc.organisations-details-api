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

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-27"   % "4.1.0"  % Test,
    "org.scalatest"           %% "scalatest"                % "3.2.5"  % Test,
    "com.typesafe.play"       %% "play-test"                % PlayVersion.current  % Test,
    "com.vladsch.flexmark"    %  "flexmark-all"             % "0.36.8" % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "4.0.3"  % "test, it"
  )
}
