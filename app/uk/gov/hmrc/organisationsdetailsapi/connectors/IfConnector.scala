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

package uk.gov.hmrc.organisationsdetailsapi.connectors

import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.organisationsdetailsapi.audit.AuditHelper
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.CorporationTaxReturnDetails._
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.EmployeeCountRequest._
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.IfVatReturnsDetailsResponse._
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework.SelfAssessmentReturnDetail._
import uk.gov.hmrc.organisationsdetailsapi.domain.integrationframework._
import uk.gov.hmrc.organisationsdetailsapi.play.RequestHeaderUtils.validateCorrelationId
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IfConnector @Inject() (
                              servicesConfig: ServicesConfig,
                              http: HttpClientV2,
                              val auditHelper: AuditHelper
) {

  private val logger = Logger(classOf[IfConnector].getName)
  private val baseUrl = servicesConfig.baseUrl("integration-framework")

  private val integrationFrameworkBearerToken =
    servicesConfig.getString(
      "microservice.services.integration-framework.authorization-token"
    )

  private val integrationFrameworkEnvironment = servicesConfig.getString(
    "microservice.services.integration-framework.environment"
  )

  def getCtReturnDetails(matchId: String, utr: String, filter: Option[String])(implicit
    hc: HeaderCarrier,
    request: RequestHeader,
    ec: ExecutionContext
  ): Future[CorporationTaxReturnDetailsResponse] = {

    val corporationTaxUrl =
      s"$baseUrl/organisations/corporation-tax/$utr/return/details${filter.map(f => s"?fields=$f").getOrElse("")}"

    call[CorporationTaxReturnDetailsResponse](corporationTaxUrl, matchId)
  }

  def getVatReturnDetails(matchId: String, vrn: String, appDate: String, filter: Option[String])(implicit
    hc: HeaderCarrier,
    request: RequestHeader,
    ec: ExecutionContext
  ): Future[IfVatReturnsDetailsResponse] = {

    val vatTaxUrl =
      s"$baseUrl/organisations/vat/$vrn/returns-details?appDate=$appDate${filter.map(f => s"&fields=$f").getOrElse("")}"

    call[IfVatReturnsDetailsResponse](vatTaxUrl, matchId)
  }

  def getSaReturnDetails(matchId: String, utr: String, filter: Option[String])(implicit
    hc: HeaderCarrier,
    request: RequestHeader,
    ec: ExecutionContext
  ): Future[SelfAssessmentReturnDetailResponse] = {

    val detailsUrl =
      s"$baseUrl/organisations/self-assessment/$utr/return/details${filter.map(f => s"?fields=$f").getOrElse("")}"

    call[SelfAssessmentReturnDetailResponse](detailsUrl, matchId)
  }

  def getEmployeeCount(matchId: String, utr: String, body: EmployeeCountRequest, filter: Option[String])(implicit
                                                                                                         hc: HeaderCarrier,
                                                                                                         request: RequestHeader,
                                                                                                         ec: ExecutionContext
  ): Future[EmployeeCountResponse] = {

    val detailsUrl =
      s"$baseUrl/organisations/employers/employee/counts${filter.map(f => s"?fields=$f").getOrElse("")}"

    post[EmployeeCountRequest, EmployeeCountResponse](detailsUrl, matchId, body)
  }

  private def extractCorrelationId(requestHeader: RequestHeader) = validateCorrelationId(requestHeader).toString

  private def setHeaders(requestHeader: RequestHeader): Seq[(String, String)] = Seq(
    HeaderNames.authorisation -> s"Bearer $integrationFrameworkBearerToken",
    "Environment"             -> integrationFrameworkEnvironment,
    "CorrelationId"           -> extractCorrelationId(requestHeader)
  )

  private def call[T](url: String, matchId: String)(implicit
    rds: HttpReads[T],
    hc: HeaderCarrier,
    request: RequestHeader,
    ec: ExecutionContext
  ) =
    recover(
      http.get(url"$url")
        .transform(_.addHttpHeaders(setHeaders(request)*))
        .execute[T]
          map { response =>
          auditHelper.auditIfApiResponse(extractCorrelationId(request), matchId, request, url, response.toString)
          response
        },
      extractCorrelationId(request),
      matchId,
      request,
      url
    )

  private def post[I, O](url: String, matchId: String, body: I)(implicit
                                                                wts: Writes[I],
                                                                reads: HttpReads[O],
                                                                hc: HeaderCarrier,
                                                                request: RequestHeader,
                                                                ec: ExecutionContext
  ) =
    recover(
      http.post(url"$url")
        .transform(_.addHttpHeaders(setHeaders(request)*))
        .withBody(Json.toJson(body)).execute[O]
        map { response =>
        auditHelper.auditIfApiResponse(extractCorrelationId(request), matchId, request, url, response.toString)
        response
      },
      extractCorrelationId(request),
      matchId,
      request,
      url
    )

  private def recover[A](
    x: Future[A],
    correlationId: String,
    matchId: String,
    request: RequestHeader,
    requestUrl: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] = x.recoverWith {
    case validationError: JsValidationException =>
      logger.warn("Integration Framework JsValidationException encountered")
      auditHelper.auditIfApiFailure(
        correlationId,
        matchId,
        request,
        requestUrl,
        s"Error parsing IF response: ${validationError.errors}"
      )
      Future.failed(new InternalServerException("Something went wrong."))

    case UpstreamErrorResponse.Upstream5xxResponse(m) =>
      logger.warn(s"Integration Framework Upstream5xxResponse encountered: ${m.statusCode}")
      auditHelper.auditIfApiFailure(correlationId, matchId, request, requestUrl, s"Internal Server error: ${m.message}")
      Future.failed(new InternalServerException("Something went wrong."))

    case UpstreamErrorResponse(msg, 400, _, _) if requestUrl.contains("/vat") && msg.contains("INVALID_DATE") =>
      logger.warn(s"Integration Framework returned invalid appDate error")
      val invalidAppDate = request.getQueryString("appDate").mkString
      auditHelper.auditIfApiFailure(
        correlationId,
        matchId,
        request,
        requestUrl,
        s"Invalid appDate: $invalidAppDate. $msg"
      )
      Future.failed(new BadRequestException(s"Invalid appDate: $invalidAppDate"))

    case UpstreamErrorResponse(msg, 400, _, _) =>
      logger.warn("Bad Request")
      auditHelper.auditIfApiFailure(
        correlationId,
        matchId,
        request,
        requestUrl,
        s"Bad Request. $msg"
      )
      Future.failed(new BadRequestException("Bad Request"))

    case UpstreamErrorResponse(msg, 429, _, _) =>
      logger.warn(s"IF Rate limited: $msg")
      auditHelper.auditIfApiFailure(correlationId, matchId, request, requestUrl, s"IF Rate limited: $msg")
      Future.failed(new TooManyRequestException(msg))

    case UpstreamErrorResponse(msg, 404, _, _) =>
      auditHelper.auditIfApiFailure(correlationId, matchId, request, requestUrl, msg)
      if msg.contains("NO_DATA_FOUND") || msg.contains("NO_VAT_RETURNS_DETAIL_FOUND") then {
        noDataFound(requestUrl)
      } else {
        logger.warn(s"Integration Framework Upstream4xxResponse encountered: 404")
        Future.failed(new InternalServerException("Something went wrong."))
      }

    case UpstreamErrorResponse(msg, code, _, _) =>
      logger.warn(s"Integration Framework Upstream4xxResponse encountered: $code")
      auditHelper.auditIfApiFailure(correlationId, matchId, request, requestUrl, msg)
      Future.failed(new InternalServerException("Something went wrong."))

    case e: Exception =>
      logger.error(s"Integration Framework Exception encountered", e)
      auditHelper.auditIfApiFailure(correlationId, matchId, request, requestUrl, e.getMessage)
      Future.failed(new InternalServerException("Something went wrong."))
  }

  private def noDataFound[A](url: String): Future[A] = {
    lazy val emptyEmployeeCountResponse = EmployeeCountResponse(None, None, Some(Seq()))
    lazy val emptyCtReturn = CorporationTaxReturnDetailsResponse(None, None, None, Some(Seq()))
    lazy val emptySaReturn = SelfAssessmentReturnDetailResponse(None, None, None, None, Some(Seq()))

    if url.contains("counts") then
      Future.successful(emptyEmployeeCountResponse.asInstanceOf[A])
    else if url.contains("corporation-tax") then
      Future.successful(emptyCtReturn.asInstanceOf[A])
    else if url.contains("self-assessment") then
      Future.successful(emptySaReturn.asInstanceOf[A])
    else if url.contains("vat") then
      Future.failed(new NotFoundException("VAT details could not be found"))
    else
      Future.failed(new InternalServerException("Something went wrong."))
  }
}
