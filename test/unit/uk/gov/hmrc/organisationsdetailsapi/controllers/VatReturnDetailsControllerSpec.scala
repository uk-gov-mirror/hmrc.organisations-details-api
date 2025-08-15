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

package unit.uk.gov.hmrc.organisationsdetailsapi.controllers

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.{any, refEq, eq => eqTo}
import org.mockito.BDDMockito.`given`
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, TOO_MANY_REQUESTS, UNAUTHORIZED}
import play.api.libs.json.{JsObject, Json}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, Enrolments, InsufficientEnrolments}
import uk.gov.hmrc.http.{InternalServerException, TooManyRequestException}
import uk.gov.hmrc.organisationsdetailsapi.audit.AuditHelper
import uk.gov.hmrc.organisationsdetailsapi.controllers.VatReturnDetailsController
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.IfVatPeriod
import uk.gov.hmrc.organisationsdetailsapi.domain.vat.VatReturnsDetailsResponse
import uk.gov.hmrc.organisationsdetailsapi.services.{ScopesService, VatReturnDetailsService}
import utils.TestSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed

class VatReturnDetailsControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with TestSupport
    with BeforeAndAfterEach {
  implicit val sys: ActorSystem = ActorSystem("MyTest")

  private val sampleCorrelationId = "188e9400-b636-4a3b-80ba-230a8c72b92a"
  private val sampleCorrelationIdHeader = "CorrelationId" -> sampleCorrelationId

  private val sampleMatchId = "32696d72-6216-475f-b213-ba76921cf459"
  private val sampleMatchIdUUID = UUID.fromString(sampleMatchId)

  private val fakeRequest = FakeRequest("GET", "/").withHeaders(sampleCorrelationIdHeader)

  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuditHelper = mock[AuditHelper]
  private val mockScopesService = mock[ScopesService]

  private val mockVatReturnDetailsService = mock[VatReturnDetailsService]
  private val vrn = "1234567890"
  private val appDate = "20160425"
  private val extractDate = "2023-04-10"

  private val controller = new VatReturnDetailsController(mockAuthConnector, Helpers.stubControllerComponents(),
    mockVatReturnDetailsService, mockAuditHelper, mockScopesService)

  private val sampleResponse = VatReturnsDetailsResponse(
    vrn = Some(vrn),
    appDate = Some(appDate),
    extractDate = Some(extractDate),
    vatPeriods = Some(Seq(
      IfVatPeriod(
        periodKey = Some("23AG"),
        billingPeriodFromDate = Some("2023-08-30"),
        billingPeriodToDate = Some("2023-08-30"),
        numDaysAssessed = Some(30),
        box6Total = Some(6542),
        returnType = Some("Regular Return"),
        source = Some("ADR(ETMP)")
      )
    )
    )
  )

  override def beforeEach(): Unit = {
    reset(mockAuditHelper)
  }

  "VatReturnsDetailsController" should {

    "return data when called successfully with a valid request" in {

      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))

      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))

      when(mockVatReturnDetailsService.get(refEq(sampleMatchIdUUID), eqTo(appDate), eqTo(Set("test-scope")))(using any(), any(), any()))
        .thenReturn(Future.successful(sampleResponse))

      val result = await(controller.vat(sampleMatchIdUUID, appDate)(fakeRequest))

      verify(mockAuditHelper, times(1)).auditApiResponse(
        any(), any(), any(), any(), any(), any())(using any())

      jsonBodyOf(result) shouldBe
        Json.obj(
          "_links" -> Json.obj("self" -> Json.obj("href" -> s"/organisations/details/vat?matchId=$sampleMatchIdUUID&appDate=$appDate"))
        ) ++ Json.toJson(sampleResponse).asInstanceOf[JsObject]
    }

    "fail when correlationId is not provided" in {
      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))
      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))

      val response = await(controller.vat(sampleMatchIdUUID, appDate)(FakeRequest()))

      verify(mockAuditHelper, times(1)).auditApiFailure(
        any(), any(), any(), any(), any())(using any())

      status(response) shouldBe BAD_REQUEST
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |  "code": "INVALID_REQUEST",
          |  "message": "CorrelationId is required"
          |}
          |""".stripMargin
      )
    }

    "fail when correlationId is not malformed" in {
      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))
      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))


      val response = await(controller.vat(sampleMatchIdUUID, appDate)(FakeRequest().withHeaders("CorrelationId" -> "Not a valid correlationId")))

      verify(mockAuditHelper, times(1)).auditApiFailure(
        any(), any(), any(), any(), any())(using any())

      status(response) shouldBe BAD_REQUEST
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |  "code": "INVALID_REQUEST",
          |  "message": "Malformed CorrelationId"
          |}
          |""".stripMargin
      )
    }

    "fail when insufficient enrolments" in {
      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))
      `given`(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .willReturn(failed(InsufficientEnrolments()))


      val response = await(controller.vat(sampleMatchIdUUID, appDate)(FakeRequest()))

      status(response) shouldBe UNAUTHORIZED
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |  "code": "UNAUTHORIZED",
          |  "message": "Insufficient Enrolments"
          |}
          |""".stripMargin
      )
    }

    "return too many requests error" in {
      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))

      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))

      when(mockVatReturnDetailsService.get(refEq(sampleMatchIdUUID), eqTo(appDate), eqTo(Set("test-scope")))(using any(), any(), any()))
        .thenReturn(failed(new TooManyRequestException("error")))

      val response = await(controller.vat(sampleMatchIdUUID, appDate)(fakeRequest))

      status(response) shouldBe TOO_MANY_REQUESTS
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |  "code": "TOO_MANY_REQUESTS",
          |  "message": "Rate limit exceeded"
          |}
          |""".stripMargin
      )

    }

    "return internal server exception error" in {
      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))

      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))

      when(mockVatReturnDetailsService.get(refEq(sampleMatchIdUUID), eqTo(appDate), eqTo(Set("test-scope")))(using any(), any(), any()))
        .thenReturn(failed(new InternalServerException("error")))

      val response = await(controller.vat(sampleMatchIdUUID, appDate)(fakeRequest))

      status(response) shouldBe INTERNAL_SERVER_ERROR
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |  "code": "INTERNAL_SERVER_ERROR",
          |  "message": "Something went wrong."
          |}
          |""".stripMargin
      )

    }

    "return invalid request when IllegalArgumentException is invoked" in {
      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))

      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))

      when(mockVatReturnDetailsService.get(refEq(sampleMatchIdUUID), eqTo(appDate), eqTo(Set("test-scope")))(using any(), any(), any()))
        .thenReturn(failed(new IllegalArgumentException("error")))

      val response = await(controller.vat(sampleMatchIdUUID, appDate)(fakeRequest))

      status(response) shouldBe BAD_REQUEST
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |  "code": "INVALID_REQUEST",
          |  "message": "error"
          |}
          |""".stripMargin
      )
    }

    "return internal server error when fallback Exception is invoked" in {
      when(mockScopesService.getEndPointScopes("vat")).thenReturn(Seq("test-scope"))

      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))

      when(mockVatReturnDetailsService.get(refEq(sampleMatchIdUUID), eqTo(appDate), eqTo(Set("test-scope")))(using any(), any(), any()))
        .thenReturn(failed(new Exception("error")))

      val response = await(controller.vat(sampleMatchIdUUID, appDate)(fakeRequest))

      status(response) shouldBe INTERNAL_SERVER_ERROR
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |  "code": "INTERNAL_SERVER_ERROR",
          |  "message": "Something went wrong."
          |}
          |""".stripMargin
      )
    }
  }
}
