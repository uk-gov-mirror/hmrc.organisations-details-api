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
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.organisationsdetailsapi.audit.AuditHelper
import uk.gov.hmrc.organisationsdetailsapi.controllers.SelfAssessmentController
import uk.gov.hmrc.organisationsdetailsapi.domain.selfassessment.{SelfAssessmentResponse, SelfAssessmentReturn}
import uk.gov.hmrc.organisationsdetailsapi.services.{ScopesService, SelfAssessmentService}
import utils.TestSupport

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SelfAssessmentControllerSpec
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

  private val mockSelfAssessmentService = mock[SelfAssessmentService]

  private val controller = new SelfAssessmentController(mockAuthConnector, Helpers.stubControllerComponents(),
    mockSelfAssessmentService, mockAuditHelper, mockScopesService)

  private val sampleResponse = SelfAssessmentResponse(
    selfAssessmentStartDate = Some(LocalDate.of(2020, 1, 1)),
    taxSolvencyStatus = Some("I"),
    taxReturns = Some(Seq(SelfAssessmentReturn(
      totalBusinessSalesTurnover = Some(50000),
      taxYear = Some("2020")
    )))
  )

  override def beforeEach(): Unit = {
    reset(mockAuditHelper)
  }

  "SelfAssessmentController" should {

    "return data when called successfully with a valid request" in {
      when(mockScopesService.getEndPointScopes("self-assessment")).thenReturn(Seq("test-scope"))

      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))

      when(mockSelfAssessmentService.get(refEq(sampleMatchIdUUID), eqTo("self-assessment"), eqTo(Set("test-scope")))(using any(), any(), any()))
        .thenReturn(Future.successful(sampleResponse))

      val result = await(controller.selfAssessment(sampleMatchIdUUID)(fakeRequest))

      verify(mockAuditHelper, times(1)).auditApiResponse(
        any(), any(), any(), any(), any(), any())(using any())

      jsonBodyOf(result) shouldBe
        Json.parse(
          s"""{
             |"selfAssessmentStartDate":"2020-01-01",
             |"taxSolvencyStatus":"I",
             |"_links":{
             |  "self":{
             |    "href":"/organisations/details/self-assessment?matchId=32696d72-6216-475f-b213-ba76921cf459"
             |  }
             |},
             |"taxReturns":[
             |  {
             |    "totalBusinessSalesTurnover":50000,
             |    "taxYear":"2020"
             |  }]
             |}""".stripMargin
        )

    }

    "fail when correlationId is not provided" in {
      when(mockAuthConnector.authorise(eqTo(Enrolment("test-scope")), refEq(Retrievals.allEnrolments))(using any(), any()))
        .thenReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))
      when(mockScopesService.getEndPointScopes("self-assessment")).thenReturn(Seq("test-scope"))

      val response = await(controller.selfAssessment(sampleMatchIdUUID)(FakeRequest()))

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
      when(mockScopesService.getEndPointScopes("self-assessment")).thenReturn(Seq("test-scope"))

      val response = await(controller.selfAssessment(sampleMatchIdUUID)(FakeRequest().withHeaders("CorrelationId" -> "Not a valid correlationId")))

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