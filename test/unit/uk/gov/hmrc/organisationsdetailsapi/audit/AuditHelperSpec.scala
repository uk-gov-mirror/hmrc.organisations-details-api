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

package unit.uk.gov.hmrc.organisationsdetailsapi.audit

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.organisationsdetailsapi.audit.AuditHelper
import uk.gov.hmrc.organisationsdetailsapi.audit.models._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class AuditHelperSpec extends AsyncWordSpec with Matchers with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val auditConnector: AuditConnector = mock[AuditConnector]
  val auditHelper = new AuditHelper(auditConnector)
  val correlationId = "test"
  val matchId = "80a6bb14-d888-436e-a541-4000674c60bb"
  val applicationId = "80a6bb14-d888-436e-a541-4000674c60bb"
  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders("X-Application-ID" -> applicationId)
  val endpoint = "/test"
  val ifResponse = "bar"
  val crn = "12345678"
  val scopes = "test"
  val ifUrl = s"host/organisations/corporation-tax/$crn/company/details"

  "auditAuthScopes" in {

    Mockito.reset(auditConnector)

    val captor = ArgumentCaptor.forClass(classOf[ScopesAuditEventModel])

    auditHelper.auditAuthScopes(matchId, scopes, request)

    verify(auditConnector, times(1)).sendExplicitAudit(eqTo("AuthScopesAuditEvent"),
      captor.capture())(using any(), any(), any())

    val capturedEvent = captor.getValue.asInstanceOf[ScopesAuditEventModel]
    capturedEvent.apiVersion shouldEqual "1.0"
    capturedEvent.matchId shouldEqual matchId
    capturedEvent.scopes shouldBe scopes
    capturedEvent.applicationId shouldBe applicationId
  }

  "auditApiFailure" in {

    Mockito.reset(auditConnector)

    val msg = "Something went wrong"

    val captor = ArgumentCaptor.forClass(classOf[ApiFailureResponseEventModel])

    auditHelper.auditApiFailure(Some(correlationId), matchId, request, "/test", msg)

    verify(auditConnector, times(1)).sendExplicitAudit(eqTo("ApiFailureEvent"),
      captor.capture())(using any(), any(), any())

    val capturedEvent = captor.getValue.asInstanceOf[ApiFailureResponseEventModel]
    capturedEvent.matchId shouldEqual matchId
    capturedEvent.correlationId shouldEqual Some(correlationId)
    capturedEvent.requestUrl shouldEqual endpoint
    capturedEvent.response shouldEqual msg
    capturedEvent.applicationId shouldBe applicationId
  }

  "auditIfApiResponse" in {

    Mockito.reset(auditConnector)

    val captor = ArgumentCaptor.forClass(classOf[IfApiResponseEventModel])

    auditHelper.auditIfApiResponse(correlationId, matchId, request, ifUrl, ifResponse)

    verify(auditConnector, times(1)).sendExplicitAudit(eqTo("IntegrationFrameworkApiResponseEvent"),
      captor.capture())(using any(), any(), any())

    val capturedEvent = captor.getValue.asInstanceOf[IfApiResponseEventModel]
    capturedEvent.matchId shouldEqual matchId
    capturedEvent.correlationId shouldEqual correlationId
    capturedEvent.requestUrl shouldBe ifUrl
    capturedEvent.ifResponse shouldBe ifResponse
    capturedEvent.applicationId shouldBe applicationId
  }

  "auditIfApiFailure" in {

    Mockito.reset(auditConnector)

    val msg = "Something went wrong"

    val captor = ArgumentCaptor.forClass(classOf[ApiFailureResponseEventModel])

    auditHelper.auditIfApiFailure(correlationId, matchId, request, ifUrl, msg)

    verify(auditConnector, times(1)).sendExplicitAudit(eqTo("IntegrationFrameworkApiFailureEvent"),
      captor.capture())(using any(), any(), any())

    val capturedEvent = captor.getValue.asInstanceOf[ApiFailureResponseEventModel]
    capturedEvent.matchId shouldEqual matchId
    capturedEvent.correlationId shouldEqual Some(correlationId)
    capturedEvent.requestUrl shouldEqual ifUrl
    capturedEvent.response shouldEqual msg
    capturedEvent.applicationId shouldBe applicationId
  }

  "auditApiResponse" in {
    Mockito.reset(auditConnector)

    val captor = ArgumentCaptor.forClass(classOf[ApiResponseEventModel])

    auditHelper.auditApiResponse(correlationId, matchId, scopes, request, ifUrl)

    verify(auditConnector, times(1)).sendExplicitAudit(eqTo("ApiResponseEvent"),
      captor.capture())(using any(), any(), any())

    val capturedEvent = captor.getValue.asInstanceOf[ApiResponseEventModel]
    capturedEvent.matchId shouldEqual matchId
    capturedEvent.correlationId shouldEqual Some(correlationId)
    capturedEvent.returnLinks shouldEqual ifUrl
    capturedEvent.applicationId shouldBe applicationId
  }

  "auditCTTaxResponseEventModel" in {
    Mockito.reset(auditConnector)

    val captor = ArgumentCaptor.forClass(classOf[ApiResponseEventModelWithResponse])

    auditHelper.auditApiResponse(correlationId, matchId, scopes, request, ifUrl)

    verify(auditConnector, times(1)).sendExplicitAudit(eqTo("ApiResponseEvent"),
      captor.capture())(using any(), any(), any())

    val capturedEvent = captor.getValue.asInstanceOf[ApiResponseEventModel]
    capturedEvent.matchId shouldEqual matchId
    capturedEvent.correlationId shouldEqual Some(correlationId)
    capturedEvent.returnLinks shouldEqual ifUrl
    capturedEvent.applicationId shouldBe applicationId
  }

}
