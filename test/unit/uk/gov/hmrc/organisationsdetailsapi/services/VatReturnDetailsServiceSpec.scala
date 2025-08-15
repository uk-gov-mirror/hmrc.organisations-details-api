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

package unit.uk.gov.hmrc.organisationsdetailsapi.services


import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import play.api.libs.json.Format
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.organisationsdetailsapi.cache.CacheRepositoryConfiguration
import uk.gov.hmrc.organisationsdetailsapi.connectors.{IfConnector, OrganisationsMatchingConnector}
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.{IfVatPeriod, IfVatReturnsDetailsResponse}
import uk.gov.hmrc.organisationsdetailsapi.domain.matching.OrganisationVatMatch
import uk.gov.hmrc.organisationsdetailsapi.domain.vat.VatReturnsDetailsResponse
import uk.gov.hmrc.organisationsdetailsapi.services._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class VatReturnDetailsServiceSpec extends AnyWordSpec with Matchers {

  private val stubbedCache = new CacheService(null, new CacheRepositoryConfiguration(Configuration())) {
    override def get[T: Format](cacheId: CacheIdBase, fallbackFunction: => Future[T]): Future[T] = {
      fallbackFunction
    }
  }

  trait Setup {
    val vrn = "1234567890"
    val matchId = "9ff2e348-ee49-4e7e-8b73-17d02ff962a2"
    val matchIdUUID: UUID = UUID.fromString(matchId)

    val mockScopesHelper: ScopesHelper = mock[ScopesHelper]
    val mockScopesService: ScopesService = mock[ScopesService]
    val mockIfConnector: IfConnector = mock[IfConnector]
    val mockOrganisationsMatchingConnector: OrganisationsMatchingConnector = mock[OrganisationsMatchingConnector]

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = FakeRequest()

    val appDate = "20210203"
    val extractionDate = "2023-04-10"

    val vatReturnDetailsService: VatReturnDetailsService =
      new VatReturnDetailsService(
        mockScopesHelper,
        mockScopesService,
        stubbedCache,
        mockIfConnector,
        mockOrganisationsMatchingConnector,
        42
      )
  }

  "VatReturnDetailsService" should {
    "get" should {

      "returns a valid payload when given a valid matchId" in new Setup {

        val endpoint = "vat"
        val scopes: Seq[String] = Seq("SomeScope")

        when(mockOrganisationsMatchingConnector.resolveVat(matchIdUUID))
          .thenReturn(Future.successful(OrganisationVatMatch(matchIdUUID, vrn)))

        when(mockScopesHelper.getQueryStringFor(scopes, endpoint))
          .thenReturn("ABC")

        when(mockScopesService.getValidFieldsForCacheKey(scopes.toList, Seq(endpoint)))
          .thenReturn("DEF")

        when(mockIfConnector.getVatReturnDetails(matchId, vrn, appDate, Some("ABC")))
          .thenReturn(Future.successful(IfVatReturnsDetailsResponse(
            Some(vrn),
            Some(appDate),
            Some(extractionDate),
            Some(Seq(
              IfVatPeriod(Some("23AG"), Some("2023-08-30"), Some("2023-08-30"), Some(30), Some(6542), Some("Regular Return"), Some("ADR(ETMP)")
              )
            )
            )
          )
          )
          )

        val response: VatReturnsDetailsResponse = Await.result(vatReturnDetailsService.get(matchIdUUID, appDate, scopes), 10 seconds)

        response.vrn.get shouldBe vrn
        response.vatPeriods.get.length shouldBe 1
      }

      "Return a failed future if IF or cache throws exception" in new Setup {
        val endpoint = "vat"
        val scopes: Seq[String] = Seq("SomeScope")

        when(mockOrganisationsMatchingConnector.resolveVat(matchIdUUID))
          .thenReturn(Future.successful(OrganisationVatMatch(matchIdUUID, vrn)))

        when(mockScopesHelper.getQueryStringFor(scopes, endpoint))
          .thenReturn("ABC")

        when(mockScopesService.getValidFieldsForCacheKey(scopes.toList, Seq(endpoint)))
          .thenReturn("DEF")

        when(mockIfConnector.getVatReturnDetails(matchId, vrn, appDate, Some("ABC")))
          .thenReturn(Future.failed(new Exception()))

        assertThrows[Exception] {
          Await.result(vatReturnDetailsService.get(matchIdUUID, appDate, scopes), 10 seconds)
        }
      }

      "propagates not found when match id can not be found" in new Setup {

        val scopes: Seq[String] = Seq("SomeScope")

        when(mockOrganisationsMatchingConnector.resolveVat(matchIdUUID))
          .thenReturn(Future.failed(new NotFoundException("NOT_FOUND")))

        assertThrows[NotFoundException] {
          Await.result(vatReturnDetailsService.get(matchIdUUID, appDate, scopes), 10 seconds)
        }
      }

      "retries once if IF returns an error" in new Setup {

        val endpoint = "vat"
        val scopes: Seq[String] = Seq("SomeScope")

        when(mockOrganisationsMatchingConnector.resolveVat(matchIdUUID))
          .thenReturn(Future.successful(OrganisationVatMatch(matchIdUUID, vrn)))

        when(mockScopesHelper.getQueryStringFor(scopes, endpoint))
          .thenReturn("ABC")

        when(mockScopesService.getValidFieldsForCacheKey(scopes.toList, Seq(endpoint)))
          .thenReturn("DEF")

        when(mockIfConnector.getVatReturnDetails(matchId, vrn, appDate, Some("ABC")))
          .thenReturn(Future.failed(UpstreamErrorResponse("""Whoops!""", 503, 503)))
          .thenReturn(Future.successful(IfVatReturnsDetailsResponse(
            Some(vrn),
            Some(appDate),
            Some(extractionDate),
            Some(Seq(
              IfVatPeriod(Some("23AG"), Some("2023-08-30"), Some("2023-08-30"), Some(30), Some(6542), Some("Regular Return"), Some("ADR(ETMP)")
              )
            )
            )
          )
          )
          )

        val response: VatReturnsDetailsResponse = Await.result(vatReturnDetailsService.get(matchIdUUID, appDate, scopes), 10 seconds)

        verify(mockIfConnector, times(2))
          .getVatReturnDetails(any(), any(), any(), any())(using any(), any(), any())

        response.vrn.get shouldBe vrn
        response.vatPeriods.get.length shouldBe 1
      }
    }
  }
}


