/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package component.uk.gov.hmrc.organisationsdetailsapi.connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.organisationsdetailsapi.connectors.OrganisationsMatchingConnector
import uk.gov.hmrc.organisationsdetailsapi.errorhandler.ErrorResponses.MatchNotFoundException
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.TestSupport

import java.util.UUID
import scala.concurrent._
import scala.concurrent.duration._

class OrganisationsMatchingConnectorSpec
  extends AnyWordSpec
    with BeforeAndAfterEach
    with TestSupport
    with MockitoSugar
    with Matchers
    with GuiceOneAppPerSuite {

  private val stubPort = sys.env.getOrElse("WIREMOCK", "11122").toInt
  private val stubHost = "localhost"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  def externalServices: Seq[String] = Seq.empty

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .bindings(bindModules*)
    .configure(
      "cache.enabled" -> false,
      "auditing.enabled" -> false,
      "metrics.jvm"-> false,
      "microservice.services.organisations-matching-api.host" -> "localhost",
      "microservice.services.organisations-matching-api.port" -> "11122"
    )
    .build()

  implicit val ec: ExecutionContext =
    fakeApplication().injector.instanceOf[ExecutionContext]

  implicit val hc: HeaderCarrier = HeaderCarrier()


  val config: ServicesConfig = fakeApplication().injector.instanceOf[ServicesConfig]
  val httpClient: HttpClientV2 = fakeApplication().injector.instanceOf[HttpClientV2]

  val organisationsMatchingConnector: OrganisationsMatchingConnector = new OrganisationsMatchingConnector(httpClient, config)

  override def beforeEach(): Unit = {
    wireMockServer.start()
    configureFor(stubHost, stubPort)
  }

  override def afterEach(): Unit = {
    wireMockServer.stop()
  }

  def stubWithResponseStatus(responseStatus: Int, body: String = "", matchId: String): Unit =
    stubFor(
      get(urlPathMatching(s"/match-record/$matchId"))
        .willReturn(aResponse().withStatus(responseStatus).withBody(body)))

  "OrganisationMatchingConnector" should {
    "resolve successfully when returned a valid payload" in {

      val matchId = "75a07eb6-2459-438a-bc5e-9cbb563675ee"
      val matchIdUUID = UUID.fromString(matchId)

      val jsonResponse =
        s"""
           |{
           |    "matchId": "$matchId",
           |    "utr": "1234567890"
           |}
           |""".stripMargin

      stubWithResponseStatus(OK, jsonResponse, matchId)

      organisationsMatchingConnector.resolve(matchIdUUID).map(result => {
        result.matchId shouldBe UUID.fromString(matchId)
        result.utr shouldBe "1234567890"
      })

    }
  }

}
