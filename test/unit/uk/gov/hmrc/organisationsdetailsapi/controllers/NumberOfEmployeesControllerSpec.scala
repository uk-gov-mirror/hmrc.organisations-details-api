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
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.PlayBodyParsers
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.organisationsdetailsapi.audit.AuditHelper
import uk.gov.hmrc.organisationsdetailsapi.controllers.NumberOfEmployeesController
import uk.gov.hmrc.organisationsdetailsapi.domain.numberofemployees.{NumberOfEmployeeCounts, NumberOfEmployeesRequest, NumberOfEmployeesResponse, PayeReference => RequestPayeReference}
import uk.gov.hmrc.organisationsdetailsapi.services.{NumberOfEmployeesService, ScopesService}
import utils.TestSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class NumberOfEmployeesControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with TestSupport
    with BeforeAndAfterEach {

  implicit val sys: ActorSystem = ActorSystem("MyTest")

  private val bodyParsers = PlayBodyParsers()

  private val sampleCorrelationId = "188e9400-b636-4a3b-80ba-230a8c72b92a"
  private val sampleCorrelationIdHeader = "CorrelationId" -> sampleCorrelationId

  private val sampleMatchId = "32696d72-6216-475f-b213-ba76921cf459"
  private val sampleMatchIdUUID = UUID.fromString(sampleMatchId)

  private val sampleRequest = NumberOfEmployeesRequest(
    "2019-10-01",
    "2020-04-05",
    Seq(
      RequestPayeReference("456", "RT882d"),
      RequestPayeReference("123", "AB888666")
    )
  )

  private val sampleRequestAsJson = Json.toJson(sampleRequest)

  private val fakeRequest = FakeRequest("GET", "/")
    .withHeaders(
      sampleCorrelationIdHeader,
      HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json",
      HeaderNames.CONTENT_TYPE -> "application/json"
    )

  private val fakeRequestNoCorrelationHeader = FakeRequest("GET", "/")
    .withHeaders(
      HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json",
      HeaderNames.CONTENT_TYPE -> "application/json"
    )

  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuditHelper = mock[AuditHelper]
  private val mockScopesService = mock[ScopesService]
  private val mockNumberOfEmployeesService = mock[NumberOfEmployeesService]

  private val controller = new NumberOfEmployeesController(mockAuthConnector, Helpers.stubControllerComponents(),
    mockNumberOfEmployeesService, mockAuditHelper, mockScopesService, bodyParsers)

  override def beforeEach(): Unit = {
    reset(mockAuditHelper)
  }

  "Number of employees Controller" should {
    "return data when called successfully with a valid request" in {

      when(mockScopesService.getEndPointScopes("number-of-employees")).thenReturn(Seq("test-scope"))

      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))

      when(mockNumberOfEmployeesService.get(refEq(sampleMatchIdUUID), eqTo(sampleRequest), eqTo(Set("test-scope")))(using any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(
          NumberOfEmployeesResponse(
            Some("123/RT882d"),
            Some(Seq(
              NumberOfEmployeeCounts(Some(1234), Some("2019-10"))
            ))
          )
        ))))


      val response = await(controller.numberOfEmployees(sampleMatchIdUUID)(fakeRequest.withBody(sampleRequestAsJson)))

      status(response) shouldBe OK
      jsonBodyOf(response) shouldBe Json.parse(
        """
          |{
          |    "_links": {
          |        "self": {
          |            "href": "/organisations/details/number-of-employees?matchId=32696d72-6216-475f-b213-ba76921cf459"
          |        }
          |    },
          |    "employeeCounts": [
          |        {
          |            "payeReference": "123/RT882d",
          |            "counts": [
          |                {
          |                    "numberOfEmployees": 1234,
          |                    "dateOfCount": "2019-10"
          |                }
          |            ]
          |        }
          |    ]
          |}
          |""".stripMargin)


      verify(mockAuditHelper, times(1)).auditNumberOfEmployeesApiResponse(
        any(), any(), any(), any(), any(), any())(using any())

    }

    "fail when correlationId is not provided" in {
      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))
      when(mockScopesService.getEndPointScopes("number-of-employees")).thenReturn(Seq("test-scope"))

      val response = await(controller.numberOfEmployees(sampleMatchIdUUID)(fakeRequestNoCorrelationHeader.withBody(sampleRequestAsJson)))

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

    "fail when correlationId is malformed" in {
      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))
      when(mockScopesService.getEndPointScopes("number-of-employees")).thenReturn(Seq("test-scope"))


      val response = await(controller.numberOfEmployees(sampleMatchIdUUID)
      (fakeRequestNoCorrelationHeader.withBody(sampleRequestAsJson).withHeaders("CorrelationId" -> "Not a valid correlationId")))

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
  }
}
