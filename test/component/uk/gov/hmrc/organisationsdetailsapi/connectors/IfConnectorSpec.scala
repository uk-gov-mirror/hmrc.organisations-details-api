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
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern
import org.mockito.ArgumentMatchers.{any, contains, matches}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HeaderNames, InternalServerException, NotFoundException}
import uk.gov.hmrc.organisationsdetailsapi.audit.AuditHelper
import uk.gov.hmrc.organisationsdetailsapi.connectors.IfConnector
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.CorporationTaxReturnDetails._
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.EmployeeCountResponse._
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.SelfAssessmentReturnDetail._
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.{IfHelpers, TestSupport}

import java.util.UUID
import scala.concurrent.ExecutionContext


class IfConnectorSpec
  extends AnyWordSpec
    with BeforeAndAfterEach
    with TestSupport
    with MockitoSugar
    with Matchers
    with GuiceOneAppPerSuite
    with IfHelpers {

  val stubPort: Int = sys.env.getOrElse("WIREMOCK", "11122").toInt
  val stubHost = "127.0.0.1"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
  val integrationFrameworkAuthorizationToken = "IF_TOKEN"
  val integrationFrameworkEnvironment = "IF_ENVIRONMENT"

  def externalServices: Seq[String] = Seq.empty

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .bindings(bindModules*)
    .configure(
      "cache.enabled" -> false,
      "auditing.enabled" -> false,
      "auditing.traceRequests" -> false,
      "metrics.jvm"-> false,
      "microservice.services.integration-framework.host" -> "127.0.0.1",
      "microservice.services.integration-framework.port" -> "11122",
      "microservice.services.integration-framework.authorization-token" -> integrationFrameworkAuthorizationToken,
      "microservice.services.integration-framework.environment" -> integrationFrameworkEnvironment
    )
    .build()


  trait Setup {
    val matchId = "80a6bb14-d888-436e-a541-4000674c60aa"
    val sampleCorrelationId = "188e9400-b636-4a3b-80ba-230a8c72b92a"
    val sampleCorrelationIdHeader: (String, String) = "CorrelationId" -> sampleCorrelationId

    implicit val ec: ExecutionContext =
      fakeApplication().injector.instanceOf[ExecutionContext]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(sampleCorrelationIdHeader)


    val config: ServicesConfig = fakeApplication().injector.instanceOf[ServicesConfig]
    val httpClient: HttpClientV2 = fakeApplication().injector.instanceOf[HttpClientV2]
    val auditHelper: AuditHelper = mock[AuditHelper]

    val underTest = new IfConnector(config, httpClient, auditHelper)
  }

  override def beforeEach(): Unit = {
    wireMockServer.start()
    configureFor(stubHost, stubPort)
  }

  override def afterEach(): Unit = {
    wireMockServer.stop()
  }

  val utr = "1234567890"
  val vrn = "1234567890"

  val taxReturn: CorporationTaxReturnDetailsResponse = createValidCorporationTaxReturnDetails()
  val saReturn: SelfAssessmentReturnDetailResponse = createValidSelfAssessmentReturnDetails()
  val employeeCountRequest: EmployeeCountRequest = createValidEmployeeCountRequest()
  val employeeCountResponse: EmployeeCountResponse = createValidEmployeeCountResponse()
  val invalidTaxReturn: CorporationTaxReturnDetailsResponse = createValidCorporationTaxReturnDetails().copy(utr = Some(""))
  val invalidSaReturn: SelfAssessmentReturnDetailResponse = createValidSelfAssessmentReturnDetails().copy(utr = Some(""))
  val invalidEmployeeCountRequest: EmployeeCountRequest = createValidEmployeeCountRequest().copy(startDate = "")
  val invalidEmployeeCountResponse: EmployeeCountResponse = createValidEmployeeCountResponse().copy(startDate = Some(""))
  val vatReturn: IfVatReturnsDetailsResponse = createValidVatReturnDetails()
  val appDate = "20230105"

  val emptyEmployeeCountResponse: EmployeeCountResponse = EmployeeCountResponse(None, None, Some(Seq()))
  val emptyCtReturn: CorporationTaxReturnDetailsResponse = CorporationTaxReturnDetailsResponse(None, None, None, Some(Seq()))
  val emptySaReturn: SelfAssessmentReturnDetailResponse = SelfAssessmentReturnDetailResponse(None, None, None, None, Some(Seq()))

  "IF Connector" should {

    "Fail when IF returns an error" in new Setup {

      Mockito.reset(underTest.auditHelper)

      stubFor(
        get(urlPathMatching(s"/organisations/corporation-tax/$utr/return/details"))
          .withQueryParam("fields", equalTo("fields(A,B,C)"))
          .willReturn(aResponse().withStatus(500)))

      intercept[InternalServerException] {
        await(
          underTest.getCtReturnDetails(UUID.randomUUID().toString, utr, Some("fields(A,B,C)"))(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )
      }

      verify(underTest.auditHelper,
        times(1)).auditIfApiFailure(any(), any(), any(), any(), any())(using any())

    }

    "Fail when IF returns a bad request" in new Setup {

      Mockito.reset(underTest.auditHelper)

      stubFor(
        get(urlPathMatching(s"/organisations/corporation-tax/$utr/return/details"))
          .withQueryParam("fields", equalTo("fields(A,B,C)"))
          .willReturn(aResponse().withStatus(400).withBody("BAD_REQUEST")))

      intercept[BadRequestException] {
        await(
          underTest.getCtReturnDetails(UUID.randomUUID().toString, utr, Some("fields(A,B,C)"))(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )
      }

      verify(underTest.auditHelper,
        times(1)).auditIfApiFailure(any(), any(), any(), any(), any())(using any())
    }

    "Fail when IF returns a NOT_FOUND and return error with empty body" in new Setup {

      Mockito.reset(underTest.auditHelper)

      stubFor(
        get(urlPathMatching(s"/organisations/corporation-tax/$utr/return/details"))
          .withQueryParam("fields", equalTo("fields(A,B,C)"))
          .willReturn(aResponse().withStatus(404)))

      intercept[InternalServerException] {
        await(
          underTest.getCtReturnDetails(UUID.randomUUID().toString, utr, Some("fields(A,B,C)"))(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )
      }
      verify(underTest.auditHelper,
        times(1)).auditIfApiFailure(any(), any(), any(), any(), any())(using any())
    }

    "getCtReturnDetails" should {

      "Fail when IF returns a NOT_DATA_FOUND and return error in body" in new Setup {

        Mockito.reset(underTest.auditHelper)

        stubFor(
          get(urlPathMatching(s"/organisations/corporation-tax/$utr/return/details"))
            .withQueryParam("fields", equalTo("fields(A,B,C)"))
            .willReturn(aResponse().withStatus(404).withBody(Json.stringify(Json.parse(
              """{
                |  "failures": [
                |    {
                |      "code": "NO_DATA_FOUND",
                |      "reason": "The remote endpoint has indicated no data was found for the provided utr."
                |    }
                |  ]
                |}""".stripMargin)))))

        val result: CorporationTaxReturnDetailsResponse = await(
          underTest.getCtReturnDetails(UUID.randomUUID().toString, utr, Some("fields(A,B,C)"))(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )

        result shouldBe emptyCtReturn

        verify(underTest.auditHelper,
          times(1)).auditIfApiFailure(any(), any(), any(), any(), contains("""NO_DATA_FOUND"""))(using any())
      }

      "successfully parse valid CorporationTaxReturnDetailsResponse from IF response" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonResponse: String = Json.prettyPrint(Json.toJson(taxReturn))

        stubFor(
          get(urlPathMatching(s"/organisations/corporation-tax/$utr/return/details"))
            .withQueryParam("fields", equalTo("fields(A,B,C)"))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(okJson(jsonResponse)))

        val result: CorporationTaxReturnDetailsResponse = await(
          underTest.getCtReturnDetails(UUID.randomUUID().toString, utr, Some("fields(A,B,C)"))(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )

        result shouldBe taxReturn

        verify(underTest.auditHelper,
          times(0)).auditIfApiFailure(any(), any(), any(), any(), any())(using any())

      }

      "successfully parse invalid CorporationTaxReturnDetailsResponse from IF response" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonResponse: String = Json.prettyPrint(Json.toJson(invalidTaxReturn))

        stubFor(
          get(urlPathMatching(s"/organisations/corporation-tax/$utr/return/details"))
            .withQueryParam("fields", equalTo("fields(A,B,C)"))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(okJson(jsonResponse)))


        intercept[InternalServerException] {
          await(
            underTest.getCtReturnDetails(UUID.randomUUID().toString, utr, Some("fields(A,B,C)"))(
              using hc,
              FakeRequest().withHeaders(sampleCorrelationIdHeader),
              ec
            )
          )
        }

        verify(underTest.auditHelper,
          times(1)).auditIfApiFailure(any(), any(), any(), any(), matches("^Error parsing IF response"))(using any())

      }
    }

    "getVatReturnDetails" should {

      "Fail when IF returns a NO_VAT_RETURNS_DETAIL_FOUND and return error in body" in new Setup {

        Mockito.reset(underTest.auditHelper)

        stubFor(
          get(urlPathMatching(s"/organisations/vat/$vrn/returns-details"))
            .withQueryParam("fields", equalTo("fields(A,B,C)"))
            .withQueryParam("appDate", equalTo(appDate))
            .willReturn(aResponse().withStatus(404).withBody(Json.stringify(Json.parse(
              """{
                |  "failures": [
                |    {
                |      "code": "NO_VAT_RETURNS_DETAIL_FOUND",
                |      "reason": "The remote endpoint has indicated that it cannot find the data for the supplied vrn, or there is no incoming data."
                |    }
                |  ]
                |}""".stripMargin)))))

        intercept[NotFoundException] {
          await(underTest.getVatReturnDetails(matchId, vrn, appDate, Some("fields(A,B,C)")))
        }

        verify(underTest.auditHelper, times(1))
          .auditIfApiFailure(any(), any(), any(), any(), contains("NO_VAT_RETURNS_DETAIL_FOUND"))(using any())
      }

      "successfully parse valid VatReturnDetailsResponse from IF response" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonResponse: String = Json.prettyPrint(Json.toJson(vatReturn))

        stubFor(
          get(urlPathMatching(s"/organisations/vat/$vrn/returns-details"))
            .withQueryParam("fields", equalTo("fields(A,B,C)"))
            .withQueryParam("appDate", equalTo(appDate))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(okJson(jsonResponse)))

        val result: IfVatReturnsDetailsResponse = await(
          underTest.getVatReturnDetails(matchId, vrn, appDate, Some("fields(A,B,C)"))
        )

        result shouldBe vatReturn

        verify(underTest.auditHelper, times(1))
          .auditIfApiResponse(any(), any(), any(), any(), any())(using any())
      }
    }

    "getSaReturnDetails" should {

      "Fail when IF returns a NOT_DATA_FOUND and return error in body" in new Setup {

        Mockito.reset(underTest.auditHelper)

        stubFor(
          get(urlPathMatching(s"/organisations/self-assessment/$utr/return/details"))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(aResponse().withStatus(404).withBody(Json.stringify(Json.parse(
              """{
                |  "failures": [
                |    {
                |      "code": "NO_DATA_FOUND",
                |      "reason": "The remote endpoint has indicated no data was found for the provided utr."
                |    }
                |  ]
                |}""".stripMargin)))))

        val result: SelfAssessmentReturnDetailResponse = await(
          underTest.getSaReturnDetails(UUID.randomUUID().toString, utr, None)(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )

        result shouldBe emptySaReturn

        verify(underTest.auditHelper,
          times(1)).auditIfApiFailure(any(), any(), any(), any(), contains("""NO_DATA_FOUND"""))(using any())
      }

      "successfully parse valid SelfAssessmentReturnDetailsResponse from IF response" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonResponse: String = Json.prettyPrint(Json.toJson(saReturn))

        stubFor(
          get(urlPathMatching(s"/organisations/self-assessment/$utr/return/details"))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(okJson(jsonResponse)))

        val result: SelfAssessmentReturnDetailResponse = await(
          underTest.getSaReturnDetails(UUID.randomUUID().toString, utr, None)(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )

        result shouldBe saReturn

        verify(underTest.auditHelper,
          times(0)).auditIfApiFailure(any(), any(), any(), any(), any())(using any())
      }

      "successfully parse invalid SelfAssessmentReturnDetailsResponse from IF response" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonResponse: String = Json.prettyPrint(Json.toJson(invalidSaReturn))

        stubFor(
          get(urlPathMatching(s"/organisations/self-assessment/$utr/return/details"))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(okJson(jsonResponse)))

        intercept[InternalServerException] {
          await(
            underTest.getSaReturnDetails(UUID.randomUUID().toString, utr, None)(
              using hc,
              FakeRequest().withHeaders(sampleCorrelationIdHeader),
              ec
            )
          )
        }

        verify(underTest.auditHelper,
          times(1)).auditIfApiFailure(any(), any(), any(), any(), matches("^Error parsing IF response"))(using any())

      }
    }

    "getEmployeeCount" should {

      "Fail when IF returns a NOT_DATA_FOUND and return error in body" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonRequest: String = Json.prettyPrint(Json.toJson(employeeCountRequest))

        stubFor(
          post(urlPathMatching(s"/organisations/employers/employee/counts"))
            .withRequestBody(new EqualToJsonPattern(jsonRequest, true, true))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(aResponse().withStatus(404).withBody(Json.stringify(Json.parse(
              """{
                |  "failures": [
                |    {
                |      "code": "NO_DATA_FOUND",
                |      "reason": "The remote endpoint has indicated that no data can be found for any employers."
                |    }
                |  ]
                |}""".stripMargin)))))

        val result: EmployeeCountResponse = await(
          underTest.getEmployeeCount(UUID.randomUUID().toString, utr, employeeCountRequest, None)(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )

        result shouldBe emptyEmployeeCountResponse
      }

      "successfully parse valid EmployeeCountResponse from IF response" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonRequest: String = Json.prettyPrint(Json.toJson(employeeCountRequest))
        val jsonResponse: String = Json.prettyPrint(Json.toJson(employeeCountResponse))

        stubFor(
          post(urlPathMatching(s"/organisations/employers/employee/counts"))
            .withRequestBody(new EqualToJsonPattern(jsonRequest, true, true))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(okJson(jsonResponse)))

        val result: EmployeeCountResponse = await(
          underTest.getEmployeeCount(UUID.randomUUID().toString, utr, employeeCountRequest, None)(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )

        result shouldBe employeeCountResponse

        verify(underTest.auditHelper,
          times(0)).auditIfApiFailure(any(), any(), any(), any(), any())(using any())

      }

      "successfully audit and consume invalid EmployeeCountResponse from IF response" in new Setup {

        Mockito.reset(underTest.auditHelper)

        val jsonRequest: String = Json.prettyPrint(Json.toJson(employeeCountRequest))
        val jsonResponse: String = Json.prettyPrint(Json.toJson(invalidEmployeeCountResponse))

        stubFor(
          post(urlPathMatching(s"/organisations/employers/employee/counts"))
            .withRequestBody(new EqualToJsonPattern(jsonRequest, true, true))
            .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
            .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
            .withHeader("CorrelationId", equalTo(sampleCorrelationId))
            .willReturn(okJson(jsonResponse)))

        intercept[InternalServerException] {
          await(
            underTest.getEmployeeCount(UUID.randomUUID().toString, utr, employeeCountRequest, None)(
              using hc,
              FakeRequest().withHeaders(sampleCorrelationIdHeader),
              ec
            )
          )
        }

        verify(underTest.auditHelper,
          times(1)).auditIfApiFailure(any(), any(), any(), any(), matches("^Error parsing IF response"))(using any())

      }
    }

    "successfully audit and consume invalid EmployeeCountRequest from IF response" in new Setup {

      Mockito.reset(underTest.auditHelper)

      val jsonRequest: String = Json.prettyPrint(Json.toJson(employeeCountRequest))
      val jsonResponse: String =
        """{
          |  "failures": [
          |    {
          |      "code": "INVALID_PAYLOAD",
          |      "reason": "Submission has not passed validation. Invalid payload."
          |    }
          |  ]
          |}""".stripMargin

      stubFor(
        post(urlPathMatching(s"/organisations/employers/employee/counts"))
          .withRequestBody(new EqualToJsonPattern(jsonRequest, true, true))
          .withHeader(HeaderNames.authorisation, equalTo(s"Bearer $integrationFrameworkAuthorizationToken"))
          .withHeader("Environment", equalTo(integrationFrameworkEnvironment))
          .withHeader("CorrelationId", equalTo(sampleCorrelationId))
          .willReturn(aResponse().withStatus(400).withBody(jsonResponse)))

      intercept[BadRequestException] {
        await(
          underTest.getEmployeeCount(UUID.randomUUID().toString, utr, employeeCountRequest, None)(
            using hc,
            FakeRequest().withHeaders(sampleCorrelationIdHeader),
            ec
          )
        )
      }

      verify(underTest.auditHelper,
        times(1)).auditIfApiFailure(any(), any(), any(), any(), contains("INVALID_PAYLOAD"))(using any())
    }
  }
}
